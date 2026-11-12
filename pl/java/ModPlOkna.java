package com.techgenius.tvoslauncher;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mod PL: okna "Najczęściej oglądane" i "Mecze piłkarskie" w układzie "lista z podglądem"
 * (wybrany wariant C z kanwy): lista po lewej, duży podgląd zaznaczonej pozycji po prawej.
 * Plik źródłowy leży w pl/java/, a pl/naloz.py kopiuje go do src/ przy każdym buildzie.
 */
final class ModPlOkna {
    private ModPlOkna() { }

    private static final ExecutorService POBIERANIE = Executors.newFixedThreadPool(3);
    private static final Handler GLOWNY = new Handler(Looper.getMainLooper());

    /** Akcent filmów (fiolet jak widżet) i meczów (błękit jak widżet). */
    private static final int FIOLET = 0xFF9333EA, FIOLET_RAMKA = 0xFFC084FC, FIOLET_JASNY = 0xFFD8B4FE;
    private static final int BLEKIT = 0xFF0284C7, BLEKIT_RAMKA = 0xFF7DD3FC, BLEKIT_JASNY = 0xFFBAE6FD;

    /** Ostatnie podglądy (duże obrazy), żeby przewijanie listy nie pobierało ich w kółko. */
    private static final Map<String, Bitmap> PODGLADY = new LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) {
            return size() > 5;
        }
    };

    // ------------------------------------------------------------------ wspólne

    private static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView tekst(Context c, String s, float rozmiarDp, String waga, int kolor) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(c, rozmiarDp));
        t.setTypeface(Ui.font(c, waga));
        t.setTextColor(kolor);
        t.setIncludeFontPadding(false);
        return t;
    }

    private static GradientDrawable tlo(int kolor, float promien, int grubosc, int ramka) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(kolor);
        g.setCornerRadius(promien);
        if (grubosc > 0) g.setStroke(grubosc, ramka);
        return g;
    }

    static void zaokraglij(View v, final float promien) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), promien);
            }
        });
        v.setClipToOutline(true);
    }

    private static void wczytaj(final Context c, final String url, final int szer, final Ladowanie gotowe) {
        if (url == null || url.isEmpty()) return;
        POBIERANIE.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap b = Suggestions.image(c, url, "w780", szer);
                if (b != null) {
                    GLOWNY.post(new Runnable() {
                        @Override
                        public void run() {
                            gotowe.ok(b);
                        }
                    });
                }
            }
        });
    }

    private interface Ladowanie {
        void ok(Bitmap b);
    }

    /** Okno nad ekranem głównym: ekran zostaje widoczny pod spodem, lekko przyciemniony (jak przy oknie pogody). */
    private static Dialog okno(MainActivity host, FrameLayout korzen) {
        Dialog d = new Dialog(host);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        View przyciemnienie = new View(host);
        przyciemnienie.setBackgroundColor(0x4D000000);
        korzen.addView(przyciemnienie, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        d.setContentView(korzen, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return d;
    }

    /**
     * Szklany panel okna w stylu okna pogody: rozmyta tapeta (LiquidGlass), barwa akcentu od lewego górnego rogu
     * i jasna krawędź. Szkło, barwa i treść to trzy warstwy tej samej wielkości dodane do korzenia
     * (LiquidGlass musi być tłem widoku, nie częścią LayerDrawable, bo bierze z niego położenie na ekranie).
     */
    private static LinearLayout panel(MainActivity host, FrameLayout korzen, int akcent) {
        Context c = host;
        float r = dp(c, 26);
        boolean jestTapeta = host.glassBg != null && !host.glassBg.isRecycled();
        View szklo = new View(c);
        MainActivity.LiquidGlass g = new MainActivity.LiquidGlass(r, jestTapeta ? 0xA60D0A13 : 0xEB14141C,
                host.glassSource, host.screenW, host.screenH, host.unit);
        g.lens = dp(c, 7);
        g.rimStrength = 0.75f;
        szklo.setBackground(g);
        korzen.addView(szklo, new FrameLayout.LayoutParams(dp(c, 880), dp(c, 470), Gravity.CENTER));

        View barwa = new View(c);
        GradientDrawable b = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{(akcent & 0x00FFFFFF) | 0x42000000, (akcent & 0x00FFFFFF) | 0x12000000, 0x00000000});
        b.setCornerRadius(r);
        barwa.setBackground(b);
        korzen.addView(barwa, new FrameLayout.LayoutParams(dp(c, 880), dp(c, 470), Gravity.CENTER));

        LinearLayout p = new LinearLayout(c);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setPadding(dp(c, 20), dp(c, 20), dp(c, 20), dp(c, 20));
        p.setClipToPadding(false);
        p.setClipChildren(false);
        korzen.addView(p, new FrameLayout.LayoutParams(dp(c, 880), dp(c, 470), Gravity.CENTER));
        return p;
    }

    private static LinearLayout naglowek(Context c, String etykieta, String tytul, int kropka, int kolorEtykiety) {
        LinearLayout kol = new LinearLayout(c);
        kol.setOrientation(LinearLayout.VERTICAL);
        kol.setPadding(dp(c, 4), 0, dp(c, 4), 0);
        LinearLayout wiersz = new LinearLayout(c);
        wiersz.setOrientation(LinearLayout.HORIZONTAL);
        wiersz.setGravity(Gravity.CENTER_VERTICAL);
        View k = new View(c);
        GradientDrawable kg = new GradientDrawable();
        kg.setShape(GradientDrawable.OVAL);
        kg.setColor(kropka);
        k.setBackground(kg);
        wiersz.addView(k, new LinearLayout.LayoutParams(dp(c, 5.5f), dp(c, 5.5f)));
        TextView et = tekst(c, etykieta, 9, "semibold", kolorEtykiety);
        et.setLetterSpacing(0.11f);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.leftMargin = dp(c, 5);
        wiersz.addView(et, elp);
        kol.addView(wiersz);
        TextView t = tekst(c, tytul, 20, "display", Color.WHITE);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(c, 6);
        kol.addView(t, tlp);
        return kol;
    }

    /** Przycisk w kształcie pigułki; zaznaczony wypełnia się kolorem akcentu. */
    private static TextView przycisk(final Context c, String napis, final int akcent) {
        final TextView b = tekst(c, napis, 11, "semibold", Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        b.setSingleLine(true);
        b.setFocusable(true);
        b.setClickable(true);
        final float r = dp(c, 15.5f);
        b.setBackground(tlo(0x24FFFFFF, r, Math.max(1, dp(c, 0.5f)), 0x38FFFFFF));
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean jest) {
                v.setBackground(jest ? tlo(akcent, r, 0, 0) : tlo(0x24FFFFFF, r, Math.max(1, dp(c, 0.5f)), 0x38FFFFFF));
                v.animate().scaleX(jest ? 1.05f : 1f).scaleY(jest ? 1.05f : 1f).setDuration(140).start();
            }
        });
        return b;
    }

    /** Stan wiersza: 0 zwykły, 1 zaznaczony pilotem, 2 wybrany (zaznaczenie jest na przyciskach). */
    private static void ustawWiersz(Context c, View w, int stan, int akcent, int ramka) {
        float r = dp(c, 9);
        if (stan == 2) {
            w.setBackground(tlo((akcent & 0x00FFFFFF) | 0x24000000, r, Math.max(1, dp(c, 1)), (ramka & 0x00FFFFFF) | 0x66000000));
            w.setElevation(0f);
        } else if (stan == 1) {
            w.setBackground(tlo((akcent & 0x00FFFFFF) | 0x4D000000, r, Math.max(1, dp(c, 1)), ramka));
            w.setElevation(dp(c, 6));
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                w.setOutlineAmbientShadowColor(akcent);
                w.setOutlineSpotShadowColor(akcent);
            }
        } else {
            w.setBackground(tlo(0x14FFFFFF, r, 0, 0));
            w.setElevation(0f);
        }
    }

    private static boolean zainstalowana(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ Najczęściej oglądane

    /** Aplikacje serwisów z JustWatch (po nazwie serwisu); pierwsza zainstalowana wygrywa. */
    private static String[] pakietySerwisu(String serwis) {
        String s = serwis == null ? "" : serwis.toLowerCase(Locale.ROOT);
        if (s.contains("netflix")) return new String[]{"com.netflix.ninja"};
        if (s.contains("prime") || s.contains("amazon")) return new String[]{"com.amazon.amazonvideo.livingroom"};
        if (s.contains("disney")) return new String[]{"com.disney.disneyplus"};
        if (s.contains("hbo") || s.equals("max")) return new String[]{"com.wbd.stream", "com.hbo.hbonow"};
        if (s.contains("skyshowtime")) return new String[]{"com.skyshowtime.skyshowtime.google", "com.skyshowtime.skyshowtime"};
        if (s.contains("polsat")) return new String[]{"pl.redefine.ipla", "pl.polsatboxgo.tv"};
        if (s.contains("player")) return new String[]{"pl.tvn.player.tv", "pl.tvn.player"};
        if (s.contains("canal")) return new String[]{"pl.canalplus.tvod.atv", "pl.canalplus.atv"};
        if (s.contains("apple")) return new String[]{"com.apple.atve.androidtv.appletv"};
        if (s.contains("viaplay")) return new String[]{"com.viaplay.android"};
        if (s.contains("youtube")) return new String[]{"org.smarttube.stable", "com.google.android.youtube.tv"};
        return new String[0];
    }

    private static String aplikacjaSerwisu(Context c, String serwis) {
        for (String p : pakietySerwisu(serwis)) if (zainstalowana(c, p)) return p;
        return null;
    }

    private static void otworzSerwis(Context c, Suggestions.Item it, String pkg) {
        PackageManager pm = c.getPackageManager();
        List<Intent> proby = new ArrayList<Intent>();
        if (it.link != null && !it.link.isEmpty()) proby.add(Suggestions.view(it.link, pkg));
        proby.add(new Intent(Intent.ACTION_SEARCH).setPackage(pkg).putExtra(android.app.SearchManager.QUERY, it.title));
        for (Intent i : proby) {
            if (i.resolveActivity(pm) != null && Suggestions.start(c, i)) return;
        }
        Intent start = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (start == null) start = pm.getLaunchIntentForPackage(pkg);
        if (start != null) Suggestions.start(c, start);
    }

    private static void zwiastun(Context c, Suggestions.Item it) {
        String[] odtwarzacze = {"org.smarttube.stable", "org.smarttube.beta", "com.teamsmart.videomanager.tv",
                "com.liskovsoft.smarttubetv.beta", "com.google.android.youtube.tv"};
        // Znany zwiastun (JustWatch): odtwarzanie od razu, bez listy wyników.
        if (it.trailer != null && it.trailer.matches("[A-Za-z0-9_-]{11}")) {
            Uri film = Uri.parse("https://www.youtube.com/watch?v=" + it.trailer);
            for (String p : odtwarzacze) {
                if (zainstalowana(c, p) && Suggestions.start(c, new Intent(Intent.ACTION_VIEW, film).setPackage(p))) return;
            }
        }
        String q = it.title + (it.year != null && !it.year.isEmpty() ? " " + it.year : "") + " " + I18n.t(c, "trailer");
        String enc;
        try {
            enc = URLEncoder.encode(q, "UTF-8");
        } catch (Exception e) {
            enc = q;
        }
        Uri uri = Uri.parse("https://www.youtube.com/results?search_query=" + enc);
        for (String p : odtwarzacze) {
            if (zainstalowana(c, p) && Suggestions.start(c, new Intent(Intent.ACTION_VIEW, uri).setPackage(p))) return;
        }
        if (!Suggestions.start(c, new Intent(Intent.ACTION_VIEW, uri))) {
            Toast.makeText(c, I18n.t(c, "Couldn't open the trailer"), Toast.LENGTH_SHORT).show();
        }
    }

    private static String typ(Context c, Suggestions.Item it) {
        boolean serial = "tv".equalsIgnoreCase(it.type) || "series".equalsIgnoreCase(it.type);
        return I18n.t(c, serial ? "Series" : "Movie");
    }

    static String krotkiSerwis(String s) {
        if (s == null) return "";
        if (s.startsWith("Amazon Prime Video")) return "Prime Video";
        return s;
    }

    /** Zwraca true, gdy okno zostało pokazane (wtedy stary kod się nie wykonuje). */
    static boolean pokazNajczesciej(final MainActivity host, final List<Suggestions.Item> wszystkie) {
        if (wszystkie == null || wszystkie.isEmpty()) return false;
        final List<Suggestions.Item> lista = new ArrayList<Suggestions.Item>(wszystkie.subList(0, Math.min(20, wszystkie.size())));
        final Context c = host;

        FrameLayout korzen = new FrameLayout(c);
        final Dialog d = okno(host, korzen);
        LinearLayout p = panel(host, korzen, 0xFFA855F7);

        // lewa kolumna: nagłówek i lista
        LinearLayout lewa = new LinearLayout(c);
        lewa.setOrientation(LinearLayout.VERTICAL);
        lewa.setClipChildren(false);
        p.addView(lewa, new LinearLayout.LayoutParams(dp(c, 300), ViewGroup.LayoutParams.MATCH_PARENT));
        String kraje = TextUtils.join(", ", HomeDashboard.getEffectiveReleasesCountries(c));
        lewa.addView(naglowek(c, "JUSTWATCH · " + kraje, I18n.t(c, "dash_latest_releases"), 0xFFA855F7, FIOLET_JASNY));

        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        zaokraglij(sv, 0f);   // lista zostaje w swoim polu, także po przewinięciu
        sv.setPadding(dp(c, 4), dp(c, 6), dp(c, 6), dp(c, 6));
        final LinearLayout kolumna = new LinearLayout(c);
        kolumna.setOrientation(LinearLayout.VERTICAL);
        sv.addView(kolumna);
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        svlp.topMargin = dp(c, 6);
        lewa.addView(sv, svlp);

        // prawa kolumna: podgląd
        LinearLayout prawa = new LinearLayout(c);
        prawa.setOrientation(LinearLayout.VERTICAL);
        prawa.setClipChildren(false);
        LinearLayout.LayoutParams prlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        prlp.leftMargin = dp(c, 20);
        p.addView(prawa, prlp);

        FrameLayout obrazRamka = new FrameLayout(c);
        obrazRamka.setBackgroundColor(0xFF1A1A22);
        zaokraglij(obrazRamka, dp(c, 13));
        final ImageView obraz = new ImageView(c);
        obraz.setScaleType(ImageView.ScaleType.CENTER_CROP);
        obrazRamka.addView(obraz, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View cien = new View(c);
        cien.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xEB08080C, 0x7308080C, 0x0008080C}));
        obrazRamka.addView(cien, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout opisObrazu = new LinearLayout(c);
        opisObrazu.setOrientation(LinearLayout.VERTICAL);
        final TextView tytul = tekst(c, "", 28, "display", Color.WHITE);
        tytul.setMaxLines(2);
        tytul.setEllipsize(TextUtils.TruncateAt.END);
        opisObrazu.addView(tytul);
        LinearLayout meta = new LinearLayout(c);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        final TextView ocena = tekst(c, "", 10.5f, "semibold", 0xFFFCD34D);
        ocena.setPadding(dp(c, 6), dp(c, 2.5f), dp(c, 6), dp(c, 2.5f));
        ocena.setBackground(tlo(0x33F5C518, dp(c, 4.5f), 0, 0));
        meta.addView(ocena);
        final TextView metaTekst = tekst(c, "", 10.5f, "semibold", 0xDBFFFFFF);
        metaTekst.setSingleLine(true);
        metaTekst.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.leftMargin = dp(c, 8);
        meta.addView(metaTekst, mlp);
        LinearLayout.LayoutParams metalp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metalp.topMargin = dp(c, 7);
        opisObrazu.addView(meta, metalp);
        FrameLayout.LayoutParams oolp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        oolp.leftMargin = dp(c, 18);
        oolp.rightMargin = dp(c, 18);
        oolp.bottomMargin = dp(c, 15);
        obrazRamka.addView(opisObrazu, oolp);
        prawa.addView(obrazRamka, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 314)));

        final TextView opis = tekst(c, "", 11, "regular", 0xD1FFFFFF);
        opis.setLineSpacing(0, 1.4f);
        opis.setMaxLines(3);
        opis.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams oplp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        oplp.topMargin = dp(c, 13);
        oplp.leftMargin = dp(c, 3);
        oplp.rightMargin = dp(c, 3);
        prawa.addView(opis, oplp);

        LinearLayout przyciski = new LinearLayout(c);
        przyciski.setOrientation(LinearLayout.HORIZONTAL);
        przyciski.setGravity(Gravity.CENTER_VERTICAL);
        przyciski.setClipChildren(false);
        final TextView bZwiastun = przycisk(c, "▶  " + I18n.t(c, "Trailer"), FIOLET);
        przyciski.addView(bZwiastun, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 31)));
        final TextView bSerwis = przycisk(c, "", FIOLET);
        LinearLayout.LayoutParams bslp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 31));
        bslp.leftMargin = dp(c, 8);
        przyciski.addView(bSerwis, bslp);
        View luz = new View(c);
        przyciski.addView(luz, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView podpowiedz = tekst(c, I18n.t(c, "▲ ▼ titles  ·  ▶ buttons"), 9, "medium", 0x80FFFFFF);
        przyciski.addView(podpowiedz);
        LinearLayout.LayoutParams przlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        przlp.topMargin = dp(c, 13);
        przlp.leftMargin = dp(c, 3);
        prawa.addView(przyciski, przlp);

        final int[] wybrany = {0};
        final List<View> wiersze = new ArrayList<View>();

        final Runnable pokazPodglad = new Runnable() {
            @Override
            public void run() {
                final int i = wybrany[0];
                final Suggestions.Item it = lista.get(i);
                tytul.setText(it.title);
                ocena.setText(it.rating > 0 ? String.format(Locale.US, "★ %.1f IMDb", it.rating) : "");
                ocena.setVisibility(it.rating > 0 ? View.VISIBLE : View.GONE);
                StringBuilder m = new StringBuilder(typ(c, it));
                if (it.year != null && !it.year.isEmpty()) m.append("  ·  ").append(it.year);
                if (it.genres != null && !it.genres.isEmpty()) m.append("  ·  ").append(it.genres.replace(" · ", ", "));
                metaTekst.setText(m.toString());
                opis.setText(it.overview == null ? "" : it.overview);
                String serwis = it.prov == null ? "" : it.prov;
                final String pkg = aplikacjaSerwisu(c, serwis);
                if (pkg != null) {
                    bSerwis.setText(I18n.t(c, "Open in ") + krotkiSerwis(serwis));
                    bSerwis.setFocusable(true);
                    bSerwis.setAlpha(1f);
                    bSerwis.setVisibility(View.VISIBLE);
                    bSerwis.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            otworzSerwis(c, it, pkg);
                        }
                    });
                } else if (!serwis.isEmpty()) {
                    bSerwis.setText(I18n.t(c, "Available on ") + krotkiSerwis(serwis));
                    bSerwis.setFocusable(false);
                    bSerwis.setAlpha(0.7f);
                    bSerwis.setVisibility(View.VISIBLE);
                    bSerwis.setOnClickListener(null);
                } else {
                    bSerwis.setVisibility(View.GONE);
                }
                Bitmap juz = PODGLADY.get(it.backdrop);
                if (juz != null) {
                    obraz.setImageBitmap(juz);
                } else {
                    obraz.setImageDrawable(null);
                    wczytaj(c, it.backdrop, dp(c, 560), new Ladowanie() {
                        @Override
                        public void ok(Bitmap b) {
                            PODGLADY.put(it.backdrop, b);
                            if (wybrany[0] == i) obraz.setImageBitmap(b);
                        }
                    });
                }
            }
        };

        bZwiastun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                zwiastun(c, lista.get(wybrany[0]));
            }
        });
        View.OnKeyListener wroc = new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int code, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT && v == bZwiastun) {
                    wiersze.get(wybrany[0]).requestFocus();
                    return true;
                }
                return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
            }
        };
        bZwiastun.setOnKeyListener(wroc);
        bSerwis.setOnKeyListener(wroc);

        for (int i = 0; i < lista.size(); i++) {
            final int idx = i;
            final Suggestions.Item it = lista.get(i);
            final LinearLayout w = new LinearLayout(c);
            w.setOrientation(LinearLayout.HORIZONTAL);
            w.setGravity(Gravity.CENTER_VERTICAL);
            w.setPadding(dp(c, 3), dp(c, 3), dp(c, 8), dp(c, 3));
            w.setFocusable(true);
            w.setClickable(true);
            ustawWiersz(c, w, 0, FIOLET, FIOLET_RAMKA);

            final ImageView miniatura = new ImageView(c);
            miniatura.setScaleType(ImageView.ScaleType.CENTER_CROP);
            miniatura.setBackgroundColor(0xFF1A1A22);
            zaokraglij(miniatura, dp(c, 6));
            w.addView(miniatura, new LinearLayout.LayoutParams(dp(c, 59), dp(c, 33)));
            wczytaj(c, it.backdrop, dp(c, 60), new Ladowanie() {
                @Override
                public void ok(Bitmap b) {
                    miniatura.setImageBitmap(b);
                }
            });

            LinearLayout teksty = new LinearLayout(c);
            teksty.setOrientation(LinearLayout.VERTICAL);
            TextView t1 = tekst(c, it.title, 10.5f, "semibold", Color.WHITE);
            t1.setSingleLine(true);
            t1.setEllipsize(TextUtils.TruncateAt.END);
            teksty.addView(t1);
            String serwis = krotkiSerwis(it.prov);
            TextView t2 = tekst(c, typ(c, it) + (serwis.isEmpty() ? "" : " · " + serwis), 8, "medium", 0x94FFFFFF);
            t2.setSingleLine(true);
            t2.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams t2lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            t2lp.topMargin = dp(c, 3);
            teksty.addView(t2, t2lp);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tlp.leftMargin = dp(c, 9);
            w.addView(teksty, tlp);
            if (it.rating > 0) {
                TextView r = tekst(c, String.format(Locale.US, "★ %.1f", it.rating), 8.5f, "semibold", 0xFFFCD34D);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.leftMargin = dp(c, 6);
                w.addView(r, rlp);
            }

            w.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean jest) {
                    if (jest && wybrany[0] != idx) {
                        int stary = wybrany[0];
                        wybrany[0] = idx;
                        if (stary >= 0 && stary < wiersze.size()) ustawWiersz(c, wiersze.get(stary), 0, FIOLET, FIOLET_RAMKA);
                        pokazPodglad.run();
                    }
                    ustawWiersz(c, v, jest ? 1 : (wybrany[0] == idx ? 2 : 0), FIOLET, FIOLET_RAMKA);
                }
            });
            w.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    zwiastun(c, it);
                }
            });
            w.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int code, KeyEvent e) {
                    if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                    if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        bZwiastun.requestFocus();
                        return true;
                    }
                    if (code == KeyEvent.KEYCODE_DPAD_LEFT) return true;
                    if (code == KeyEvent.KEYCODE_DPAD_UP && idx == 0) return true;
                    return code == KeyEvent.KEYCODE_DPAD_DOWN && idx == lista.size() - 1;
                }
            });
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 40));
            if (i > 0) wlp.topMargin = dp(c, 4);
            kolumna.addView(w, wlp);
            wiersze.add(w);
        }

        pokazPodglad.run();
        d.show();
        wiersze.get(0).requestFocus();
        return true;
    }

    // ------------------------------------------------------------------ Mecze piłkarskie

    /** Małe litery bez polskich i innych znaków diakrytycznych: "Lech Poznań" i "Lech Poznan" to to samo. */
    static String bezOgonkow(String s) {
        if (s == null) return "";
        String t = s.toLowerCase(Locale.ROOT).replace('ł', 'l');
        t = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD);
        return t.replaceAll("\\p{M}+", "");
    }

    /** Stan meczu po polsku: Na żywo 67', Przerwa, Koniec, Dziś 21:00, Jutro 18:30, Pt. 14.11 20:45. */
    static String status(Context c, HomeDashboard.MatchItem m) {
        String s = m.statusText == null ? "" : m.statusText.trim();
        if (m.isLive) {
            if (s.equalsIgnoreCase("HT")) return I18n.t(c, "Half time");
            return I18n.t(c, "Live") + (s.isEmpty() ? "" : " " + s);
        }
        if (m.isFinished) {
            String u = s.toUpperCase(Locale.ROOT);
            if (u.contains("PEN")) return I18n.t(c, "After penalties");
            if (u.contains("AET")) return I18n.t(c, "After extra time");
            if (u.equals("ABD")) return I18n.t(c, "Abandoned");
            return I18n.t(c, "Full time");
        }
        if (s.equalsIgnoreCase("PST")) return I18n.t(c, "Postponed");
        if (s.equalsIgnoreCase("CANC")) return I18n.t(c, "Cancelled");
        if (m.startEpochMs <= 0) return s;
        Locale loc = I18n.locale(c);
        Date dt = new Date(m.startEpochMs);
        String godz = new SimpleDateFormat("HH:mm", loc).format(dt);
        Calendar dzis = Calendar.getInstance();
        Calendar kiedy = Calendar.getInstance();
        kiedy.setTime(dt);
        int roznica = (kiedy.get(Calendar.YEAR) - dzis.get(Calendar.YEAR)) * 400
                + kiedy.get(Calendar.DAY_OF_YEAR) - dzis.get(Calendar.DAY_OF_YEAR);
        if (roznica == 0) return I18n.t(c, "Today") + " " + godz;
        if (roznica == 1) return I18n.t(c, "tomorrow") + " " + godz;
        String dzien = new SimpleDateFormat("EEE d.MM", loc).format(dt);
        if (!dzien.isEmpty()) dzien = dzien.substring(0, 1).toUpperCase(loc) + dzien.substring(1);
        return dzien + " " + godz;
    }

    private static String wynik(HomeDashboard.MatchItem m) {
        if (m.isLive || m.isFinished) return m.homeScore + " : " + m.awayScore;
        return "–";
    }

    private static String klucz(HomeDashboard.MatchItem m) {
        return m.id != null && !m.id.isEmpty() ? m.id : m.homeTeam + "|" + m.awayTeam + "|" + m.startEpochMs;
    }

    // --- kafelek na ekranie głównym

    /** Prawa strona wiersza kafelka dla meczu trwającego lub zakończonego: "2:1 · 67'" albo "2:1". */
    static String wynikKafelka(Context c, HomeDashboard.MatchItem m) {
        String w = m.homeScore + ":" + m.awayScore;
        if (!m.isLive) return w;
        String s = m.statusText == null ? "" : m.statusText.trim();
        if (s.equalsIgnoreCase("HT")) s = I18n.t(c, "Half time").toLowerCase(I18n.locale(c));
        return s.isEmpty() ? w : w + " · " + s;
    }

    /** Prawa strona wiersza kafelka przed meczem: "Dziś 20:45", "Sob. 21.11 14:45". */
    static String terminKafelka(Context c, HomeDashboard.MatchItem m) {
        String s = status(c, m);
        return s.isEmpty() ? "–" : s;
    }

    /**
     * Odświeża kafelek meczów, póki jest na ekranie: co minutę, gdy coś trwa albo zaraz się zaczyna,
     * w pozostałym czasie co 5 minut. Gdy launcher jest w tle, niczego nie pobiera.
     */
    static void pilnujMeczow(final View kafelek, final MainActivity host, final Runnable odswiez) {
        final Runnable[] petla = new Runnable[1];
        petla[0] = new Runnable() {
            @Override
            public void run() {
                if (!kafelek.isAttachedToWindow()) return;
                if (kafelek.isShown() && kafelek.getWindowVisibility() == View.VISIBLE) {
                    HomeDashboard.fetchFootballMatches(host, true, new HomeDashboard.FootballCallback() {
                        @Override
                        public void onResult(List<HomeDashboard.MatchItem> wynik) {
                            if (kafelek.isAttachedToWindow()) odswiez.run();
                        }
                    });
                }
                boolean pilne = ModPlMecze.cosSieDzieje(HomeDashboard.getCachedMatches(host));
                GLOWNY.postDelayed(this, pilne ? 60_000L : 5 * 60_000L);
            }
        };
        kafelek.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                GLOWNY.removeCallbacks(petla[0]);
                GLOWNY.postDelayed(petla[0], 60_000L);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                GLOWNY.removeCallbacks(petla[0]);
            }
        });
        if (kafelek.isAttachedToWindow()) GLOWNY.postDelayed(petla[0], 60_000L);
    }

    // --- okno meczów

    private static TextView wierszListy(final Context c, String napis) {
        final TextView t = tekst(c, napis, 11, "semibold", Color.WHITE);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(c, 12), 0, dp(c, 12), 0);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setFocusable(true);
        t.setClickable(true);
        ustawWiersz(c, t, 0, BLEKIT, BLEKIT_RAMKA);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean jest) {
                ustawWiersz(c, v, jest ? 1 : 0, BLEKIT, BLEKIT_RAMKA);
            }
        });
        return t;
    }

    static boolean pokazMecze(final MainActivity host, final Runnable poZmianie) {
        final Context c = host;
        final List<HomeDashboard.MatchItem> mecze = new ArrayList<HomeDashboard.MatchItem>(HomeDashboard.getCachedMatches(c));

        final FrameLayout korzen = new FrameLayout(c);
        final Dialog d = okno(host, korzen);
        final LinearLayout p = panel(host, korzen, 0xFF38BDF8);

        LinearLayout lewa = new LinearLayout(c);
        lewa.setOrientation(LinearLayout.VERTICAL);
        lewa.setClipChildren(false);
        p.addView(lewa, new LinearLayout.LayoutParams(dp(c, 330), ViewGroup.LayoutParams.MATCH_PARENT));

        final String liga = HomeDashboard.getSelectedLeague(c);
        String nazwaLigi = HomeDashboard.LEAGUES[0][1];
        for (String[] lg : HomeDashboard.LEAGUES) if (lg[0].equals(liga)) nazwaLigi = lg[1];
        lewa.addView(naglowek(c, I18n.t(c, nazwaLigi).toUpperCase(I18n.locale(c)),
                I18n.t(c, "dash_football"), 0xFF38BDF8, BLEKIT_JASNY));

        LinearLayout opcje = new LinearLayout(c);
        opcje.setOrientation(LinearLayout.HORIZONTAL);
        opcje.setClipChildren(false);
        opcje.setClipToPadding(false);
        opcje.setPadding(dp(c, 4), 0, dp(c, 4), 0);
        final TextView bLiga = przycisk(c, I18n.t(c, "League: ") + I18n.t(c, nazwaLigi) + "  ›", BLEKIT);
        opcje.addView(bLiga, new LinearLayout.LayoutParams(0, dp(c, 28), 1f));
        final TextView bUlubione = przycisk(c, "★ " + I18n.t(c, "football_fav_teams") + "  ›", BLEKIT);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(0, dp(c, 28), 1f);
        ulp.leftMargin = dp(c, 6);
        opcje.addView(bUlubione, ulp);
        LinearLayout.LayoutParams oplp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        oplp.topMargin = dp(c, 12);
        lewa.addView(opcje, oplp);

        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        zaokraglij(sv, 0f);   // lista zostaje w swoim polu, także po przewinięciu
        sv.setPadding(dp(c, 4), dp(c, 6), dp(c, 6), dp(c, 6));
        final LinearLayout kolumna = new LinearLayout(c);
        kolumna.setOrientation(LinearLayout.VERTICAL);
        sv.addView(kolumna);
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        svlp.topMargin = dp(c, 6);
        lewa.addView(sv, svlp);

        // prawa kolumna: tablica meczu
        LinearLayout prawa = new LinearLayout(c);
        prawa.setOrientation(LinearLayout.VERTICAL);
        prawa.setClipChildren(false);
        LinearLayout.LayoutParams prlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        prlp.leftMargin = dp(c, 20);
        p.addView(prawa, prlp);

        LinearLayout tablica = new LinearLayout(c);
        tablica.setOrientation(LinearLayout.VERTICAL);
        tablica.setGravity(Gravity.CENTER_HORIZONTAL);
        tablica.setBackground(tlo(0x14FFFFFF, dp(c, 20), Math.max(1, dp(c, 0.5f)), 0x29FFFFFF));
        tablica.setPadding(dp(c, 18), dp(c, 18), dp(c, 18), dp(c, 18));
        prawa.addView(tablica, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 376)));

        final TextView tLiga = tekst(c, "", 9.5f, "semibold", BLEKIT_JASNY);
        tLiga.setLetterSpacing(0.1f);
        tLiga.setGravity(Gravity.CENTER);
        tablica.addView(tLiga);
        final TextView tData = tekst(c, "", 10, "medium", 0xD6FFFFFF);
        tData.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tdlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tdlp.topMargin = dp(c, 6);
        tablica.addView(tData, tdlp);
        LinearLayout druzyny = new LinearLayout(c);
        druzyny.setOrientation(LinearLayout.HORIZONTAL);
        druzyny.setGravity(Gravity.CENTER);
        tablica.addView(druzyny, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final ImageView herbDom = new ImageView(c), herbGosc = new ImageView(c);
        final TextView nDom = tekst(c, "", 13, "semibold", Color.WHITE), nGosc = tekst(c, "", 13, "semibold", Color.WHITE);
        LinearLayout kDom = new LinearLayout(c), kGosc = new LinearLayout(c);
        ImageView[] herby = {herbDom, herbGosc};
        TextView[] nazwy = {nDom, nGosc};
        LinearLayout[] kolumny = {kDom, kGosc};
        for (int i = 0; i < 2; i++) {
            kolumny[i].setOrientation(LinearLayout.VERTICAL);
            kolumny[i].setGravity(Gravity.CENTER_HORIZONTAL);
            herby[i].setScaleType(ImageView.ScaleType.FIT_CENTER);
            // herb ma najwyżej 64 px, więc leży na szklanym krążku zamiast być rozciągany na całe pole
            FrameLayout krazek = new FrameLayout(c);
            GradientDrawable krazekTlo = new GradientDrawable();
            krazekTlo.setShape(GradientDrawable.OVAL);
            krazekTlo.setColor(0x1AFFFFFF);
            krazekTlo.setStroke(Math.max(1, dp(c, 0.5f)), 0x38FFFFFF);
            krazek.setBackground(krazekTlo);
            krazek.addView(herby[i], new FrameLayout.LayoutParams(dp(c, 52), dp(c, 52), Gravity.CENTER));
            kolumny[i].addView(krazek, new LinearLayout.LayoutParams(dp(c, 84), dp(c, 84)));
            nazwy[i].setGravity(Gravity.CENTER);
            nazwy[i].setMaxLines(2);
            nazwy[i].setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.topMargin = dp(c, 12);
            kolumny[i].addView(nazwy[i], nlp);
        }
        druzyny.addView(kDom, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout srodek = new LinearLayout(c);
        srodek.setOrientation(LinearLayout.VERTICAL);
        srodek.setGravity(Gravity.CENTER_HORIZONTAL);
        final TextView tWynik = tekst(c, "", 40, "display", Color.WHITE);
        tWynik.setGravity(Gravity.CENTER);
        srodek.addView(tWynik);
        final TextView tStatus = tekst(c, "", 10, "semibold", Color.WHITE);
        tStatus.setPadding(dp(c, 9), dp(c, 4), dp(c, 9), dp(c, 4));
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stlp.topMargin = dp(c, 10);
        srodek.addView(tStatus, stlp);
        druzyny.addView(srodek, new LinearLayout.LayoutParams(dp(c, 150), ViewGroup.LayoutParams.WRAP_CONTENT));
        druzyny.addView(kGosc, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout przyciski = new LinearLayout(c);
        przyciski.setOrientation(LinearLayout.HORIZONTAL);
        przyciski.setGravity(Gravity.CENTER_VERTICAL);
        przyciski.setClipChildren(false);
        final TextView bDom = przycisk(c, "", BLEKIT), bGosc = przycisk(c, "", BLEKIT);
        przyciski.addView(bDom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 31)));
        LinearLayout.LayoutParams bglp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 31));
        bglp.leftMargin = dp(c, 8);
        przyciski.addView(bGosc, bglp);
        LinearLayout.LayoutParams przlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        przlp.topMargin = dp(c, 16);
        przlp.leftMargin = dp(c, 3);
        prawa.addView(przyciski, przlp);

        // nakładka z listą (wybór ligi, ulubione drużyny) - nad całym oknem
        final FrameLayout nakladka = new FrameLayout(c);
        nakladka.setBackgroundColor(0x73000000);
        nakladka.setVisibility(View.GONE);
        nakladka.setClickable(true);
        korzen.addView(nakladka, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout okienko = new LinearLayout(c);
        okienko.setOrientation(LinearLayout.VERTICAL);
        okienko.setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 16));
        boolean tapetaJest = host.glassBg != null && !host.glassBg.isRecycled();
        MainActivity.LiquidGlass og = new MainActivity.LiquidGlass(dp(c, 22), tapetaJest ? 0xA6120E18 : 0xF214141C,
                host.glassSource, host.screenW, host.screenH, host.unit);
        og.lens = dp(c, 6);
        og.rimStrength = 0.75f;
        okienko.setBackground(og);
        FrameLayout.LayoutParams oklp = new FrameLayout.LayoutParams(dp(c, 360), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        oklp.topMargin = dp(c, 36);
        oklp.bottomMargin = dp(c, 36);
        nakladka.addView(okienko, oklp);
        final TextView tytulNakladki = tekst(c, "", 16, "display", Color.WHITE);
        okienko.addView(tytulNakladki);
        final TextView opisNakladki = tekst(c, "", 9, "medium", 0xC7FFFFFF);
        LinearLayout.LayoutParams onlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        onlp.topMargin = dp(c, 4);
        okienko.addView(opisNakladki, onlp);
        ScrollView svN = new ScrollView(c);
        svN.setVerticalScrollBarEnabled(false);
        svN.setClipToPadding(false);
        svN.setPadding(dp(c, 3), dp(c, 5), dp(c, 5), dp(c, 5));
        final LinearLayout kolN = new LinearLayout(c);
        kolN.setOrientation(LinearLayout.VERTICAL);
        svN.addView(kolN);
        LinearLayout.LayoutParams svnlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        svnlp.topMargin = dp(c, 8);
        okienko.addView(svN, svnlp);

        final int[] wybrany = {0};
        final List<View> wiersze = new ArrayList<View>();
        final List<TextView[]> napisyWierszy = new ArrayList<TextView[]>();   // {para, czas, wynik}
        final boolean[] zmieniono = {false};
        final View[] powrotFokusu = {bLiga};

        final Runnable odswiezGwiazdki = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < mecze.size() && i < napisyWierszy.size(); i++) {
                    HomeDashboard.MatchItem m = mecze.get(i);
                    boolean f = ModPlMecze.ulubiony(c, m);
                    TextView[] t = napisyWierszy.get(i);
                    t[0].setText((f ? "★ " : "") + m.homeTeam + " – " + m.awayTeam);
                    t[0].setTextColor(f ? 0xFFFDE047 : Color.WHITE);
                }
            }
        };

        final Runnable pokazPodglad = new Runnable() {
            @Override
            public void run() {
                if (mecze.isEmpty()) return;
                final HomeDashboard.MatchItem m = mecze.get(wybrany[0]);
                tLiga.setText(m.leagueName == null ? "" : m.leagueName.toUpperCase(I18n.locale(c)));
                if (m.startEpochMs > 0) {
                    String kiedy = new SimpleDateFormat("EEEE, d MMMM · HH:mm", I18n.locale(c)).format(new Date(m.startEpochMs));
                    tData.setText(kiedy.isEmpty() ? "" : kiedy.substring(0, 1).toUpperCase(I18n.locale(c)) + kiedy.substring(1));
                } else {
                    tData.setText("");
                }
                nDom.setText(m.homeTeam);
                nGosc.setText(m.awayTeam);
                tWynik.setText(m.isLive || m.isFinished ? m.homeScore + " : " + m.awayScore
                        : (m.startEpochMs > 0 ? new SimpleDateFormat("HH:mm", I18n.locale(c)).format(new Date(m.startEpochMs)) : "–"));
                String st = status(c, m);
                if (!m.isLive && !m.isFinished && m.startEpochMs > 0) {
                    // godzina jest już duża nad znaczkiem - w znaczku zostaje sam dzień
                    String godz = new SimpleDateFormat("HH:mm", I18n.locale(c)).format(new Date(m.startEpochMs));
                    if (st.endsWith(" " + godz)) st = st.substring(0, st.length() - godz.length() - 1);
                }
                tStatus.setText(st.toUpperCase(I18n.locale(c)));
                tStatus.setBackground(tlo(m.isLive ? 0xFF16A34A : 0x33FFFFFF, dp(c, 6), 0, 0));
                final String k = klucz(m);
                if (!k.equals(herbDom.getTag())) {
                    herbDom.setTag(k);
                    herbDom.setImageDrawable(null);
                    herbGosc.setImageDrawable(null);
                    wczytaj(c, m.homeLogo, dp(c, 92), new Ladowanie() {
                        @Override
                        public void ok(Bitmap b) {
                            if (k.equals(herbDom.getTag())) herbDom.setImageBitmap(b);
                        }
                    });
                    wczytaj(c, m.awayLogo, dp(c, 92), new Ladowanie() {
                        @Override
                        public void ok(Bitmap b) {
                            if (k.equals(herbDom.getTag())) herbGosc.setImageBitmap(b);
                        }
                    });
                }
                TextView[] bt = {bDom, bGosc};
                String[] dr = {m.homeTeam, m.awayTeam};
                final Runnable ja = this;
                for (int i = 0; i < 2; i++) {
                    final String druzyna = dr[i];
                    final boolean f = ModPlMecze.ulubionaDruzyna(c, druzyna, m.glowna);
                    bt[i].setText((f ? "★ " + I18n.t(c, "Following ") : "☆ " + I18n.t(c, "Follow ")) + druzyna);
                    bt[i].setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (f) {
                                for (String u : HomeDashboard.getFavoriteTeams(c)) {
                                    if (ModPlMecze.pasujeDo(druzyna, u, m.glowna)) HomeDashboard.toggleFavoriteTeam(c, u);
                                }
                            } else {
                                HomeDashboard.toggleFavoriteTeam(c, druzyna);
                            }
                            zmieniono[0] = true;
                            odswiezGwiazdki.run();
                            ja.run();
                        }
                    });
                }
            }
        };

        // Po zmianie ligi albo ulubionych: pobierz (zwykle z pamięci) i pokaż okno od nowa.
        final Runnable odNowa = new Runnable() {
            @Override
            public void run() {
                zmieniono[0] = false;
                HomeDashboard.fetchFootballMatches(c, true, new HomeDashboard.FootballCallback() {
                    @Override
                    public void onResult(List<HomeDashboard.MatchItem> wynik) {
                        if (poZmianie != null) poZmianie.run();
                        if (d.isShowing()) {
                            d.dismiss();
                            pokazMecze(host, poZmianie);
                        }
                    }
                });
            }
        };

        final Runnable zamknijNakladke = new Runnable() {
            @Override
            public void run() {
                nakladka.setVisibility(View.GONE);
                p.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
                if (zmieniono[0]) odNowa.run();
                else powrotFokusu[0].requestFocus();
            }
        };

        final View.OnKeyListener klawiszeNakladki = new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int code, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
                int i = kolN.indexOfChild(v);
                if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    // Zaznaczenie przenosimy sami na sąsiedni wiersz listy. Systemowe szukanie brało też pod uwagę
                    // przyciski i wiersze okna pod nakładką, więc przy krótkiej liście zaznaczenie uciekało pod spód.
                    int krok = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
                    for (int k = i + krok; k >= 0 && k < kolN.getChildCount(); k += krok) {
                        View n = kolN.getChildAt(k);
                        if (n.isFocusable() && n.getVisibility() == View.VISIBLE) {
                            n.requestFocus();
                            return true;
                        }
                    }
                    return true;   // pierwszy albo ostatni wiersz: zaznaczenie zostaje na miejscu
                }
                return false;
            }
        };

        // --- lista lig
        bLiga.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                powrotFokusu[0] = bLiga;
                tytulNakladki.setText(I18n.t(c, "Choose a league"));
                opisNakladki.setText(I18n.t(c, "Followed teams' matches are always on top."));
                kolN.removeAllViews();
                View doFokusu = null;
                for (final String[] lg : HomeDashboard.LEAGUES) {
                    boolean ta = lg[0].equals(liga);
                    TextView w = wierszListy(c, (ta ? "✓  " : "     ") + I18n.t(c, lg[1]));
                    if (ta) {
                        w.setTextColor(BLEKIT_JASNY);
                        doFokusu = w;
                    }
                    w.setOnKeyListener(klawiszeNakladki);
                    w.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View x) {
                            if (lg[0].equals(liga)) {
                                zamknijNakladke.run();
                                return;
                            }
                            HomeDashboard.setSelectedLeague(c, lg[0]);
                            ((TextView) x).setText("…  " + I18n.t(c, lg[1]));
                            zmieniono[0] = true;
                            zamknijNakladke.run();
                        }
                    });
                    LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 32));
                    if (kolN.getChildCount() > 0) wlp.topMargin = dp(c, 3);
                    kolN.addView(w, wlp);
                }
                p.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                nakladka.setVisibility(View.VISIBLE);
                if (doFokusu == null) doFokusu = kolN.getChildAt(0);
                doFokusu.requestFocus();
            }
        });

        // --- ulubione drużyny
        final Runnable[] zbudujUlubione = new Runnable[1];
        zbudujUlubione[0] = new Runnable() {
            @Override
            public void run() {
                View zFokusem = kolN.findFocus();
                int indeks = zFokusem == null ? -1 : kolN.indexOfChild(zFokusem);
                kolN.removeAllViews();
                List<String> moje = new ArrayList<String>(HomeDashboard.getFavoriteTeams(c));
                java.util.Collections.sort(moje, String.CASE_INSENSITIVE_ORDER);
                List<String[]> pozycje = new ArrayList<String[]>();   // {nazwa, "1" = obserwowana}
                for (String u : moje) pozycje.add(new String[]{u, "1"});
                for (String pop : HomeDashboard.POPULAR_TEAMS) {
                    boolean jest = false;
                    for (String u : moje) if (bezOgonkow(u).equals(bezOgonkow(pop))) jest = true;
                    if (!jest) pozycje.add(new String[]{pop, "0"});
                }
                TextView dodaj = wierszListy(c, "＋  " + I18n.t(c, "Add another team…"));
                dodaj.setOnKeyListener(klawiszeNakladki);
                dodaj.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View x) {
                        final android.widget.EditText in = new android.widget.EditText(c);
                        in.setSingleLine(true);
                        in.setHint(I18n.t(c, "e.g. Arsenal, Legia, Poland"));
                        android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(c, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                .setTitle(I18n.t(c, "Add Favorite Football Team"))
                                .setView(in)
                                .setPositiveButton(I18n.t(c, "Add"), new android.content.DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(android.content.DialogInterface di, int w) {
                                        String nazwa = in.getText().toString().trim();
                                        if (!nazwa.isEmpty()) {
                                            HomeDashboard.addCustomFavoriteTeam(c, nazwa);
                                            zmieniono[0] = true;
                                            zbudujUlubione[0].run();
                                        }
                                    }
                                })
                                .setNegativeButton(I18n.t(c, "Cancel"), null)
                                .show();
                        Ui.setupDialogEditText(in, dlg);
                        in.requestFocus();
                    }
                });
                kolN.addView(dodaj, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 32)));
                boolean naglowekDodany = false;
                for (final String[] poz : pozycje) {
                    final boolean obs = "1".equals(poz[1]);
                    if (!obs && !naglowekDodany) {
                        naglowekDodany = true;
                        TextView n = tekst(c, I18n.t(c, "Popular teams").toUpperCase(I18n.locale(c)), 8, "semibold", 0x99BAE6FD);
                        n.setLetterSpacing(0.1f);
                        n.setPadding(dp(c, 6), dp(c, 10), 0, dp(c, 4));
                        kolN.addView(n);
                    }
                    TextView w = wierszListy(c, (obs ? "★  " : "☆  ") + poz[0]);
                    if (obs) w.setTextColor(0xFFFDE047);
                    w.setOnKeyListener(klawiszeNakladki);
                    w.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View x) {
                            HomeDashboard.toggleFavoriteTeam(c, poz[0]);
                            zmieniono[0] = true;
                            zbudujUlubione[0].run();
                        }
                    });
                    LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 32));
                    if (kolN.getChildCount() > 0) wlp.topMargin = dp(c, 3);
                    kolN.addView(w, wlp);
                }
                View cel = null;
                for (int k = Math.max(0, Math.min(indeks, kolN.getChildCount() - 1)); k < kolN.getChildCount(); k++) {
                    if (kolN.getChildAt(k).isFocusable()) {
                        cel = kolN.getChildAt(k);
                        break;
                    }
                }
                if (cel == null) {
                    for (int k = kolN.getChildCount() - 1; k >= 0 && cel == null; k--) {
                        if (kolN.getChildAt(k).isFocusable()) cel = kolN.getChildAt(k);
                    }
                }
                if (cel != null) cel.requestFocus();
            }
        };
        bUlubione.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                powrotFokusu[0] = bUlubione;
                tytulNakladki.setText("★ " + I18n.t(c, "football_fav_teams"));
                opisNakladki.setText(I18n.t(c, "OK adds or removes a team. Any team can be followed from its match."));
                p.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                nakladka.setVisibility(View.VISIBLE);
                zbudujUlubione[0].run();
            }
        });

        d.setOnKeyListener(new android.content.DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(android.content.DialogInterface di, int code, KeyEvent e) {
                if (code == KeyEvent.KEYCODE_BACK && nakladka.getVisibility() == View.VISIBLE) {
                    if (e.getAction() == KeyEvent.ACTION_UP) zamknijNakladke.run();
                    return true;
                }
                return false;
            }
        });

        View.OnKeyListener wroc = new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int code, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT && v == bDom && !wiersze.isEmpty()) {
                    wiersze.get(wybrany[0]).requestFocus();
                    return true;
                }
                return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
            }
        };
        bDom.setOnKeyListener(wroc);
        bGosc.setOnKeyListener(wroc);
        View.OnKeyListener opcjeKlawisze = new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int code, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code == KeyEvent.KEYCODE_DPAD_DOWN && !wiersze.isEmpty()) {
                    wiersze.get(wybrany[0]).requestFocus();
                    return true;
                }
                return code == KeyEvent.KEYCODE_DPAD_UP || (code == KeyEvent.KEYCODE_DPAD_LEFT && v == bLiga)
                        || (code == KeyEvent.KEYCODE_DPAD_RIGHT && v == bUlubione);
            }
        };
        bLiga.setOnKeyListener(opcjeKlawisze);
        bUlubione.setOnKeyListener(opcjeKlawisze);

        if (mecze.isEmpty()) {
            final TextView pusto = tekst(c, I18n.t(c, "football_loading"), 11, "medium", 0x99FFFFFF);
            pusto.setPadding(dp(c, 4), dp(c, 16), dp(c, 4), dp(c, 16));
            kolumna.addView(pusto);
            tWynik.setText("–");
            bDom.setVisibility(View.GONE);
            bGosc.setVisibility(View.GONE);
            // Okno otwarte przed pierwszym pobraniem: pobierz i pokaż od nowa, gdy mecze przyjdą.
            HomeDashboard.fetchFootballMatches(c, true, new HomeDashboard.FootballCallback() {
                @Override
                public void onResult(List<HomeDashboard.MatchItem> wynik) {
                    if (!d.isShowing()) return;
                    if (wynik != null && !wynik.isEmpty()) {
                        if (poZmianie != null) poZmianie.run();
                        d.dismiss();
                        pokazMecze(host, poZmianie);
                    } else {
                        pusto.setText(I18n.t(c, "football_no_matches"));
                    }
                }
            });
        }

        long teraz = System.currentTimeMillis();
        int poprzedniaSekcja = -1;
        for (int i = 0; i < mecze.size(); i++) {
            final int idx = i;
            final HomeDashboard.MatchItem m = mecze.get(i);
            int sek = ModPlMecze.sekcja(m, teraz);
            if (sek != poprzedniaSekcja) {
                poprzedniaSekcja = sek;
                TextView n = tekst(c, I18n.t(c, ModPlMecze.NAZWY_SEKCJI[sek]).toUpperCase(I18n.locale(c)), 8, "semibold",
                        sek == 0 ? 0xFF4ADE80 : 0x99BAE6FD);
                n.setLetterSpacing(0.1f);
                n.setPadding(dp(c, 6), dp(c, i == 0 ? 2 : 12), 0, dp(c, 5));
                kolumna.addView(n);
            }
            LinearLayout w = new LinearLayout(c);
            w.setOrientation(LinearLayout.HORIZONTAL);
            w.setGravity(Gravity.CENTER_VERTICAL);
            w.setPadding(dp(c, 10), 0, dp(c, 10), 0);
            w.setFocusable(true);
            w.setClickable(true);
            ustawWiersz(c, w, 0, BLEKIT, BLEKIT_RAMKA);

            TextView czas = tekst(c, m.isFinished ? I18n.t(c, "Full time") : status(c, m), 8, "semibold",
                    m.isLive ? 0xFF4ADE80 : 0x99FFFFFF);
            czas.setSingleLine(true);
            czas.setEllipsize(TextUtils.TruncateAt.END);
            w.addView(czas, new LinearLayout.LayoutParams(dp(c, 80), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView para = tekst(c, "", 10.5f, "semibold", Color.WHITE);
            para.setSingleLine(true);
            para.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams plp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            plp2.leftMargin = dp(c, 6);
            w.addView(para, plp2);
            TextView sc = tekst(c, wynik(m), 10.5f, "display", m.isLive ? 0xFF4ADE80 : Color.WHITE);
            LinearLayout.LayoutParams sclp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sclp.leftMargin = dp(c, 6);
            w.addView(sc, sclp);
            napisyWierszy.add(new TextView[]{para, czas, sc});

            w.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean jest) {
                    if (jest && wybrany[0] != idx) {
                        int stary = wybrany[0];
                        wybrany[0] = idx;
                        if (stary >= 0 && stary < wiersze.size()) ustawWiersz(c, wiersze.get(stary), 0, BLEKIT, BLEKIT_RAMKA);
                        pokazPodglad.run();
                    }
                    ustawWiersz(c, v, jest ? 1 : (wybrany[0] == idx ? 2 : 0), BLEKIT, BLEKIT_RAMKA);
                }
            });
            w.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    bDom.requestFocus();
                }
            });
            w.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int code, KeyEvent e) {
                    if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                    if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        bDom.requestFocus();
                        return true;
                    }
                    if (code == KeyEvent.KEYCODE_DPAD_LEFT) return true;
                    if (code == KeyEvent.KEYCODE_DPAD_UP && idx == 0) {
                        bLiga.requestFocus();
                        return true;
                    }
                    return code == KeyEvent.KEYCODE_DPAD_DOWN && idx == mecze.size() - 1;
                }
            });
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 34));
            wlp.topMargin = dp(c, 3);
            kolumna.addView(w, wlp);
            wiersze.add(w);
        }
        odswiezGwiazdki.run();

        // Wyniki i minuty na żywo: co minutę, póki okno jest otwarte i coś trwa albo zaraz się zaczyna.
        final Runnable[] tykanie = new Runnable[1];
        tykanie[0] = new Runnable() {
            @Override
            public void run() {
                if (!d.isShowing()) return;
                if (ModPlMecze.cosSieDzieje(mecze)) {
                    HomeDashboard.fetchFootballMatches(c, true, new HomeDashboard.FootballCallback() {
                        @Override
                        public void onResult(List<HomeDashboard.MatchItem> nowe) {
                            if (!d.isShowing() || nowe == null) return;
                            java.util.Map<String, HomeDashboard.MatchItem> poKluczu = new java.util.HashMap<String, HomeDashboard.MatchItem>();
                            for (HomeDashboard.MatchItem n : nowe) poKluczu.put(klucz(n), n);
                            for (int i = 0; i < mecze.size() && i < napisyWierszy.size(); i++) {
                                HomeDashboard.MatchItem n = poKluczu.get(klucz(mecze.get(i)));
                                if (n == null) continue;
                                mecze.set(i, n);
                                TextView[] t = napisyWierszy.get(i);
                                t[1].setText(n.isFinished ? I18n.t(c, "Full time") : status(c, n));
                                t[1].setTextColor(n.isLive ? 0xFF4ADE80 : 0x99FFFFFF);
                                t[2].setText(wynik(n));
                                t[2].setTextColor(n.isLive ? 0xFF4ADE80 : Color.WHITE);
                            }
                            pokazPodglad.run();
                            if (poZmianie != null) poZmianie.run();
                        }
                    });
                }
                GLOWNY.postDelayed(this, 60_000L);
            }
        };
        GLOWNY.postDelayed(tykanie[0], 60_000L);

        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface di) {
                GLOWNY.removeCallbacks(tykanie[0]);
                if (zmieniono[0]) {
                    HomeDashboard.fetchFootballMatches(c, true, new HomeDashboard.FootballCallback() {
                        @Override
                        public void onResult(List<HomeDashboard.MatchItem> w) {
                            if (poZmianie != null) poZmianie.run();
                        }
                    });
                }
            }
        });

        pokazPodglad.run();
        d.show();
        if (!wiersze.isEmpty()) wiersze.get(0).requestFocus();
        else bLiga.requestFocus();
        return true;
    }
}
