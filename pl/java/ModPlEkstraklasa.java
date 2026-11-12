package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Mod PL: mecze PKO BP Ekstraklasy z TheSportsDB (ESPN nie ma polskiej ligi).
 * Pokazuje ostatnią rozegraną (lub trwającą) kolejkę i następną: wyniki plus nadchodzące mecze.
 * Darmowy klucz "123" ma limit zapytań, więc odświeżenie to 2 zapytania (eventsround), a numer
 * kolejki ustala jedno zapytanie o ostatni rozegrany mecz (eventspastleague). Wyniki bywają opóźnione.
 * Plik źródłowy leży w pl/java/, a pl/naloz.py kopiuje go do src/ przy każdym buildzie.
 */
final class ModPlEkstraklasa {
    private ModPlEkstraklasa() { }

    static final String KOD = "pol.1";
    private static final String TAG = "ModPlEkstraklasa";
    private static final String API = "https://www.thesportsdb.com/api/v1/json/123/";
    private static final String LIGA = "4422";
    private static final String PREFS = "mod_pl", K_JSON = "ekstraklasa_kolejki_json", K_CZAS = "ekstraklasa_kolejki_czas";
    private static final String K_RUNDA = "ekstraklasa_kolejka", K_SEZON = "ekstraklasa_sezon", K_SZUKANO = "ekstraklasa_szukano";
    private static final long WAZNOSC_MS = 10 * 60 * 1000L;
    /** Numer kolejki sprawdzamy na nowo najwyżej raz na 6 godzin (zmienia się raz w tygodniu). */
    private static final long SZUKAJ_CO_MS = 6 * 3600 * 1000L;
    private static final int KOLEJEK = 34;

    private static List<HomeDashboard.MatchItem> pamiec = new ArrayList<HomeDashboard.MatchItem>();

    /** Mecze z ostatniego pobrania (bez sieci); pusta lista, gdy nic nie ma. */
    static synchronized List<HomeDashboard.MatchItem> zPamieci(Context c) {
        if (pamiec.isEmpty()) {
            String raw = prefs(c).getString(K_JSON, null);
            if (raw != null) pamiec = parsuj(raw);
        }
        return new ArrayList<HomeDashboard.MatchItem>(pamiec);
    }

    /** Sezon w zapisie TheSportsDB, np. "2026-2027" (nowy sezon od lipca); zapas, gdy serwer go nie poda. */
    private static String sezonZDaty() {
        Calendar cal = Calendar.getInstance();
        int rok = cal.get(Calendar.YEAR);
        int start = cal.get(Calendar.MONTH) >= Calendar.JULY ? rok : rok - 1;
        return start + "-" + (start + 1);
    }

    /** Mecze jednej kolejki; null przy błędzie sieci, pusta tablica poza zakresem. */
    private static JSONArray kolejka(String sezon, int nr) {
        if (nr < 1 || nr > KOLEJEK) return new JSONArray();
        try {
            JSONArray ev = new JSONObject(get(API + "eventsround.php?id=" + LIGA + "&r=" + nr + "&s=" + sezon))
                    .optJSONArray("events");
            return ev != null ? ev : new JSONArray();
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "kolejka " + nr + ": " + e.getMessage());
            return null;
        }
    }

    private static boolean ruszyla(JSONArray k) {
        for (int i = 0; k != null && i < k.length(); i++) {
            JSONObject e = k.optJSONObject(i);
            if (e == null) continue;
            String st = tekst(e, "strStatus").toUpperCase(Locale.ROOT);
            if (!st.isEmpty() && !st.equals("NS") && !st.equals("TBD") && !st.equals("PST")) return true;
            if (!liczba(e, "intHomeScore").isEmpty()) return true;
        }
        return false;
    }

    /** {kolejka, sezon} ostatniego rozegranego meczu (1 zapytanie), a gdy go nie ma - następnego minus 1. */
    private static Object[] znajdzKolejke() {
        String[][] proby = {{"eventspastleague", "0"}, {"eventsnextleague", "-1"}};
        for (String[] p : proby) {
            try {
                JSONObject o = new JSONObject(get(API + p[0] + ".php?id=" + LIGA));
                JSONArray ev = o.optJSONArray("events");
                if (ev == null) ev = o.optJSONArray("results");
                if (ev == null || ev.length() == 0) continue;
                JSONObject e = ev.getJSONObject(0);
                int r = e.optInt("intRound", 0) + Integer.parseInt(p[1]);
                String s = tekst(e, "strSeason");
                if (r > 0) return new Object[]{Math.min(r, KOLEJEK), s.isEmpty() ? sezonZDaty() : s};
            } catch (Throwable e) {
                ModPlDziennik.w(TAG, p[0] + ": " + e.getMessage());
            }
        }
        return null;
    }

    /** Wywoływać poza wątkiem głównym. Przy błędzie zwraca ostatnie mecze z pamięci. */
    static List<HomeDashboard.MatchItem> pobierz(Context c) {
        return pobierz(c, WAZNOSC_MS);
    }

    /** Jak pobierz(c), ale z własnym czasem ważności pamięci (terminy zmieniają się rzadko). */
    static List<HomeDashboard.MatchItem> pobierz(Context c, long waznosc) {
        SharedPreferences p = prefs(c);
        long teraz = System.currentTimeMillis();
        if (teraz - p.getLong(K_CZAS, 0L) < waznosc) {
            List<HomeDashboard.MatchItem> z = zPamieci(c);
            if (!z.isEmpty()) return z;
        }
        int r = p.getInt(K_RUNDA, 0);
        String s = p.getString(K_SEZON, "");
        if (r <= 0 || s.isEmpty() || teraz - p.getLong(K_SZUKANO, 0L) > SZUKAJ_CO_MS) {
            Object[] k = znajdzKolejke();
            if (k != null) {
                r = Math.max(r > 0 && s.equals(k[1]) ? r : 0, (Integer) k[0]);
                s = (String) k[1];
                p.edit().putLong(K_SZUKANO, teraz).apply();
            }
        }
        if (r <= 0 || s.isEmpty()) return zPamieci(c);
        JSONArray biezaca = kolejka(s, r);
        JSONArray nast = kolejka(s, r + 1);
        if (biezaca == null || nast == null) return zPamieci(c);
        if (ruszyla(nast)) {  // następna kolejka już się zaczęła - przesuwamy się dalej
            r++;
            biezaca = nast;
            nast = kolejka(s, r + 1);
            if (nast == null) nast = new JSONArray();
        }
        JSONArray wszystkie = new JSONArray();
        for (int i = 0; i < biezaca.length(); i++) wszystkie.put(biezaca.opt(i));
        for (int i = 0; i < nast.length(); i++) wszystkie.put(nast.opt(i));
        ModPlDziennik.i(TAG, "sezon " + s + ", kolejki " + r + "-" + (r + 1) + ", meczów " + wszystkie.length());
        String raw = wszystkie.toString();
        p.edit().putString(K_JSON, raw).putLong(K_CZAS, teraz).putInt(K_RUNDA, r).putString(K_SEZON, s).apply();
        synchronized (ModPlEkstraklasa.class) {
            pamiec = parsuj(raw);
            return new ArrayList<HomeDashboard.MatchItem>(pamiec);
        }
    }

    private static List<HomeDashboard.MatchItem> parsuj(String raw) {
        List<HomeDashboard.MatchItem> out = new ArrayList<HomeDashboard.MatchItem>();
        try {
            JSONArray a = new JSONArray(raw);
            SimpleDateFormat utc = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            utc.setTimeZone(TimeZone.getTimeZone("UTC"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject e = a.optJSONObject(i);
                if (e == null) continue;
                HomeDashboard.MatchItem m = new HomeDashboard.MatchItem();
                m.homeTeam = e.optString("strHomeTeam", "");
                m.awayTeam = e.optString("strAwayTeam", "");
                if (m.homeTeam.isEmpty() || m.awayTeam.isEmpty()) continue;
                m.homeScore = liczba(e, "intHomeScore");
                m.awayScore = liczba(e, "intAwayScore");
                m.homeLogo = tekst(e, "strHomeTeamBadge");
                m.awayLogo = tekst(e, "strAwayTeamBadge");
                m.leagueName = "PKO BP Ekstraklasa";
                String ts = tekst(e, "strTimestamp");
                if (ts.length() >= 19) {
                    try {
                        Date d = utc.parse(ts.substring(0, 19));
                        if (d != null) m.startEpochMs = d.getTime();
                    } catch (Throwable ignored) {
                        // bez godziny mecz i tak się pokaże
                    }
                }
                String st = tekst(e, "strStatus").toUpperCase(Locale.ROOT);
                String postep = tekst(e, "strProgress");
                boolean wynik = !m.homeScore.isEmpty() && !m.awayScore.isEmpty();
                if (st.equals("FT") || st.equals("AET") || st.equals("PEN") || st.contains("FINISHED")) {
                    m.isFinished = true;
                    m.statusText = st;
                } else if (st.equals("1H") || st.equals("2H") || st.equals("HT") || st.equals("ET")
                        || st.equals("BT") || st.equals("P") || st.equals("LIVE")) {
                    m.isLive = true;
                    m.statusText = st.equals("HT") ? "HT" : (!postep.isEmpty() ? postep + "'" : st);
                } else if (st.equals("PST") || "yes".equalsIgnoreCase(tekst(e, "strPostponed"))) {
                    m.statusText = "PST";
                } else {
                    m.statusText = "";
                    if (wynik && m.startEpochMs > 0 && m.startEpochMs < System.currentTimeMillis() - 3 * 3600 * 1000L) {
                        m.isFinished = true;
                        m.statusText = "FT";
                    }
                }
                if (!m.isLive && !m.isFinished) {
                    m.homeScore = "";
                    m.awayScore = "";
                }
                out.add(m);
            }
        } catch (Throwable ignored) {
            // uszkodzona pamięć podręczna - zwracamy to, co się dało odczytać
        }
        return out;
    }

    private static String tekst(JSONObject e, String k) {
        if (e.isNull(k)) return "";
        String s = e.optString(k, "");
        return "null".equals(s) ? "" : s.trim();
    }

    private static String liczba(JSONObject e, String k) {
        String s = tekst(e, k);
        return s.matches("\\d{1,3}") ? s : "";
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String czytaj(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toString("UTF-8");
    }

    private static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(7000);
        con.setReadTimeout(9000);
        con.setRequestProperty("User-Agent", "tvOSLauncher-PL (Android TV)");
        con.setRequestProperty("Accept", "application/json");
        try {
            int kod = con.getResponseCode();
            if (kod >= 400) {
                String tresc;
                try (InputStream err = con.getErrorStream()) {
                    tresc = czytaj(err);
                }
                if (tresc.length() > 160) tresc = tresc.substring(0, 160);
                throw new IOException("HTTP " + kod + " " + tresc.replace('\n', ' '));
            }
            try (InputStream in = con.getInputStream()) {
                return czytaj(in);
            }
        } finally {
            con.disconnect();
        }
    }
}
