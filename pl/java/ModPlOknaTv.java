package com.techgenius.tvoslauncher;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mod PL: okna widżetów w stylu tvOS 27 (wybór z kanwy 24.11.2026): filmy, mecze, wiadomości i panel pogody.
 * Okno zajmuje cały ekran (pogoda: panel z boku), kolor bierze się z treści (kadr filmu, herby drużyn, tapeta),
 * a zaznaczenie to biała pigułka z czarnym tekstem albo powiększona karta z białą ramką.
 * Wymiary są podawane w pikselach kanwy 1920×1080 i przeliczane funkcją {@link #j} na piksele ekranu.
 * Plik źródłowy leży w pl/java/, a pl/naloz.py kopiuje go do src/ przy każdym buildzie.
 */
final class ModPlOknaTv {
    private ModPlOknaTv() { }

    private static final Handler GLOWNY = new Handler(Looper.getMainLooper());
    /** Małe herby na liście meczów: osobna kolejka, żeby nie blokowały obrazów podglądu (kolejka ModPlOkna). */
    private static final ExecutorService HERBY = Executors.newFixedThreadPool(2);

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    static final int BIALY = 0xFFFFFFFF;
    /** Tekst drugorzędny: biel 86%, 78%, 70% i 62% (tvOS: podpisy jaśnieją w fokusie do 100%). */
    static final int TEKST_86 = 0xDBFFFFFF, TEKST_78 = 0xC7FFFFFF, TEKST_70 = 0xB3FFFFFF, TEKST_62 = 0x9EFFFFFF;
    /** Tekst na białej pigułce fokusu. */
    private static final int CZARNY = 0xFF000000, CZARNY_62 = 0x9E000000;
    /** Mecz na żywo: zieleń na ciemnym tle, ciemniejsza na białej pigułce i tło znaczka stanu. */
    private static final int ZIELEN = 0xFF4ADE80, ZIELEN_CIEMNA = 0xFF15803D, ZIELEN_TLO = 0xFF16A34A;
    /** Szkło przycisku bez fokusu: biel 16% z jasną krawędzią. */
    private static final int SZKLO = 0x29FFFFFF, SZKLO_RAMKA = 0x42FFFFFF;
    private static final int TLO_OKNA = 0xFF05080B;

    /** Ostatnie duże kadry (każdy to ok. 7 MB), żeby powrót do tytułu nie pobierał go od nowa. */
    private static final Map<String, Bitmap> KADRY = new LinkedHashMap<String, Bitmap>(4, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) {
            return size() > 3;
        }
    };

    // ------------------------------------------------------------------ wspólne

    /** Piksele kanwy 1920×1080 → piksele tego ekranu. */
    static int j(Context c, float pxKanwy) {
        return Math.round(pxKanwy * Ui.unit(c) / 2f);
    }

    static TextView napis(Context c, String s, float pxKanwy, String waga, int kolor) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, pxKanwy * Ui.unit(c) / 2f);
        t.setTypeface(Ui.font(c, waga));
        t.setTextColor(kolor);
        t.setIncludeFontPadding(false);
        return t;
    }

    static GradientDrawable ksztalt(int kolor, float promien, int grubosc, int ramka) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(kolor);
        g.setCornerRadius(promien);
        if (grubosc > 0) g.setStroke(grubosc, ramka);
        return g;
    }

    private static void bezSystemowegoPodswietlenia(View v) {
        if (Build.VERSION.SDK_INT >= 26) v.setDefaultFocusHighlightEnabled(false);
    }

    /** Okno na cały ekran nad ekranem głównym; klawisz HOME zamyka je tak jak pozostałe okna widżetów. */
    static Dialog okno(MainActivity host, FrameLayout korzen) {
        Dialog d = new Dialog(host);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        korzen.setClipChildren(false);
        d.setContentView(korzen, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // Jeszcze raz po setContentView: tworząc dekorację okna pływającego system wraca do rozmiaru „na treść”
        // i na Androidzie 13 okno z samą listą wychodziło o 40 px węższe z każdej strony.
        if (w != null) w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        HomeDashboard.modPlOkno(d);
        return d;
    }

    /** Tytuł okna i źródło danych w lewym górnym rogu oraz mała kapsuła z godziną w prawym. */
    static void naglowek(Context c, FrameLayout korzen, String tytul, String zrodlo) {
        LinearLayout wiersz = new LinearLayout(c);
        wiersz.setOrientation(LinearLayout.HORIZONTAL);
        TextView t = napis(c, tytul, 38, "semibold", BIALY);
        t.setLetterSpacing(-0.01f);
        wiersz.addView(t);
        TextView z = napis(c, zrodlo, 25, "medium", TEKST_78);
        LinearLayout.LayoutParams zlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        zlp.leftMargin = j(c, 20);
        wiersz.addView(z, zlp);
        FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
        wlp.leftMargin = j(c, 80);
        wlp.topMargin = j(c, 62);
        korzen.addView(wiersz, wlp);

        TextClock zegar = new TextClock(c);
        zegar.setFormat24Hour("HH:mm");
        zegar.setFormat12Hour("h:mm");
        zegar.setTextSize(TypedValue.COMPLEX_UNIT_PX, 25 * Ui.unit(c) / 2f);
        zegar.setTypeface(Ui.font(c, "semibold"));
        zegar.setTextColor(BIALY);
        zegar.setIncludeFontPadding(false);
        zegar.setGravity(Gravity.CENTER);
        zegar.setPadding(j(c, 24), 0, j(c, 24), 0);
        zegar.setBackground(ksztalt(SZKLO, j(c, 28), Math.max(1, j(c, 1)), SZKLO_RAMKA));
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, j(c, 56), Gravity.TOP | Gravity.RIGHT);
        glp.rightMargin = j(c, 80);
        glp.topMargin = j(c, 56);
        korzen.addView(zegar, glp);
    }

    /** Przycisk-kapsuła: bez fokusu szkło z jasną krawędzią, w fokusie biały z czarnym tekstem i lekko większy. */
    static TextView przycisk(final Context c, String tekst) {
        return przycisk(c, tekst, 29, 40);
    }

    /** Jak wyżej, z własną wielkością tekstu i marginesem bocznym (piksele kanwy), np. niższa kapsuła filtra. */
    static TextView przycisk(final Context c, String tekst, float pxTekstu, float pxBoku) {
        final TextView b = napis(c, tekst, pxTekstu, "semibold", BIALY);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setPadding(j(c, pxBoku), 0, j(c, pxBoku), 0);
        b.setFocusable(true);
        b.setClickable(true);
        bezSystemowegoPodswietlenia(b);
        stanPrzycisku(c, b, false);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean jest) {
                stanPrzycisku(c, b, jest);
                v.animate().scaleX(jest ? 1.06f : 1f).scaleY(jest ? 1.06f : 1f).setDuration(160).start();
            }
        });
        return b;
    }

    private static void stanPrzycisku(Context c, TextView b, boolean fokus) {
        float r = j(c, 33);
        if (fokus) {
            b.setBackground(ksztalt(BIALY, r, 0, 0));
            b.setTextColor(0xFF000000);
            b.setElevation(j(c, 14));
        } else {
            b.setBackground(ksztalt(SZKLO, r, Math.max(1, j(c, 1)), SZKLO_RAMKA));
            b.setTextColor(BIALY);
            b.setElevation(0f);
        }
    }

    private static boolean serial(Suggestions.Item it) {
        return "tv".equalsIgnoreCase(it.type) || "series".equalsIgnoreCase(it.type);
    }

    // ------------------------------------------------------------------ Najczęściej oglądane i Top filmy i seriale

    /** Jedna karta na półce: całość łapie fokus, obraz rośnie i dostaje białą ramkę, gdy tytuł jest wybrany. */
    private static final class Karta {
        LinearLayout calosc;
        View obraz, ramka;
        TextView tytul, dol;
    }

    private static void wygladKarty(Context c, Karta k, boolean wybrana, float skala) {
        k.obraz.animate().scaleX(wybrana ? skala : 1f).scaleY(wybrana ? skala : 1f).setDuration(170).start();
        k.obraz.setElevation(wybrana ? j(c, 20) : j(c, 5));
        k.ramka.setVisibility(wybrana ? View.VISIBLE : View.INVISIBLE);
        k.tytul.setTextColor(wybrana ? BIALY : TEKST_78);
        k.dol.setTextColor(wybrana ? TEKST_86 : TEKST_62);
    }

    /** Kadr w największym rozmiarze, jaki daje źródło (JustWatch: 1920 px zamiast 1440). */
    private static String duzyKadr(Suggestions.Item it) {
        String u = it.backdrop == null ? "" : it.backdrop;
        return u.contains("images.justwatch.com") ? u.replace("/s1440/", "/s1920/") : u;
    }

    /** „49 min”, „2 godz. 28 min”. */
    private static String czasTrwania(Context c, int minut) {
        if (minut < 60) return String.format(Locale.US, "%d min", minut);
        if (minut % 60 == 0) return String.format(Locale.US, I18n.t(c, "%d h"), minut / 60);
        return String.format(Locale.US, I18n.t(c, "%d h %d min"), minut / 60, minut % 60);
    }

    /** Oznaczenie wieku ze źródła: sama liczba dostaje plus („16+”), inne zapisy (PG-13, TV-MA) zostają bez zmian. */
    private static String oznaczenieWieku(String w) {
        if (w == null) return "";
        w = w.trim();
        if (w.isEmpty() || w.length() > 8 || "0".equals(w) || "null".equalsIgnoreCase(w)) return "";
        return w.matches("\\d+") ? w + "+" : w;
    }

    private static String miejsce(Context c, boolean ranking, boolean zakladkaSeriali, String kraje, int nr) {
        if (ranking) {
            return String.format(I18n.locale(c), I18n.t(c, zakladkaSeriali ? "No. %d among TV shows" : "No. %d among films"), nr);
        }
        if ("PL".equalsIgnoreCase(kraje)) return String.format(I18n.locale(c), I18n.t(c, "No. %d in Poland"), nr);
        return String.format(I18n.locale(c), I18n.t(c, "No. %d"), nr) + (kraje == null || kraje.isEmpty() ? "" : "  ·  " + kraje);
    }

    /**
     * Okno filmów: kadr wybranego tytułu na całym ekranie, po lewej tytuł, ocena, opis i przyciski, na dole półka kart.
     * {@code ranking}: okno „Top filmy i seriale” (plakaty z numerami miejsc i zakładki Seriale / Filmy);
     * bez niego „Najczęściej oglądane” (poziome kadry z numerem miejsca). Zwraca true, gdy okno zostało pokazane.
     */
    static boolean pokazFilmy(final MainActivity host, List<Suggestions.Item> wszystkie, String zrodlo, String tytulOkna,
                              final boolean ranking, final String kraje, int start) {
        if (wszystkie == null || wszystkie.isEmpty()) return false;
        final Context c = host;
        final List<Suggestions.Item> wszystko = new ArrayList<Suggestions.Item>(
                wszystkie.subList(0, Math.min(ranking ? 24 : 20, wszystkie.size())));
        final List<Suggestions.Item> seriale = new ArrayList<Suggestions.Item>(), filmy = new ArrayList<Suggestions.Item>();
        for (Suggestions.Item it : wszystko) (serial(it) ? seriale : filmy).add(it);
        final boolean zakladki = ranking && !seriale.isEmpty() && !filmy.isEmpty();
        final Suggestions.Item poczatek = wszystko.get(Math.max(0, Math.min(start, wszystko.size() - 1)));
        final List<Suggestions.Item> lista = new ArrayList<Suggestions.Item>();
        final int[] zakladka = {zakladki && !serial(poczatek) ? 1 : (ranking && seriale.isEmpty() ? 1 : 0)};
        lista.addAll(zakladki ? (zakladka[0] == 0 ? seriale : filmy) : wszystko);
        final int[] wybrany = {Math.max(0, lista.indexOf(poczatek))};

        final FrameLayout korzen = new FrameLayout(c);
        korzen.setBackgroundColor(TLO_OKNA);
        final Dialog d = okno(host, korzen);

        // kadr na cały ekran i przyciemnienia pod tekst: u góry, z lewej i u dołu
        final ImageView kadr = new ImageView(c);
        kadr.setScaleType(ImageView.ScaleType.CENTER_CROP);
        korzen.addView(kadr, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View gora = new View(c);
        gora.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xA803090C, 0x0003090C}));
        korzen.addView(gora, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, j(c, 260), Gravity.TOP));
        View lewy = new View(c);
        lewy.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xE803090C, 0xA603090C, 0x3803090C, 0x0003090C}));
        korzen.addView(lewy, new FrameLayout.LayoutParams(j(c, 1380), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT));
        View dolny = new View(c);
        dolny.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xF703090C, 0xD603090C, 0x6B03090C, 0x0003090C}));
        korzen.addView(dolny, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, j(c, ranking ? 640 : 600), Gravity.BOTTOM));

        naglowek(c, korzen, tytulOkna, zrodlo);

        // zakładki Seriale / Filmy (tylko ranking)
        final TextView[] zakl = new TextView[2];
        if (zakladki) {
            LinearLayout pasek = new LinearLayout(c);
            pasek.setOrientation(LinearLayout.HORIZONTAL);
            pasek.setPadding(j(c, 4), j(c, 4), j(c, 4), j(c, 4));
            pasek.setBackground(ksztalt(0x24FFFFFF, j(c, 28), Math.max(1, j(c, 1)), 0x38FFFFFF));
            String[] nazwy = {I18n.t(c, "TV shows"), I18n.t(c, "Films")};
            for (int k = 0; k < 2; k++) {
                TextView z = napis(c, nazwy[k], 25, "semibold", BIALY);
                z.setGravity(Gravity.CENTER);
                z.setPadding(j(c, 28), 0, j(c, 28), 0);
                z.setFocusable(true);
                bezSystemowegoPodswietlenia(z);
                LinearLayout.LayoutParams zlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, j(c, 46));
                if (k > 0) zlp.leftMargin = j(c, 4);
                pasek.addView(z, zlp);
                zakl[k] = z;
            }
            FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
            plp.leftMargin = j(c, 80);
            plp.topMargin = j(c, 130);
            korzen.addView(pasek, plp);
        }

        // lewa strona: miejsce, tytuł, ocena z opisem i przyciski; blok rośnie w górę od półki
        LinearLayout blok = new LinearLayout(c);
        blok.setOrientation(LinearLayout.VERTICAL);
        blok.setClipChildren(false);
        final TextView miejsceT = napis(c, "", 25, "semibold", TEKST_78);
        blok.addView(miejsceT);
        final TextView tytul = napis(c, "", 76, "display", BIALY);
        tytul.setLetterSpacing(-0.02f);
        tytul.setMaxLines(2);
        tytul.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = j(c, 8);
        blok.addView(tytul, tlp);
        LinearLayout meta = new LinearLayout(c);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        final TextView ocena = napis(c, "", 25, "semibold", BIALY);
        meta.addView(ocena);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        final TextView metaTekst = napis(c, "", 25, "medium", TEKST_86);
        metaTekst.setSingleLine(true);
        metaTekst.setEllipsize(TextUtils.TruncateAt.END);
        // waga 1 przy szerokości „na treść”: długi tekst skraca się wielokropkiem, a znaczek wieku zawsze się mieści
        meta.addView(metaTekst, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView wiek = napis(c, "", 21, "semibold", TEKST_86);
        wiek.setGravity(Gravity.CENTER);
        wiek.setSingleLine(true);
        wiek.setPadding(j(c, 9), 0, j(c, 9), 0);
        wiek.setBackground(ksztalt(Color.TRANSPARENT, j(c, 7), Math.max(1, j(c, 2)), 0xB3FFFFFF));
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, j(c, 30));
        wlp.leftMargin = j(c, 18);
        meta.addView(wiek, wlp);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = j(c, 14);
        blok.addView(meta, mlp);
        final TextView opis = napis(c, "", 27, "regular", 0xD1FFFFFF);
        opis.setLineSpacing(0f, 1.24f);
        opis.setMaxLines(3);
        opis.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(j(c, 860), ViewGroup.LayoutParams.WRAP_CONTENT);
        olp.topMargin = j(c, 18);
        blok.addView(opis, olp);
        LinearLayout przyciski = new LinearLayout(c);
        przyciski.setOrientation(LinearLayout.HORIZONTAL);
        przyciski.setClipChildren(false);
        final TextView bZwiastun = przycisk(c, "▶  " + I18n.t(c, "Trailer"));
        przyciski.addView(bZwiastun, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, j(c, 66)));
        final TextView bSerwis = przycisk(c, "");
        LinearLayout.LayoutParams bslp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, j(c, 66));
        bslp.leftMargin = j(c, 28);
        przyciski.addView(bSerwis, bslp);
        LinearLayout.LayoutParams prlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        prlp.topMargin = j(c, 30);
        blok.addView(przyciski, prlp);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                j(c, ranking ? 760 : 900), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT);
        blp.leftMargin = j(c, 80);
        blp.bottomMargin = j(c, ranking ? 1080 - 614 : 1080 - 716);
        korzen.addView(blok, blp);

        // półka kart
        final HorizontalScrollView polkaSv = new HorizontalScrollView(c);
        polkaSv.setHorizontalScrollBarEnabled(false);
        polkaSv.setClipToPadding(false);
        polkaSv.setClipChildren(false);
        polkaSv.setPadding(j(c, 80), j(c, 36), j(c, 80), j(c, 12));
        final LinearLayout polka = new LinearLayout(c);
        polka.setOrientation(LinearLayout.HORIZONTAL);
        polka.setClipChildren(false);
        polka.setClipToPadding(false);
        polkaSv.addView(polka);
        FrameLayout.LayoutParams svlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        svlp.topMargin = j(c, (ranking ? 648 : 770) - 36);
        korzen.addView(polkaSv, svlp);

        final List<Karta> karty = new ArrayList<Karta>();
        final float skala = ranking ? 1.08f : 1.1f;
        final Runnable[] ladowanie = {null};

        final Runnable pokaz = new Runnable() {
            @Override
            public void run() {
                final int i = wybrany[0];
                final Suggestions.Item it = lista.get(i);
                miejsceT.setText(miejsce(c, ranking, zakladka[0] == 0, kraje, i + 1));
                tytul.setText(it.title);
                ocena.setText(it.rating > 0 ? String.format(Locale.US, "★ %.1f IMDb", it.rating) : "");
                ocena.setVisibility(it.rating > 0 ? View.VISIBLE : View.GONE);
                StringBuilder m = new StringBuilder(it.rating > 0 ? "  ·  " : "");
                m.append(ModPlOkna.typ(c, it));
                if (it.year != null && !it.year.isEmpty()) m.append("  ·  ").append(it.year);
                if (it.genres != null && !it.genres.isEmpty()) m.append("  ·  ").append(ModPlOkna.gatunki(c, it.genres));
                if (it.czas > 0) m.append("  ·  ").append(czasTrwania(c, it.czas));
                metaTekst.setText(m.toString());
                String w = oznaczenieWieku(it.wiek);
                wiek.setText(w);
                wiek.setVisibility(w.isEmpty() ? View.GONE : View.VISIBLE);
                String o = it.overview == null ? "" : it.overview.trim();
                opis.setText(o);
                opis.setVisibility(o.isEmpty() ? View.GONE : View.VISIBLE);
                String serwis = it.prov == null ? "" : it.prov;
                final String pkg = ModPlOkna.aplikacjaSerwisu(c, serwis);
                if (pkg != null) {
                    bSerwis.setText(I18n.t(c, "Open in ") + ModPlOkna.krotkiSerwis(serwis));
                    bSerwis.setFocusable(true);
                    bSerwis.setAlpha(1f);
                    bSerwis.setVisibility(View.VISIBLE);
                    bSerwis.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            ModPlOkna.otworzSerwis(c, it, pkg);
                        }
                    });
                } else if (!serwis.isEmpty()) {
                    bSerwis.setText(I18n.t(c, "Available on ") + ModPlOkna.krotkiSerwis(serwis));
                    bSerwis.setFocusable(false);
                    bSerwis.setAlpha(0.72f);
                    bSerwis.setVisibility(View.VISIBLE);
                    bSerwis.setOnClickListener(null);
                } else {
                    bSerwis.setVisibility(View.GONE);
                }
                // duży kadr: z pamięci od razu, z sieci po krótkiej chwili, żeby szybkie przewijanie półki nie pobierało wszystkiego
                if (ladowanie[0] != null) GLOWNY.removeCallbacks(ladowanie[0]);
                final String url = duzyKadr(it);
                Bitmap juz = KADRY.get(url);
                if (juz != null && !juz.isRecycled()) {
                    ustawKadr(kadr, juz);
                } else if (!url.isEmpty()) {
                    ladowanie[0] = new Runnable() {
                        @Override
                        public void run() {
                            ModPlOkna.wczytaj(c, url, host.screenW / 2 + 1, new ModPlOkna.Ladowanie() {
                                @Override
                                public void ok(Bitmap b) {
                                    KADRY.put(url, b);
                                    if (d.isShowing() && wybrany[0] < lista.size() && lista.get(wybrany[0]) == it) ustawKadr(kadr, b);
                                }
                            });
                        }
                    };
                    GLOWNY.postDelayed(ladowanie[0], 220);
                }
            }
        };

        final Runnable odswiezZakladki = new Runnable() {
            @Override
            public void run() {
                if (!zakladki) return;
                for (int k = 0; k < 2; k++) {
                    boolean fokus = zakl[k].isFocused(), czynna = zakladka[0] == k;
                    zakl[k].setBackground(fokus ? ksztalt(BIALY, j(c, 23), 0, 0)
                            : czynna ? ksztalt(0x52FFFFFF, j(c, 23), 0, 0) : null);
                    zakl[k].setTextColor(fokus ? 0xFF000000 : czynna ? BIALY : TEKST_86);
                }
            }
        };

        final Runnable[] zbuduj = {null};
        zbuduj[0] = new Runnable() {
            @Override
            public void run() {
                polka.removeAllViews();
                karty.clear();
                polkaSv.scrollTo(0, 0);
                for (int i = 0; i < lista.size(); i++) {
                    final int idx = i;
                    final Suggestions.Item it = lista.get(i);
                    final Karta k = ranking ? kartaPlakatu(c, it, i + 1) : kartaKadru(c, it, i + 1);
                    k.calosc.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                        @Override
                        public void onFocusChange(View v, boolean jest) {
                            if (!jest) return;
                            if (polkaSv.getWidth() > 0) polkaSv.smoothScrollTo(srodekPolki(polkaSv, v), 0);
                            if (wybrany[0] == idx) return;
                            int stary = wybrany[0];
                            wybrany[0] = idx;
                            if (stary >= 0 && stary < karty.size()) wygladKarty(c, karty.get(stary), false, skala);
                            wygladKarty(c, k, true, skala);
                            pokaz.run();
                        }
                    });
                    k.calosc.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            ModPlOkna.zwiastun(c, it);
                        }
                    });
                    k.calosc.setOnKeyListener(new View.OnKeyListener() {
                        @Override
                        public boolean onKey(View v, int code, KeyEvent e) {
                            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                            if (code == KeyEvent.KEYCODE_DPAD_UP) {
                                bZwiastun.requestFocus();
                                return true;
                            }
                            if (code == KeyEvent.KEYCODE_DPAD_DOWN) return true;
                            if (code == KeyEvent.KEYCODE_DPAD_LEFT && idx == 0) return true;
                            return code == KeyEvent.KEYCODE_DPAD_RIGHT && idx == lista.size() - 1;
                        }
                    });
                    LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                            j(c, ranking ? 250 : 253), ViewGroup.LayoutParams.WRAP_CONTENT);
                    if (i > 0) klp.leftMargin = j(c, ranking ? 52 : 48);
                    polka.addView(k.calosc, klp);
                    karty.add(k);
                    wygladKarty(c, k, i == wybrany[0], skala);
                }
            }
        };

        bZwiastun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ModPlOkna.zwiastun(c, lista.get(wybrany[0]));
            }
        });
        View.OnKeyListener klawiszePrzyciskow = new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int code, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (wybrany[0] < karty.size()) karty.get(wybrany[0]).calosc.requestFocus();
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    if (zakladki) zakl[zakladka[0]].requestFocus();
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) return v == bZwiastun;
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    return v == bSerwis || bSerwis.getVisibility() != View.VISIBLE || !bSerwis.isFocusable();
                }
                return false;
            }
        };
        bZwiastun.setOnKeyListener(klawiszePrzyciskow);
        bSerwis.setOnKeyListener(klawiszePrzyciskow);

        if (zakladki) {
            for (int k = 0; k < 2; k++) {
                final int kk = k;
                zakl[k].setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean jest) {
                        if (jest && zakladka[0] != kk) {
                            zakladka[0] = kk;
                            lista.clear();
                            lista.addAll(kk == 0 ? seriale : filmy);
                            wybrany[0] = 0;
                            zbuduj[0].run();
                            pokaz.run();
                        }
                        odswiezZakladki.run();
                    }
                });
                zakl[k].setOnKeyListener(new View.OnKeyListener() {
                    @Override
                    public boolean onKey(View v, int code, KeyEvent e) {
                        if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                        if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                            bZwiastun.requestFocus();
                            return true;
                        }
                        if (code == KeyEvent.KEYCODE_DPAD_UP) return true;
                        if (code == KeyEvent.KEYCODE_DPAD_LEFT) return kk == 0;
                        return code == KeyEvent.KEYCODE_DPAD_RIGHT && kk == 1;
                    }
                });
            }
        }

        zbuduj[0].run();
        odswiezZakladki.run();
        pokaz.run();
        d.show();
        karty.get(wybrany[0]).calosc.requestFocus();
        polkaSv.post(new Runnable() {
            @Override
            public void run() {
                if (wybrany[0] < karty.size()) polkaSv.scrollTo(srodekPolki(polkaSv, karty.get(wybrany[0]).calosc), 0);
            }
        });
        return true;
    }

    /** Przewinięcie półki, przy którym karta stoi na środku ekranu (pierwsze karty zostają przy lewym marginesie). */
    private static int srodekPolki(HorizontalScrollView sv, View karta) {
        return Math.max(0, sv.getPaddingLeft() + karta.getLeft() - (sv.getWidth() - karta.getWidth()) / 2);
    }

    private static void ustawKadr(ImageView kadr, Bitmap b) {
        kadr.animate().cancel();
        kadr.setImageBitmap(b);
        kadr.setAlpha(0.35f);
        kadr.animate().alpha(1f).setDuration(280).start();
    }

    /** Biała ramka wybranej karty; leży na obrazie i ma te same zaokrąglenia. */
    private static View ramkaKarty(Context c) {
        View r = new View(c);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.TRANSPARENT);
        g.setCornerRadius(j(c, 14));
        g.setStroke(Math.max(2, j(c, 3)), 0xEBFFFFFF);
        r.setBackground(g);
        r.setVisibility(View.INVISIBLE);
        return r;
    }

    private static Karta nowaKarta(Context c) {
        Karta k = new Karta();
        k.calosc = new LinearLayout(c);
        k.calosc.setOrientation(LinearLayout.VERTICAL);
        k.calosc.setClipChildren(false);
        k.calosc.setFocusable(true);
        k.calosc.setClickable(true);
        bezSystemowegoPodswietlenia(k.calosc);
        return k;
    }

    private static TextView podpis(Context c, String s, String waga, int kolor) {
        TextView t = napis(c, s, 23, waga, kolor);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    /** Karta „Najczęściej oglądane”: poziomy kadr 253×142 z numerem miejsca w rogu, pod nim tytuł i serwis. */
    private static Karta kartaKadru(Context c, Suggestions.Item it, int nr) {
        Karta k = nowaKarta(c);
        FrameLayout obraz = new FrameLayout(c);
        obraz.setBackgroundColor(0xFF1A1A22);
        ModPlOkna.zaokraglij(obraz, j(c, 14));
        final ImageView img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        obraz.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ModPlOkna.wczytaj(c, it.backdrop, j(c, 253), new ModPlOkna.Ladowanie() {
            @Override
            public void ok(Bitmap b) {
                img.setImageBitmap(b);
            }
        });
        View cien = new View(c);
        cien.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xBD000000, 0x00000000}));
        obraz.addView(cien, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, j(c, 84), Gravity.BOTTOM));
        TextView numer = napis(c, String.valueOf(nr), 52, "display", BIALY);
        numer.setLetterSpacing(-0.03f);
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT);
        nlp.leftMargin = j(c, 14);
        nlp.bottomMargin = j(c, 8);
        obraz.addView(numer, nlp);
        k.ramka = ramkaKarty(c);
        obraz.addView(k.ramka, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        obraz.setPivotX(j(c, 253) / 2f);
        obraz.setPivotY(j(c, 142));
        k.obraz = obraz;
        k.calosc.addView(obraz, new LinearLayout.LayoutParams(j(c, 253), j(c, 142)));
        k.tytul = podpis(c, it.title, "semibold", TEKST_78);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = j(c, 12);
        k.calosc.addView(k.tytul, tlp);
        String serwis = ModPlOkna.krotkiSerwis(it.prov);
        k.dol = podpis(c, serwis.isEmpty() ? ModPlOkna.typ(c, it) : serwis, "medium", TEKST_62);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = j(c, 4);
        k.calosc.addView(k.dol, dlp);
        return k;
    }

    /** Karta rankingu: duży numer miejsca po lewej i plakat 176×264, pod plakatem tytuł, ocena i rok. */
    private static Karta kartaPlakatu(Context c, Suggestions.Item it, int nr) {
        Karta k = nowaKarta(c);
        LinearLayout rzad = new LinearLayout(c);
        rzad.setOrientation(LinearLayout.HORIZONTAL);
        rzad.setGravity(Gravity.BOTTOM);
        rzad.setClipChildren(false);
        TextView numer = napis(c, String.valueOf(nr), 104, "display", 0xEBFFFFFF);
        numer.setLetterSpacing(-0.04f);
        numer.setGravity(Gravity.RIGHT | Gravity.BOTTOM);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(j(c, 64), ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.bottomMargin = -j(c, 5);
        rzad.addView(numer, nlp);
        FrameLayout obraz = new FrameLayout(c);
        obraz.setBackgroundColor(0xFF1A1A22);
        ModPlOkna.zaokraglij(obraz, j(c, 14));
        final ImageView img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        obraz.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        String plakat = it.logo != null && !it.logo.isEmpty() ? it.logo : it.backdrop;
        ModPlOkna.wczytaj(c, plakat, j(c, 176), new ModPlOkna.Ladowanie() {
            @Override
            public void ok(Bitmap b) {
                img.setImageBitmap(b);
            }
        });
        k.ramka = ramkaKarty(c);
        obraz.addView(k.ramka, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        obraz.setPivotX(j(c, 176) / 2f);
        obraz.setPivotY(j(c, 264));
        k.obraz = obraz;
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(j(c, 176), j(c, 264));
        olp.leftMargin = j(c, 10);
        rzad.addView(obraz, olp);
        k.calosc.addView(rzad, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        k.tytul = podpis(c, it.title, "semibold", TEKST_78);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(j(c, 176), ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = j(c, 74);
        tlp.topMargin = j(c, 12);
        k.calosc.addView(k.tytul, tlp);
        String rok = it.year == null ? "" : it.year;
        String dol = it.rating > 0 ? String.format(Locale.US, "★ %.1f", it.rating) + (rok.isEmpty() ? "" : "  ·  " + rok) : rok;
        k.dol = podpis(c, dol, "medium", TEKST_62);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(j(c, 176), ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.leftMargin = j(c, 74);
        dlp.topMargin = j(c, 4);
        k.calosc.addView(k.dol, dlp);
        return k;
    }

    // ------------------------------------------------------------------ wspólne dla okien z listą

    /**
     * Tło wiersza listy: 0 zwykły (bez tła), 1 w fokusie (biała pigułka z cieniem, lekko większa),
     * 2 wybrany, gdy fokus przeszedł na przyciski (szkło z jasną krawędzią). Kolory tekstu ustawia wołający.
     */
    private static void tloWiersza(Context c, View w, int stan, float promienKanwy, float skala) {
        float r = j(c, promienKanwy);
        if (stan == 1) {
            w.setBackground(ksztalt(BIALY, r, 0, 0));
            w.setElevation(j(c, 16));
        } else if (stan == 2) {
            w.setBackground(ksztalt(SZKLO, r, Math.max(1, j(c, 1)), SZKLO_RAMKA));
            w.setElevation(0f);
        } else {
            w.setBackground(null);
            w.setElevation(0f);
        }
        float s = stan == 1 ? skala : 1f;
        w.animate().scaleX(s).scaleY(s).setDuration(150).start();
    }

    /** Lista przewijana w pionie z zapasem na powiększony wiersz i jego cień; zwraca {ScrollView, kolumna}. */
    private static ViewGroup[] lista(Context c, int zapasBokow, int zapasGory, int zapasDolu) {
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        // Zapas na górze i na dole należy do przewijanej kolumny, nie do ScrollView: system rysuje wygaszanie krawędzi
        // wewnątrz marginesów widoku, więc przy pionowych marginesach nachodziło ono na zaznaczony wiersz.
        sv.setPadding(j(c, zapasBokow), 0, j(c, zapasBokow), 0);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(j(c, 36));
        // Lista ma zostać w swoim polu także po przewinięciu: bez przycinania wiersze wychodzą poza nią,
        // gdy rodzic nie przycina dzieci (a nie przycina, żeby cień i powiększenie wiersza miały miejsce).
        ModPlOkna.zaokraglij(sv, 0f);
        LinearLayout kolumna = new LinearLayout(c);
        kolumna.setOrientation(LinearLayout.VERTICAL);
        kolumna.setClipChildren(false);
        kolumna.setClipToPadding(false);
        kolumna.setPadding(0, j(c, zapasGory), 0, j(c, zapasDolu));
        sv.addView(kolumna);
        return new ViewGroup[]{sv, kolumna};
    }

    // ------------------------------------------------------------------ mecze

    /** Poświata bez koloru drużyny (herb szary albo jeszcze niepobrany). */
    private static final int NEUTRALNA = 0xFF9AA0A8;

    /**
     * Tło z dwiema miękkimi poświatami w kolorach drużyn na ciemnej podstawie. Kolory zmieniają się płynnie,
     * gdy na liście zostaje wybrany inny mecz. Miejsca i promienie poświat są ułamkami szerokości i wysokości pola.
     */
    private static final class Poswiata extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final int gora, dol;
        private final float[][] miejsca;   // {środek x, środek y, promień x, promień y}
        private final int[] alfy;          // krycie środka poświaty, 0..255
        private final int[] kolory = {NEUTRALNA, NEUTRALNA};
        private ValueAnimator anim;

        Poswiata(int gora, int dol, float[][] miejsca, int[] alfy) {
            this.gora = gora;
            this.dol = dol;
            this.miejsca = miejsca;
            this.alfy = alfy;
        }

        /** Nowe kolory (0 = bez koloru); przejście trwa ok. 1/3 sekundy i zaczyna się od tego, co właśnie widać. */
        void ustaw(int k0, int k1) {
            final int[] od = {kolory[0], kolory[1]};
            final int[] cel = {k0 == 0 ? NEUTRALNA : k0, k1 == 0 ? NEUTRALNA : k1};
            if (od[0] == cel[0] && od[1] == cel[1]) return;
            if (anim != null) anim.cancel();
            final ArgbEvaluator miks = new ArgbEvaluator();
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(340);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    float t = (Float) a.getAnimatedValue();
                    kolory[0] = (Integer) miks.evaluate(t, od[0], cel[0]);
                    kolory[1] = (Integer) miks.evaluate(t, od[1], cel[1]);
                    invalidateSelf();
                }
            });
            anim.start();
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            p.setShader(new LinearGradient(0, b.top, 0, b.bottom, gora, dol, Shader.TileMode.CLAMP));
            c.drawRect(b, p);
            for (int i = 0; i < 2; i++) {
                float rx = b.width() * miejsca[i][2], ry = b.height() * miejsca[i][3];
                if (rx <= 0f || ry <= 0f) continue;
                int rgb = kolory[i] & 0x00FFFFFF;
                p.setShader(new RadialGradient(0f, 0f, rx,
                        new int[]{(alfy[i] << 24) | rgb, ((alfy[i] * 2 / 5) << 24) | rgb, rgb},
                        new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                c.save();
                c.translate(b.left + b.width() * miejsca[i][0], b.top + b.height() * miejsca[i][1]);
                c.scale(1f, ry / rx);
                c.drawCircle(0f, 0f, rx, p);
                c.restore();
            }
            p.setShader(null);
        }

        @Override
        public void setAlpha(int alpha) { }

        @Override
        public void setColorFilter(ColorFilter cf) { }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /**
     * Najmocniejszy kolor herbu, do poświaty: piksele wyraźnie barwne są grupowane po odcieniu (co 30°) i wygrywa
     * grupa o największej sumie nasycenia i jasności. Zwraca 0, gdy herb jest w zasadzie szary, biały albo czarny.
     */
    static int kolorHerbu(Bitmap b) {
        if (b == null || b.isRecycled() || b.getWidth() < 2 || b.getHeight() < 2) return 0;
        int w = b.getWidth(), h = b.getHeight(), krok = Math.max(1, Math.max(w, h) / 32), widoczne = 0;
        float[] hsv = new float[3], waga = new float[12], sr = new float[12], sg = new float[12], sb = new float[12];
        try {
            for (int y = 0; y < h; y += krok) {
                for (int x = 0; x < w; x += krok) {
                    int px = b.getPixel(x, y);
                    if (Color.alpha(px) < 128) continue;
                    widoczne++;
                    Color.colorToHSV(px, hsv);
                    if (hsv[1] < 0.35f || hsv[2] < 0.25f) continue;
                    int grupa = ((int) (hsv[0] / 30f)) % 12;
                    float wg = hsv[1] * hsv[2];
                    waga[grupa] += wg;
                    sr[grupa] += Color.red(px) * wg;
                    sg[grupa] += Color.green(px) * wg;
                    sb[grupa] += Color.blue(px) * wg;
                }
            }
        } catch (RuntimeException e) {
            return 0;   // bitmapa sprzętowa albo zwolniona w trakcie: zostaje poświata bez koloru
        }
        int naj = 0;
        for (int i = 1; i < 12; i++) if (waga[i] > waga[naj]) naj = i;
        if (widoczne == 0 || waga[naj] < widoczne * 0.03f) return 0;
        Color.colorToHSV(Color.rgb(Math.round(sr[naj] / waga[naj]), Math.round(sg[naj] / waga[naj]),
                Math.round(sb[naj] / waga[naj])), hsv);
        hsv[1] = Math.max(hsv[1], 0.55f);
        hsv[2] = Math.max(hsv[2], 0.80f);
        return Color.HSVToColor(hsv);
    }

    /** Wiersz meczu: drużyny z herbami jedna pod drugą, po prawej dzień i godzina albo stan i wynik. */
    private static final class WierszMeczu {
        LinearLayout calosc;
        final TextView[] nazwa = new TextView[2], gwiazdka = new TextView[2];
        TextView gora, dol;
        boolean naZywo;
        int stan;
    }

    private static void malujWiersz(Context c, WierszMeczu w) {
        tloWiersza(c, w.calosc, w.stan, 24, 1.03f);
        boolean f = w.stan == 1;
        for (int i = 0; i < 2; i++) {
            w.nazwa[i].setTextColor(f ? CZARNY : BIALY);
            w.gwiazdka[i].setTextColor(f ? CZARNY_62 : TEKST_70);
        }
        w.gora.setTextColor(w.naZywo ? (f ? ZIELEN_CIEMNA : ZIELEN) : (f ? CZARNY_62 : TEKST_70));
        w.dol.setTextColor(f ? CZARNY : BIALY);
    }

    private static String wielkaLitera(Context c, String s) {
        return s == null || s.isEmpty() ? "" : s.substring(0, 1).toUpperCase(I18n.locale(c)) + s.substring(1);
    }

    /** {górna linia, dolna linia} wiersza: „Dziś” i „21:00”, „Na żywo 67'” i „2 : 1”, „Koniec” i „1 : 0”. */
    private static String[] terminMeczu(Context c, HomeDashboard.MatchItem m) {
        String st = ModPlOkna.status(c, m);
        if (m.isLive || m.isFinished) return new String[]{st, m.homeScore + " : " + m.awayScore};
        if (m.startEpochMs > 0) {
            String godz = new SimpleDateFormat("HH:mm", I18n.locale(c)).format(new Date(m.startEpochMs));
            if (st.endsWith(" " + godz)) {
                return new String[]{wielkaLitera(c, st.substring(0, st.length() - godz.length() - 1)), godz};
            }
        }
        return new String[]{st, "–"};   // przełożony, odwołany albo bez terminu
    }

    private static void ustawTermin(Context c, WierszMeczu w, HomeDashboard.MatchItem m) {
        String[] t = terminMeczu(c, m);
        w.gora.setText(t[0]);
        w.dol.setText(t[1]);
        w.naZywo = m.isLive;
    }

    /** Herb 30 px przy nazwie drużyny; pobierany osobną kolejką, żeby lista nie blokowała dużych herbów podglądu. */
    private static void malyHerb(final Context c, final ImageView v, final String url, final boolean[] otwarte) {
        if (url == null || url.isEmpty()) return;
        v.setTag(url);
        final int szer = j(c, 30);
        HERBY.execute(new Runnable() {
            @Override
            public void run() {
                if (!otwarte[0]) return;
                final Bitmap b = Suggestions.image(c, url, "w780", szer);
                if (b == null) return;
                GLOWNY.post(new Runnable() {
                    @Override
                    public void run() {
                        if (otwarte[0] && url.equals(v.getTag())) v.setImageBitmap(b);
                    }
                });
            }
        });
    }

    private static WierszMeczu wierszMeczu(Context c, HomeDashboard.MatchItem m, boolean[] otwarte) {
        WierszMeczu w = new WierszMeczu();
        w.calosc = new LinearLayout(c);
        w.calosc.setOrientation(LinearLayout.HORIZONTAL);
        w.calosc.setGravity(Gravity.CENTER_VERTICAL);
        w.calosc.setPadding(j(c, 26), 0, j(c, 26), 0);
        w.calosc.setFocusable(true);
        w.calosc.setClickable(true);
        bezSystemowegoPodswietlenia(w.calosc);
        LinearLayout druzyny = new LinearLayout(c);
        druzyny.setOrientation(LinearLayout.VERTICAL);
        String[] nazwy = {m.homeTeam, m.awayTeam}, loga = {m.homeLogo, m.awayLogo};
        for (int i = 0; i < 2; i++) {
            LinearLayout linia = new LinearLayout(c);
            linia.setOrientation(LinearLayout.HORIZONTAL);
            linia.setGravity(Gravity.CENTER_VERTICAL);
            ImageView herb = new ImageView(c);
            herb.setScaleType(ImageView.ScaleType.FIT_CENTER);
            linia.addView(herb, new LinearLayout.LayoutParams(j(c, 30), j(c, 30)));
            malyHerb(c, herb, loga[i], otwarte);
            w.nazwa[i] = napis(c, nazwy[i], 25, "semibold", BIALY);
            w.nazwa[i].setSingleLine(true);
            w.nazwa[i].setEllipsize(TextUtils.TruncateAt.END);
            // waga 1 przy szerokości „na treść”: długa nazwa skraca się wielokropkiem, a gwiazdka zostaje widoczna
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(WRAP, WRAP, 1f);
            nlp.leftMargin = j(c, 12);
            linia.addView(w.nazwa[i], nlp);
            w.gwiazdka[i] = napis(c, "★", 19, "semibold", TEKST_70);
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(WRAP, WRAP);
            glp.leftMargin = j(c, 10);
            linia.addView(w.gwiazdka[i], glp);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(WRAP, WRAP);
            if (i == 1) llp.topMargin = j(c, 6);
            druzyny.addView(linia, llp);
        }
        w.calosc.addView(druzyny, new LinearLayout.LayoutParams(0, WRAP, 1f));
        LinearLayout prawa = new LinearLayout(c);
        prawa.setOrientation(LinearLayout.VERTICAL);
        prawa.setGravity(Gravity.RIGHT);
        w.gora = napis(c, "", 23, "semibold", TEKST_70);
        w.gora.setSingleLine(true);
        prawa.addView(w.gora);
        w.dol = napis(c, "", 27, "semibold", BIALY);
        w.dol.setFontFeatureSettings("tnum");
        w.dol.setSingleLine(true);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(WRAP, WRAP);
        dlp.topMargin = j(c, 3);
        prawa.addView(w.dol, dlp);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(WRAP, WRAP);
        plp.leftMargin = j(c, 20);
        w.calosc.addView(prawa, plp);
        ustawTermin(c, w, m);
        return w;
    }

    /** Wiersz listy w nakładce (liga, drużyna): w fokusie biała pigułka z czarnym tekstem. */
    private static TextView wierszNakladki(final Context c, String tekst) {
        final TextView t = napis(c, tekst, 27, "semibold", BIALY);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(j(c, 26), 0, j(c, 26), 0);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setFocusable(true);
        t.setClickable(true);
        bezSystemowegoPodswietlenia(t);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean jest) {
                tloWiersza(c, v, jest ? 1 : 0, 22, 1.02f);
                t.setTextColor(jest ? CZARNY : BIALY);
            }
        });
        return t;
    }

    /**
     * Okno meczów: po lewej lista (dwie drużyny w wierszu, dzień i godzina albo stan i wynik), po prawej szklana karta
     * wybranego meczu i przyciski obserwowania drużyn. Tło okna i karty świeci kolorami obu drużyn, branymi z herbów.
     * Wybór ligi i ulubionych drużyn otwiera listę na środku ekranu.
     */
    static boolean pokazMecze(final MainActivity host, final Runnable poZmianie) {
        final Context c = host;
        final List<HomeDashboard.MatchItem> mecze = new ArrayList<HomeDashboard.MatchItem>(HomeDashboard.getCachedMatches(c));
        final boolean[] otwarte = {true};

        final FrameLayout korzen = new FrameLayout(c);
        final Poswiata tloOkna = new Poswiata(0xFF11141B, 0xFF07090D,
                new float[][]{{0.58f, 0.44f, 0.39f, 0.49f}, {0.97f, 0.50f, 0.36f, 0.49f}}, new int[]{0x4D, 0x5C});
        korzen.setBackground(tloOkna);
        final Dialog d = okno(host, korzen);

        // treść okna bez nakładki: na czas nakładki nie przyjmuje fokusu
        final FrameLayout tresc = new FrameLayout(c);
        tresc.setClipChildren(false);
        korzen.addView(tresc, new FrameLayout.LayoutParams(MATCH, MATCH));
        naglowek(c, tresc, I18n.t(c, "dash_football"), "");

        final String liga = HomeDashboard.getSelectedLeague(c);
        String nazwaLigi = HomeDashboard.LEAGUES[0][1];
        for (String[] lg : HomeDashboard.LEAGUES) if (lg[0].equals(liga)) nazwaLigi = lg[1];
        LinearLayout opcje = new LinearLayout(c);
        opcje.setOrientation(LinearLayout.HORIZONTAL);
        opcje.setClipChildren(false);
        final TextView bLiga = przycisk(c, I18n.t(c, "League: ") + I18n.t(c, nazwaLigi) + "  ›", 25, 26);
        opcje.addView(bLiga, new LinearLayout.LayoutParams(WRAP, j(c, 56)));
        final TextView bUlubione = przycisk(c, "★  " + I18n.t(c, "football_fav_teams") + "  ›", 25, 26);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(WRAP, j(c, 56));
        ulp.leftMargin = j(c, 16);
        opcje.addView(bUlubione, ulp);
        FrameLayout.LayoutParams oplp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.LEFT);
        oplp.leftMargin = j(c, 80);
        oplp.topMargin = j(c, 130);
        tresc.addView(opcje, oplp);

        // lista meczów
        ViewGroup[] l = lista(c, 24, 14, 40);
        final LinearLayout kolumna = (LinearLayout) l[1];
        FrameLayout.LayoutParams svlp = new FrameLayout.LayoutParams(j(c, 640 + 48), MATCH, Gravity.TOP | Gravity.LEFT);
        svlp.leftMargin = j(c, 80 - 24);
        svlp.topMargin = j(c, 216 - 14);
        svlp.bottomMargin = j(c, 20);
        tresc.addView(l[0], svlp);

        // karta wybranego meczu
        final FrameLayout karta = new FrameLayout(c);
        final Poswiata tloKarty = new Poswiata(0x12FFFFFF, 0x12FFFFFF,
                new float[][]{{0.18f, 0.58f, 0.32f, 0.44f}, {0.82f, 0.58f, 0.32f, 0.44f}}, new int[]{0x57, 0x5C});
        karta.setBackground(tloKarty);
        ModPlOkna.zaokraglij(karta, j(c, 44));
        LinearLayout tablica = new LinearLayout(c);
        tablica.setOrientation(LinearLayout.VERTICAL);
        tablica.setGravity(Gravity.CENTER_HORIZONTAL);
        tablica.setPadding(j(c, 56), j(c, 48), j(c, 56), j(c, 56));
        karta.addView(tablica, new FrameLayout.LayoutParams(MATCH, MATCH));
        View obwodka = new View(c);
        obwodka.setBackground(ksztalt(Color.TRANSPARENT, j(c, 44), Math.max(1, j(c, 1)), 0x33FFFFFF));
        karta.addView(obwodka, new FrameLayout.LayoutParams(MATCH, MATCH));
        FrameLayout.LayoutParams klp = new FrameLayout.LayoutParams(j(c, 1040), j(c, 560), Gravity.TOP | Gravity.LEFT);
        klp.leftMargin = j(c, 800);
        klp.topMargin = j(c, 216);
        tresc.addView(karta, klp);

        final TextView tLiga = napis(c, "", 23, "semibold", 0xCCFFFFFF);
        tLiga.setLetterSpacing(0.1f);
        tLiga.setGravity(Gravity.CENTER);
        tLiga.setSingleLine(true);
        tLiga.setEllipsize(TextUtils.TruncateAt.END);
        tablica.addView(tLiga);
        final TextView tData = napis(c, "", 29, "medium", BIALY);
        tData.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tdlp = new LinearLayout.LayoutParams(WRAP, WRAP);
        tdlp.topMargin = j(c, 6);
        tablica.addView(tData, tdlp);
        LinearLayout druzyny = new LinearLayout(c);
        druzyny.setOrientation(LinearLayout.HORIZONTAL);
        druzyny.setGravity(Gravity.CENTER_VERTICAL);
        tablica.addView(druzyny, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        final ImageView[] herby = {new ImageView(c), new ImageView(c)};
        final TextView[] nazwy = {napis(c, "", 31, "semibold", BIALY), napis(c, "", 31, "semibold", BIALY)};
        LinearLayout[] kolumny = {new LinearLayout(c), new LinearLayout(c)};
        for (int i = 0; i < 2; i++) {
            kolumny[i].setOrientation(LinearLayout.VERTICAL);
            kolumny[i].setGravity(Gravity.CENTER_HORIZONTAL);
            herby[i].setScaleType(ImageView.ScaleType.FIT_CENTER);
            kolumny[i].addView(herby[i], new LinearLayout.LayoutParams(j(c, 128), j(c, 128)));
            nazwy[i].setGravity(Gravity.CENTER);
            nazwy[i].setMaxLines(2);
            nazwy[i].setEllipsize(TextUtils.TruncateAt.END);
            nazwy[i].setLineSpacing(0f, 1.15f);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(MATCH, WRAP);
            nlp.topMargin = j(c, 18);
            kolumny[i].addView(nazwy[i], nlp);
        }
        druzyny.addView(kolumny[0], new LinearLayout.LayoutParams(j(c, 270), WRAP));
        LinearLayout srodek = new LinearLayout(c);
        srodek.setOrientation(LinearLayout.VERTICAL);
        srodek.setGravity(Gravity.CENTER_HORIZONTAL);
        final TextView tDuzy = napis(c, "", 128, "display", BIALY);
        tDuzy.setLetterSpacing(-0.03f);
        tDuzy.setFontFeatureSettings("tnum");
        tDuzy.setGravity(Gravity.CENTER);
        tDuzy.setSingleLine(true);
        srodek.addView(tDuzy);
        final TextView tStan = napis(c, "", 23, "display", BIALY);
        tStan.setLetterSpacing(0.07f);
        tStan.setPadding(j(c, 18), j(c, 5), j(c, 18), j(c, 5));
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(WRAP, WRAP);
        stlp.topMargin = j(c, 10);
        srodek.addView(tStan, stlp);
        druzyny.addView(srodek, new LinearLayout.LayoutParams(0, WRAP, 1f));
        druzyny.addView(kolumny[1], new LinearLayout.LayoutParams(j(c, 270), WRAP));

        final LinearLayout przyciski = new LinearLayout(c);
        przyciski.setOrientation(LinearLayout.HORIZONTAL);
        przyciski.setClipChildren(false);
        final TextView bDom = przycisk(c, "", 27, 34), bGosc = przycisk(c, "", 27, 34);
        bDom.setMaxWidth(j(c, 508));
        bGosc.setMaxWidth(j(c, 508));
        bDom.setEllipsize(TextUtils.TruncateAt.END);
        bGosc.setEllipsize(TextUtils.TruncateAt.END);
        przyciski.addView(bDom, new LinearLayout.LayoutParams(WRAP, j(c, 66)));
        LinearLayout.LayoutParams bglp = new LinearLayout.LayoutParams(WRAP, j(c, 66));
        bglp.leftMargin = j(c, 20);
        przyciski.addView(bGosc, bglp);
        FrameLayout.LayoutParams przlp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.LEFT);
        przlp.leftMargin = j(c, 800);
        przlp.topMargin = j(c, 808);
        tresc.addView(przyciski, przlp);

        // nakładka z listą (wybór ligi, ulubione drużyny) nad całym oknem
        final FrameLayout nakladka = new FrameLayout(c);
        nakladka.setBackgroundColor(0xA6000000);
        nakladka.setVisibility(View.GONE);
        nakladka.setClickable(true);
        korzen.addView(nakladka, new FrameLayout.LayoutParams(MATCH, MATCH));
        LinearLayout okienko = new LinearLayout(c);
        okienko.setOrientation(LinearLayout.VERTICAL);
        okienko.setPadding(j(c, 30), j(c, 40), j(c, 30), j(c, 26));
        okienko.setBackground(ksztalt(0xF21C1E26, j(c, 44), Math.max(1, j(c, 1)), 0x38FFFFFF));
        okienko.setElevation(j(c, 30));
        FrameLayout.LayoutParams oklp = new FrameLayout.LayoutParams(j(c, 760), WRAP, Gravity.CENTER);
        oklp.topMargin = j(c, 70);
        oklp.bottomMargin = j(c, 70);
        nakladka.addView(okienko, oklp);
        final TextView tytulNakladki = napis(c, "", 38, "semibold", BIALY);
        tytulNakladki.setPadding(j(c, 26), 0, j(c, 26), 0);
        okienko.addView(tytulNakladki);
        final TextView opisNakladki = napis(c, "", 23, "medium", TEKST_70);
        opisNakladki.setLineSpacing(0f, 1.2f);
        opisNakladki.setPadding(j(c, 26), 0, j(c, 26), 0);
        LinearLayout.LayoutParams onlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        onlp.topMargin = j(c, 8);
        okienko.addView(opisNakladki, onlp);
        ViewGroup[] ln = lista(c, 14, 12, 16);
        final LinearLayout kolN = (LinearLayout) ln[1];
        LinearLayout.LayoutParams svnlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        svnlp.topMargin = j(c, 12);
        svnlp.leftMargin = -j(c, 14);
        svnlp.rightMargin = -j(c, 14);
        okienko.addView(ln[0], svnlp);

        final int[] wybrany = {0};
        final List<WierszMeczu> wiersze = new ArrayList<WierszMeczu>();
        final boolean[] zmieniono = {false};
        final View[] powrotFokusu = {bLiga};
        final int[] kolory = {0, 0};

        final Runnable odswiezGwiazdki = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < mecze.size() && i < wiersze.size(); i++) {
                    HomeDashboard.MatchItem m = mecze.get(i);
                    WierszMeczu w = wiersze.get(i);
                    w.gwiazdka[0].setVisibility(ModPlMecze.ulubionaDruzyna(c, m.homeTeam, m.glowna) ? View.VISIBLE : View.GONE);
                    w.gwiazdka[1].setVisibility(ModPlMecze.ulubionaDruzyna(c, m.awayTeam, m.glowna) ? View.VISIBLE : View.GONE);
                }
            }
        };

        final Runnable pokazPodglad = new Runnable() {
            @Override
            public void run() {
                if (mecze.isEmpty()) return;
                final HomeDashboard.MatchItem m = mecze.get(Math.min(wybrany[0], mecze.size() - 1));
                Locale loc = I18n.locale(c);
                boolean wynikJest = m.isLive || m.isFinished;
                String godz = m.startEpochMs > 0 ? new SimpleDateFormat("HH:mm", loc).format(new Date(m.startEpochMs)) : "";
                tLiga.setText(m.leagueName == null ? "" : m.leagueName.toUpperCase(loc));
                if (m.startEpochMs > 0) {
                    String kiedy = wielkaLitera(c, new SimpleDateFormat("EEEE, d MMMM", loc).format(new Date(m.startEpochMs)));
                    tData.setText(wynikJest ? kiedy + "  ·  " + godz : kiedy);   // godzina jest już duża na środku
                } else {
                    tData.setText("");
                }
                nazwy[0].setText(m.homeTeam);
                nazwy[1].setText(m.awayTeam);
                tDuzy.setText(wynikJest ? m.homeScore + " : " + m.awayScore : (godz.isEmpty() ? "–" : godz));
                String st = ModPlOkna.status(c, m);
                if (!wynikJest && !godz.isEmpty() && st.endsWith(" " + godz)) st = st.substring(0, st.length() - godz.length() - 1);
                tStan.setText(st.toUpperCase(loc));
                tStan.setVisibility(st.isEmpty() ? View.INVISIBLE : View.VISIBLE);
                tStan.setBackground(ksztalt(m.isLive ? ZIELEN_TLO : 0x33FFFFFF, j(c, 12), 0, 0));

                final String k = ModPlOkna.klucz(m);
                if (!k.equals(herby[0].getTag())) {
                    herby[0].setTag(k);
                    kolory[0] = kolory[1] = 0;
                    String[] loga = {m.homeLogo, m.awayLogo};
                    for (int i = 0; i < 2; i++) {
                        final int nr = i;
                        herby[i].setImageDrawable(null);
                        ModPlOkna.wczytaj(c, loga[i], j(c, 128), new ModPlOkna.Ladowanie() {
                            @Override
                            public void ok(Bitmap b) {
                                if (!otwarte[0] || !k.equals(herby[0].getTag())) return;
                                herby[nr].setImageBitmap(b);
                                kolory[nr] = kolorHerbu(b);
                                tloOkna.ustaw(kolory[0], kolory[1]);
                                tloKarty.ustaw(kolory[0], kolory[1]);
                            }
                        });
                    }
                    // drużyna bez herbu albo z szarym herbem: po chwili poświata traci kolor poprzedniego meczu
                    GLOWNY.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!otwarte[0] || !k.equals(herby[0].getTag())) return;
                            tloOkna.ustaw(kolory[0], kolory[1]);
                            tloKarty.ustaw(kolory[0], kolory[1]);
                        }
                    }, 450);
                }

                TextView[] bt = {bDom, bGosc};
                String[] dr = {m.homeTeam, m.awayTeam};
                final Runnable ja = this;
                for (int i = 0; i < 2; i++) {
                    final String druzyna = dr[i];
                    final boolean f = ModPlMecze.ulubionaDruzyna(c, druzyna, m.glowna);
                    bt[i].setText((f ? "★  " + I18n.t(c, "Following ") : "☆  " + I18n.t(c, "Follow ")) + druzyna);
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
                tresc.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
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
                    for (int n = i + krok; n >= 0 && n < kolN.getChildCount(); n += krok) {
                        View nast = kolN.getChildAt(n);
                        if (nast.isFocusable() && nast.getVisibility() == View.VISIBLE) {
                            nast.requestFocus();
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
                    TextView w = wierszNakladki(c, (ta ? "✓  " : "      ") + I18n.t(c, lg[1]));
                    if (ta) doFokusu = w;
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
                    LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(MATCH, j(c, 66));
                    if (kolN.getChildCount() > 0) wlp.topMargin = j(c, 4);
                    kolN.addView(w, wlp);
                }
                tresc.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
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
                    for (String u : moje) if (ModPlOkna.bezOgonkow(u).equals(ModPlOkna.bezOgonkow(pop))) jest = true;
                    if (!jest) pozycje.add(new String[]{pop, "0"});
                }
                TextView dodaj = wierszNakladki(c, "＋  " + I18n.t(c, "Add another team…"));
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
                                    public void onClick(android.content.DialogInterface di, int ktory) {
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
                kolN.addView(dodaj, new LinearLayout.LayoutParams(MATCH, j(c, 66)));
                boolean naglowekDodany = false;
                for (final String[] poz : pozycje) {
                    final boolean obs = "1".equals(poz[1]);
                    if (!obs && !naglowekDodany) {
                        naglowekDodany = true;
                        TextView n = napis(c, I18n.t(c, "Popular teams").toUpperCase(I18n.locale(c)), 23, "semibold", TEKST_70);
                        n.setLetterSpacing(0.08f);
                        n.setPadding(j(c, 26), j(c, 22), 0, j(c, 8));
                        kolN.addView(n);
                    }
                    TextView w = wierszNakladki(c, (obs ? "★  " : "☆  ") + poz[0]);
                    w.setOnKeyListener(klawiszeNakladki);
                    w.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View x) {
                            HomeDashboard.toggleFavoriteTeam(c, poz[0]);
                            zmieniono[0] = true;
                            zbudujUlubione[0].run();
                        }
                    });
                    LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(MATCH, j(c, 66));
                    if (kolN.getChildCount() > 0) wlp.topMargin = j(c, 4);
                    kolN.addView(w, wlp);
                }
                View cel = null;
                for (int n = Math.max(0, Math.min(indeks, kolN.getChildCount() - 1)); n < kolN.getChildCount(); n++) {
                    if (kolN.getChildAt(n).isFocusable()) {
                        cel = kolN.getChildAt(n);
                        break;
                    }
                }
                if (cel == null) {
                    for (int n = kolN.getChildCount() - 1; n >= 0 && cel == null; n--) {
                        if (kolN.getChildAt(n).isFocusable()) cel = kolN.getChildAt(n);
                    }
                }
                if (cel != null) cel.requestFocus();
            }
        };
        bUlubione.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                powrotFokusu[0] = bUlubione;
                tytulNakladki.setText("★  " + I18n.t(c, "football_fav_teams"));
                opisNakladki.setText(I18n.t(c, "OK adds or removes a team. Any team can be followed from its match."));
                tresc.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
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

        View.OnKeyListener klawiszeObserwowania = new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int code, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT && v == bDom && !wiersze.isEmpty()) {
                    wiersze.get(Math.min(wybrany[0], wiersze.size() - 1)).calosc.requestFocus();
                    return true;
                }
                return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                        || (code == KeyEvent.KEYCODE_DPAD_RIGHT && v == bGosc);
            }
        };
        bDom.setOnKeyListener(klawiszeObserwowania);
        bGosc.setOnKeyListener(klawiszeObserwowania);
        View.OnKeyListener klawiszeOpcji = new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int code, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code == KeyEvent.KEYCODE_DPAD_DOWN && !wiersze.isEmpty()) {
                    wiersze.get(Math.min(wybrany[0], wiersze.size() - 1)).calosc.requestFocus();
                    return true;
                }
                return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                        || (code == KeyEvent.KEYCODE_DPAD_LEFT && v == bLiga)
                        || (code == KeyEvent.KEYCODE_DPAD_RIGHT && v == bUlubione);
            }
        };
        bLiga.setOnKeyListener(klawiszeOpcji);
        bUlubione.setOnKeyListener(klawiszeOpcji);

        if (mecze.isEmpty()) {
            final TextView pusto = napis(c, I18n.t(c, "football_loading"), 27, "medium", TEKST_70);
            pusto.setPadding(j(c, 26), j(c, 12), j(c, 26), 0);
            kolumna.addView(pusto);
            karta.setVisibility(View.INVISIBLE);
            przyciski.setVisibility(View.GONE);
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
                TextView n = napis(c, I18n.t(c, ModPlMecze.NAZWY_SEKCJI[sek]).toUpperCase(I18n.locale(c)), 23, "semibold",
                        sek == 0 ? ZIELEN : TEKST_70);
                n.setLetterSpacing(0.08f);
                n.setPadding(j(c, 26), j(c, i == 0 ? 0 : 22), 0, j(c, 6));
                kolumna.addView(n);
            }
            final WierszMeczu w = wierszMeczu(c, m, otwarte);
            w.calosc.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean jest) {
                    if (jest && wybrany[0] != idx) {
                        int stary = wybrany[0];
                        wybrany[0] = idx;
                        if (stary >= 0 && stary < wiersze.size()) {
                            wiersze.get(stary).stan = 0;
                            malujWiersz(c, wiersze.get(stary));
                        }
                        pokazPodglad.run();
                    }
                    w.stan = jest ? 1 : (wybrany[0] == idx ? 2 : 0);
                    malujWiersz(c, w);
                }
            });
            w.calosc.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    bDom.requestFocus();
                }
            });
            w.calosc.setOnKeyListener(new View.OnKeyListener() {
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
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(MATCH, j(c, 92));
            wlp.topMargin = j(c, 4);
            kolumna.addView(w.calosc, wlp);
            wiersze.add(w);
            malujWiersz(c, w);
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
                            Map<String, HomeDashboard.MatchItem> poKluczu = new HashMap<String, HomeDashboard.MatchItem>();
                            for (HomeDashboard.MatchItem n : nowe) poKluczu.put(ModPlOkna.klucz(n), n);
                            for (int i = 0; i < mecze.size() && i < wiersze.size(); i++) {
                                HomeDashboard.MatchItem n = poKluczu.get(ModPlOkna.klucz(mecze.get(i)));
                                if (n == null) continue;
                                mecze.set(i, n);
                                ustawTermin(c, wiersze.get(i), n);
                                malujWiersz(c, wiersze.get(i));
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
                otwarte[0] = false;
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
        if (!wiersze.isEmpty()) wiersze.get(0).calosc.requestFocus();
        else bLiga.requestFocus();
        return true;
    }

    // ------------------------------------------------------------------ wiadomości

    private static final class WierszWiadomosci {
        LinearLayout calosc;
        TextView zrodlo, tytul;
        int stan;
    }

    private static void malujWiersz(Context c, WierszWiadomosci w) {
        tloWiersza(c, w.calosc, w.stan, 26, 1.025f);
        boolean f = w.stan == 1;
        w.zrodlo.setTextColor(f ? 0xA3000000 : 0xC2FFFFFF);
        w.tytul.setTextColor(f ? CZARNY : BIALY);
        w.tytul.setTypeface(Ui.font(c, f ? "semibold" : "medium"));
    }

    /** Ciemne szkło pod listą i pod kartą czytania: tapeta w tle zostaje widoczna, a biały tekst czytelny. */
    private static GradientDrawable ciemneSzklo(Context c) {
        return ksztalt(0x5C08080E, j(c, 44), Math.max(1, j(c, 1)), 0x29FFFFFF);
    }

    /**
     * Tło okna wiadomości: rozmyta tapeta launchera z mocniejszymi barwami i lekkim przyciemnieniem. Własna kopia,
     * bo launcher zwalnia swoją bitmapę przy zmianie tapety. Zwraca null, gdy tapety nie ma.
     */
    private static Bitmap tloZTapety(MainActivity host) {
        Bitmap zrodlo = host.glassBg;
        if (zrodlo == null || zrodlo.isRecycled()) return null;
        try {
            Bitmap kopia = Bitmap.createBitmap(zrodlo.getWidth(), zrodlo.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas k = new Canvas(kopia);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            ColorMatrix barwy = new ColorMatrix();
            barwy.setSaturation(1.8f);
            p.setColorFilter(new ColorMatrixColorFilter(barwy));
            k.drawBitmap(zrodlo, 0f, 0f, p);
            k.drawColor(0x3D000000);
            return kopia;
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    /**
     * Okno wiadomości: nagłówki po lewej, wybrany duży po prawej, pod nim przyciski. Kolor okna bierze się z tapety.
     * {@code start}: numer nagłówka, który właśnie pokazywał kafelek.
     */
    static boolean pokazWiadomosci(final MainActivity host, final Runnable poZmianie, int start) {
        final Context c = host;
        final List<HomeDashboard.NewsHeadline> lista = HomeDashboard.modPlWiadomosci();
        final FrameLayout korzen = new FrameLayout(c);
        korzen.setBackgroundColor(TLO_OKNA);
        final Dialog d = okno(host, korzen);

        Bitmap tapeta = tloZTapety(host);
        if (tapeta != null) {
            ImageView tlo = new ImageView(c);
            tlo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tlo.setImageBitmap(tapeta);
            korzen.addView(tlo, new FrameLayout.LayoutParams(MATCH, MATCH));
        }
        String kraj = I18n.isPl(c) ? new Locale("", "PL").getDisplayCountry(I18n.locale(c)) : I18n.getLang(c).toUpperCase(Locale.ROOT);
        naglowek(c, korzen, I18n.t(c, "dash_news"), "Google News · " + kraj);

        // lista nagłówków na ciemnym szkle
        ViewGroup[] l = lista(c, 24, 20, 20);
        final LinearLayout kolumna = (LinearLayout) l[1];
        l[0].setBackground(ciemneSzklo(c));
        ModPlOkna.zaokraglij(l[0], j(c, 44));
        FrameLayout.LayoutParams svlp = new FrameLayout.LayoutParams(j(c, 828), j(c, 836), Gravity.TOP | Gravity.LEFT);
        svlp.leftMargin = j(c, 56);
        svlp.topMargin = j(c, 132);
        korzen.addView(l[0], svlp);

        // karta czytania
        LinearLayout karta = new LinearLayout(c);
        karta.setOrientation(LinearLayout.VERTICAL);
        karta.setPadding(j(c, 56), j(c, 54), j(c, 56), j(c, 40));
        karta.setBackground(ciemneSzklo(c));
        FrameLayout.LayoutParams klp = new FrameLayout.LayoutParams(j(c, 900), j(c, 460), Gravity.TOP | Gravity.LEFT);
        klp.leftMargin = j(c, 940);
        klp.topMargin = j(c, 132);
        korzen.addView(karta, klp);
        final TextView zrodlo = napis(c, "", 23, "semibold", 0xCCFFFFFF);
        zrodlo.setLetterSpacing(0.085f);
        zrodlo.setSingleLine(true);
        zrodlo.setEllipsize(TextUtils.TruncateAt.END);
        karta.addView(zrodlo);
        final TextView tytul = napis(c, "", 57, "display", BIALY);
        tytul.setLetterSpacing(-0.02f);
        tytul.setLineSpacing(0f, 1.16f);
        tytul.setMaxLines(4);
        tytul.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(MATCH, WRAP);
        tl.topMargin = j(c, 22);
        karta.addView(tytul, tl);
        if (lista.isEmpty()) tytul.setText(I18n.t(c, "Loading top headlines…"));

        LinearLayout pasek = new LinearLayout(c);
        pasek.setOrientation(LinearLayout.HORIZONTAL);
        pasek.setClipChildren(false);
        final TextView bCzytaj = przycisk(c, "↗  " + I18n.t(c, "Read in browser"), 27, 34);
        pasek.addView(bCzytaj, new LinearLayout.LayoutParams(WRAP, j(c, 66)));
        final TextView bOdswiez = przycisk(c, "↻  " + I18n.t(c, "Refresh"), 27, 34);
        LinearLayout.LayoutParams bol = new LinearLayout.LayoutParams(WRAP, j(c, 66));
        bol.leftMargin = j(c, 20);
        pasek.addView(bOdswiez, bol);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.LEFT);
        pl.leftMargin = j(c, 940);
        pl.topMargin = j(c, 624);
        korzen.addView(pasek, pl);

        final int[] wybrany = {Math.max(0, Math.min(start, lista.size() - 1))};
        final List<WierszWiadomosci> wiersze = new ArrayList<WierszWiadomosci>();
        final Runnable podglad = new Runnable() {
            @Override
            public void run() {
                if (lista.isEmpty()) {
                    bCzytaj.setVisibility(View.GONE);
                    return;
                }
                HomeDashboard.NewsHeadline n = lista.get(Math.min(wybrany[0], lista.size() - 1));
                String kiedy = ModPlKafelki.ileTemu(c, ModPlKafelki.czasRss(n.pubDate));
                zrodlo.setText((n.source + (kiedy.isEmpty() ? "" : "  ·  " + kiedy)).toUpperCase(I18n.locale(c)));
                tytul.setText(n.title);
                boolean link = n.link != null && n.link.startsWith("http");
                bCzytaj.setVisibility(link ? View.VISIBLE : View.GONE);
            }
        };
        for (int i = 0; i < lista.size(); i++) {
            final int idx = i;
            HomeDashboard.NewsHeadline n = lista.get(i);
            String kiedy = ModPlKafelki.ileTemu(c, ModPlKafelki.czasRss(n.pubDate));
            final WierszWiadomosci w = new WierszWiadomosci();
            w.calosc = new LinearLayout(c);
            w.calosc.setOrientation(LinearLayout.VERTICAL);
            w.calosc.setGravity(Gravity.CENTER_VERTICAL);
            w.calosc.setPadding(j(c, 28), 0, j(c, 28), 0);
            w.calosc.setFocusable(true);
            w.calosc.setClickable(true);
            bezSystemowegoPodswietlenia(w.calosc);
            w.zrodlo = napis(c, n.source + (kiedy.isEmpty() ? "" : "  ·  " + kiedy), 23, "semibold", 0xC2FFFFFF);
            w.zrodlo.setSingleLine(true);
            w.zrodlo.setEllipsize(TextUtils.TruncateAt.END);
            w.calosc.addView(w.zrodlo);
            w.tytul = napis(c, n.title, 27, "medium", BIALY);
            w.tytul.setMaxLines(2);
            w.tytul.setEllipsize(TextUtils.TruncateAt.END);
            w.tytul.setLineSpacing(0f, 1.2f);
            LinearLayout.LayoutParams ttl = new LinearLayout.LayoutParams(MATCH, WRAP);
            ttl.topMargin = j(c, 6);
            w.calosc.addView(w.tytul, ttl);
            w.calosc.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean jest) {
                    if (jest && wybrany[0] != idx) {
                        int stary = wybrany[0];
                        wybrany[0] = idx;
                        if (stary >= 0 && stary < wiersze.size()) {
                            wiersze.get(stary).stan = 0;
                            malujWiersz(c, wiersze.get(stary));
                        }
                        podglad.run();
                    }
                    w.stan = jest ? 1 : (wybrany[0] == idx ? 2 : 0);
                    malujWiersz(c, w);
                }
            });
            w.calosc.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int code, KeyEvent e) {
                    if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                    if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (bCzytaj.getVisibility() == View.VISIBLE) bCzytaj.requestFocus();
                        else bOdswiez.requestFocus();
                        return true;
                    }
                    if (code == KeyEvent.KEYCODE_DPAD_LEFT) return true;
                    if (code == KeyEvent.KEYCODE_DPAD_UP && idx == 0) return true;
                    return code == KeyEvent.KEYCODE_DPAD_DOWN && idx == lista.size() - 1;
                }
            });
            w.calosc.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (bCzytaj.getVisibility() == View.VISIBLE) bCzytaj.requestFocus();
                }
            });
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(MATCH, j(c, 128));
            if (i > 0) wlp.topMargin = j(c, 6);
            kolumna.addView(w.calosc, wlp);
            wiersze.add(w);
        }
        View.OnKeyListener klawiszePrzyciskow = new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int code, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                boolean pierwszy = v == bCzytaj || bCzytaj.getVisibility() != View.VISIBLE;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT && pierwszy && !wiersze.isEmpty()) {
                    wiersze.get(Math.max(0, Math.min(wybrany[0], wiersze.size() - 1))).calosc.requestFocus();
                    return true;
                }
                return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                        || (code == KeyEvent.KEYCODE_DPAD_RIGHT && v == bOdswiez);
            }
        };
        bCzytaj.setOnKeyListener(klawiszePrzyciskow);
        bOdswiez.setOnKeyListener(klawiszePrzyciskow);
        bCzytaj.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (lista.isEmpty()) return;
                String link = lista.get(Math.min(wybrany[0], lista.size() - 1)).link;
                if (link == null || !link.startsWith("http")
                        || !Suggestions.start(c, new Intent(Intent.ACTION_VIEW, Uri.parse(link)))) {
                    Toast.makeText(c, I18n.t(c, "No browser to open the article"), Toast.LENGTH_SHORT).show();
                }
            }
        });
        bOdswiez.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bOdswiez.setText(I18n.t(c, "Loading top headlines…"));
                HomeDashboard.fetchLiveNews(c, true, new Runnable() {
                    @Override
                    public void run() {
                        if (!d.isShowing()) return;
                        d.dismiss();
                        pokazWiadomosci(host, poZmianie, 0);
                        if (poZmianie != null) poZmianie.run();
                    }
                });
            }
        });
        podglad.run();
        d.show();
        if (!wiersze.isEmpty()) wiersze.get(wybrany[0]).calosc.requestFocus();
        else bOdswiez.requestFocus();
        return true;
    }

    // ------------------------------------------------------------------ pytanie o wskaźnik głośności (25.11.2026)

    /**
     * Jednorazowe pytanie po aktualizacji (ModPlGlosnosc.zapytajPoAktualizacji): włączyć szklany wskaźnik głośności
     * czy zostawić pasek systemowy. Wygląd A z kanwy 25.11.2026 (wybór użytkownika): pełny ekran jak okna widżetów,
     * rozmyta tapeta, tekst i przyciski po lewej, żywy podgląd pigułki w jej prawdziwym miejscu przy prawej krawędzi.
     * BACK i HOME zamykają bez zmiany: zostaje pasek systemowy, a wybór jest w ustawieniach.
     */
    static void pytanieGlosnosci(final MainActivity host) {
        final Context c = host;
        final FrameLayout korzen = new FrameLayout(c);
        final Dialog d = okno(host, korzen);

        TextView etykieta = napis(c, I18n.t(c, "NEW IN THIS VERSION"), 23, "semibold", TEKST_70);
        etykieta.setLetterSpacing(0.085f);
        TextView tytul = napis(c, I18n.t(c, "Glass volume indicator"), 76, "display", BIALY);
        tytul.setLetterSpacing(-0.02f);
        tytul.setLineSpacing(0f, 1.1f);
        TextView tresc = napis(c, I18n.t(c, "When you change the volume with the remote, a glass pill in the tvOS style appears at the right edge of the screen instead of Android's volume bar."),
                31, "regular", TEKST_86);
        tresc.setLineSpacing(0f, 1.22f);
        TextView gdzie = napis(c, I18n.t(c, "You can change it anytime in Settings → Look and scenes → Dock."),
                25, "regular", TEKST_62);
        gdzie.setLineSpacing(0f, 1.18f);

        final TextView tak = przycisk(c, I18n.t(c, "Turn on the glass pill"));
        final TextView nie = przycisk(c, I18n.t(c, "Keep the system bar"));
        tak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                ModPlGlosnosc.wybierz(host, true);
            }
        });
        nie.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                ModPlGlosnosc.wybierz(host, false);
            }
        });
        LinearLayout przyciski = new LinearLayout(c);
        przyciski.setOrientation(LinearLayout.HORIZONTAL);
        przyciski.setClipChildren(false);
        przyciski.setClipToPadding(false);
        int wysPrzycisku = j(c, 66);
        przyciski.addView(tak, new LinearLayout.LayoutParams(WRAP, wysPrzycisku));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(WRAP, wysPrzycisku);
        nlp.leftMargin = j(c, 24);
        przyciski.addView(nie, nlp);

        LinearLayout kolumna = new LinearLayout(c);
        kolumna.setOrientation(LinearLayout.VERTICAL);
        kolumna.setClipChildren(false);
        kolumna.setClipToPadding(false);
        kolumna.addView(etykieta);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        tlp.topMargin = j(c, 18);
        kolumna.addView(tytul, tlp);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        rlp.topMargin = j(c, 26);
        kolumna.addView(tresc, rlp);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(MATCH, WRAP);
        glp.topMargin = j(c, 20);
        kolumna.addView(gdzie, glp);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(WRAP, WRAP);
        plp.topMargin = j(c, 60);
        kolumna.addView(przyciski, plp);

        // rozmyta tapeta, przyciemniona z lewej (tam jest tekst), i pigułka tam, gdzie się pokazuje
        korzen.setBackgroundColor(TLO_OKNA);
        Bitmap tapeta = tloZTapety(host);
        if (tapeta != null) {
            ImageView tlo = new ImageView(c);
            tlo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tlo.setImageBitmap(tapeta);
            korzen.addView(tlo, new FrameLayout.LayoutParams(MATCH, MATCH));
        }
        View cien = new View(c);
        cien.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xD9000000, 0x99000000, 0x40000000}));
        korzen.addView(cien, new FrameLayout.LayoutParams(MATCH, MATCH));
        FrameLayout.LayoutParams klp = new FrameLayout.LayoutParams(j(c, 1040), WRAP, Gravity.CENTER_VERTICAL | Gravity.LEFT);
        klp.leftMargin = j(c, 80);
        korzen.addView(kolumna, klp);
        korzen.addView(ModPlGlosnosc.podglad(c, c.getResources().getDisplayMetrics().widthPixels / 1920f),
                ModPlGlosnosc.miejscePigulki(c));
        d.show();
        tak.requestFocus();
    }

    // ------------------------------------------------------------------ pogoda (rysowana na widoku WeatherView oryginału)

    private static final Paint PEDZEL = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private static final RectF POLE = new RectF();

    /** Napis panelu pogody z miękkim cieniem, żeby biały tekst był czytelny na przezroczystym szkle. */
    private static void tekstPogody(Context c, Canvas k, String t, float x, float y, float rozmiar, String waga, int kolor,
                                    Paint.Align wyrownanie, float odstepLiter) {
        Paint p = PEDZEL;
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Ui.font(c, waga));
        p.setTextSize(rozmiar);
        p.setTextAlign(wyrownanie);
        p.setLetterSpacing(odstepLiter);
        p.setColor(kolor);
        p.setShadowLayer(rozmiar * 0.12f, 0f, rozmiar * 0.04f, 0x59000000);
        k.drawText(t, x, y, p);
        p.clearShadowLayer();
        p.setLetterSpacing(0f);
    }

    /** Szklana karta wewnątrz panelu pogody: biel 10% z jasną krawędzią. */
    private static void kartaPogody(Canvas k, float lewo, float gora, float prawo, float dol, float s) {
        Paint p = PEDZEL;
        p.setShader(null);
        POLE.set(lewo, gora, prawo, dol);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x1AFFFFFF);
        k.drawRoundRect(POLE, 28 * s, 28 * s, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, s));
        p.setColor(0x2EFFFFFF);
        k.drawRoundRect(POLE, 28 * s, 28 * s, p);
        p.setStyle(Paint.Style.FILL);
    }

    private static void ikonaPogody(Canvas k, int kod, boolean dzien, float x, float y, float rozmiar) {
        Bitmap b = WeatherIcons.get(kod, dzien, Math.round(rozmiar));
        if (b != null) k.drawBitmap(b, x, y, null);
    }

    /** Etykieta przed liczbą („↑”, „Wiatr”, „Aktualizacja:”) z dokładnie jedną spacją na końcu. */
    private static String etykieta(String s) {
        return s == null ? "" : s.trim() + " ";
    }

    /**
     * Pełny panel pogody w układzie z kanwy: nagłówek na środku, dwie szklane karty (godziny i 5 dni) i źródło na dole.
     * Rysuje się na widoku WeatherView oryginału (szkło panelu jest jego tłem), który ma 620×960 pikseli kanwy.
     */
    static void rysujPogode(View v, Canvas k, Weather.Data d) {
        if (d == null || v.getWidth() <= 0) return;
        Context c = v.getContext();
        Paint p = PEDZEL;
        float s = v.getWidth() / 620f, w = v.getWidth(), h = v.getHeight();
        float x0 = 36 * s, x1 = w - 36 * s, mid = w / 2f;
        int godziny = Math.min(6, d.hours.size()), dni = Math.min(5, d.days.size());
        float wiersz = 56 * s;
        float hNaglowka = 260 * s, hGodzin = godziny > 0 ? 200 * s : 0f, hDni = dni > 0 ? 64 * s + wiersz * dni : 0f, hStopki = 30 * s;
        float odstep = Math.max(8 * s, (h - 64 * s - hNaglowka - hGodzin - hDni - hStopki) / 3f);
        float y = 36 * s;

        // nagłówek
        tekstPogody(c, k, d.place.isEmpty() ? I18n.s(c, "My Location", "Konumum") : d.place, mid, y + 27 * s, 29 * s,
                "semibold", BIALY, Paint.Align.CENTER, 0f);
        tekstPogody(c, k, d.temp + "°", mid + 13 * s, y + 134 * s, 112 * s, "regular", BIALY, Paint.Align.CENTER, -0.03f);
        tekstPogody(c, k, Weather.describe(c, d.code, d.day), mid, y + 181 * s, 31 * s, "medium", BIALY, Paint.Align.CENTER, 0f);
        tekstPogody(c, k, etykieta(I18n.s(c, "H:", "Y:")) + d.max + "°      " + etykieta(I18n.s(c, "L:", "D:")) + d.min + "°",
                mid, y + 218 * s, 25 * s, "medium", TEKST_86, Paint.Align.CENTER, 0f);
        tekstPogody(c, k, etykieta(I18n.s(c, "Humidity ", "Nem %")) + d.humidity + (I18n.isTr(c) ? "" : "%") + "  ·  "
                        + etykieta(I18n.s(c, "Wind ", "Rüzgar ")) + d.wind + " " + d.windUnit,
                mid, y + 252 * s, 23 * s, "medium", TEKST_78, Paint.Align.CENTER, 0f);
        y += hNaglowka + odstep;

        // prognoza godzinowa
        if (godziny > 0) {
            kartaPogody(k, x0, y, x1, y + hGodzin, s);
            tekstPogody(c, k, I18n.s(c, "HOURLY FORECAST", "SAATLİK TAHMİN"), x0 + 22 * s, y + 40 * s, 23 * s,
                    "semibold", TEKST_78, Paint.Align.LEFT, 0.06f);
            float kol = (x1 - x0 - 44 * s) / godziny;
            for (int i = 0; i < godziny; i++) {
                Weather.Hour g = d.hours.get(i);
                float cx = x0 + 22 * s + kol * (i + 0.5f);
                tekstPogody(c, k, g.label, cx, y + 83 * s, 25 * s, "semibold", i == 0 ? BIALY : TEKST_86, Paint.Align.CENTER, 0f);
                ikonaPogody(k, g.code, g.day, cx - 20 * s, y + 98 * s, 40 * s);
                tekstPogody(c, k, g.temp + "°", cx, y + 172 * s, 29 * s, "semibold", BIALY, Paint.Align.CENTER, 0f);
            }
            y += hGodzin + odstep;
        }

        // prognoza na 5 dni: dzień, ikona, najniższa, pasek zakresu w barwach temperatury, najwyższa
        if (dni > 0) {
            kartaPogody(k, x0, y, x1, y + hDni, s);
            tekstPogody(c, k, I18n.s(c, "5-DAY FORECAST", "5 GÜNLÜK TAHMİN"), x0 + 22 * s, y + 40 * s, 23 * s,
                    "semibold", TEKST_78, Paint.Align.LEFT, 0.06f);
            int lo = d.temp, hi = d.temp;
            for (int i = 0; i < dni; i++) {
                lo = Math.min(lo, d.days.get(i).min);
                hi = Math.max(hi, d.days.get(i).max);
            }
            float zakres = Math.max(1, hi - lo);
            float wx0 = x0 + 22 * s, wx1 = x1 - 22 * s, bx0 = wx0 + 222 * s, bx1 = wx1 - 62 * s, grubosc = 8 * s;
            boolean fahrenheit = d.windUnit.contains("mp");
            for (int i = 0; i < dni; i++) {
                Weather.Day dz = d.days.get(i);
                float gora = y + 56 * s + wiersz * i, cy = gora + wiersz / 2f, linia = cy + 10 * s;
                p.setShader(null);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x29FFFFFF);
                p.setStrokeWidth(Math.max(1f, s));
                k.drawLine(wx0, gora, wx1, gora, p);
                tekstPogody(c, k, dz.label, wx0, linia, 27 * s, "semibold", BIALY, Paint.Align.LEFT, 0f);
                float ix = wx0 + 110 * s;
                if (dz.rain >= 20) {
                    ikonaPogody(k, dz.code, true, ix + 3 * s, gora + 3 * s, 30 * s);
                    tekstPogody(c, k, dz.rain + "%", ix + 18 * s, gora + wiersz - 5 * s, 19 * s, "semibold", 0xFF64C8FF,
                            Paint.Align.CENTER, 0f);
                } else {
                    ikonaPogody(k, dz.code, true, ix, cy - 18 * s, 36 * s);
                }
                tekstPogody(c, k, dz.min + "°", wx0 + 208 * s, linia, 27 * s, "medium", TEKST_78, Paint.Align.RIGHT, 0f);
                tekstPogody(c, k, dz.max + "°", wx1, linia, 27 * s, "semibold", BIALY, Paint.Align.RIGHT, 0f);
                p.setShader(null);
                p.setColor(0x33FFFFFF);
                POLE.set(bx0, cy - grubosc / 2, bx1, cy + grubosc / 2);
                k.drawRoundRect(POLE, grubosc / 2, grubosc / 2, p);
                float a = bx0 + (bx1 - bx0) * (dz.min - lo) / zakres, b = bx0 + (bx1 - bx0) * (dz.max - lo) / zakres;
                p.setColor(BIALY);   // pełne krycie: przezroczystość toru nie może przejść na zakres
                p.setShader(new LinearGradient(a, 0, Math.max(b, a + 1), 0, WeatherView.tempColor(dz.min, fahrenheit),
                        WeatherView.tempColor(dz.max, fahrenheit), Shader.TileMode.CLAMP));
                POLE.set(a, cy - grubosc / 2, Math.max(b, a + grubosc), cy + grubosc / 2);
                k.drawRoundRect(POLE, grubosc / 2, grubosc / 2, p);
                p.setShader(null);
                if (i == 0) {   // bieżąca temperatura na pasku dzisiejszego dnia
                    float nx = bx0 + (bx1 - bx0) * (d.temp - lo) / zakres;
                    p.setColor(0xFF1C1C26);
                    k.drawCircle(nx, cy, 7 * s, p);
                    p.setColor(BIALY);
                    k.drawCircle(nx, cy, 4.2f * s, p);
                }
            }
        }

        // źródło i godzina ostatniej aktualizacji
        String kiedy = android.text.format.DateFormat.getTimeFormat(c).format(new Date(d.fetchedAt));
        tekstPogody(c, k, Weather.sourceName(d.source) + "  ·  " + etykieta(I18n.s(c, "Updated ", "Güncellendi ")) + kiedy,
                mid, h - 36 * s, 23 * s, "medium", 0xBDFFFFFF, Paint.Align.CENTER, 0f);
    }
}
