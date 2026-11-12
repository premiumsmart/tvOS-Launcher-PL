package com.techgenius.tvoslauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mod PL: nowe kafelki ekranu głównego (wybór użytkownika z kanwy, 11.11.2026: mecze z wariantu A,
 * filmy z wariantu C, pogoda z wariantu A, wersja przezroczysta).
 * <ul>
 *   <li>mecze: jeden mecz na dużo (herby, godzina albo wynik), pod spodem następny; trwające mecze
 *       mają pierwszeństwo i przełączają się same, gdy jest ich kilka,</li>
 *   <li>filmy: szklany kafelek, kadr z prawej wtapia się w szkło, pięć pierwszych tytułów zmienia się samo,</li>
 *   <li>pogoda: duża temperatura i trzy kolejne dni.</li>
 * </ul>
 * Rozmiar i miejsce kafelków zostają z oryginału (HomeDashboard.buildDashboardViewInternal).
 * Plik źródłowy leży w pl/java/, a pl/naloz.py kopiuje go do src/ przy każdym buildzie.
 */
final class ModPlKafelki {
    private ModPlKafelki() { }

    /** Wyłącznik awaryjny: false przywraca kafelki oryginału bez ruszania łatek. */
    static final boolean NOWE = true;

    /** Wygląd klasyczny do wyboru w ustawieniach i w kreatorze. Schowany 11.11.2026 na prośbę użytkownika. */
    static final boolean KLASYCZNY = false;

    private static final Handler GLOWNY = new Handler(Looper.getMainLooper());
    private static final ExecutorService POBIERANIE = Executors.newFixedThreadPool(2);

    private static final int BIALY = Color.WHITE;
    private static final int JASNY = 0xD1FFFFFF;
    private static final int PRZYGASZONY = 0xBDFFFFFF;
    private static final int ZLOTY = 0xFFFFD84D;
    private static final int ZIELONY = 0xFF86EFAC;
    private static final int CIEN = 0x73000000;

    private static final long CO_ILE_MECZ = 8_000L;
    private static final long CO_ILE_FILM = 12_000L;
    /** Tyle po rozpoczęciu meczu jego wynik zostaje na kafelku (mecz ok. 2 h + 2 h po końcu). */
    private static final long WYNIK_ZOSTAJE = 4 * 3_600_000L + 15 * 60_000L;
    private static final int ILE_FILMOW = 5;

    /** Herby i kadry w pamięci, żeby przełączanie nie dekodowało ich w kółko. */
    private static final Map<String, Bitmap> OBRAZY = new LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) {
            return size() > 14;
        }
    };

    // ------------------------------------------------------------------ wspólne

    private static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    private static TextView tekst(Context c, String s, float rozmiarDp, String waga, int kolor) {
        TextView t = ModPlOkna.tekst(c, s, rozmiarDp, waga, kolor);
        t.setShadowLayer(dp(c, 1), 0, dp(c, 0.5f), CIEN);
        return t;
    }

    private static TextView jednaLinia(TextView t) {
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private static LinearLayout rzad(Context c, boolean pionowy) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(pionowy ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        return l;
    }

    private static GradientDrawable tlo(int kolor, float promien, int grubosc, int ramka) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(kolor);
        g.setCornerRadius(promien);
        if (grubosc > 0) g.setStroke(grubosc, ramka);
        return g;
    }

    private static View kropka(Context c, int kolor, float srednicaDp) {
        View v = new View(c);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(kolor);
        v.setBackground(g);
        int s = dp(c, srednicaDp);
        v.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        return v;
    }

    private static String wielka(String s, Locale loc) {
        return s == null || s.isEmpty() ? "" : s.substring(0, 1).toUpperCase(loc) + s.substring(1);
    }

    private interface Gotowe {
        /** {@code b} jest null, gdy obrazu nie udało się pobrać. */
        void obraz(Bitmap b);
    }

    /** Adresy, których ostatnio nie udało się pobrać (np. drużyna bez herbu), z czasem próby. */
    private static final Map<String, Long> NIEUDANE = new java.util.HashMap<String, Long>();
    private static final long PONOW_PO = 30 * 60_000L;

    private static void obraz(final Context c, final String url, final int szer, final Gotowe gotowe) {
        Bitmap b;
        synchronized (OBRAZY) {
            b = OBRAZY.get(url);
            Long kiedy = NIEUDANE.get(url);
            if (b == null && kiedy != null && System.currentTimeMillis() - kiedy < PONOW_PO) {
                gotowe.obraz(null);
                return;
            }
        }
        if (b != null) {
            gotowe.obraz(b);
            return;
        }
        final Context app = c.getApplicationContext();
        POBIERANIE.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap pobrany = null;
                try {
                    pobrany = Suggestions.image(app, url, "w780", szer);
                } catch (Throwable ignored) {
                    // brak obrazu = zaślepka herbu albo kafelek bez kadru
                }
                synchronized (OBRAZY) {
                    if (pobrany != null) {
                        OBRAZY.put(url, pobrany);
                        NIEUDANE.remove(url);
                    } else {
                        NIEUDANE.put(url, System.currentTimeMillis());
                    }
                }
                final Bitmap wynik = pobrany;
                GLOWNY.post(new Runnable() {
                    @Override
                    public void run() {
                        gotowe.obraz(wynik);
                    }
                });
            }
        });
    }

    /**
     * Wstawia obraz do widoku, o ile widok nadal czeka na ten sam adres (lista mogła się już zmienić).
     * {@code zastepczy} pokazuje się, gdy obrazu nie ma albo nie dał się pobrać (null = puste miejsce).
     */
    private static void wstaw(final Context c, final ImageView widok, final String url, int szer,
                              final boolean przenikanie, final Drawable zastepczy) {
        if (url == null || url.isEmpty()) {
            widok.setTag(null);
            widok.setImageDrawable(zastepczy);
            return;
        }
        if (url.equals(widok.getTag())) return;
        widok.setTag(url);
        boolean wPamieci;
        synchronized (OBRAZY) {
            wPamieci = OBRAZY.containsKey(url);
        }
        // herb innej drużyny nie może zostać na ekranie na czas pobierania; kadr filmu zostaje do podmiany
        if (!wPamieci && !przenikanie) widok.setImageDrawable(zastepczy);
        final boolean[] odRazu = {true};
        obraz(c, url, szer, new Gotowe() {
            @Override
            public void obraz(Bitmap b) {
                if (!url.equals(widok.getTag())) return;
                if (b == null) {
                    widok.setImageDrawable(zastepczy);
                    return;
                }
                widok.setImageBitmap(b);
                if (przenikanie) {
                    widok.setAlpha(odRazu[0] ? 0.55f : 0f);
                    widok.animate().alpha(1f).setDuration(260).start();
                }
            }
        });
        odRazu[0] = false;
    }

    /** Zaślepka herbu: szklane kółko z pierwszą literą nazwy drużyny (gdy LiveScore nie ma herbu). */
    private static final class Zaslepka extends Drawable {
        private final String litera;
        private final Paint wypelnienie = new Paint(Paint.ANTI_ALIAS_FLAG), obrys = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint napis = new Paint(Paint.ANTI_ALIAS_FLAG);

        Zaslepka(Context c, String nazwa) {
            String n = nazwa == null ? "" : nazwa.trim();
            litera = n.isEmpty() ? "" : n.substring(0, 1).toUpperCase(I18n.locale(c));
            wypelnienie.setColor(0x24FFFFFF);
            obrys.setStyle(Paint.Style.STROKE);
            obrys.setStrokeWidth(Math.max(1, dp(c, 0.75f)));
            obrys.setColor(0x52FFFFFF);
            napis.setColor(0xE6FFFFFF);
            napis.setTypeface(Ui.font(c, "semibold"));
            napis.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float r = Math.min(b.width(), b.height()) / 2f * 0.86f;
            float cx = b.exactCenterX(), cy = b.exactCenterY();
            c.drawCircle(cx, cy, r, wypelnienie);
            c.drawCircle(cx, cy, r, obrys);
            napis.setTextSize(r * 0.95f);
            c.drawText(litera, cx, cy - (napis.descent() + napis.ascent()) / 2f, napis);
        }

        @Override
        public void setAlpha(int a) { }

        @Override
        public void setColorFilter(ColorFilter cf) { }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Biała ramka z miękką poświatą na zewnątrz: zaznaczenie w wersji przezroczystej. */
    private static final class Ramka extends Drawable {
        private final float r, grubosc;
        private final Paint obrys = new Paint(Paint.ANTI_ALIAS_FLAG), poswiata = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF pole = new RectF();

        Ramka(float r, float grubosc) {
            this.r = r;
            this.grubosc = grubosc;
            obrys.setStyle(Paint.Style.STROKE);
            obrys.setStrokeWidth(grubosc);
            obrys.setColor(0xD1FFFFFF);
            poswiata.setStyle(Paint.Style.STROKE);
            poswiata.setStrokeWidth(grubosc);
            poswiata.setColor(Color.WHITE);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            for (int i = 6; i >= 1; i--) {
                float o = (i - 0.5f) * grubosc;
                float sila = (7 - i) / 6f;
                poswiata.setAlpha(Math.round(64f * sila * sila));
                pole.set(b.left - o, b.top - o, b.right + o, b.bottom + o);
                c.drawRoundRect(pole, r + o, r + o, poswiata);
            }
            float w = grubosc / 2f;
            pole.set(b.left + w, b.top + w, b.right - w, b.bottom - w);
            c.drawRoundRect(pole, r - w, r - w, obrys);
        }

        @Override
        public void setAlpha(int a) { }

        @Override
        public void setColorFilter(ColorFilter cf) { }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /**
     * Szklany kafelek: rozmyta tapeta launchera (LiquidGlass), a przy zaznaczeniu jaśniejsze szkło,
     * biała ramka z poświatą i lekkie uniesienie.
     */
    private static FrameLayout podstawa(final MainActivity host) {
        final FrameLayout card = new FrameLayout(host) {
            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }
        };
        final float r = dp(host, 15);
        MainActivity.LiquidGlass szklo = new MainActivity.LiquidGlass(
                r, 0x66120E18, host.glassSource, host.screenW, host.screenH, host.unit);
        szklo.lens = dp(host, 6);
        szklo.rimStrength = 0.6f;
        card.setBackground(szklo);

        final View blask = new View(host);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x33FFFFFF, 0x1AFFFFFF});
        g.setCornerRadius(r);
        blask.setBackground(g);
        blask.setAlpha(0f);
        card.addView(blask, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final Ramka ramka = new Ramka(r, dp(host, 2));
        card.setFocusable(true);
        card.setFocusableInTouchMode(false);
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                card.setForeground(hasFocus ? ramka : null);
                blask.animate().alpha(hasFocus ? 1f : 0f).setDuration(150).start();
                v.animate()
                        .scaleX(hasFocus ? 1.035f : 1.0f)
                        .scaleY(hasFocus ? 1.035f : 1.0f)
                        .translationY(hasFocus ? -dp(host, 2) : 0f)
                        .translationZ(hasFocus ? dp(host, 8) : 0f)
                        .setDuration(150)
                        .setInterpolator(MainActivity.EASE)
                        .start();
                v.invalidate();
            }
        });
        return card;
    }

    /** Uruchamia {@code krok} co {@code coIle} ms, póki kafelek jest na ekranie. */
    private static void cyklicznie(final View kafelek, final long coIle, final Runnable krok) {
        final Runnable[] petla = new Runnable[1];
        petla[0] = new Runnable() {
            @Override
            public void run() {
                if (!kafelek.isAttachedToWindow()) return;
                if (kafelek.isShown() && kafelek.getWindowVisibility() == View.VISIBLE) krok.run();
                GLOWNY.postDelayed(this, coIle);
            }
        };
        kafelek.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                GLOWNY.removeCallbacks(petla[0]);
                GLOWNY.postDelayed(petla[0], coIle);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                GLOWNY.removeCallbacks(petla[0]);
            }
        });
        if (kafelek.isAttachedToWindow()) GLOWNY.postDelayed(petla[0], coIle);
    }

    private static TextView etykieta(Context c) {
        TextView t = jednaLinia(tekst(c, "", 8f, "semibold", BIALY));
        t.setLetterSpacing(0.075f);
        return t;
    }

    // ------------------------------------------------------------------ mecze

    /** Co pokazać na kafelku meczów. */
    private static final class Wybor {
        HomeDashboard.MatchItem glowny, nastepny;
        int ileZywo, nrZywo;
        boolean nastepnyZywo;
    }

    private static final Comparator<HomeDashboard.MatchItem> ULUBIONE_PO_CZASIE = new Comparator<HomeDashboard.MatchItem>() {
        @Override
        public int compare(HomeDashboard.MatchItem a, HomeDashboard.MatchItem b) {
            if (a.isFav != b.isFav) return a.isFav ? -1 : 1;
            return Long.compare(a.startEpochMs, b.startEpochMs);
        }
    };

    /**
     * Kolejność: trwający mecz obserwowanej drużyny, inny trwający, świeżo zakończony (ok. 2 h po meczu),
     * najbliższy mecz obserwowanej drużyny, najbliższy z ligi, a gdy nic nie zostało: ostatni wynik.
     */
    private static Wybor wybierz(List<HomeDashboard.MatchItem> lista, int indeks) {
        Wybor w = new Wybor();
        if (lista == null || lista.isEmpty()) return w;
        long teraz = System.currentTimeMillis();
        List<HomeDashboard.MatchItem> zywe = new ArrayList<HomeDashboard.MatchItem>();
        List<HomeDashboard.MatchItem> przyszle = new ArrayList<HomeDashboard.MatchItem>();
        HomeDashboard.MatchItem swiezy = null, ostatni = null;
        for (HomeDashboard.MatchItem m : lista) {
            if (m == null) continue;
            if (m.isLive) {
                zywe.add(m);
            } else if (m.isFinished) {
                if (ostatni == null || m.startEpochMs > ostatni.startEpochMs) ostatni = m;
                long temu = teraz - m.startEpochMs;
                if (m.startEpochMs > 0 && temu >= 0 && temu <= WYNIK_ZOSTAJE
                        && (swiezy == null || (m.isFav && !swiezy.isFav)
                        || (m.isFav == swiezy.isFav && m.startEpochMs > swiezy.startEpochMs))) {
                    swiezy = m;
                }
            } else if (m.startEpochMs <= 0 || m.startEpochMs > teraz - 15 * 60_000L) {
                przyszle.add(m);
            }
        }
        Collections.sort(zywe, ULUBIONE_PO_CZASIE);
        Collections.sort(przyszle, ULUBIONE_PO_CZASIE);
        if (!zywe.isEmpty()) {
            int n = zywe.size();
            int i = ((indeks % n) + n) % n;
            w.glowny = zywe.get(i);
            w.ileZywo = n;
            w.nrZywo = i + 1;
            if (n > 1) {
                w.nastepny = zywe.get((i + 1) % n);
                w.nastepnyZywo = true;
            } else if (!przyszle.isEmpty()) {
                w.nastepny = przyszle.get(0);
            }
        } else if (swiezy != null) {
            w.glowny = swiezy;
            if (!przyszle.isEmpty()) w.nastepny = przyszle.get(0);
        } else if (!przyszle.isEmpty()) {
            w.glowny = przyszle.get(0);
            if (przyszle.size() > 1) w.nastepny = przyszle.get(1);
        } else {
            w.glowny = ostatni;
        }
        return w;
    }

    /** "Dziś", "Jutro" albo "Sob. 14.11". */
    private static String dzien(Context c, long ms) {
        if (ms <= 0) return "";
        Locale loc = I18n.locale(c);
        Calendar dzis = Calendar.getInstance();
        Calendar kiedy = Calendar.getInstance();
        kiedy.setTimeInMillis(ms);
        int roznica = (kiedy.get(Calendar.YEAR) - dzis.get(Calendar.YEAR)) * 400
                + kiedy.get(Calendar.DAY_OF_YEAR) - dzis.get(Calendar.DAY_OF_YEAR);
        if (roznica == 0) return wielka(I18n.t(c, "Today"), loc);
        if (roznica == 1) return wielka(I18n.t(c, "tomorrow"), loc);
        return wielka(new SimpleDateFormat("EEE d.MM", loc).format(new Date(ms)), loc);
    }

    private static String minuta(Context c, HomeDashboard.MatchItem m) {
        String s = m.statusText == null ? "" : m.statusText.trim();
        return s.equalsIgnoreCase("HT") ? I18n.t(c, "Half time") : s;
    }

    private static void druzyna(Context c, TextView t, String nazwa, boolean ulubiona) {
        t.setText(ulubiona ? "★ " + nazwa : nazwa);
        t.setTextColor(ulubiona ? ZLOTY : BIALY);
    }

    static View mecze(final MainActivity host) {
        final FrameLayout card = podstawa(host);
        final Locale loc = I18n.locale(host);
        LinearLayout col = rzad(host, true);
        col.setPadding(dp(host, 12), dp(host, 8), dp(host, 12), dp(host, 7));

        // nagłówek: rozgrywki po lewej, termin albo "NA ŻYWO" po prawej
        LinearLayout head = rzad(host, false);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(kropka(host, BIALY, 5));
        final TextView liga = etykieta(host);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(host, 5);
        lp.rightMargin = dp(host, 6);
        head.addView(liga, lp);
        final TextView termin = jednaLinia(tekst(host, "", 8.5f, "medium", JASNY));
        head.addView(termin);
        final LinearLayout pigulka = rzad(host, false);
        pigulka.setGravity(Gravity.CENTER_VERTICAL);
        pigulka.setPadding(dp(host, 5.5f), 0, dp(host, 5.5f), 0);
        pigulka.setBackground(tlo(0xB8166534, dp(host, 6.5f), Math.max(1, dp(host, 0.5f)), 0xB34ADE80));
        pigulka.addView(kropka(host, 0xFF4ADE80, 4));
        final TextView zywoTv = jednaLinia(ModPlOkna.tekst(host, "", 7.5f, "semibold", 0xFFDCFCE7));
        zywoTv.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams zlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        zlp.leftMargin = dp(host, 3.5f);
        pigulka.addView(zywoTv, zlp);
        pigulka.setVisibility(View.GONE);
        head.addView(pigulka, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(host, 13)));
        col.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(host, 13)));

        // tablica: herb i nazwa, godzina albo wynik, herb i nazwa
        final LinearLayout tablica = rzad(host, false);
        tablica.setGravity(Gravity.CENTER_VERTICAL);
        final ImageView herb1 = new ImageView(host), herb2 = new ImageView(host);
        final TextView nazwa1 = jednaLinia(tekst(host, "", 9.5f, "semibold", BIALY));
        final TextView nazwa2 = jednaLinia(tekst(host, "", 9.5f, "semibold", BIALY));
        final TextView srodek = tekst(host, "", 23f, "display", BIALY);
        srodek.setLetterSpacing(-0.02f);
        srodek.setSingleLine(true);
        for (int strona = 0; strona < 2; strona++) {
            LinearLayout d = rzad(host, true);
            d.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView herb = strona == 0 ? herb1 : herb2;
            herb.setScaleType(ImageView.ScaleType.FIT_CENTER);
            d.addView(herb, new LinearLayout.LayoutParams(dp(host, 28), dp(host, 28)));
            TextView nazwa = strona == 0 ? nazwa1 : nazwa2;
            nazwa.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.topMargin = dp(host, 2.5f);
            d.addView(nazwa, nlp);
            tablica.addView(d, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (strona == 0) {
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                slp.leftMargin = dp(host, 4);
                slp.rightMargin = dp(host, 4);
                tablica.addView(srodek, slp);
            }
        }
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        tlp.topMargin = dp(host, 3);
        col.addView(tablica, tlp);

        // brak meczów
        final LinearLayout brak = rzad(host, true);
        brak.setGravity(Gravity.CENTER);
        final TextView brak1 = tekst(host, "", 11f, "semibold", BIALY);
        brak1.setGravity(Gravity.CENTER);
        brak.addView(brak1);
        TextView brak2 = tekst(host, I18n.t(host, "OK – choose a league and favourite teams"), 8.5f, "regular", PRZYGASZONY);
        brak2.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        b2.topMargin = dp(host, 3);
        brak.addView(brak2, b2);
        brak.setVisibility(View.GONE);
        col.addView(brak, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // stopka: następny mecz
        final LinearLayout stopka = rzad(host, false);
        stopka.setGravity(Gravity.CENTER_VERTICAL);
        final TextView potem = jednaLinia(tekst(host, "", 8.5f, "medium", PRZYGASZONY));
        stopka.addView(potem);
        final TextView gwiazdka = tekst(host, "★", 8.5f, "medium", ZLOTY);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.leftMargin = dp(host, 4);
        stopka.addView(gwiazdka, glp);
        final TextView nastepny = jednaLinia(tekst(host, "", 8.5f, "medium", 0xF2FFFFFF));
        LinearLayout.LayoutParams nlp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nlp2.leftMargin = dp(host, 4);
        stopka.addView(nastepny, nlp2);
        final TextView kiedy = jednaLinia(tekst(host, "", 8.5f, "medium", PRZYGASZONY));
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        klp.leftMargin = dp(host, 6);
        stopka.addView(kiedy, klp);
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(host, 11));
        stlp.topMargin = dp(host, 3);
        col.addView(stopka, stlp);

        card.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final int[] indeks = {0};
        final int[] ileZywo = {0};
        final String[] pokazany = {""};
        final Runnable odswiez = new Runnable() {
            @Override
            public void run() {
                Wybor w = wybierz(HomeDashboard.getCachedMatches(host), indeks[0]);
                ileZywo[0] = w.ileZywo;
                HomeDashboard.MatchItem m = w.glowny;
                if (m == null) {
                    tablica.setVisibility(View.GONE);
                    stopka.setVisibility(View.GONE);
                    brak.setVisibility(View.VISIBLE);
                    pigulka.setVisibility(View.GONE);
                    termin.setVisibility(View.GONE);
                    liga.setText(I18n.t(host, "dash_football").toUpperCase(loc));
                    brak1.setText(I18n.t(host, ModPlMecze.dziala ? "football_no_matches" : "football_loading"));
                    pokazany[0] = "";
                    return;
                }
                brak.setVisibility(View.GONE);
                tablica.setVisibility(View.VISIBLE);
                String nazwaLigi = m.leagueName == null || m.leagueName.isEmpty() ? I18n.t(host, "dash_football") : m.leagueName;
                liga.setText(nazwaLigi.toUpperCase(loc));
                if (m.isLive) {
                    termin.setVisibility(View.GONE);
                    pigulka.setVisibility(View.VISIBLE);
                    String min = minuta(host, m);
                    String napis = w.ileZywo > 1
                            ? I18n.t(host, "Live") + " " + w.nrZywo + "/" + w.ileZywo + (min.isEmpty() ? "" : " · " + min)
                            : ModPlOkna.status(host, m);
                    zywoTv.setText(napis.toUpperCase(loc));
                    srodek.setText(m.homeScore + " : " + m.awayScore);
                } else if (m.isFinished) {
                    pigulka.setVisibility(View.GONE);
                    termin.setVisibility(View.VISIBLE);
                    String d = dzien(host, m.startEpochMs);
                    termin.setText(ModPlOkna.status(host, m) + (d.isEmpty() ? "" : " · " + d));
                    srodek.setText(m.homeScore + " : " + m.awayScore);
                } else {
                    pigulka.setVisibility(View.GONE);
                    termin.setVisibility(View.VISIBLE);
                    String s = m.statusText == null ? "" : m.statusText.trim();
                    boolean bezGodziny = s.equalsIgnoreCase("PST") || s.equalsIgnoreCase("CANC") || m.startEpochMs <= 0;
                    termin.setText(bezGodziny ? ModPlOkna.status(host, m) : dzien(host, m.startEpochMs));
                    srodek.setText(bezGodziny ? "–" : new SimpleDateFormat("HH:mm", loc).format(new Date(m.startEpochMs)));
                }
                druzyna(host, nazwa1, m.homeTeam, ModPlMecze.ulubionaDruzyna(host, m.homeTeam, m.glowna));
                druzyna(host, nazwa2, m.awayTeam, ModPlMecze.ulubionaDruzyna(host, m.awayTeam, m.glowna));
                wstaw(host, herb1, m.homeLogo, dp(host, 32), false, new Zaslepka(host, m.homeTeam));
                wstaw(host, herb2, m.awayLogo, dp(host, 32), false, new Zaslepka(host, m.awayTeam));
                String klucz = m.homeTeam + "|" + m.awayTeam + "|" + m.startEpochMs;
                if (!klucz.equals(pokazany[0]) && !pokazany[0].isEmpty()) {
                    tablica.setAlpha(0.25f);
                    tablica.animate().alpha(1f).setDuration(220).start();
                }
                pokazany[0] = klucz;

                HomeDashboard.MatchItem n = w.nastepny;
                if (n == null) {
                    stopka.setVisibility(View.INVISIBLE);
                    return;
                }
                stopka.setVisibility(View.VISIBLE);
                gwiazdka.setVisibility(n.isFav ? View.VISIBLE : View.GONE);
                nastepny.setText(n.homeTeam + " – " + n.awayTeam);
                if (w.nastepnyZywo) {
                    potem.setText(I18n.t(host, "Also live"));
                    kiedy.setText(ModPlOkna.wynikKafelka(host, n));
                    kiedy.setTextColor(ZIELONY);
                } else {
                    boolean wczesniej = !m.isLive && !m.isFinished && n.startEpochMs > 0 && n.startEpochMs < m.startEpochMs;
                    potem.setText(I18n.t(host, wczesniej ? "Earlier" : "Then"));
                    kiedy.setText(ModPlOkna.terminKafelka(host, n));
                    kiedy.setTextColor(PRZYGASZONY);
                }
            }
        };

        odswiez.run();
        HomeDashboard.fetchFootballMatches(host, false, new HomeDashboard.FootballCallback() {
            @Override
            public void onResult(List<HomeDashboard.MatchItem> matches) {
                odswiez.run();
            }
        });
        ModPlOkna.pilnujMeczow(card, host, odswiez);
        cyklicznie(card, CO_ILE_MECZ, new Runnable() {
            @Override
            public void run() {
                if (ileZywo[0] > 1) {
                    indeks[0]++;
                    odswiez.run();
                }
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ModPlOkna.pokazMecze(host, odswiez);
            }
        });
        return card;
    }

    // ------------------------------------------------------------------ najczęściej oglądane

    /** Kadr, który od lewej krawędzi wtapia się w szkło kafelka (przezroczystość rośnie od zera do pełnej). */
    private static final class KadrZanikajacy extends ImageView {
        private final Paint maska = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int szerMaski = -1;

        KadrZanikajacy(Context c) {
            super(c);
            maska.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0 || getDrawable() == null) return;
            if (w != szerMaski) {
                szerMaski = w;
                maska.setShader(new android.graphics.LinearGradient(0, 0, w, 0,
                        new int[]{0x00000000, 0xE6000000, 0xFF000000}, new float[]{0f, 0.58f, 1f},
                        android.graphics.Shader.TileMode.CLAMP));
            }
            int warstwa = c.saveLayer(0, 0, w, h, null);
            super.onDraw(c);
            c.drawRect(0, 0, w, h, maska);
            c.restoreToCount(warstwa);
        }
    }

    static View filmy(final MainActivity host) {
        final FrameLayout card = podstawa(host);
        final Locale loc = I18n.locale(host);
        final float r = dp(host, 15);

        FrameLayout tresc = new FrameLayout(host);
        ModPlOkna.zaokraglij(tresc, r);
        // kadr zajmuje prawe 61% kafelka i od lewej wtapia się w szkło (odmiana nr 2 z kanwy)
        final ImageView kadr = new KadrZanikajacy(host);
        kadr.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout podKadr = rzad(host, false);
        podKadr.setWeightSum(1f);
        podKadr.addView(new View(host), new LinearLayout.LayoutParams(0, 1, 0.39f));
        podKadr.addView(kadr, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.61f));
        tresc.addView(podKadr, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // tekst zajmuje lewe dwie trzecie kafelka, prawa strona zostaje dla kadru
        LinearLayout uklad = rzad(host, false);
        uklad.setWeightSum(1f);
        LinearLayout col = rzad(host, true);
        col.setPadding(dp(host, 12), dp(host, 8), 0, dp(host, 7));
        LinearLayout head = rzad(host, false);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(kropka(host, BIALY, 5));
        final TextView etykieta = etykieta(host);
        etykieta.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, dp(host, 7.5f));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        elp.leftMargin = dp(host, 5);
        head.addView(etykieta, elp);
        col.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(host, 12)));

        final TextView tytul = tekst(host, "", 14f, "display", BIALY);
        tytul.setMaxLines(2);
        tytul.setEllipsize(TextUtils.TruncateAt.END);
        tytul.setLetterSpacing(-0.012f);
        tytul.setLineSpacing(0f, 1.08f);
        LinearLayout.LayoutParams ttlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ttlp.topMargin = dp(host, 3);
        col.addView(tytul, ttlp);

        LinearLayout meta = rzad(host, false);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        final TextView typRok = jednaLinia(tekst(host, "", 9f, "medium", 0xE0FFFFFF));
        meta.addView(typRok);
        final TextView ocena = tekst(host, "", 9f, "semibold", 0xFFFCD34D);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        olp.leftMargin = dp(host, 4);
        meta.addView(ocena, olp);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(host, 3);
        col.addView(meta, mlp);

        col.addView(new View(host), new LinearLayout.LayoutParams(1, 0, 1f));
        final TextView serwis = jednaLinia(ModPlOkna.tekst(host, "", 7.5f, "semibold", BIALY));
        serwis.setGravity(Gravity.CENTER_VERTICAL);
        serwis.setPadding(dp(host, 6), 0, dp(host, 6), 0);
        serwis.setBackground(tlo(0x29FFFFFF, dp(host, 7), Math.max(1, dp(host, 0.5f)), 0x47FFFFFF));
        col.addView(serwis, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(host, 14)));
        uklad.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.62f));
        tresc.addView(uklad, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // kropki: który z pięciu tytułów jest teraz na kafelku
        final LinearLayout kropki = rzad(host, false);
        kropki.setGravity(Gravity.CENTER_VERTICAL);
        kropki.setPadding(dp(host, 4.5f), 0, dp(host, 4.5f), 0);
        kropki.setBackground(tlo(0x9908080C, dp(host, 5.5f), 0, 0));
        final View[] znacznik = new View[ILE_FILMOW];
        for (int i = 0; i < ILE_FILMOW; i++) {
            znacznik[i] = new View(host);
            LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(dp(host, 4), dp(host, 4));
            if (i > 0) kp.leftMargin = dp(host, 3.5f);
            kropki.addView(znacznik[i], kp);
        }
        FrameLayout.LayoutParams krlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(host, 11), Gravity.BOTTOM | Gravity.END);
        krlp.rightMargin = dp(host, 8);
        krlp.bottomMargin = dp(host, 7);
        tresc.addView(kropki, krlp);

        card.addView(tresc, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // kadr zasłania jasną krawędź szkła po prawej, więc kafelek dostaje własną cienką obwódkę jak sąsiednie
        View obwodka = new View(host);
        obwodka.setBackground(tlo(0x00000000, r, Math.max(1, dp(host, 0.75f)), 0x3DFFFFFF));
        card.addView(obwodka, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final List<Suggestions.Item> pozycje = new ArrayList<Suggestions.Item>();
        final int[] indeks = {0};
        final Runnable pokaz = new Runnable() {
            @Override
            public void run() {
                String nazwa = I18n.t(host, "dash_latest_releases").toUpperCase(loc);
                if (pozycje.isEmpty()) {
                    etykieta.setText(nazwa);
                    tytul.setText(I18n.t(host, "releases_loading"));
                    typRok.setText("");
                    ocena.setText("");
                    serwis.setVisibility(View.GONE);
                    kropki.setVisibility(View.GONE);
                    return;
                }
                int ile = Math.min(ILE_FILMOW, pozycje.size());
                if (indeks[0] >= ile || indeks[0] < 0) indeks[0] = 0;
                Suggestions.Item it = pozycje.get(indeks[0]);
                etykieta.setText(nazwa + " · " + I18n.t(host, "No.").toUpperCase(loc) + " " + (indeks[0] + 1));
                tytul.setText(it.title != null ? it.title : "");
                boolean serial = "tv".equalsIgnoreCase(it.type) || "series".equalsIgnoreCase(it.type);
                String typ = I18n.t(host, serial ? "TV Series" : "Movie");
                typRok.setText(it.year != null && !it.year.isEmpty() ? typ + " · " + it.year : typ);
                ocena.setText(it.rating > 0 ? String.format(Locale.US, "★ %.1f", it.rating) : "");
                String s = ModPlOkna.krotkiSerwis(it.prov);
                serwis.setText(s);
                serwis.setVisibility(s == null || s.isEmpty() ? View.GONE : View.VISIBLE);
                kropki.setVisibility(ile > 1 ? View.VISIBLE : View.GONE);
                for (int i = 0; i < ILE_FILMOW; i++) {
                    boolean ta = i == indeks[0];
                    znacznik[i].setVisibility(i < ile ? View.VISIBLE : View.GONE);
                    znacznik[i].setBackground(tlo(ta ? BIALY : 0x8CFFFFFF, dp(host, 2), 0, 0));
                    ViewGroup.LayoutParams kp = znacznik[i].getLayoutParams();
                    kp.width = dp(host, ta ? 10 : 4);
                    znacznik[i].setLayoutParams(kp);
                }
                wstaw(host, kadr, it.backdrop, dp(host, 300), true, null);
            }
        };

        List<Suggestions.Item> zPamieci = HomeDashboard.getCachedReleases(host);
        if (zPamieci != null) pozycje.addAll(zPamieci);
        pokaz.run();
        HomeDashboard.fetchLatestReleases(host, false, new HomeDashboard.ReleasesCallback() {
            @Override
            public void onResult(List<Suggestions.Item> swieze) {
                if (swieze != null && !swieze.isEmpty()) {
                    pozycje.clear();
                    pozycje.addAll(swieze);
                    pokaz.run();
                }
            }
        });
        cyklicznie(card, CO_ILE_FILM, new Runnable() {
            @Override
            public void run() {
                if (Math.min(ILE_FILMOW, pozycje.size()) > 1) {
                    indeks[0]++;
                    pokaz.run();
                }
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ModPlOkna.pokazNajczesciej(host, pozycje);
            }
        });
        return card;
    }

    // ------------------------------------------------------------------ pogoda

    /** Ikona pogody rysowana kształtami (chmura, słońce, krople), w polu 48×36 jednostek. */
    private static final class IkonaPogody extends View {
        private int typ = Weather.ICON_CLOUD;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path chmura = new Path();

        IkonaPogody(Context c) {
            super(c);
            chmura.addCircle(13f, 20f, 10f, Path.Direction.CW);
            chmura.addCircle(24.5f, 14f, 12.5f, Path.Direction.CW);
            chmura.addCircle(36f, 21f, 9f, Path.Direction.CW);
            chmura.addRect(13f, 20f, 36f, 30f, Path.Direction.CW);
        }

        void typ(int t) {
            if (t != typ) {
                typ = t;
                invalidate();
            }
        }

        private void chmura(Canvas c, float skala, float dx, float dy, int kolor) {
            c.save();
            c.translate(dx, dy);
            c.scale(skala, skala);
            p.setStyle(Paint.Style.FILL);
            p.setColor(kolor);
            c.drawPath(chmura, p);
            c.restore();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float s = Math.min(w / 48f, h / 36f);
            c.save();
            c.translate((w - 48f * s) / 2f, (h - 36f * s) / 2f);
            c.scale(s, s);
            if (typ == Weather.ICON_SUN) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFD43B);
                c.drawCircle(24f, 18f, 12f, p);
            } else if (typ == Weather.ICON_PARTLY) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFD43B);
                c.drawCircle(17f, 13f, 10f, p);
                chmura(c, 0.78f, 9f, 9f, 0xFFFFFFFF);
            } else if (typ == Weather.ICON_CLOUD) {
                chmura(c, 1f, 0f, 3f, 0xFFFFFFFF);
            } else {
                chmura(c, 0.84f, 4f, 0f, typ == Weather.ICON_STORM ? 0xFFD1D5DB : 0xFFFFFFFF);
                if (typ == Weather.ICON_SNOW) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xFFFFFFFF);
                    c.drawCircle(16f, 31.5f, 2.2f, p);
                    c.drawCircle(24f, 32.5f, 2.2f, p);
                    c.drawCircle(32f, 31.5f, 2.2f, p);
                } else {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    p.setStrokeWidth(2.6f);
                    p.setColor(typ == Weather.ICON_STORM ? 0xFFFBBF24 : 0xFF7DD3FC);
                    c.drawLine(17f, 28.5f, 15f, 33.5f, p);
                    c.drawLine(25f, 28.5f, 23f, 33.5f, p);
                    c.drawLine(33f, 28.5f, 31f, 33.5f, p);
                }
            }
            c.restore();
        }
    }

    /** Skrót dnia tygodnia z daty "2026-11-12" → "Czw."; gdy daty nie da się odczytać, początek opisu dnia. */
    private static String skrotDnia(Context c, Weather.Day d) {
        Locale loc = I18n.locale(c);
        try {
            Date data = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d.date);
            if (data != null) return wielka(new SimpleDateFormat("EEE", loc).format(data), loc);
        } catch (Exception ignored) {
            // zostaje opis dnia z danych pogody
        }
        String l = d.label == null ? "" : d.label;
        return l.length() > 3 ? l.substring(0, 3) + "." : l;
    }

    static View pogoda(final MainActivity host, final HomeDashboard.NavBridge nav) {
        final FrameLayout card = podstawa(host);
        final Locale loc = I18n.locale(host);
        LinearLayout col = rzad(host, true);
        col.setPadding(dp(host, 12), dp(host, 8), dp(host, 12), dp(host, 7));

        LinearLayout head = rzad(host, false);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(kropka(host, BIALY, 5));
        TextView etykieta = etykieta(host);
        etykieta.setText(I18n.t(host, "weather").toUpperCase(loc));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        elp.leftMargin = dp(host, 5);
        elp.rightMargin = dp(host, 6);
        head.addView(etykieta, elp);
        final TextView miasto = jednaLinia(tekst(host, "", 8.5f, "medium", JASNY));
        head.addView(miasto);
        col.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(host, 13)));

        LinearLayout srodek = rzad(host, false);
        srodek.setGravity(Gravity.CENTER_VERTICAL);
        final IkonaPogody ikona = new IkonaPogody(host);
        srodek.addView(ikona, new LinearLayout.LayoutParams(dp(host, 35), dp(host, 26)));
        LinearLayout teraz = rzad(host, true);
        final TextView temp = tekst(host, "--°", 29f, "display", BIALY);
        temp.setLetterSpacing(-0.03f);
        temp.setSingleLine(true);
        teraz.addView(temp);
        final TextView opis = jednaLinia(tekst(host, "", 9.5f, "semibold", 0xE6FFFFFF));
        LinearLayout.LayoutParams oplp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        oplp.topMargin = dp(host, 1.5f);
        teraz.addView(opis, oplp);
        LinearLayout.LayoutParams terazLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        terazLp.leftMargin = dp(host, 7);
        srodek.addView(teraz, terazLp);

        final View kreska = new View(host);
        kreska.setBackgroundColor(0x33FFFFFF);
        LinearLayout.LayoutParams krlp = new LinearLayout.LayoutParams(Math.max(1, dp(host, 0.5f)), ViewGroup.LayoutParams.MATCH_PARENT);
        krlp.leftMargin = dp(host, 10);
        krlp.rightMargin = dp(host, 6);
        krlp.topMargin = dp(host, 5);
        krlp.bottomMargin = dp(host, 5);
        srodek.addView(kreska, krlp);

        final LinearLayout dni = rzad(host, false);
        final LinearLayout[] dzienKol = new LinearLayout[3];
        final TextView[] dzienNazwa = new TextView[3], dzienTemp = new TextView[3];
        final IkonaPogody[] dzienIkona = new IkonaPogody[3];
        for (int i = 0; i < 3; i++) {
            LinearLayout d = rzad(host, true);
            d.setGravity(Gravity.CENTER_HORIZONTAL);
            dzienNazwa[i] = jednaLinia(tekst(host, "", 8f, "semibold", 0xC7FFFFFF));
            d.addView(dzienNazwa[i]);
            dzienIkona[i] = new IkonaPogody(host);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(host, 20), dp(host, 15));
            ilp.topMargin = dp(host, 3);
            d.addView(dzienIkona[i], ilp);
            dzienTemp[i] = jednaLinia(tekst(host, "", 8.5f, "semibold", BIALY));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = dp(host, 3);
            d.addView(dzienTemp[i], tlp);
            dzienKol[i] = d;
            dni.addView(d, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        srodek.addView(dni, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        slp.topMargin = dp(host, 2);
        col.addView(srodek, slp);
        card.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final int[] ileDni = {3};
        final Runnable pokaz = new Runnable() {
            @Override
            public void run() {
                Weather.Data w = Weather.cached(host);
                if (w == null) {
                    temp.setText("--°");
                    opis.setText(I18n.t(host, "weather_updating"));
                    miasto.setText("");
                    kreska.setVisibility(View.GONE);
                    dni.setVisibility(View.GONE);
                    return;
                }
                miasto.setText(w.place != null ? w.place : "");
                ikona.typ(Weather.iconType(w.code));
                temp.setText(Weather.formatTemp(host, w.temp));
                opis.setText(Weather.codeText(host, w.code));
                int pokazane = 0;
                for (int i = 0; i < 3; i++) {
                    Weather.Day d = w.days != null && w.days.size() > i + 1 ? w.days.get(i + 1) : null;
                    if (d == null || i >= ileDni[0]) {
                        dzienKol[i].setVisibility(View.GONE);
                        continue;
                    }
                    pokazane++;
                    dzienKol[i].setVisibility(View.VISIBLE);
                    dzienNazwa[i].setText(skrotDnia(host, d));
                    dzienIkona[i].typ(Weather.iconType(d.code));
                    String max = Weather.formatTemp(host, d.max);
                    SpannableString t = new SpannableString(max + " / " + Weather.formatTemp(host, d.min));
                    t.setSpan(new ForegroundColorSpan(0xB8FFFFFF), max.length(), t.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    dzienTemp[i].setText(t);
                }
                kreska.setVisibility(pokazane > 0 ? View.VISIBLE : View.GONE);
                dni.setVisibility(pokazane > 0 ? View.VISIBLE : View.GONE);
            }
        };
        pokaz.run();

        // przy czterech kafelkach w rzędzie kafelek jest wąski: zostają dwa dni zamiast trzech
        card.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                int ile = (r - l) < dp(host, 250) ? 2 : 3;
                if (ile != ileDni[0]) {
                    ileDni[0] = ile;
                    card.post(pokaz);
                }
            }
        });
        card.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                pokaz.run();
            }

            @Override
            public void onViewDetachedFromWindow(View v) { }
        });
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (nav != null) nav.onOpenWeather();
                else host.showWeather();
            }
        });
        return card;
    }
}
