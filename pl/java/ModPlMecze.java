package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

/**
 * Mod PL: główne źródło meczów - LiveScore (publiczne dane aplikacji, bez klucza): wyniki na żywo
 * z minutą, wyniki z ostatnich dni i terminy na dwa tygodnie naprzód, Ekstraklasa i ligi zagraniczne.
 * Jedno zapytanie = wszystkie mecze jednego dnia; osobne, lekkie zapytanie = mecze trwające teraz.
 * Każdy dzień leży w pliku tekstowym w pamięci aplikacji, więc zmiana ligi i ulubionych nie wymaga sieci.
 * Gdy LiveScore nie odpowiada, pobierz() zwraca null i HomeDashboard używa starych źródeł
 * (ESPN + TheSportsDB). Terminy Ekstraklasy poprawia TheSportsDB (LiveScore podaje je zbiorczo).
 * Plik źródłowy leży w pl/java/, a pl/naloz.py kopiuje go do src/ przy każdym buildzie.
 */
final class ModPlMecze {
    private ModPlMecze() { }

    private static final String TAG = "ModPlMecze";
    private static final String API = "https://prod-public-api.livescore.com/v1/api/app/";
    /** "high" to największy rozmiar, jaki LiveScore udostępnia (64×64 px); "medium" ma 48×48. */
    private static final String HERBY = "https://lsm-static-prod.livescore.com/high/";
    private static final String PREFS = "mod_pl", K_MIGAWKA = "mecze_migawka", K_MIGAWKA_LIGA = "mecze_migawka_liga";
    /** Okno dni: szerokie, żeby przerwa na reprezentacje nie zostawiała pustej listy. */
    private static final int DNI_WSTECZ = 5, DNI_NAPRZOD = 14;
    private static final long MIN = 60_000L, GODZ = 3_600_000L;
    private static final int POL = 12;   // pól w wierszu pliku dnia

    /** {kod ligi z HomeDashboard.LEAGUES, identyfikator rozgrywek w LiveScore, nazwa (klucz tłumaczenia)}. */
    static final String[][] LIGI = {
        {"pol.1", "92", "Ekstraklasa"},
        {"uefa.champions", "60", "Champions League"},
        {"uefa.europa", "36", "Europa League"},
        {"uefa.europa.conf", "62", "Conference League"},
        {"eng.1", "65", "Premier League"},
        {"esp.1", "75", "LaLiga"},
        {"ita.1", "77", "Serie A"},
        {"ger.1", "67", "Bundesliga"},
        {"fra.1", "68", "Ligue 1"},
    };

    /** LiveScore pisze nazwy bez polskich liter - poprawiamy najczęstsze. */
    private static final String[][] NAZWY = {
        {"Lech Poznan", "Lech Poznań"}, {"Zaglebie Lubin", "Zagłębie Lubin"}, {"Slask Wroclaw", "Śląsk Wrocław"},
        {"Rakow Czestochowa", "Raków Częstochowa"}, {"Pogon Szczecin", "Pogoń Szczecin"},
        {"Gornik Zabrze", "Górnik Zabrze"}, {"Jagiellonia Bialystok", "Jagiellonia Białystok"},
        {"Wisla Krakow", "Wisła Kraków"}, {"Wisla Plock", "Wisła Płock"}, {"Widzew Lodz", "Widzew Łódź"},
        {"Wieczysta Krakow", "Wieczysta Kraków"}, {"Lechia Gdansk", "Lechia Gdańsk"},
        {"Puszcza Niepolomice", "Puszcza Niepołomice"}, {"LKS Lodz", "ŁKS Łódź"},
        {"Zaglebie Sosnowiec", "Zagłębie Sosnowiec"}, {"Gornik Leczna", "Górnik Łęczna"},
        {"Ruch Chorzow", "Ruch Chorzów"}, {"Miedz Legnica", "Miedź Legnica"}, {"Stal Rzeszow", "Stal Rzeszów"},
        {"Znicz Pruszkow", "Znicz Pruszków"}, {"Chrobry Glogow", "Chrobry Głogów"},
        {"Pogon Siedlce", "Pogoń Siedlce"}, {"Poland", "Polska"},
        // reprezentacje i nazwy krajów przy mniej znanych rozgrywkach
        {"Romania", "Rumunia"}, {"Sweden", "Szwecja"}, {"Germany", "Niemcy"}, {"France", "Francja"},
        {"Spain", "Hiszpania"}, {"Italy", "Włochy"}, {"England", "Anglia"}, {"Portugal", "Portugalia"},
        {"Netherlands", "Holandia"}, {"Belgium", "Belgia"}, {"Croatia", "Chorwacja"}, {"Czech Republic", "Czechy"},
        {"Czechia", "Czechy"}, {"Slovakia", "Słowacja"}, {"Ukraine", "Ukraina"}, {"Lithuania", "Litwa"},
        {"Latvia", "Łotwa"}, {"Finland", "Finlandia"}, {"Norway", "Norwegia"}, {"Denmark", "Dania"},
        {"Scotland", "Szkocja"}, {"Wales", "Walia"}, {"Northern Ireland", "Irlandia Północna"},
        {"Republic of Ireland", "Irlandia"}, {"Ireland", "Irlandia"}, {"Switzerland", "Szwajcaria"},
        {"Hungary", "Węgry"}, {"Slovenia", "Słowenia"}, {"Bosnia and Herzegovina", "Bośnia i Hercegowina"},
        {"Bosnia-Herzegovina", "Bośnia i Hercegowina"}, {"Montenegro", "Czarnogóra"},
        {"North Macedonia", "Macedonia Północna"}, {"Greece", "Grecja"}, {"Turkey", "Turcja"}, {"Turkiye", "Turcja"},
        {"Türkiye", "Turcja"}, {"Bulgaria", "Bułgaria"}, {"Moldova", "Mołdawia"}, {"Belarus", "Białoruś"},
        {"Russia", "Rosja"}, {"Georgia", "Gruzja"}, {"Azerbaijan", "Azerbejdżan"}, {"Kazakhstan", "Kazachstan"},
        {"Israel", "Izrael"}, {"Cyprus", "Cypr"}, {"Iceland", "Islandia"}, {"Luxembourg", "Luksemburg"},
        {"Kosovo", "Kosowo"}, {"Faroe Islands", "Wyspy Owcze"}, {"Andorra", "Andora"}, {"Brazil", "Brazylia"},
        {"Argentina", "Argentyna"}, {"Mexico", "Meksyk"}, {"Japan", "Japonia"}, {"South Korea", "Korea Południowa"},
        {"Morocco", "Maroko"}, {"Egypt", "Egipt"}, {"Uruguay", "Urugwaj"}, {"Colombia", "Kolumbia"},
        {"Canada", "Kanada"},
    };
    private static final Map<String, String> NAZWY_MAPA = new HashMap<String, String>();
    static {
        for (String[] n : NAZWY) NAZWY_MAPA.put(n[0], n[1]);
    }

    private static final class Mecz {
        String id = "", comp = "", kraj = "", liga = "", eps = "";
        String dom = "", gosc = "", domImg = "", goscImg = "", w1 = "", w2 = "";
        long start;      // początek jako czas epoki (po ewentualnej poprawce z TheSportsDB)
        boolean bylNaZywo;
    }

    private static final class Dzien {
        long ts, proba;
        int tz, wszystkich;
        List<Mecz> mecze = new ArrayList<Mecz>();
    }

    private static final Map<String, Dzien> dni = new HashMap<String, Dzien>();
    private static String podpisUlubionych = "";
    private static long blokadaDo, ostatnieNaZywo;
    // Czytane także z wątku głównego (migawka), dlatego volatile i bez wspólnej blokady z pobierz().
    private static volatile List<HomeDashboard.MatchItem> ostatnia = new ArrayList<HomeDashboard.MatchItem>();
    private static volatile String ostatniaLiga = "";
    /** Czy ostatnie pobranie z LiveScore się udało (wtedy pusta lista znaczy "brak meczów", nie "ładowanie"). */
    static volatile boolean dziala;

    // ------------------------------------------------------------------ czas i nazwy

    private static int strefa(long teraz) {
        return (int) (TimeZone.getDefault().getOffset(teraz) / GODZ);
    }

    private static String kluczDnia(long czas, int tz) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(czas + tz * GODZ);
        return String.format(Locale.US, "%04d%02d%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));
    }

    private static long epoka(long esd, int tz) {
        if (esd <= 0) return 0L;
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.clear();
        cal.set((int) (esd / 10000000000L), (int) (esd / 100000000L % 100) - 1, (int) (esd / 1000000L % 100),
                (int) (esd / 10000L % 100), (int) (esd / 100L % 100), (int) (esd % 100));
        return cal.getTimeInMillis() - tz * GODZ;
    }

    private static String ladna(String nazwa) {
        String p = NAZWY_MAPA.get(nazwa);
        return p != null ? p : nazwa;
    }

    /** Język launchera przy ostatnim pobraniu: polskie nazwy krajów w nazwach rozgrywek tylko po polsku. */
    private static volatile boolean polskie = true;

    /** Nazwa do pokazania: po polsku tylko przy polskim języku launchera, w innych językach oryginał z LiveScore. */
    private static String pokaz(Context c, String nazwa) {
        return "pl".equals(I18n.getLang(c)) ? ladna(nazwa) : nazwa;
    }

    private static String norm(String s) {
        String n = ModPlOkna.bezOgonkow(s).trim();
        return n.equals("poland") ? "polska" : n;
    }

    /** Rezerwy, drużyny kobiet i młodzieżowe nie liczą się przy dopasowaniu "zawiera". */
    private static boolean poboczna(String znorm) {
        int sp = znorm.lastIndexOf(' ');
        if (sp < 0) return false;
        String ost = znorm.substring(sp + 1);
        return ost.equals("ii") || ost.equals("iii") || ost.equals("b") || ost.equals("w") || ost.equals("(w)")
                || ost.equals("women") || ost.equals("kobiet") || ost.matches("u\\d\\d");
    }

    private static boolean pasuje(String druzyna, Set<String> ulubione, boolean glowna) {
        if (ulubione.isEmpty()) return false;
        String d = norm(druzyna);
        for (String u : ulubione) {
            if (u.isEmpty()) continue;
            if (d.equals(u)) return true;
            if (glowna && d.contains(u) && !poboczna(d)) return true;
        }
        return false;
    }

    private static Set<String> ulubioneZnorm(Context c) {
        Set<String> out = new TreeSet<String>();
        for (String u : HomeDashboard.getFavoriteTeams(c)) {
            if (u != null && !u.trim().isEmpty()) out.add(norm(u));
        }
        return out;
    }

    /** Czy mecz dotyczy obserwowanej drużyny (to samo sprawdzenie dla listy, gwiazdek i kafelka). */
    static boolean ulubiony(Context c, HomeDashboard.MatchItem m) {
        Set<String> ul = ulubioneZnorm(c);
        return pasuje(m.homeTeam, ul, m.glowna) || pasuje(m.awayTeam, ul, m.glowna);
    }

    static boolean ulubionaDruzyna(Context c, String druzyna, boolean glowna) {
        return pasuje(druzyna, ulubioneZnorm(c), glowna);
    }

    /** Czy wpis z listy ulubionych obejmuje tę drużynę (do zdejmowania obserwowania). */
    static boolean pasujeDo(String druzyna, String wpisUlubionych, boolean glowna) {
        Set<String> jeden = new HashSet<String>();
        jeden.add(norm(wpisUlubionych));
        return pasuje(druzyna, jeden, glowna);
    }

    private static String ligaPoKodzie(String comp) {
        for (String[] l : LIGI) if (l[1].equals(comp)) return l[2];
        return null;
    }

    private static boolean glowna(String comp, String kraj) {
        return ligaPoKodzie(comp) != null || "poland".equals(kraj);
    }

    // ------------------------------------------------------------------ stan meczu

    /** 0 przed meczem, 1 trwa, 2 zakończony, 3 przełożony lub odwołany. */
    private static int stan(String eps) {
        String u = eps == null ? "" : eps.trim().toUpperCase(Locale.ROOT);
        if (u.isEmpty() || u.equals("NS")) return 0;
        if (u.equals("FT") || u.equals("AET") || u.equals("AP") || u.startsWith("ABAN") || u.startsWith("AWAR")) return 2;
        if (u.startsWith("POSTP") || u.startsWith("CANC")) return 3;
        if (Character.isDigit(u.charAt(0)) || u.equals("HT") || u.equals("ET") || u.startsWith("PEN")
                || u.equals("BREAK") || u.startsWith("INT") || u.startsWith("SUSP")) return 1;
        return 0;
    }

    private static HomeDashboard.MatchItem naPozycje(Context c, Mecz m) {
        HomeDashboard.MatchItem mi = new HomeDashboard.MatchItem();
        mi.id = m.id;
        mi.homeTeam = pokaz(c, m.dom);
        mi.awayTeam = pokaz(c, m.gosc);
        mi.homeLogo = m.domImg.isEmpty() ? "" : HERBY + m.domImg;
        mi.awayLogo = m.goscImg.isEmpty() ? "" : HERBY + m.goscImg;
        mi.startEpochMs = m.start;
        mi.glowna = glowna(m.comp, m.kraj);
        String znana = ligaPoKodzie(m.comp);
        mi.leagueName = I18n.t(c, znana != null ? znana : m.liga);
        int s = stan(m.eps);
        String u = m.eps.trim().toUpperCase(Locale.ROOT);
        mi.isLive = s == 1;
        mi.isFinished = s == 2;
        if (s == 1) mi.statusText = u.equals("HT") || u.equals("BREAK") ? "HT" : m.eps.trim();
        else if (s == 2) mi.statusText = u.equals("AP") ? "PEN" : u.equals("AET") ? "AET" : u.startsWith("ABAN") ? "ABD" : "FT";
        else if (s == 3) mi.statusText = u.startsWith("CANC") ? "CANC" : "PST";
        else mi.statusText = "";
        if (s == 1 || s == 2) {
            mi.homeScore = m.w1.isEmpty() ? "0" : m.w1;
            mi.awayScore = m.w2.isEmpty() ? "0" : m.w2;
        }
        return mi;
    }

    // ------------------------------------------------------------------ wiersze i pliki dni

    private static String tekst(JSONObject o, String k) {
        if (o == null || o.isNull(k)) return "";
        String s = o.optString(k, "");
        if ("null".equals(s)) return "";
        return s.indexOf('\t') >= 0 || s.indexOf('\n') >= 0 ? s.replace('\t', ' ').replace('\n', ' ') : s;
    }

    /**
     * Odpowiedź LiveScore -> wiersze wszystkich meczów: id, rozgrywki, kraj, nazwa ligi, początek
     * (RRRRMMDDGGMMSS), stan, gospodarz, gość, herb gospodarza, herb gościa, wynik gospodarza, wynik gościa.
     */
    private static List<String[]> wiersze(String json) throws Exception {
        List<String[]> out = new ArrayList<String[]>();
        JSONArray st = new JSONObject(json).optJSONArray("Stages");
        for (int i = 0; st != null && i < st.length(); i++) {
            JSONObject s = st.optJSONObject(i);
            if (s == null) continue;
            String comp = tekst(s, "CompId"), kraj = tekst(s, "Ccd");
            String liga = tekst(s, "CompN");
            if (liga.isEmpty()) {
                liga = tekst(s, "Snm");
                String panstwo = polskie ? ladna(tekst(s, "Cnm")) : tekst(s, "Cnm");   // "Czechy · Cup" zamiast samego "Cup"
                if (!panstwo.isEmpty() && !liga.contains(panstwo)) liga = panstwo + " · " + liga;
            }
            JSONArray ev = s.optJSONArray("Events");
            for (int k = 0; ev != null && k < ev.length(); k++) {
                JSONObject e = ev.optJSONObject(k);
                if (e == null) continue;
                JSONArray t1 = e.optJSONArray("T1"), t2 = e.optJSONArray("T2");
                JSONObject a = t1 != null ? t1.optJSONObject(0) : null, b = t2 != null ? t2.optJSONObject(0) : null;
                if (a == null || b == null) continue;
                out.add(new String[]{tekst(e, "Eid"), comp, kraj, liga, String.valueOf(e.optLong("Esd", 0L)),
                        tekst(e, "Eps"), tekst(a, "Nm"), tekst(b, "Nm"), tekst(a, "Img"), tekst(b, "Img"),
                        tekst(e, "Tr1"), tekst(e, "Tr2")});
            }
        }
        return out;
    }

    private static Mecz zWiersza(String[] w, int tz) {
        Mecz m = new Mecz();
        m.id = w[0];
        m.comp = w[1];
        m.kraj = w[2];
        m.liga = w[3];
        long esd = 0L;
        try {
            esd = Long.parseLong(w[4]);
        } catch (NumberFormatException ignored) {
            // mecz bez godziny i tak się pokaże
        }
        m.eps = w[5];
        m.dom = w[6];
        m.gosc = w[7];
        m.domImg = w[8];
        m.goscImg = w[9];
        m.w1 = w[10];
        m.w2 = w[11];
        m.start = epoka(esd, tz);
        return m;
    }

    /** W pamięci zostają: główne ligi, polskie rozgrywki i mecze obserwowanych drużyn. */
    private static boolean ciekawy(String[] w, Set<String> ul) {
        if (w[6].isEmpty() || w[7].isEmpty()) return false;
        return glowna(w[1], w[2]) || pasuje(ladna(w[6]), ul, false) || pasuje(ladna(w[7]), ul, false);
    }

    private static File katalog(Context c) {
        File k = new File(c.getFilesDir(), "mod_pl_mecze");
        if (!k.exists() && !k.mkdirs()) ModPlDziennik.w(TAG, "nie można utworzyć " + k);
        return k;
    }

    /** Plik dnia: pierwsza linia "czas pobrania, strefa", dalej po jednym meczu w linii (pola rozdzielone tabulatorem). */
    private static Dzien zDysku(Context c, String klucz, Set<String> ul) {
        File f = new File(katalog(c), klucz + ".txt");
        if (!f.exists()) return null;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"), 16384)) {
            String naglowek = r.readLine();
            if (naglowek == null) return null;
            String[] n = naglowek.split("\t");
            Dzien d = new Dzien();
            d.ts = Long.parseLong(n[0]);
            d.tz = Integer.parseInt(n[1]);
            String linia;
            while ((linia = r.readLine()) != null) {
                String[] w = linia.split("\t", -1);
                if (w.length < POL) continue;
                d.wszystkich++;
                if (ciekawy(w, ul)) d.mecze.add(zWiersza(w, d.tz));
            }
            return d;
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "odczyt " + klucz + ": " + e.getMessage());
            return null;
        }
    }

    private static void naDysk(Context c, String klucz, List<String[]> wiersze, long ts, int tz) {
        File k = katalog(c), tmp = new File(k, klucz + ".tmp"), cel = new File(k, klucz + ".txt");
        try {
            try (Writer out = new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8")) {
                StringBuilder sb = new StringBuilder(wiersze.size() * 110 + 32);
                sb.append(ts).append('\t').append(tz).append('\n');
                for (String[] w : wiersze) {
                    for (int i = 0; i < POL; i++) {
                        if (i > 0) sb.append('\t');
                        sb.append(w[i]);
                    }
                    sb.append('\n');
                }
                out.write(sb.toString());
            }
            if (!tmp.renameTo(cel)) ModPlDziennik.w(TAG, "nie można zapisać " + cel.getName());
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "zapis " + klucz + ": " + e.getMessage());
        }
    }

    private static void sprzatnij(Context c, Set<String> potrzebne) {
        File[] pliki = katalog(c).listFiles();
        if (pliki == null) return;
        for (File f : pliki) {
            String n = f.getName();
            int kropka = n.indexOf('.');
            String klucz = kropka > 0 ? n.substring(0, kropka) : n;
            if ((!potrzebne.contains(klucz) || !n.endsWith(".txt")) && !f.delete()) ModPlDziennik.w(TAG, "nie można usunąć " + n);
        }
    }

    // ------------------------------------------------------------------ pobieranie

    private static boolean nieswiezy(String klucz, Dzien d, String dzis, String bliski, long teraz, int tz) {
        if (d == null) return true;
        if (d.tz != tz) return true;
        if (teraz - d.proba < 30 * MIN && d.proba > d.ts) return false;        // niedawna nieudana próba
        if (d.wszystkich == 0 && teraz - d.ts > 30 * MIN) return true;         // pusty dzień to prawie na pewno błąd
        int por = klucz.compareTo(dzis);
        if (por < 0) {
            long koniec = epoka(Long.parseLong(klucz) * 1000000L + 235959L, tz);
            return d.ts < koniec + 3 * GODZ;
        }
        if (por == 0) {
            boolean trwa = false;
            for (Mecz m : d.mecze) if (stan(m.eps) < 2) trwa = true;
            return teraz - d.ts > (trwa ? 10 * MIN : 60 * MIN);
        }
        return teraz - d.ts > (klucz.compareTo(bliski) <= 0 ? 6 * GODZ : 12 * GODZ);
    }

    private static boolean pobierzDzien(Context c, String klucz, long teraz, int tz, Set<String> ul) {
        Dzien stary = dni.get(klucz);
        if (stary != null) stary.proba = teraz;
        try {
            List<String[]> w = wiersze(get(API + "date/soccer/" + klucz + "/" + tz + "?MD=1"));
            naDysk(c, klucz, w, teraz, tz);
            Dzien d = new Dzien();
            d.ts = teraz;
            d.tz = tz;
            d.wszystkich = w.size();
            for (String[] x : w) if (ciekawy(x, ul)) d.mecze.add(zWiersza(x, tz));
            dni.put(klucz, d);
            return true;
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "dzień " + klucz + ": " + e.getMessage());
            return false;
        }
    }

    /** Mecze trwające teraz: aktualizuje wyniki i minuty; zwraca dni, które trzeba pobrać ponownie. */
    private static Set<String> naZywo(long teraz, int tz, String dzis, Set<String> ul) {
        Set<String> doOdswiezenia = new HashSet<String>();
        boolean potrzebne = false;
        for (Dzien d : dni.values()) {
            for (Mecz m : d.mecze) {
                int s = stan(m.eps);
                if (s == 1 || (s == 0 && m.start <= teraz + 2 * MIN && m.start > teraz - 4 * GODZ)) potrzebne = true;
            }
        }
        if (!potrzebne || teraz - ostatnieNaZywo < 40_000L) return doOdswiezenia;
        ostatnieNaZywo = teraz;
        Map<String, Mecz> zywe = new HashMap<String, Mecz>();
        Map<String, String[]> zyweWiersze = new HashMap<String, String[]>();
        try {
            for (String[] w : wiersze(get(API + "live/soccer/" + tz + "?MD=1"))) {
                zywe.put(w[0], zWiersza(w, tz));
                zyweWiersze.put(w[0], w);
            }
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "na żywo: " + e.getMessage());
            return doOdswiezenia;
        }
        Set<String> znane = new HashSet<String>();
        for (Map.Entry<String, Dzien> wpis : dni.entrySet()) {
            for (Mecz m : wpis.getValue().mecze) {
                znane.add(m.id);
                Mecz z = zywe.get(m.id);
                if (z != null) {
                    m.eps = z.eps;
                    m.w1 = z.w1;
                    m.w2 = z.w2;
                    m.bylNaZywo = true;
                } else if (m.bylNaZywo || stan(m.eps) == 1) {
                    m.bylNaZywo = false;      // zniknął z listy na żywo: koniec meczu, wynik końcowy jest w pliku dnia
                    doOdswiezenia.add(wpis.getKey());
                    doOdswiezenia.add(dzis);
                }
            }
        }
        // Mecz na żywo, którego nie ma w pamięci (LiveScore trzymał go pod innym dniem): dopisz do dzisiejszych.
        Dzien d = dni.get(dzis);
        if (d != null) {
            for (Map.Entry<String, Mecz> wpis : zywe.entrySet()) {
                if (znane.contains(wpis.getKey()) || !ciekawy(zyweWiersze.get(wpis.getKey()), ul)) continue;
                wpis.getValue().bylNaZywo = true;
                d.mecze.add(wpis.getValue());
            }
        }
        return doOdswiezenia;
    }

    /**
     * Trwające teraz mecze obserwowanych drużyn prosto z LiveScore: jedno zapytanie, nic nie zapisuje.
     * Zwraca null, gdy pobranie się nie udało. Dla powiadomień działających w tle (ModPlGole).
     */
    static List<HomeDashboard.MatchItem> zywoUlubionych(Context c) {
        polskie = "pl".equals(I18n.getLang(c));
        long teraz = System.currentTimeMillis();
        int tz = strefa(teraz);
        try {
            Set<String> ul = ulubioneZnorm(c);
            List<HomeDashboard.MatchItem> out = new ArrayList<HomeDashboard.MatchItem>();
            for (String[] w : wiersze(get(API + "live/soccer/" + tz + "?MD=1"))) {
                if (w[6].isEmpty() || w[7].isEmpty()) continue;
                boolean gl = glowna(w[1], w[2]);
                if (!pasuje(ladna(w[6]), ul, gl) && !pasuje(ladna(w[7]), ul, gl)) continue;
                HomeDashboard.MatchItem m = naPozycje(c, zWiersza(w, tz));
                m.isFav = true;
                out.add(m);
            }
            return out;
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "na żywo dla powiadomień: " + e);
            return null;
        }
    }

    private static boolean luzno(String a, String b) {
        for (String t : norm(a).replace("warsaw", "warszawa").split("[ .-]+")) {
            if (t.length() < 4) continue;
            for (String v : norm(b).replace("warsaw", "warszawa").split("[ .-]+")) if (t.equals(v)) return true;
        }
        return false;
    }

    /** Terminy przyszłych meczów Ekstraklasy z TheSportsDB (LiveScore podaje je zbiorczo, np. wszystkie 18:00). */
    private static void poprawTerminyEkstraklasy(Context c) {
        List<HomeDashboard.MatchItem> ts;
        try {
            ts = ModPlEkstraklasa.pobierz(c, 6 * GODZ);
        } catch (Throwable e) {
            return;
        }
        if (ts == null || ts.isEmpty()) return;
        for (Dzien d : dni.values()) {
            for (Mecz m : d.mecze) {
                if (!"92".equals(m.comp) || stan(m.eps) != 0) continue;
                for (HomeDashboard.MatchItem t : ts) {
                    if (t.isFinished || t.startEpochMs <= 0) continue;
                    if (luzno(m.dom, t.homeTeam) && luzno(m.gosc, t.awayTeam)) {
                        if (Math.abs(t.startEpochMs - m.start) > 5 * MIN) m.start = t.startEpochMs;
                        break;
                    }
                }
            }
        }
    }

    /**
     * Mecze dla wybranej ligi ("all" = główne ligi i wszystkie mecze obserwowanych), posortowane.
     * Zwraca null, gdy LiveScore nie odpowiada i nie ma nic w pamięci (wtedy stare źródła).
     * Wywoływać poza wątkiem głównym.
     */
    static synchronized List<HomeDashboard.MatchItem> pobierz(Context c, String liga) {
        polskie = "pl".equals(I18n.getLang(c));
        try {
            List<HomeDashboard.MatchItem> out = pobierzBezOchrony(c, liga);
            dziala = out != null;
            return out;
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "pobierz: " + e, e);
            dziala = false;
            return null;
        }
    }

    private static List<HomeDashboard.MatchItem> pobierzBezOchrony(Context c, String liga) {
        long teraz = System.currentTimeMillis();
        int tz = strefa(teraz);
        Set<String> ul = ulubioneZnorm(c);
        String podpis = ul.toString();
        if (!podpis.equals(podpisUlubionych)) {   // inne ulubione: dni filtrujemy z plików od nowa (bez sieci)
            dni.clear();
            podpisUlubionych = podpis;
        }
        String dzis = kluczDnia(teraz, tz), bliski = kluczDnia(teraz + 3 * 24 * GODZ, tz);
        List<String> klucze = new ArrayList<String>();   // od dziś na zewnątrz: najbliższe dni pobierają się pierwsze
        klucze.add(dzis);
        for (int p = 1; p <= Math.max(DNI_WSTECZ, DNI_NAPRZOD); p++) {
            if (p <= DNI_NAPRZOD) klucze.add(kluczDnia(teraz + p * 24 * GODZ, tz));
            if (p <= DNI_WSTECZ) klucze.add(kluczDnia(teraz - p * 24 * GODZ, tz));
        }
        Set<String> potrzebne = new HashSet<String>(klucze);
        for (String k : new ArrayList<String>(dni.keySet())) if (!potrzebne.contains(k)) dni.remove(k);

        boolean siecDziala = teraz >= blokadaDo;
        int pobrane = 0;
        for (String k : klucze) {
            Dzien d = dni.get(k);
            if (d == null) {
                d = zDysku(c, k, ul);
                if (d != null) dni.put(k, d);
            }
            if (siecDziala && nieswiezy(k, d, dzis, bliski, teraz, tz)) {
                if (pobierzDzien(c, k, teraz, tz, ul)) {
                    pobrane++;
                } else {
                    siecDziala = false;           // jedna nieudana próba kończy pobieranie na 10 minut
                    blokadaDo = teraz + 10 * MIN;
                }
            }
        }
        if (dni.isEmpty()) return null;

        for (String k : naZywo(teraz, tz, dzis, ul)) {
            if (teraz >= blokadaDo && potrzebne.contains(k) && pobierzDzien(c, k, teraz, tz, ul)) pobrane++;
        }
        boolean wszystkie = "all".equals(liga);
        if (wszystkie || "pol.1".equals(liga)) poprawTerminyEkstraklasy(c);
        if (pobrane > 0) sprzatnij(c, potrzebne);

        String comp = null;
        for (String[] l : LIGI) if (l[0].equals(liga)) comp = l[1];
        if (!wszystkie && comp == null) return null;   // liga, której tu nie znamy: stare źródło
        List<HomeDashboard.MatchItem> out = new ArrayList<HomeDashboard.MatchItem>();
        Set<String> dodane = new HashSet<String>();
        for (String k : klucze) {
            Dzien d = dni.get(k);
            if (d == null) continue;
            for (Mecz m : d.mecze) {
                if (!dodane.add(m.id)) continue;
                boolean gl = glowna(m.comp, m.kraj);
                boolean bierz = wszystkie
                        ? ligaPoKodzie(m.comp) != null || pasuje(ladna(m.dom), ul, gl) || pasuje(ladna(m.gosc), ul, gl)
                        : comp.equals(m.comp);
                if (bierz) out.add(naPozycje(c, m));
            }
        }
        sortuj(c, out);
        if (pobrane > 0) {
            ModPlDziennik.i(TAG, "liga " + liga + ": pobrano dni " + pobrane + ", w pamięci " + dni.size() + ", meczów " + out.size());
        }
        ostatnia = new ArrayList<HomeDashboard.MatchItem>(out);
        ostatniaLiga = liga;
        zapiszMigawke(c, liga, out);
        return out;
    }

    // ------------------------------------------------------------------ kolejność i grupy

    /** 0 na żywo, 1 dziś, 2 obserwowane (przyszłe), 3 wyniki obserwowanych, 4 nadchodzące, 5 wyniki. */
    static int sekcja(HomeDashboard.MatchItem m, long teraz) {
        if (m.isLive) return 0;
        long przes = TimeZone.getDefault().getOffset(teraz);
        if (!m.isFinished && m.startEpochMs > 0
                && Math.floorDiv(m.startEpochMs + przes, 24 * GODZ) == Math.floorDiv(teraz + przes, 24 * GODZ)
                && !"PST".equals(m.statusText) && !"CANC".equals(m.statusText)) {
            return 1;
        }
        boolean wynik = m.isFinished || (m.startEpochMs > 0 && m.startEpochMs < teraz);
        if (m.isFav) return wynik ? 3 : 2;
        return wynik ? 5 : 4;
    }

    /** Klucze nazw grup (tłumaczone przez I18n). */
    static final String[] NAZWY_SEKCJI = {"Live now", "Today's matches", "Followed teams", "Followed: results",
            "Upcoming matches", "Latest results"};

    /** Ustawia gwiazdki, sortuje i przycina listę; używane dla każdego źródła meczów. */
    static void sortuj(Context c, List<HomeDashboard.MatchItem> lista) {
        final long teraz = System.currentTimeMillis();
        Set<String> ul = ulubioneZnorm(c);
        final Map<HomeDashboard.MatchItem, Integer> grupa = new HashMap<HomeDashboard.MatchItem, Integer>();
        for (HomeDashboard.MatchItem m : lista) {
            m.isFav = pasuje(m.homeTeam, ul, m.glowna) || pasuje(m.awayTeam, ul, m.glowna);
            grupa.put(m, sekcja(m, teraz));
        }
        Collections.sort(lista, new Comparator<HomeDashboard.MatchItem>() {
            @Override
            public int compare(HomeDashboard.MatchItem a, HomeDashboard.MatchItem b) {
                int sa = grupa.get(a), sb = grupa.get(b);
                if (sa != sb) return sa < sb ? -1 : 1;
                if (sa <= 1 && a.isFav != b.isFav) return a.isFav ? -1 : 1;
                int por = Long.compare(a.startEpochMs, b.startEpochMs);
                return sa == 3 || sa == 5 ? -por : por;
            }
        });
        int[] limity = {30, 30, 12, 8, 30, 20};
        int[] ile = new int[6];
        List<HomeDashboard.MatchItem> out = new ArrayList<HomeDashboard.MatchItem>();
        for (HomeDashboard.MatchItem m : lista) {
            int s = grupa.get(m);
            if (ile[s]++ < limity[s] && out.size() < 90) out.add(m);
        }
        lista.clear();
        lista.addAll(out);
    }

    // ------------------------------------------------------------------ migawka (natychmiastowy start)

    private static void zapiszMigawke(Context c, String liga, List<HomeDashboard.MatchItem> lista) {
        try {
            JSONArray a = new JSONArray();
            for (int i = 0; i < lista.size() && i < 40; i++) {
                HomeDashboard.MatchItem m = lista.get(i);
                a.put(new JSONArray().put(m.id).put(m.homeTeam).put(m.awayTeam).put(m.homeScore).put(m.awayScore)
                        .put(m.statusText).put(m.isLive).put(m.isFinished).put(m.isFav).put(m.leagueName)
                        .put(m.startEpochMs).put(m.homeLogo).put(m.awayLogo).put(m.glowna));
            }
            String nowa = a.toString();
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (nowa.equals(p.getString(K_MIGAWKA, "")) && liga.equals(p.getString(K_MIGAWKA_LIGA, ""))) return;
            p.edit().putString(K_MIGAWKA, nowa).putString(K_MIGAWKA_LIGA, liga).apply();
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "migawka: " + e.getMessage());
        }
    }

    /**
     * Ostatnia lista dla wybranej ligi bez sieci i bez czytania plików dni (start launchera).
     * Zwraca null, gdy LiveScore jeszcze nic dla tej ligi nie dał (wtedy stara pamięć podręczna).
     */
    static List<HomeDashboard.MatchItem> migawka(Context c) {
        String liga = HomeDashboard.getSelectedLeague(c);
        if (liga.equals(ostatniaLiga)) return new ArrayList<HomeDashboard.MatchItem>(ostatnia);
        try {
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (!liga.equals(p.getString(K_MIGAWKA_LIGA, ""))) return null;
            List<HomeDashboard.MatchItem> out = new ArrayList<HomeDashboard.MatchItem>();
            JSONArray a = new JSONArray(p.getString(K_MIGAWKA, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONArray w = a.getJSONArray(i);
                HomeDashboard.MatchItem m = new HomeDashboard.MatchItem();
                m.id = w.optString(0, "");
                m.homeTeam = w.optString(1, "");
                m.awayTeam = w.optString(2, "");
                m.homeScore = w.optString(3, "");
                m.awayScore = w.optString(4, "");
                m.statusText = w.optString(5, "");
                m.isLive = w.optBoolean(6, false);
                m.isFinished = w.optBoolean(7, false);
                m.isFav = w.optBoolean(8, false);
                m.leagueName = w.optString(9, "");
                m.startEpochMs = w.optLong(10, 0L);
                m.homeLogo = w.optString(11, "");
                m.awayLogo = w.optString(12, "");
                m.glowna = w.optBoolean(13, true);
                // po restarcie nie wiemy, czy mecz nadal trwa: wynik na żywo wróci po odświeżeniu
                if (m.isLive) {
                    m.isLive = false;
                    m.statusText = "";
                    m.homeScore = "";
                    m.awayScore = "";
                }
                out.add(m);
            }
            return out;
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "odczyt migawki: " + e.getMessage());
            return null;
        }
    }

    /** Czy warto odświeżać co minutę: coś trwa albo zaczyna się w ciągu kwadransa. */
    static boolean cosSieDzieje(List<HomeDashboard.MatchItem> lista) {
        long teraz = System.currentTimeMillis();
        for (HomeDashboard.MatchItem m : lista) {
            if (m.isLive) return true;
            if (!m.isFinished && m.startEpochMs > teraz - 3 * GODZ && m.startEpochMs < teraz + 15 * MIN
                    && !"PST".equals(m.statusText) && !"CANC".equals(m.statusText)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ sieć

    private static String czytaj(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toString("UTF-8");
    }

    private static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(7000);
        con.setReadTimeout(10000);
        con.setRequestProperty("User-Agent", "tvOSLauncher-PL (Android TV)");
        con.setRequestProperty("Accept", "application/json");
        try {
            int kod = con.getResponseCode();
            if (kod >= 400) throw new IOException("HTTP " + kod);
            try (InputStream in = con.getInputStream()) {
                return czytaj(in);
            }
        } finally {
            con.disconnect();
        }
    }
}
