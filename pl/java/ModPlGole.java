package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Mod PL: powiadomienia o meczach obserwowanych drużyn, pokazywane nad dowolną aplikacją
 * (wariant A z kanwy, wersja przezroczysta: mała plakietka w rogu ekranu).
 * <ul>
 *   <li>{@link Usluga} działa tylko w czasie meczu obserwowanej drużyny: co pół minuty pyta LiveScore o trwające
 *       mecze i porównuje wynik z poprzednim odczytem. Poza meczami niczego nie pobiera; następne pilnowanie
 *       ustawia budzik na kilka minut przed początkiem najbliższego meczu.</li>
 *   <li>Plakietka wymaga zgody na wyświetlanie nad innymi aplikacjami ({@code SYSTEM_ALERT_WINDOW});
 *       bez niej zostaje zwykły dymek systemowy (Toast).</li>
 * </ul>
 * Plik źródłowy leży w pl/java/, a pl/naloz.py kopiuje go do src/ przy każdym buildzie.
 */
public final class ModPlGole {
    private ModPlGole() { }

    private static final String TAG = "ModPlGole";
    private static final String PREFS = "mod_pl", K_TRYB = "gole_tryb", K_ROG = "gole_rog";
    private static final String KANAL = "mod_pl_mecze";
    private static final int ID_POWIADOMIENIA = 4711;

    /** Co ma wyskakiwać. */
    static final int WYL = 0, GOLE = 1, GOLE_START_KONIEC = 2, WSZYSTKO = 3;
    private static final String[] NAZWY_TRYBOW = {"Off", "Goals only", "Goals, kick-off and full time", "Everything, with half time"};
    private static final String[] NAZWY_ROGOW = {"Top right", "Top left", "Bottom right", "Bottom left"};

    /** Rodzaje zdarzeń. */
    static final int START = 0, GOL = 1, KOREKTA = 2, PRZERWA = 3, KONIEC = 4;

    private static final long CO_ILE = 30_000L;                       // odpytywanie w czasie meczu
    private static final long PRZED = 4 * 60_000L;                    // tyle przed początkiem zaczyna się pilnowanie
    private static final long CZEKANIE_NA_START = 25 * 60_000L;       // tyle po planowanej godzinie czekamy na początek
    private static final long OKNO = 3 * 3_600_000L + 30 * 60_000L;   // mecz z dogrywką i karnymi mieści się w tym czasie
    private static final long WIDOCZNE = 8_000L;                      // tyle plakietka jest na ekranie

    private static final Handler GLOWNY = new Handler(Looper.getMainLooper());
    private static View aktualna;

    // ------------------------------------------------------------------ ustawienia

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int tryb(Context c) {
        int t = prefs(c).getInt(K_TRYB, WYL);
        return t < WYL || t > WSZYSTKO ? WYL : t;
    }

    static int rog(Context c) {
        int r = prefs(c).getInt(K_ROG, 0);
        return r < 0 || r > 3 ? 0 : r;
    }

    static String opisTrybu(Context c) {
        return I18n.t(c, NAZWY_TRYBOW[tryb(c)]);
    }

    static String opisRogu(Context c) {
        return I18n.t(c, NAZWY_ROGOW[rog(c)]);
    }

    /** Następne ustawienie po OK w ustawieniach. Przy włączaniu prosi o zgodę na wyświetlanie nad innymi aplikacjami. */
    static void nastepnyTryb(final Activity a) {
        int nowy = (tryb(a) + 1) % (WSZYSTKO + 1);
        prefs(a).edit().putInt(K_TRYB, nowy).apply();
        if (nowy == WYL) {
            anulujBudzik(a);
            a.stopService(new Intent(a, Usluga.class));
            return;
        }
        pilnuj(a);
        if (nowy == GOLE && !mozeRysowac(a)) ModPlAdb.wlaczNakladki(a);
    }

    static void nastepnyRog(Context c) {
        prefs(c).edit().putInt(K_ROG, (rog(c) + 1) % 4).apply();
    }

    static boolean mozeRysowac(Context c) {
        try {
            return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c.getApplicationContext());
        } catch (Throwable e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ kiedy pilnować

    private static boolean pomijany(HomeDashboard.MatchItem m) {
        if (m == null || !m.isFav || m.isFinished || m.startEpochMs <= 0) return true;
        String s = m.statusText == null ? "" : m.statusText.trim();
        return s.equalsIgnoreCase("PST") || s.equalsIgnoreCase("CANC");
    }

    /**
     * Czy według ostatnio zapisanej listy meczów trwa albo zaraz zaczyna się mecz obserwowanej drużyny.
     * Lista odświeża się tylko na ekranie głównym, więc jej znacznik "trwa" bywa nieaktualny: usługa mu nie ufa
     * ({@code ufajZywym} = false) i patrzy wyłącznie na godzinę rozpoczęcia.
     */
    private static boolean trwaOkno(Context c, long teraz, long poStarcie, boolean ufajZywym) {
        for (HomeDashboard.MatchItem m : HomeDashboard.getCachedMatches(c)) {
            if (ufajZywym && m != null && m.isFav && m.isLive) return true;
            if (pomijany(m)) continue;
            if (teraz >= m.startEpochMs - PRZED && teraz <= m.startEpochMs + poStarcie) return true;
        }
        return false;
    }

    /** Początek pilnowania najbliższego przyszłego meczu obserwowanej drużyny albo -1, gdy żadnego nie widać. */
    private static long nastepnePilnowanie(Context c, long teraz) {
        long najblizsze = -1L;
        for (HomeDashboard.MatchItem m : HomeDashboard.getCachedMatches(c)) {
            if (pomijany(m)) continue;
            long od = m.startEpochMs - PRZED;
            if (od > teraz && (najblizsze < 0 || od < najblizsze)) najblizsze = od;
        }
        return najblizsze;
    }

    private static PendingIntent budzik(Context c) {
        Intent i = new Intent(c, Usluga.class);
        int flagi = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return Build.VERSION.SDK_INT >= 26 ? PendingIntent.getForegroundService(c, 1, i, flagi) : PendingIntent.getService(c, 1, i, flagi);
    }

    private static void ustawBudzik(Context c, long kiedy) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            // dokładny budzik wolno ustawić tylko za zgodą systemu (Android 12+); inaczej zostaje przybliżony
            if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, kiedy, budzik(c));
            } else if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 31) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, kiedy, budzik(c));
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, kiedy, budzik(c));
            }
            else am.set(AlarmManager.RTC_WAKEUP, kiedy, budzik(c));
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "budzik: " + e);
        }
    }

    private static void anulujBudzik(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(budzik(c.getApplicationContext()));
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "anulowanie budzika: " + e);
        }
    }

    /**
     * Wołane przy każdym powrocie na ekran główny i po zmianie ustawień: gdy mecz obserwowanej drużyny trwa
     * albo zaraz się zaczyna, uruchamia pilnowanie; w przeciwnym razie tylko ustawia budzik na najbliższy mecz.
     */
    static void pilnuj(Context c) {
        final Context app = c.getApplicationContext();
        try {
            if (tryb(app) == WYL) return;
            long teraz = System.currentTimeMillis();
            if (trwaOkno(app, teraz, OKNO, true)) {
                Intent i = new Intent(app, Usluga.class);
                if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i);
                else app.startService(i);
            } else {
                long kiedy = nastepnePilnowanie(app, teraz);
                if (kiedy > 0) ustawBudzik(app, kiedy);
            }
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "pilnuj: " + e);
        }
    }

    // ------------------------------------------------------------------ usługa pilnująca meczu

    private static final class Stan {
        HomeDashboard.MatchItem m;
        int g1, g2, brak;
        boolean przerwa;
    }

    public static class Usluga extends Service {
        private HandlerThread watek;
        private Handler h;
        private boolean dziala;
        private final Map<String, Stan> sledzone = new HashMap<String, Stan>();
        private final Set<String> skonczone = new HashSet<String>();

        private final Runnable petla = new Runnable() {
            @Override
            public void run() {
                try {
                    krok();
                } catch (Throwable e) {
                    ModPlDziennik.w(TAG, "krok: " + e);
                    if (h != null) h.postDelayed(this, CO_ILE);
                }
            }
        };

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public void onCreate() {
            super.onCreate();
            watek = new HandlerThread("mod-pl-gole");
            watek.start();
            h = new Handler(watek.getLooper());
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            naPierwszyPlan();
            if (!dziala) {
                dziala = true;
                h.post(petla);
            }
            return START_STICKY;
        }

        @Override
        public void onDestroy() {
            dziala = false;
            if (h != null) h.removeCallbacksAndMessages(null);
            if (watek != null) watek.quitSafely();
            super.onDestroy();
        }

        /** Usługa pierwszoplanowa: system nie zamknie jej, gdy na ekranie jest inna aplikacja. */
        private void naPierwszyPlan() {
            try {
                Notification.Builder b;
                if (Build.VERSION.SDK_INT >= 26) {
                    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    if (nm != null) {
                        nm.createNotificationChannel(new NotificationChannel(KANAL, I18n.t(this, "Match alerts"),
                                NotificationManager.IMPORTANCE_MIN));
                    }
                    b = new Notification.Builder(this, KANAL);
                } else {
                    b = new Notification.Builder(this);
                }
                b.setSmallIcon(getApplicationInfo().icon)
                        .setContentTitle(I18n.t(this, "Match alerts"))
                        .setContentText(I18n.t(this, "Watching the match of a followed team"))
                        .setOngoing(true);
                startForeground(ID_POWIADOMIENIA, b.build());
            } catch (Throwable e) {
                ModPlDziennik.w(TAG, "pierwszy plan: " + e);
            }
        }

        private void zakoncz() {
            dziala = false;
            try {
                long kiedy = nastepnePilnowanie(this, System.currentTimeMillis());
                if (kiedy > 0) ustawBudzik(getApplicationContext(), kiedy);
                stopForeground(true);
            } catch (Throwable e) {
                ModPlDziennik.w(TAG, "zakończenie: " + e);
            }
            stopSelf();
        }

        private void krok() {
            int tryb = tryb(this);
            if (tryb == WYL) {
                zakoncz();
                return;
            }
            long teraz = System.currentTimeMillis();
            List<HomeDashboard.MatchItem> zywe = ModPlMecze.zywoUlubionych(this);
            if (zywe != null) przetworz(zywe, tryb);
            ModPlDziennik.i(TAG, "odczyt: " + (zywe == null ? "brak sieci" : zywe.size() + " na żywo") + ", śledzone: " + sledzone.size());
            // zostajemy, póki coś śledzimy albo czekamy na początek zaplanowanego meczu;
            // bez sieci próbujemy dalej, ale najwyżej do końca okna meczu
            if (sledzone.isEmpty() && !trwaOkno(this, teraz, zywe == null ? OKNO : CZEKANIE_NA_START, false)) {
                zakoncz();
                return;
            }
            h.postDelayed(petla, CO_ILE);
        }

        private void przetworz(List<HomeDashboard.MatchItem> zywe, int tryb) {
            Set<String> obecne = new HashSet<String>();
            for (HomeDashboard.MatchItem m : zywe) {
                if (m == null) continue;
                String id = klucz(m);
                if (skonczone.contains(id)) continue;
                obecne.add(id);
                int g1 = liczba(m.homeScore), g2 = liczba(m.awayScore);
                boolean przerwa = "HT".equalsIgnoreCase(m.statusText);
                Stan s = sledzone.get(id);
                if (m.isFinished) {
                    if (s != null) sledzone.remove(id);
                    skonczone.add(id);
                    if (s != null && tryb >= GOLE_START_KONIEC) pokaz(this, KONIEC, m);
                    continue;
                }
                if (!m.isLive) continue;
                if (s == null) {
                    s = new Stan();
                    sledzone.put(id, s);
                    int min = minuta(m.statusText);
                    // początek zgłaszamy tylko wtedy, gdy trafiliśmy na pierwsze minuty; w trakcie meczu dołączamy po cichu
                    if (min >= 0 && min <= 3 && g1 + g2 == 0 && tryb >= GOLE_START_KONIEC) pokaz(this, START, m);
                } else if (g1 != s.g1 || g2 != s.g2) {
                    pokaz(this, g1 + g2 > s.g1 + s.g2 ? GOL : KOREKTA, m);
                } else if (przerwa && !s.przerwa && tryb >= WSZYSTKO) {
                    pokaz(this, PRZERWA, m);
                }
                s.m = m;
                s.g1 = g1;
                s.g2 = g2;
                s.przerwa = przerwa;
                s.brak = 0;
            }
            // mecz zniknął z listy trwających: koniec, ale dopiero po dwóch odczytach z rzędu
            for (Iterator<Map.Entry<String, Stan>> it = sledzone.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Stan> e = it.next();
                if (obecne.contains(e.getKey())) continue;
                Stan s = e.getValue();
                if (++s.brak < 2) continue;
                it.remove();
                skonczone.add(e.getKey());
                if (tryb >= GOLE_START_KONIEC && s.m != null) pokaz(this, KONIEC, s.m);
            }
        }
    }

    private static String klucz(HomeDashboard.MatchItem m) {
        return m.id != null && !m.id.isEmpty() ? m.id : m.homeTeam + "|" + m.awayTeam;
    }

    private static int liczba(String s) {
        try {
            return Integer.parseInt(s == null ? "" : s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Minuta z napisu w rodzaju "67'" albo "45+2'"; -1, gdy to nie minuta (np. przerwa). */
    private static int minuta(String s) {
        if (s == null) return -1;
        int n = 0, cyfr = 0;
        for (int i = 0; i < s.length(); i++) {
            char z = s.charAt(i);
            if (z >= '0' && z <= '9') {
                n = n * 10 + (z - '0');
                cyfr++;
            } else if (cyfr > 0) {
                break;
            }
        }
        return cyfr > 0 ? n : -1;
    }

    // ------------------------------------------------------------------ plakietka

    private static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    private static String etykieta(Context c, int typ) {
        String en = typ == START ? "Kick-off" : typ == GOL ? "Goal!" : typ == KOREKTA ? "Score update"
                : typ == PRZERWA ? "Half time" : "Full time";
        return I18n.t(c, en).toUpperCase(I18n.locale(c));
    }

    private static int kolor(int typ) {
        return typ == START ? 0xFF38BDF8 : typ == GOL ? 0xFF22C55E : typ == PRZERWA ? 0xFFF59E0B : 0xFF94A3B8;
    }

    private static String minutaNapis(Context c, int typ, HomeDashboard.MatchItem m) {
        if (typ == KONIEC || typ == PRZERWA) return "";
        String s = m.statusText == null ? "" : m.statusText.trim();
        return s.equalsIgnoreCase("HT") || s.equalsIgnoreCase("FT") ? "" : s;
    }

    private static Drawable szklo(Context c, float r, int ciemne, int jasnoGora, int jasnoDol, int obwodka) {
        GradientDrawable spod = new GradientDrawable();
        spod.setColor(ciemne);
        spod.setCornerRadius(r);
        GradientDrawable blask = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{jasnoGora, jasnoDol});
        blask.setCornerRadius(r);
        blask.setStroke(Math.max(1, dp(c, 1)), obwodka);
        return new LayerDrawable(new Drawable[]{spod, blask});
    }

    /** Plakietka z wariantu A: [● GOL!] herb 0 : 1 herb 23'. Nad cudzą aplikacją nie ma rozmycia, więc szkło jest ciemne. */
    private static View plakietka(Context c, int typ, HomeDashboard.MatchItem m, Bitmap herb1, Bitmap herb2) {
        LinearLayout p = new LinearLayout(c);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPadding(dp(c, 7), 0, dp(c, 13), 0);
        p.setBackground(szklo(c, dp(c, 21), 0xB30E0C14, 0x38FFFFFF, 0x14FFFFFF, 0xB8FFFFFF));
        p.setElevation(dp(c, 10));

        LinearLayout znak = new LinearLayout(c);
        znak.setOrientation(LinearLayout.HORIZONTAL);
        znak.setGravity(Gravity.CENTER_VERTICAL);
        znak.setPadding(dp(c, 7), 0, dp(c, 9), 0);
        znak.setBackground(szklo(c, dp(c, 13), 0x00000000, 0x33FFFFFF, 0x26FFFFFF, 0x6BFFFFFF));
        View kropka = new View(c);
        GradientDrawable kg = new GradientDrawable();
        kg.setShape(GradientDrawable.OVAL);
        kg.setColor(kolor(typ));
        kropka.setBackground(kg);
        znak.addView(kropka, new LinearLayout.LayoutParams(dp(c, 6), dp(c, 6)));
        TextView napis = ModPlOkna.tekst(c, etykieta(c, typ), 10.5f, "semibold", 0xFFFFFFFF);
        napis.setLetterSpacing(0.05f);
        napis.setSingleLine(true);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.leftMargin = dp(c, 4.5f);
        znak.addView(napis, nlp);
        p.addView(znak, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 26)));

        Bitmap[] herby = {herb1, herb2};
        for (int i = 0; i < 2; i++) {
            if (i == 1) {
                boolean jestWynik = typ != START || (m.homeScore != null && !m.homeScore.isEmpty());
                String wynik = jestWynik ? liczba(m.homeScore) + " : " + liczba(m.awayScore) : "0 : 0";
                TextView w = ModPlOkna.tekst(c, wynik, 18f, "display", 0xFFFFFFFF);
                w.setSingleLine(true);
                w.setShadowLayer(dp(c, 1), 0, dp(c, 0.5f), 0x80000000);
                LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                wlp.leftMargin = dp(c, 7);
                wlp.rightMargin = dp(c, 7);
                p.addView(w, wlp);
            }
            ImageView herb = new ImageView(c);
            herb.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (herby[i] != null) {
                herb.setImageBitmap(herby[i]);
            } else {
                // drużyna bez herbu: pierwsza litera nazwy
                String nazwa = i == 0 ? m.homeTeam : m.awayTeam;
                TextView litera = ModPlOkna.tekst(c, nazwa == null || nazwa.isEmpty() ? "" : nazwa.substring(0, 1).toUpperCase(Locale.ROOT),
                        11f, "semibold", 0xFFFFFFFF);
                litera.setGravity(Gravity.CENTER);
                GradientDrawable kolko = new GradientDrawable();
                kolko.setShape(GradientDrawable.OVAL);
                kolko.setColor(0x33FFFFFF);
                kolko.setStroke(Math.max(1, dp(c, 0.5f)), 0x66FFFFFF);
                litera.setBackground(kolko);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(c, 24), dp(c, 24));
                if (i == 0) llp.leftMargin = dp(c, 9);
                p.addView(litera, llp);
                continue;
            }
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(dp(c, 24), dp(c, 24));
            if (i == 0) hlp.leftMargin = dp(c, 9);
            p.addView(herb, hlp);
        }

        String min = minutaNapis(c, typ, m);
        if (!min.isEmpty()) {
            TextView t = ModPlOkna.tekst(c, min, 11f, "semibold", 0xE6FFFFFF);
            t.setSingleLine(true);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.leftMargin = dp(c, 9);
            p.addView(t, tlp);
        }
        p.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 42)));
        return p;
    }

    private static String dymek(Context c, int typ, HomeDashboard.MatchItem m) {
        String min = minutaNapis(c, typ, m);
        return etykieta(c, typ) + "  " + m.homeTeam + " " + liczba(m.homeScore) + " : " + liczba(m.awayScore) + " " + m.awayTeam
                + (min.isEmpty() ? "" : "  " + min);
    }

    /** Pokazuje powiadomienie: herby pobiera w tle, plakietkę wyświetla na wątku głównym. */
    static void pokaz(Context c, final int typ, final HomeDashboard.MatchItem m) {
        final Context app = c.getApplicationContext();
        ModPlDziennik.i(TAG, "zdarzenie " + typ + ": " + m.homeTeam + " " + m.homeScore + ":" + m.awayScore + " " + m.awayTeam + " " + m.statusText);
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap h1 = null, h2 = null;
                try {
                    if (m.homeLogo != null && !m.homeLogo.isEmpty()) h1 = Suggestions.image(app, m.homeLogo, "w780", dp(app, 32));
                    if (m.awayLogo != null && !m.awayLogo.isEmpty()) h2 = Suggestions.image(app, m.awayLogo, "w780", dp(app, 32));
                } catch (Throwable e) {
                    ModPlDziennik.w(TAG, "herby: " + e);
                }
                final Bitmap herb1 = h1, herb2 = h2;
                GLOWNY.post(new Runnable() {
                    @Override
                    public void run() {
                        wyswietl(app, typ, m, herb1, herb2);
                    }
                });
            }
        }, "mod-pl-gole-herby").start();
    }

    private static void wyswietl(final Context app, int typ, HomeDashboard.MatchItem m, Bitmap herb1, Bitmap herb2) {
        if (!mozeRysowac(app)) {
            Toast.makeText(app, dymek(app, typ, m), Toast.LENGTH_LONG).show();
            return;
        }
        try {
            final WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) return;
            if (aktualna != null) {
                try {
                    wm.removeViewImmediate(aktualna);
                } catch (Throwable ignored) {
                    // poprzednia plakietka mogła już zniknąć sama
                }
                aktualna = null;
            }
            final View v = plakietka(app, typ, m, herb1, herb2);
            int r = rog(app);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(app, 42),
                    Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = (r < 2 ? Gravity.TOP : Gravity.BOTTOM) | (r % 2 == 0 ? Gravity.END : Gravity.START);
            lp.x = dp(app, 32);
            lp.y = dp(app, 27);
            lp.windowAnimations = android.R.style.Animation_Toast;
            wm.addView(v, lp);
            aktualna = v;
            v.setAlpha(0f);
            v.animate().alpha(1f).setDuration(220).start();
            GLOWNY.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (aktualna != v) return;
                    aktualna = null;
                    try {
                        wm.removeView(v);
                    } catch (Throwable ignored) {
                        // okno mogło zostać zabrane razem z procesem
                    }
                }
            }, WIDOCZNE);
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "plakietka: " + e);
            Toast.makeText(app, dymek(app, typ, m), Toast.LENGTH_LONG).show();
        }
    }

    /** Przykładowe powiadomienie z ustawień: najbliższy mecz obserwowanej drużyny z wymyślonym wynikiem 0 : 1. */
    static void przyklad(final Activity a) {
        if (!mozeRysowac(a)) {
            ModPlAdb.wlaczNakladki(a);
            return;
        }
        HomeDashboard.MatchItem wzor = null;
        for (HomeDashboard.MatchItem m : HomeDashboard.getCachedMatches(a)) {
            if (m != null && (wzor == null || (m.isFav && !wzor.isFav))) wzor = m;
        }
        HomeDashboard.MatchItem p = new HomeDashboard.MatchItem();
        p.homeTeam = wzor != null ? wzor.homeTeam : "Rumunia";
        p.awayTeam = wzor != null ? wzor.awayTeam : "Polska";
        p.homeLogo = wzor != null ? wzor.homeLogo : "";
        p.awayLogo = wzor != null ? wzor.awayLogo : "";
        p.homeScore = "0";
        p.awayScore = "1";
        p.statusText = "23'";
        p.isLive = true;
        pokaz(a, GOL, p);
    }
}
