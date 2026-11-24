package com.techgenius.tvoslauncher;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Mod PL: własny wskaźnik głośności zamiast białego paska systemu (wariant B z kanwy 24.11.2026): pionowa szklana
 * pigułka przy prawej krawędzi, neutralne ciemne szkło, wypełnienie półprzezroczystą bielą, na dole ikona wyjścia
 * dźwięku (głośnik albo słuchawki).
 * Opcję wybiera użytkownik: w kreatorze (krok „Wskaźnik głośności”) albo w jednorazowym pytaniu na ekranie głównym
 * po aktualizacji lub po pominiętym kreatorze; do czasu wyboru zostaje pasek systemowy (prośba użytkownika
 * 25.11.2026: „zrób tak że mogą wybrać i przy pierwszej instalacji i przy aktualizacji”).
 * Pigułka wymaga działającej usługi dostępności launchera (PowerService). Są dwie drogi:
 * 1. Telewizory i boxy (Android TV): system sam obsługuje klawisze głośności i nie oddaje ich żadnej aplikacji
 *    (PhoneWindowManager, „On TVs volume keys never go to the foreground app”), więc przejąć ich się nie da.
 *    Zamiast tego pomocnik ModPlPowloka, uruchomiony przez klienta ADB launchera, odłącza systemowy pasek od usługi
 *    dźwięku ({@link #odlaczPasek}); klawisze dalej obsługuje system, a launcher pokazuje pigułkę po każdym
 *    rozgłoszeniu zmiany głośności. Odłączenie trwa do ponownego uruchomienia boxa, więc jest powtarzane przy
 *    starcie usługi. Bez dostępu do ADB zostaje pasek systemowy.
 * 2. Pozostałe urządzenia: klawisze docierają do usługi dostępności, która zmienia głośność bez flagi
 *    FLAG_SHOW_UI (system nie pokazuje wtedy swojego paska) i po każdym kroku woła {@link #poKroku}.
 * Klawisze zostają przy systemie, gdy głośność boxa jest stała albo pełna (dźwiękiem steruje telewizor lub
 * soundbar przez HDMI-CEC i to on pokazuje wskaźnik) oraz gdy poprawka przytrzymania głośności pracuje w trybie 2.
 * Zabezpieczenia drogi 1 (przegląd kodu AOSP 9-16, 24.11.2026): Android 9 i telewizory są pomijane, stan paska
 * sprawdzamy w usłudze dźwięku (SystemUI podłącza go znowu przy każdym swoim starcie), „głośniej” bez skutku
 * oddaje pasek systemowi (ochrona słuchu przy słuchawkach), a zatrzymanie usługi dostępności też go oddaje.
 * Plik źródłowy leży w pl/java/, a pl/naloz.py kopiuje go do src/ przy każdym buildzie.
 */
final class ModPlGlosnosc {
    private ModPlGlosnosc() { }

    private static final String TAG = "ModPlGlosnosc";
    private static final String POMOCNIK = "com.techgenius.tvoslauncher.ModPlPowloka";
    private static final String ZAPIS = "mod_pl_glosnosc";
    /** Pasek systemu jest odłączony (droga 1); potwierdzane w usłudze dźwięku przy starcie i po przerwach. */
    private static volatile boolean odlaczony;
    /** Do usługi dostępności dotarł klawisz głośności, czyli działa droga 2. */
    private static volatile boolean klawiszeDocieraja;
    private static boolean odbiornik;
    private static volatile boolean trwaProba;
    /** Wywołania czekające na wynik trwającej próby odłączenia. */
    private static final List<Runnable> PO_PROBIE = new ArrayList<Runnable>();
    /**
     * Pasek oddany systemowi do zmiany wyjścia dźwięku (albo do ponownego uruchomienia boxa): „głośniej” dwa razy
     * nie zmieniło poziomu, choć do maksimum daleko. Tak działa ochrona słuchu przy słuchawkach (system blokuje
     * głośniej i chce pokazać ostrzeżenie, którego bez jego paska nikt by nie zobaczył) oraz głośność ustawiana
     * poza boxem (telewizor przez HDMI-CEC, sterownik producenta).
     */
    private static volatile boolean wstrzymane;
    /** Dlaczego został pasek systemu (komunikat w ustawieniach): null, android, droga2, telewizor, wstrzymane, adb, blad. */
    private static volatile String powod;
    /** Chwila ostatniego rozgłoszenia zmiany głośności albo wyciszenia (dowolny strumień). */
    private static volatile long ostatniaZmianaMs;
    private static long ostatnieSprawdzenieMs, ostatniNieudanyMs;
    private static volatile long restartSystemuMs = -100000L;
    private static int nieudaneGlosniej;
    private static final Handler GLOWNY = new Handler(Looper.getMainLooper());
    /** Ile wskaźnik zostaje na ekranie po ostatniej zmianie głośności. */
    private static final long WIDOCZNY_MS = 1700L;
    /** Wymiary z kanwy 1920×1080: pigułka 88×400, 96 px od prawej krawędzi, 340 px od góry; zapas na cień. */
    private static final float SZER = 88f, WYS = 400f, OD_PRAWEJ = 96f, OD_GORY = 340f, ZAPAS = 28f;

    private static Wskaznik widok;
    private static WindowManager okna;

    private static final Runnable CHOWANIE = new Runnable() {
        @Override
        public void run() {
            final Wskaznik w = widok;
            if (w == null) return;
            w.animate().alpha(0f).setDuration(240).withEndAction(new Runnable() {
                @Override
                public void run() {
                    if (widok == w) zdejmij();
                }
            }).start();
        }
    };

    /** Wołane z PowerService.onKeyEvent, gdy do usługi dotarł klawisz głośności: od tej chwili działa droga 2. */
    static boolean przyKlawiszu(Context c) {
        klawiszeDocieraja = true;
        return wlaczony(c);
    }

    /**
     * Start usługi dostępności i włączenie opcji: nasłuch zmian głośności i odłączenie paska systemu (droga 1).
     */
    static void start(PowerService s) {
        if (s == null) return;
        final Context app = s.getApplicationContext();
        if (!odbiornik) {
            odbiornik = true;
            IntentFilter f = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
            f.addAction("android.media.STREAM_MUTE_CHANGED_ACTION");
            app.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent i) {
                    if (i == null) return;
                    ostatniaZmianaMs = SystemClock.uptimeMillis();
                    if (i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1) != AudioManager.STREAM_MUSIC) return;
                    PowerService u = PowerService.instance;
                    if (u == null || !odlaczony || pelna || !Scenes.isModPlVolumeIndicator(u)) return;
                    if (widok == null) sprawdzOdlaczenie(u.getApplicationContext());
                    pokaz(u);
                }
            }, f);
        }
        pilnujWyjsc(app);
        if (Scenes.isModPlVolumeIndicator(app)) odlaczPasek(app, null);
    }

    /** Numer bieżącego uruchomienia systemu (oddanie paska systemowi pamiętamy tylko w jego obrębie). */
    private static int uruchomienie(Context c) {
        return Settings.Global.getInt(c.getContentResolver(), "boot_count", -1);
    }

    /**
     * Odłącza pasek systemu przez pomocnika uruchomionego z uprawnieniami powłoki ADB. Najpierw pyta usługę dźwięku,
     * czy pasek nie jest już odłączony (linia mVolumeController w dumpsys audio), więc to samo wywołanie służy też
     * do sprawdzenia: SystemUI podłącza pasek przy każdym swoim starcie (awaria, aktualizacja, w Androidzie 14+
     * także sam restart po zmianie flag), a wtedy odłączamy go znowu. Pomijane są: Android 9 (powłoka nie ma
     * uprawnienia STATUS_BAR_SERVICE), urządzenia bez trybu TV (klawisze docierają do usługi dostępności, droga 2)
     * i telewizory (typ HDMI-CEC 0: producent może rysować własny wskaźnik poza systemem i wyszłyby dwa).
     * {@code poProbie} dostaje wynik na wątku głównym.
     */
    static void odlaczPasek(final Context app, final Runnable poProbie) {
        String pomin = null;
        if (Build.VERSION.SDK_INT < 29) {
            pomin = "android";
        } else if (!app.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            pomin = "droga2";
        } else if (wstrzymane || app.getSharedPreferences(ZAPIS, Context.MODE_PRIVATE)
                .getInt("wstrzymane_w_uruchomieniu", -2) == uruchomienie(app)) {
            wstrzymane = true;
            pomin = "wstrzymane";
        } else if (!LocalAdb.isPaired(app)) {
            // Bez wcześniejszego udanego połączenia launchera z ADB nie próbujemy: próba z niezatwierdzonym kluczem
            // wywołałaby na telewizorze okno „Zezwolić na debugowanie?”.
            pomin = "adb";
        }
        if (pomin != null) {
            if (!pomin.equals(powod)) ModPlDziennik.i(TAG, "zostaje pasek systemu: " + pomin);
            powod = pomin;
            if (poProbie != null) GLOWNY.post(poProbie);
            return;
        }
        synchronized (PO_PROBIE) {
            if (poProbie != null) PO_PROBIE.add(poProbie);
            if (trwaProba) return;
            trwaProba = true;
        }
        // Po naszym restarcie SystemUI czekamy, aż podłączy swój pasek; inaczej zrobiłoby to po nas.
        final long czekaj = Math.max(0L, restartSystemuMs + 8000L - SystemClock.uptimeMillis());
        new Thread(new Runnable() {
            @Override
            public void run() {
                String wynik;
                try {
                    if (czekaj > 0) Thread.sleep(czekaj);
                    wynik = LocalAdb.execShellOutput(app, "v=$(dumpsys audio 2>/dev/null | grep -m1 'mVolumeController='); "
                            + "case \"$v\" in *'(null'*) echo 'OK bez zmian';; *) "
                            + "case \",$(getprop ro.hdmi.device_type),\" in *,0,*) echo 'POMIN telewizor';; *) "
                            + "CLASSPATH=" + app.getPackageCodePath() + " app_process / " + POMOCNIK
                            + " glosnosc-odlacz 2>&1;; esac;; esac");
                } catch (Throwable e) {
                    wynik = "BLAD " + e;
                }
                String w = String.valueOf(wynik).trim();
                if (w.contains("OK bez zmian") || w.contains("OK glosnosc-odlacz")) {
                    if (!odlaczony || w.contains("OK glosnosc-odlacz")) ModPlDziennik.i(TAG, "odłączenie paska systemu: " + w);
                    odlaczony = true;
                    powod = null;
                } else if (w.contains("POMIN telewizor")) {
                    odlaczony = false;
                    powod = "telewizor";
                    ModPlDziennik.i(TAG, "telewizor (typ HDMI-CEC 0): zostaje wskaźnik systemu albo producenta");
                } else if (w.contains("BLAD glosnosc-odlacz")) {
                    // pomocnik nie odłączył paska, więc pokazuje go system
                    odlaczony = false;
                    powod = "blad";
                    ModPlDziennik.i(TAG, "odłączenie paska systemu: " + w);
                } else {
                    // brak odpowiedzi powłoki (np. chwilowo bez ADB): zostaje poprzedni stan
                    if (!odlaczony) powod = "adb";
                    ModPlDziennik.w(TAG, "sprawdzenie paska systemu bez wyniku: " + w);
                }
                synchronized (PO_PROBIE) {
                    trwaProba = false;
                    for (Runnable r : PO_PROBIE) GLOWNY.post(r);
                    PO_PROBIE.clear();
                }
            }
        }, "mod-pl-glosnosc").start();
    }

    /** Pierwszy klawisz po przerwie: czy SystemUI nie podłączyło znowu swojego paska (najwyżej co 5 s). */
    private static void sprawdzOdlaczenie(Context app) {
        long teraz = SystemClock.uptimeMillis();
        if (trwaProba || teraz - ostatnieSprawdzenieMs < 5000L) return;
        ostatnieSprawdzenieMs = teraz;
        odlaczPasek(app, null);
    }

    /** Powrót do paska systemu po wyłączeniu opcji. */
    static void przywrocPasek(final Context c) {
        zdejmij();
        if (!odlaczony) return;
        oddajSystemowi(c.getApplicationContext(), "pasek systemu przywrócony");
    }

    /**
     * Usługa dostępności się zatrzymuje (wyłączona w ustawieniach Androida albo launcher się zamyka): bez niej nikt
     * nie pokaże pigułki, więc pasek wraca do systemu. Ponowny start usługi znowu go odłączy.
     */
    static void poZatrzymaniuUslugi(Context c) {
        zdejmij();
        if (odlaczony) oddajSystemowi(c.getApplicationContext(), "usługa dostępności zatrzymana: pasek systemu wraca");
    }

    /**
     * Oddaje pasek systemowi: SystemUI podłącza go tylko przy własnym starcie, więc uruchamiamy je od nowa
     * (trwa chwilę, na telewizorze nie ma paska stanu, który by przy tym mignął).
     */
    private static void oddajSystemowi(final Context app, final String opis) {
        odlaczony = false;
        restartSystemuMs = SystemClock.uptimeMillis();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    LocalAdb.execShellOutput(app, "am force-stop com.android.systemui");
                    ModPlDziennik.i(TAG, opis + " (SystemUI uruchomione od nowa)");
                } catch (Throwable e) {
                    ModPlDziennik.w(TAG, opis + ": " + e);
                }
            }
        }, "mod-pl-glosnosc").start();
    }

    /** Pasek wraca do systemu do zmiany wyjścia dźwięku albo do ponownego uruchomienia boxa (zob. {@link #wstrzymane}). */
    private static void wstrzymaj(Context app) {
        nieudaneGlosniej = 0;
        wstrzymane = true;
        powod = "wstrzymane";
        zdejmij();
        int start = uruchomienie(app);
        if (start >= 0) {
            app.getSharedPreferences(ZAPIS, Context.MODE_PRIVATE).edit().putInt("wstrzymane_w_uruchomieniu", start).apply();
        }
        oddajSystemowi(app, "„głośniej” nie zmienia poziomu (ochrona słuchu albo głośność poza boxem): pasek systemu wraca");
    }

    /**
     * Wołane z obserwatorów klawiszy oryginału (getevent przy stałym ADB, zdarzenia AudioService przy uprawnieniu
     * DUMP), gdy system obsłużył klawisz głośności ({@code kierunek}: 1 głośniej, -1 ciszej, 0 nieznany). Pokazuje
     * pigułkę także na krańcach skali, gdzie rozgłoszenie zmiany nie przychodzi, a 0,7 s później sprawdza skutek
     * klawisza ({@link #sprawdzSkutek}). Pierwszy klawisz po przerwie sprawdza też, czy pasek dalej jest odłączony.
     */
    static void poKlawiszuSystemu(final int kierunek) {
        final PowerService s = PowerService.instance;
        if (s == null || !odlaczony || pelna || !Scenes.isModPlVolumeIndicator(s)) return;
        final long klawiszMs = SystemClock.uptimeMillis();
        naGlownym(new Runnable() {
            @Override
            public void run() {
                try {
                    AudioManager am = (AudioManager) s.getSystemService(Context.AUDIO_SERVICE);
                    if (am == null) return;
                    if (widok == null) sprawdzOdlaczenie(s.getApplicationContext());
                    int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                    int poziom = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                    boolean cisza = Build.VERSION.SDK_INT >= 23 && am.isStreamMute(AudioManager.STREAM_MUSIC);
                    // „ciszej” na maksimum: pigułkę pokaże rozgłoszenie, jeśli poziom naprawdę spadnie
                    if (!(kierunek < 0 && poziom >= max && !cisza)) pokaz(s);
                } catch (Throwable e) {
                    ModPlDziennik.w(TAG, "po klawiszu systemu: " + e);
                }
                if (kierunek != 0) {
                    GLOWNY.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            sprawdzSkutek(s, kierunek, klawiszMs);
                        }
                    }, 700L);
                }
            }
        });
    }

    /**
     * 0,7 s po klawiszu: czy system zmienił poziom (przyszło rozgłoszenie). Jeśli nie:
     * - „ciszej” na maksimum to pełna głośność przez HDMI-CEC (poziom ustawia i pokazuje telewizor), więc do zmiany
     *   wyjścia dźwięku pigułki nie pokazujemy ({@code pelna}),
     * - „głośniej” poniżej maksimum dwa razy z rzędu to ochrona słuchu albo głośność poza boxem: {@link #wstrzymaj}.
     */
    private static void sprawdzSkutek(PowerService s, int kierunek, long klawiszMs) {
        if (!odlaczony || pelna || PowerService.instance != s) return;
        if (ostatniaZmianaMs >= klawiszMs - 400L) {   // rozgłoszenie tuż przed zgłoszeniem klawisza albo po nim
            if (kierunek > 0) nieudaneGlosniej = 0;
            return;
        }
        try {
            AudioManager am = (AudioManager) s.getSystemService(Context.AUDIO_SERVICE);
            if (am == null || (Build.VERSION.SDK_INT >= 23 && am.isStreamMute(AudioManager.STREAM_MUSIC))) return;
            int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            int poziom = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (kierunek < 0 && poziom >= max) {
                pelna = true;
                zdejmij();
                ModPlDziennik.i(TAG, "„ciszej” nie zmienia pełnej głośności (HDMI-CEC?): wskaźnik pokazuje telewizor");
            } else if (kierunek > 0 && poziom < max) {
                if (klawiszMs - ostatniNieudanyMs > 250L) {   // na niektórych pilotach jedno naciśnięcie to dwa kroki
                    nieudaneGlosniej++;
                    ostatniNieudanyMs = klawiszMs;
                }
                if (nieudaneGlosniej >= 2) wstrzymaj(s.getApplicationContext());
            }
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "skutek klawisza: " + e);
        }
    }

    /** Czy klawisze głośności obsługujemy sami i pokazujemy własny wskaźnik. */
    static boolean wlaczony(Context c) {
        if (c == null || pelna || !Scenes.isModPlVolumeIndicator(c)) return false;
        // Na telewizorach klawisze do nas nie docierają: bez odłączonego paska systemu wyszłyby dwa wskaźniki naraz.
        if (!odlaczony && !klawiszeDocieraja) return false;
        if (ButtonMapper.isVolumeHoldFixEnabled(c)
                && ButtonMapper.getVolumeHoldFixMode(c) == ButtonMapper.VOLUME_FIX_MODE_2) return false;
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        return am != null && !am.isVolumeFixed();
    }

    /**
     * Flaga dla AudioManager: bez paska systemowego, gdy pokazujemy własny wskaźnik, ale z ostrzeżeniami systemu
     * (FLAG_SHOW_UI_WARNINGS: ochrona słuchu przy słuchawkach; bez niej system po cichu nie podgłośniłby ponad próg).
     */
    static int flaga(Context c) {
        return wlaczony(c) ? FLAG_OSTRZEZENIA : AudioManager.FLAG_SHOW_UI;
    }

    /** AudioManager.FLAG_SHOW_UI_WARNINGS (1 << 10), ukryta w publicznym SDK. */
    private static final int FLAG_OSTRZEZENIA = 1 << 10;

    /** Wołane przez PowerService po każdym kroku głośności ({@code kierunek}: 1 głośniej, -1 ciszej). */
    static void poKroku(final PowerService s, int kierunek) {
        if (s == null || !wlaczony(s)) return;
        poZmianie(s, kierunek);
    }

    /**
     * Przy obrazie przez HDMI z telewizorem sterowanym przez HDMI-CEC system trzyma głośność boxa na pełnym poziomie
     * i przesyła klawisze do telewizora, który pokazuje własny wskaźnik. Rozpoznajemy to po kroku „ciszej”, po którym
     * poziom dalej jest pełny: od tej chwili klawisze wracają do systemu ({@code pelna}). Dopóki poziom ani razu nie
     * był niższy od pełnego ({@code zwykla}), przy pełnej głośności wskaźnika nie pokazujemy. Obie wiedze kasuje
     * zmiana urządzeń wyjściowych dźwięku (np. podłączenie słuchawek Bluetooth).
     */
    private static boolean pelna, zwykla, nasluch, pierwszeZgloszenie;

    private static void poZmianie(final PowerService s, final int kierunek) {
        naGlownym(new Runnable() {
            @Override
            public void run() {
                try {
                    pilnujWyjsc(s);
                    AudioManager am = (AudioManager) s.getSystemService(Context.AUDIO_SERVICE);
                    if (am == null) return;
                    int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                    int teraz = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                    boolean cisza = Build.VERSION.SDK_INT >= 23 && am.isStreamMute(AudioManager.STREAM_MUSIC);
                    if (teraz < max) zwykla = true;
                    if (!zwykla && !cisza && teraz >= max) {
                        if (kierunek < 0) {
                            pelna = true;
                            zdejmij();
                            ModPlDziennik.i(TAG, "głośność boxa stale pełna (HDMI-CEC?): klawisze wracają do systemu");
                        }
                        return;
                    }
                } catch (Throwable e) {
                    ModPlDziennik.w(TAG, "po zmianie: " + e);
                }
                pokaz(s);
            }
        });
    }

    /**
     * Zmiana urządzeń wyjściowych dźwięku kasuje wiedzę o pełnej i zwykłej głośności oraz oddanie paska systemowi
     * (np. odłączone słuchawki: znowu własny wskaźnik).
     */
    private static void pilnujWyjsc(Context c) {
        if (nasluch || Build.VERSION.SDK_INT < 23) return;
        final Context app = c.getApplicationContext();
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        nasluch = true;
        pierwszeZgloszenie = true;
        am.registerAudioDeviceCallback(new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] urzadzenia) {
                if (pierwszeZgloszenie) {   // przy rejestracji system od razu zgłasza obecne urządzenia
                    pierwszeZgloszenie = false;
                    return;
                }
                poZmianieWyjscia(app);
            }

            @Override
            public void onAudioDevicesRemoved(AudioDeviceInfo[] urzadzenia) {
                poZmianieWyjscia(app);
            }
        }, GLOWNY);
    }

    private static void poZmianieWyjscia(Context app) {
        zwykla = false;
        pelna = false;
        nieudaneGlosniej = 0;
        if (!wstrzymane) return;
        wstrzymane = false;
        app.getSharedPreferences(ZAPIS, Context.MODE_PRIVATE).edit().remove("wstrzymane_w_uruchomieniu").apply();
        ModPlDziennik.i(TAG, "zmiana wyjścia dźwięku: znowu własny wskaźnik");
        if (Scenes.isModPlVolumeIndicator(app) && PowerService.instance != null) odlaczPasek(app, null);
    }

    /** Kierunek przytrzymanego klawisza (1 głośniej, -1 ciszej, 0 nic) i chwila jego naciśnięcia. */
    private static int trzymany;
    private static long trzymanyOd;
    private static PowerService usluga;
    /** Po tylu milisekundach przytrzymania powtarzanie samo się kończy, nawet gdy puszczenie klawisza nie dotarło. */
    private static final long NAJDLUZEJ_MS = 4000L;

    private static final Runnable POWTARZANIE = new Runnable() {
        @Override
        public void run() {
            PowerService s = usluga;
            if (trzymany == 0 || s == null || PowerService.instance != s) return;
            if (SystemClock.uptimeMillis() - trzymanyOd > NAJDLUZEJ_MS) {
                trzymany = 0;
                return;
            }
            krok(s, trzymany);
            GLOWNY.postDelayed(this, 110L);
        }
    };

    private static void krok(PowerService s, int kierunek) {
        try {
            AudioManager am = (AudioManager) s.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            am.adjustSuggestedStreamVolume(kierunek > 0 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                    AudioManager.USE_DEFAULT_STREAM_TYPE, FLAG_OSTRZEZENIA);
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "krok: " + e);
        }
        poZmianie(s, kierunek);
    }

    /**
     * Klawisze głośności i wyciszenia, których nie obsłużyła poprawka przytrzymania oryginału w PowerService.
     * {@code powtarzaLauncher}: kroki przytrzymanego klawisza powtarza launcher (obserwator klawiszy przez lokalne
     * ADB), więc robimy tylko pierwszy krok. W przeciwnym razie usługa nie dostaje powtórzeń klawisza i powtarzamy
     * sami: pierwszy krok od razu, kolejne po 350 ms co 110 ms, do puszczenia klawisza i nie dłużej niż 4 sekundy.
     */
    static void klawisz(final PowerService s, KeyEvent e, final boolean powtarzaLauncher) {
        int kod = e.getKeyCode();
        boolean dol = e.getAction() == KeyEvent.ACTION_DOWN;
        if (kod == KeyEvent.KEYCODE_VOLUME_MUTE) {
            if (dol && e.getRepeatCount() == 0) wycisz(s);
            return;
        }
        final int kierunek = kod == KeyEvent.KEYCODE_VOLUME_UP ? 1 : -1;
        if (!dol) {
            if (e.getAction() == KeyEvent.ACTION_UP && trzymany == kierunek) {
                trzymany = 0;
                GLOWNY.removeCallbacks(POWTARZANIE);
            }
            return;
        }
        if (e.getRepeatCount() > 0) return;
        naGlownym(new Runnable() {
            @Override
            public void run() {
                GLOWNY.removeCallbacks(POWTARZANIE);
                usluga = s;
                trzymany = powtarzaLauncher ? 0 : kierunek;
                trzymanyOd = SystemClock.uptimeMillis();
                krok(s, kierunek);
                if (!powtarzaLauncher) GLOWNY.postDelayed(POWTARZANIE, 350L);
            }
        });
    }

    /** Klawisz wyciszenia: przełącza wyciszenie bez paska systemowego i pokazuje wskaźnik. */
    static void wycisz(final PowerService s) {
        try {
            AudioManager am = (AudioManager) s.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            am.adjustSuggestedStreamVolume(AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.USE_DEFAULT_STREAM_TYPE, 0);
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "wyciszenie: " + e);
        }
        naGlownym(new Runnable() {
            @Override
            public void run() {
                pokaz(s);
            }
        });
    }

    /** Drugi odczyt chwilę po zmianie: system potrafi zgłosić nowy poziom albo wyciszenie z małym opóźnieniem. */
    private static final Runnable ODSWIEZENIE = new Runnable() {
        @Override
        public void run() {
            PowerService s = PowerService.instance;
            Wskaznik w = widok;
            if (s == null || w == null) return;
            try {
                AudioManager am = (AudioManager) s.getSystemService(Context.AUDIO_SERVICE);
                if (am == null) return;
                int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                w.ustaw(am.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max,
                        Build.VERSION.SDK_INT >= 23 && am.isStreamMute(AudioManager.STREAM_MUSIC),
                        am.isBluetoothA2dpOn() || am.isWiredHeadsetOn(), true);
            } catch (Throwable e) {
                ModPlDziennik.w(TAG, "odświeżenie: " + e);
            }
        }
    };

    /** Po włączeniu opcji w ustawieniach: krótkie wyjaśnienie i podgląd wskaźnika. */
    static void przyklad(final Activity a) {
        final PowerService s = PowerService.instance;
        if (s == null) {
            Toast.makeText(a, I18n.t(a, "The glass volume indicator needs the launcher's accessibility service."),
                    Toast.LENGTH_LONG).show();
            return;
        }
        start(s);
        odlaczPasek(s.getApplicationContext(), new Runnable() {
            @Override
            public void run() {
                if (a.isFinishing()) return;
                if (odlaczony || klawiszeDocieraja || "droga2".equals(powod)) {
                    pokaz(s);
                } else {
                    Toast.makeText(a, I18n.t(a, komunikat(powod)), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /** Wyjaśnienie w ustawieniach, dlaczego został pasek systemu (klucze tłumaczeń w pl/pl_dodatkowe.txt). */
    private static String komunikat(String p) {
        if ("android".equals(p)) return "Android 9 and older don't allow replacing the system volume bar.";
        if ("telewizor".equals(p)) return "On a TV the volume indicator stays with the system or the TV maker.";
        if ("wstrzymane".equals(p)) {
            return "The system volume bar is back until the audio output changes (e.g. hearing protection for headphones).";
        }
        return "Couldn't turn off the system volume bar. On a TV this needs ADB debugging to be on and allowed for the launcher.";
    }

    private static void naGlownym(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else GLOWNY.post(r);
    }

    private static void pokaz(PowerService s) {
        try {
            AudioManager am = (AudioManager) s.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            int teraz = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            boolean cisza = Build.VERSION.SDK_INT >= 23 && am.isStreamMute(AudioManager.STREAM_MUSIC);
            boolean sluchawki = am.isBluetoothA2dpOn() || am.isWiredHeadsetOn();
            if (widok == null) {
                WindowManager wm = (WindowManager) s.getSystemService(Context.WINDOW_SERVICE);
                if (wm == null) return;
                float skala = s.getResources().getDisplayMetrics().widthPixels / 1920f;
                Wskaznik w = new Wskaznik(s, skala);
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        Math.round((SZER + 2 * ZAPAS) * skala), Math.round((WYS + 2 * ZAPAS) * skala),
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.TOP | Gravity.RIGHT;
                lp.x = Math.round((OD_PRAWEJ - ZAPAS) * skala);
                lp.y = Math.round((OD_GORY - ZAPAS) * skala);
                lp.setTitle("ModPlGlosnosc");
                w.ustaw(teraz / (float) max, cisza, sluchawki, false);
                w.setAlpha(0f);
                wm.addView(w, lp);
                okna = wm;
                widok = w;
            } else {
                widok.ustaw(teraz / (float) max, cisza, sluchawki, true);
            }
            widok.animate().cancel();
            widok.animate().alpha(1f).setDuration(140).start();
            GLOWNY.removeCallbacks(CHOWANIE);
            GLOWNY.postDelayed(CHOWANIE, WIDOCZNY_MS);
            GLOWNY.removeCallbacks(ODSWIEZENIE);
            GLOWNY.postDelayed(ODSWIEZENIE, 90L);
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "wskaźnik: " + e);
            zdejmij();
        }
    }

    private static void zdejmij() {
        GLOWNY.removeCallbacks(CHOWANIE);
        Wskaznik w = widok;
        WindowManager wm = okna;
        widok = null;
        okna = null;
        if (w == null || wm == null) return;
        try {
            wm.removeViewImmediate(w);
        } catch (Throwable ignored) {
            // okno mogło zniknąć razem z usługą
        }
    }

    // ------------------------------------------------------------------ wybór: kreator i pytanie po aktualizacji

    /** W zapisie modułu: pytanie po aktualizacji już się pokazało (pada najwyżej raz). */
    private static final String K_ZAPYTANO = "zapytano_o_wskaznik";
    private static boolean pytanieZaplanowane;

    private static boolean leanback(Context c) {
        return c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    /**
     * Czy pigułka w ogóle może tu działać: nigdy na telewizorze, na przystawce w trybie TV od Androida 10 (odłączanie
     * paska przez ADB), na pozostałych urządzeniach zawsze (klawisze docierają do usługi dostępności).
     */
    static boolean mozliwyNaUrzadzeniu(Context c) {
        return !telewizor() && (!leanback(c) || Build.VERSION.SDK_INT >= 29);
    }

    /** Typ urządzenia HDMI-CEC z wartością 0 to telewizor (to samo sprawdza powłoka w {@link #odlaczPasek}). */
    private static boolean telewizor() {
        try {
            Object typ = Class.forName("android.os.SystemProperties").getMethod("get", String.class)
                    .invoke(null, "ro.hdmi.device_type");
            for (String t : String.valueOf(typ).split(",")) {
                if ("0".equals(t.trim())) return true;
            }
        } catch (Throwable ignored) {
            // bez odczytu właściwości rozstrzyga powłoka przy odłączaniu paska
        }
        return false;
    }

    /** Czy pigułka zadziała od razu: usługa dostępności działa, a przystawka TV ma stałe ADB z zatwierdzonym kluczem. */
    static boolean dzialaTeraz(Context c) {
        if (PowerService.instance == null || !mozliwyNaUrzadzeniu(c)) return false;
        return !leanback(c) || LocalAdb.getAdbMode(c) == LocalAdb.ADB_MODE_PERSISTENT && LocalAdb.isPaired(c);
    }

    /** Wybór z kreatora albo z pytania: zapis i od razu skutek (pigułka na próbę albo powrót paska systemu). */
    static void wybierz(Activity a, boolean pigulka) {
        Scenes.setModPlVolumeIndicator(a, pigulka);
        ModPlDziennik.i(TAG, "wybór użytkownika: " + (pigulka ? "szklana pigułka" : "pasek systemowy"));
        if (pigulka) przyklad(a);
        else przywrocPasek(a);
    }

    /**
     * Krok kreatora „Wskaźnik głośności” (SetupActivity.modPlGlosnosc, dodawany tylko tam, gdzie pigułka może działać):
     * dwie opcje w stylu kreatora i obok żywy podgląd pigułki. Wybór od razu pokazuje pigułkę na próbę.
     */
    static void krokKreatora(final SetupActivity k) {
        k.title(I18n.t(k, "Volume indicator"));
        String opis = I18n.t(k, "What should appear when you change the volume with the remote? You can change it later in Settings → Look and scenes → Dock.");
        if (leanback(k) && LocalAdb.getAdbMode(k) != LocalAdb.ADB_MODE_PERSISTENT) {
            opis += " " + I18n.t(k, "The glass pill needs the persistent ADB mode.");
        }
        TextView podpis = k.subtitle(opis);
        ((LinearLayout.LayoutParams) podpis.getLayoutParams()).width = k.u(600);

        // lista opcji kreatora i podgląd pigułki obok niej
        LinearLayout lista = k.list();
        k.page.removeView(lista);
        LinearLayout rzad = new LinearLayout(k);
        rzad.setOrientation(LinearLayout.HORIZONTAL);
        rzad.setGravity(android.view.Gravity.CENTER_VERTICAL);
        rzad.setClipChildren(false);
        rzad.setClipToPadding(false);
        rzad.addView(lista, new LinearLayout.LayoutParams(k.u(484), ViewGroup.LayoutParams.WRAP_CONTENT));
        float skala = k.getResources().getDisplayMetrics().widthPixels / 1920f;
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(szerokoscPodgladu(skala), wysokoscPodgladu(skala));
        plp.setMarginStart(k.u(36));
        rzad.addView(podglad(k, skala), plp);
        k.page.addView(rzad, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        boolean wybrane = Scenes.isModPlVolumeIndicatorChosen(k), pigulka = Scenes.isModPlVolumeIndicator(k);
        k.option(lista, I18n.t(k, "Glass pill"), I18n.t(k, "In the tvOS style, at the right edge of the screen"),
                wybrane && pigulka, () -> {
                    wybierz(k, true);
                    k.next();
                });
        k.option(lista, I18n.t(k, "System bar"), I18n.t(k, "Android's own volume bar"),
                wybrane && !pigulka, () -> {
                    wybierz(k, false);
                    k.next();
                });
        k.dots();
    }

    /**
     * Ekran główny po aktualizacji albo po pominiętym kreatorze: raz pyta o wskaźnik, gdy wybór jeszcze nie padł,
     * kreator jest zakończony i pigułka zadziała od razu (inaczej zapyta przy którymś następnym powrocie na ekran
     * główny, np. po włączeniu stałego ADB). Pytanie po 3 s, tylko gdy ekran główny dalej jest na wierzchu; dopiero
     * wtedy sprawdzamy usługę dostępności, bo po aktualizacji system podłącza ją z opóźnieniem.
     */
    static void zapytajPoAktualizacji(final MainActivity a) {
        if (pytanieZaplanowane || a == null || Scenes.isModPlVolumeIndicatorChosen(a) || !SetupActivity.done(a)) return;
        final SharedPreferences zapis = a.getSharedPreferences(ZAPIS, Context.MODE_PRIVATE);
        if (zapis.getBoolean(K_ZAPYTANO, false) || !mozliwyNaUrzadzeniu(a)) return;
        pytanieZaplanowane = true;
        GLOWNY.postDelayed(new Runnable() {
            @Override
            public void run() {
                pytanieZaplanowane = false;
                // bieżący ekran główny, nie ten z chwili planowania: zmiana języka tworzy go od nowa (recreate)
                MainActivity m = MainActivity.activeInstance;
                if (m == null || m.isFinishing() || !m.resumed
                        || Scenes.isModPlVolumeIndicatorChosen(m) || zapis.getBoolean(K_ZAPYTANO, false)) return;
                if (!dzialaTeraz(m)) {
                    ModPlDziennik.i(TAG, "pytanie o wskaźnik odłożone: pigułka jeszcze by nie zadziałała");
                    return;
                }
                zapis.edit().putBoolean(K_ZAPYTANO, true).apply();
                ModPlDziennik.i(TAG, "pytanie o wskaźnik głośności (po aktualizacji albo pominiętym kreatorze)");
                ModPlOknaTv.pytanieGlosnosci(m);
            }
        }, 3000L);
    }

    /** Podgląd pigułki do kreatora i pytania: ten sam rysunek co na ekranie, poziom łagodnie faluje. */
    static View podglad(Context c, float skala) {
        final Wskaznik w = new Wskaznik(c, skala);
        w.ustaw(0.6f, false, false, false);
        final ValueAnimator fala = ValueAnimator.ofFloat(0.3f, 0.82f);
        fala.setDuration(1600L);
        fala.setRepeatCount(ValueAnimator.INFINITE);
        fala.setRepeatMode(ValueAnimator.REVERSE);
        fala.setInterpolator(new AccelerateDecelerateInterpolator());
        fala.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                w.ustaw((Float) a.getAnimatedValue(), false, false, false);
            }
        });
        w.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                fala.start();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                fala.cancel();
            }
        });
        return w;
    }

    static int szerokoscPodgladu(float skala) {
        return Math.round((SZER + 2 * ZAPAS) * skala);
    }

    static int wysokoscPodgladu(float skala) {
        return Math.round((WYS + 2 * ZAPAS) * skala);
    }

    /** Podgląd w miejscu prawdziwej pigułki (prawa krawędź, 340 px od góry przy 1920×1080). */
    static FrameLayout.LayoutParams miejscePigulki(Context c) {
        float skala = c.getResources().getDisplayMetrics().widthPixels / 1920f;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(szerokoscPodgladu(skala), wysokoscPodgladu(skala),
                Gravity.TOP | Gravity.RIGHT);
        lp.rightMargin = Math.round((OD_PRAWEJ - ZAPAS) * skala);
        lp.topMargin = Math.round((OD_GORY - ZAPAS) * skala);
        return lp;
    }

    /** Pigułka: ciemne szkło, wypełnienie od dołu do poziomu głośności i ikona wyjścia dźwięku. */
    private static final class Wskaznik extends View {
        private final float s;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF pole = new RectF();
        private final Path obrys = new Path(), ikona = new Path();
        private float poziom, cel;
        private boolean cisza, sluchawki;
        private ValueAnimator anim;

        Wskaznik(Context c, float skala) {
            super(c);
            s = skala;
        }

        void ustaw(float nowy, boolean wyciszone, boolean sluch, boolean plynnie) {
            cisza = wyciszone;
            sluchawki = sluch;
            cel = Math.max(0f, Math.min(1f, nowy));
            if (anim != null) anim.cancel();
            if (!plynnie) {
                poziom = cel;
                invalidate();
                return;
            }
            anim = ValueAnimator.ofFloat(poziom, cel);
            anim.setDuration(130);
            anim.setInterpolator(new DecelerateInterpolator());
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    poziom = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            anim.start();
        }

        @Override
        protected void onDraw(Canvas c) {
            float l = ZAPAS * s, t = ZAPAS * s, w = SZER * s, h = WYS * s, r = w / 2f;
            pole.set(l, t, l + w, t + h);

            // cień pod pigułką (na Androidzie 9 i nowszych; starsze rysują ją bez cienia)
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            if (Build.VERSION.SDK_INT >= 28) {
                p.setColor(0x33000000);
                p.setShadowLayer(16 * s, 0f, 8 * s, 0x66000000);
                c.drawRoundRect(pole, r, r, p);
                p.clearShadowLayer();
            }

            // ciemne szkło
            p.setColor(0xFFFFFFFF);
            p.setShader(new LinearGradient(0, t, 0, t + h, 0xC42A2A30, 0xD9101014, Shader.TileMode.CLAMP));
            c.drawRoundRect(pole, r, r, p);
            p.setShader(null);

            // wypełnienie do poziomu głośności, przycięte do kształtu pigułki
            float wysokosc = cisza ? 0f : h * poziom;
            if (wysokosc > 0.5f) {
                obrys.reset();
                obrys.addRoundRect(pole, r, r, Path.Direction.CW);
                c.save();
                c.clipPath(obrys);
                p.setColor(0xADFFFFFF);
                c.drawRect(l, t + h - wysokosc, l + w, t + h, p);
                c.restore();
            }

            // jasna krawędź
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, s));
            p.setColor(0x33FFFFFF);
            c.drawRoundRect(pole, r, r, p);

            // ikona 34 px, 26 px od lewej i 30 px od dołu pigułki; ciemna, gdy leży na wypełnieniu
            float j = 34f * s / 24f;
            c.save();
            c.translate(l + 26 * s, t + h - (30 + 34) * s);
            c.scale(j, j);
            int kolor = wysokosc >= 50 * s ? 0xFF1C1C22 : 0xFFFFFFFF;
            if (sluchawki) rysujSluchawki(c, kolor);
            else rysujGlosnik(c, kolor);
            c.restore();
        }

        /** Wyciszone albo ściszone do zera (pilot bez klawisza wyciszenia ścisza do zera): ikona przekreślona. */
        private boolean bezDzwieku() {
            return cisza || cel <= 0.001f;
        }

        /** Promienie trzech fal głośnika (pole 24×24, środek fal przy przedniej krawędzi bryły). */
        private static final float[] FALE = {3.6f, 7f, 10.4f};

        /**
         * Głośnik w polu 24×24 jak symbol Apple „speaker.wave.3” (iPhone): bryła i trzy fale, każda dla jednej trzeciej
         * skali (świeci przy poziomie powyżej 0, 1/3 i 2/3), nieosiągnięte przygaszone do 30%; sama bryła się nie
         * zmienia. Bez dźwięku (wyciszenie albo poziom 0) głośnik przekreślony jak „speaker.slash”, bez fal.
         */
        private void rysujGlosnik(Canvas c, int kolor) {
            boolean bez = bezDzwieku();
            int warstwa = bez ? c.saveLayer(0f, 0f, 24f, 24f, null) : -1;
            p.setColor(kolor);
            p.setStyle(Paint.Style.FILL_AND_STROKE);
            p.setStrokeWidth(1.2f);
            p.setStrokeJoin(Paint.Join.ROUND);
            ikona.reset();
            ikona.moveTo(2.7f, 9.4f);
            ikona.lineTo(6.1f, 9.4f);
            ikona.lineTo(10.5f, 5.4f);
            ikona.lineTo(10.5f, 18.6f);
            ikona.lineTo(6.1f, 14.6f);
            ikona.lineTo(2.7f, 14.6f);
            ikona.close();
            c.drawPath(ikona, p);
            if (bez) {
                przekresl(c, kolor, warstwa);
                return;
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.9f);
            p.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < FALE.length; i++) {
                p.setColor(cel > i / 3f ? kolor : (kolor & 0x00FFFFFF) | 0x4D000000);
                float r = FALE[i];
                pole.set(10.7f - r, 12f - r, 10.7f + r, 12f + r);
                c.drawArc(pole, -42f, 84f, false, p);
            }
            p.setStrokeCap(Paint.Cap.BUTT);
        }

        /** Słuchawki w polu 24×24: pałąk i dwie nauszniki; bez dźwięku przekreślone. */
        private void rysujSluchawki(Canvas c, int kolor) {
            boolean bez = bezDzwieku();
            int warstwa = bez ? c.saveLayer(0f, 0f, 24f, 24f, null) : -1;
            p.setColor(kolor);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            p.setStrokeCap(Paint.Cap.ROUND);
            pole.set(5f, 5f, 19f, 19f);
            c.drawArc(pole, 180f, 180f, false, p);
            p.setStyle(Paint.Style.FILL);
            pole.set(3.6f, 12f, 8.2f, 19.5f);
            c.drawRoundRect(pole, 1.6f, 1.6f, p);
            pole.set(15.8f, 12f, 20.4f, 19.5f);
            c.drawRoundRect(pole, 1.6f, 1.6f, p);
            if (bez) przekresl(c, kolor, warstwa);
            p.setStrokeCap(Paint.Cap.BUTT);
        }

        /**
         * Ukośna kreska jak w symbolach Apple: najpierw wycina w ikonie szczelinę szerszą od kreski (na osobnej
         * warstwie {@code warstwa}), potem rysuje samą kreskę, więc odcina się ona od bryły pod spodem.
         */
        private void przekresl(Canvas c, int kolor, int warstwa) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(4.6f);
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            c.drawLine(3.4f, 3.6f, 20.6f, 20.8f, p);
            p.setXfermode(null);
            c.restoreToCount(warstwa);
            p.setColor(kolor);
            p.setStrokeWidth(1.9f);
            c.drawLine(3.4f, 3.6f, 20.6f, 20.8f, p);
            p.setStrokeCap(Paint.Cap.BUTT);
        }
    }
}
