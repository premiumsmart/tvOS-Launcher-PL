package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mod PL: wejścia do okien widżetów (same okna rysuje ModPlOknaTv) oraz wspólne funkcje: pobieranie obrazów,
 * zwiastuny, otwieranie serwisów, opisy stanu meczu i odświeżanie kafelka meczów.
 * Plik źródłowy leży w pl/java/, a pl/naloz.py kopiuje go do src/ przy każdym buildzie.
 */
final class ModPlOkna {
    private ModPlOkna() { }

    private static final ExecutorService POBIERANIE = Executors.newFixedThreadPool(3);
    private static final Handler GLOWNY = new Handler(Looper.getMainLooper());

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

    static void zaokraglij(View v, final float promien) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), promien);
            }
        });
        v.setClipToOutline(true);
    }

    static void wczytaj(final Context c, final String url, final int szer, final Ladowanie gotowe) {
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

    interface Ladowanie {
        void ok(Bitmap b);
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

    static String aplikacjaSerwisu(Context c, String serwis) {
        for (String p : pakietySerwisu(serwis)) if (zainstalowana(c, p)) return p;
        return null;
    }

    static void otworzSerwis(Context c, Suggestions.Item it, String pkg) {
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

    static void zwiastun(Context c, Suggestions.Item it) {
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

    static String typ(Context c, Suggestions.Item it) {
        boolean serial = "tv".equalsIgnoreCase(it.type) || "series".equalsIgnoreCase(it.type);
        return I18n.t(c, serial ? "Series" : "Movie");
    }

    static String krotkiSerwis(String s) {
        if (s == null) return "";
        if (s.startsWith("Amazon Prime Video")) return "Prime Video";
        return s;
    }

    private static final java.util.regex.Pattern MD_NAGLOWEK = java.util.regex.Pattern.compile("(?m)^(#{1,6})[ \\t]*(.*)$");

    /**
     * Lista zmian w oknie aktualizacji: opis wydania z GitHuba jest w Markdownie, a TextView pokazuje surowy tekst.
     * Nagłówki tracą „#” (od drugiego poziomu wielkimi literami), „[tekst](adres)” zostaje samym tekstem,
     * znika pogrubienie i kod, a wypunktowania „- ” dostają kropkę „• ”.
     */
    static String bezMarkdown(String s) {
        if (s == null) return "";
        String t = s.replace("\r\n", "\n");
        java.util.regex.Matcher m = MD_NAGLOWEK.matcher(t);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String naglowek = m.group(2).trim();
            if (m.group(1).length() > 1) naglowek = naglowek.toUpperCase(Locale.ROOT);
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(naglowek));
        }
        m.appendTail(sb);
        t = sb.toString();
        t = t.replaceAll("\\[([^\\]\\n]+)\\]\\((?:https?://)[^)\\s]+\\)", "$1");
        t = t.replace("**", "").replace("`", "");
        t = t.replaceAll("(?m)^([ \\t]*)[-*][ \\t]+", "$1• ");
        t = t.replaceAll("\\n{3,}", "\n\n");
        return t.trim();
    }

    /** Zwraca true, gdy okno zostało pokazane (wtedy stary kod się nie wykonuje). */
    static boolean pokazNajczesciej(final MainActivity host, final List<Suggestions.Item> wszystkie) {
        return pokazNajczesciej(host, wszystkie, 0);
    }

    /** Jak wyżej, z zaznaczonym tytułem, który właśnie pokazywał kafelek ({@code start} to jego numer na liście). */
    static boolean pokazNajczesciej(final MainActivity host, final List<Suggestions.Item> wszystkie, int start) {
        List<String> kody = HomeDashboard.getEffectiveReleasesCountries(host);
        String kraje = TextUtils.join(", ", kody);
        // jeden albo dwa kraje: pełne nazwy w języku interfejsu („Polska”), więcej: same kody, żeby zmieściły się w nagłówku
        String opisKrajow = kraje;
        if (kody.size() <= 2) {
            List<String> nazwy = new ArrayList<String>();
            for (String kod : kody) {
                String nazwa = new Locale("", kod).getDisplayCountry(I18n.locale(host));
                nazwy.add(nazwa == null || nazwa.isEmpty() ? kod : nazwa);
            }
            opisKrajow = TextUtils.join(", ", nazwy);
        }
        return ModPlOknaTv.pokazFilmy(host, wszystkie, "JustWatch · " + opisKrajow, I18n.t(host, "dash_latest_releases"),
                false, kraje, start);
    }

    /** Okno „Top filmy i seriale” (ranking IMDb z katalogu Cinemeta): duży kadr i półka plakatów, zob. ModPlOknaTv. */
    static boolean pokazTop(final MainActivity host, final List<Suggestions.Item> wszystkie) {
        return pokazTop(host, wszystkie, 0);
    }

    static boolean pokazTop(final MainActivity host, final List<Suggestions.Item> wszystkie, int start) {
        return ModPlOknaTv.pokazFilmy(host, wszystkie, "IMDb · Cinemeta", I18n.t(host, "dash_top_media"),
                true, null, start);
    }

    /** Gatunki po polsku („Drama · Thriller” → „Dramat, Thriller”). */
    static String gatunki(Context c, String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String g : s.split(" · ")) {
            if (g.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(I18n.t(c, g.trim()));
        }
        return sb.toString();
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

    static String klucz(HomeDashboard.MatchItem m) {
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

    // --- okna meczów i wiadomości

    /** Okno meczów w stylu tvOS 27 (kolory drużyn z herbów), zob. ModPlOknaTv. */
    static boolean pokazMecze(final MainActivity host, final Runnable poZmianie) {
        return ModPlOknaTv.pokazMecze(host, poZmianie);
    }

    /** Okno wiadomości w stylu tvOS 27 (kolor z tapety), zob. ModPlOknaTv. */
    static boolean pokazWiadomosci(final MainActivity host, final Runnable poZmianie) {
        return ModPlOknaTv.pokazWiadomosci(host, poZmianie, 0);
    }

    /** {@code start}: numer nagłówka, który właśnie pokazywał kafelek. */
    static boolean pokazWiadomosci(final MainActivity host, final Runnable poZmianie, int start) {
        return ModPlOknaTv.pokazWiadomosci(host, poZmianie, start);
    }
}
