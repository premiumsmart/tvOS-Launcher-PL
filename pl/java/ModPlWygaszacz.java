package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.dreams.DreamService;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Mod PL: wygaszacz ekranu wzorowany na Apple TV (wariant B z kanwy, przezroczysty).
 *
 * Na ekranie jest losowe zdjęcie z internetu, duży szklany zegar, a nad nim data i temperatura. Zegar chowa
 * się za pierwszym planem zdjęcia: szukamy linii nieba (góry, drzewa, budynki), wszystko pod nią rysujemy
 * jeszcze raz NA zegarze i dobieramy wysokość zegara tak, żeby dół cyfr wszedł za tę linię, a żadna cyfra
 * nie znikła. Zdjęcia, na których się nie da (same chmury, morski horyzont, brak nieba), są pomijane.
 *
 * Źródła zdjęć: wyróżnione zdjęcia krajobrazów z Wikimedia Commons i zdjęcie dnia Bing (oba bez klucza).
 * Trafione zdjęcia zostają na dysku jako zapas; bez internetu i bez zapasu pokazują się sceny launchera.
 *
 * Algorytm linii nieba ma prototyp w Pythonie: kanwa-kafelki/narzedzia/linia_nieba.py (te same progi).
 * Plik źródłowy leży w pl/java/, a pl/naloz.py kopiuje go do src/ przy każdym buildzie.
 */
public class ModPlWygaszacz extends DreamService {
    private static final String TAG = "ModPlWygaszacz";
    private static final String PREFS = "mod_pl";
    private static final String K_CO_ILE = "wyg_co_ile", K_TEMP = "wyg_temperatura", K_TYLKO = "wyg_tylko_chowane",
            K_ODRZUCONE = "wyg_odrzucone", K_POPRZEDNI = "wyg_poprzedni", K_TEMATY = "wyg_tematy", K_PODPIS = "wyg_podpis", K_AUTO = "wyg_auto";
    private static final int[] CO_ILE = {1, 2, 5, 10, 15, 30};
    private static final String UA = "tvOS-Launcher-PL-wygaszacz/1.0 (Android TV screensaver)";
    private static final int MAKS_ZAPAS = 40;          // tyle trafionych zdjęć trzymamy na dysku
    private static final int PROB_NA_ZMIANE = 12;      // tylu kandydatów sprawdzamy przy jednej zmianie zdjęcia
                                                       // (12, bo wykrywacz aut odrzuca zdjęcia z zegarem na aucie)
    private static final long PRZEJSCIE_MS = 1400L;
    private static final float POWIEKSZENIE = 0.05f;   // zdjęcie powoli rośnie o 5% przez czas pokazywania

    /** Tematy zdjęć (bity w ustawieniu wyg_tematy). */
    static final int KRAJOBRAZY = 1, MIASTA = 2, SAMOCHODY = 4, CYBERPUNK = 8, WSZYSTKIE = 15;
    /** Domyślnie sportowe auta i miasta cyberpunk (wybór użytkownika 11.11.2026); krajobrazy i miasta są do włączenia w ustawieniach. */
    static final int DOMYSLNE = SAMOCHODY | CYBERPUNK;
    private static final String[] KAT_KRAJOBRAZY = {
        "Featured_pictures_of_landscapes", "Quality_images_of_landscapes", "Featured_pictures_of_mountains",
        "Quality_images_of_mountains", "Featured_pictures_of_lakes", "Featured_pictures_of_sunsets",
        "Featured_pictures_of_waterfalls", "Featured_pictures_of_forests", "Featured_pictures_of_coasts",
        "Featured_pictures_of_volcanoes"
    };
    private static final String[] KAT_MIASTA = {
        "Featured_pictures_of_cityscapes", "Featured_pictures_of_cityscapes", "Quality_images_of_cityscapes",
        "Featured_pictures_of_skylines", "Quality_images_of_skyscrapers", "Featured_pictures_of_bridges",
        "Featured_pictures_of_towers", "Featured_pictures_of_castles"
    };
    /**
     * Wykluczenia w stylu RS7 (użytkownik 12.11.2026: „cyberpunk nowoczesne”, „po tuningu i supercary, w tym RS i AMG”):
     * bez rysunków, pikseli, synthwave, gier i postaci z gier, bez starych aut, wnętrz i F1. Realistyczne grafiki są
     * dozwolone (RS7 też jest grafiką), a kategorię anime i tak pomija parametr categories=100. Wykluczenia w rodzaju
     * „-anime”, „-women” albo „-digital art” obcinały pulę wielokrotnie (np. „cyberpunk car”: 418 zdjęć -> 19).
     */
    private static final String BEZ_RYSUNKOW = " -\"anime girls\" -\"anime boys\" -manga -\"pixel art\" -\"vector art\""
            + " -cartoon -sketch -\"line art\" -\"low poly\" -\"video game characters\" -synthwave -retrowave -outrun"
            + " -\"retro wave\" -vaporwave -\"Forza Horizon\" -\"Forza Horizon 4\" -\"Forza Horizon 5\" -\"Need for Speed\"";
    /** Dla aut dodatkowo: gry wyścigowe, stare auta, wnętrza i bolidy F1. */
    private static final String BEZ_AUT = BEZ_RYSUNKOW + " -\"Forza Motorsport\" -\"Gran Turismo\" -\"Gran Turismo 7\""
            + " -\"Assetto Corsa\" -\"The Crew\" -\"Cyberpunk 2077\" -\"classic car\" -vintage -oldtimer -\"old car\""
            + " -\"muscle cars\" -interior -\"car interior\" -\"steering wheel\" -dashboard -\"Formula 1\"";
    /**
     * Sportowe auta w stylu RS7 do wyszukiwarki tapet Wallhaven: {zapytanie, podpis, „c” = tylko ciemne (nocne) zdjęcia
     * według kolorów dominujących}. Zapytania nocne i neonowe biorą wszystko, a zapytania marek i tuningu tylko ciemne
     * kadry (noc, ciemne studio), bo za dnia te auta nie pasują do stylu. Audi RS, Mercedes-AMG i Lamborghini częściej.
     * Tapety ekranu głównego biorą tę listę, wygaszacz tę i AUTA_TUNING.
     */
    private static final String[][] AUTA = {
        {"supercar night", null}, {"supercar night", null}, {"sports car night", null}, {"supercar neon", null},
        {"lamborghini night", "Lamborghini"}, {"lamborghini night", "Lamborghini"}, {"lamborghini neon", "Lamborghini"},
        {"porsche neon", "Porsche"}, {"amg night", "Mercedes-AMG"}, {"audi r8 night", "Audi R8"},
        {"nissan gt-r neon", "Nissan GT-R"}, {"jdm neon", null}, {"jdm night", null}, {"bugatti night", "Bugatti"},
        {"mercedes amg", "Mercedes-AMG", "c"}, {"mercedes amg", "Mercedes-AMG", "c"}, {"mercedes amg gt", "Mercedes-AMG GT", "c"},
        {"brabus", "Brabus", "c"}, {"audi rs", "Audi RS", "c"}, {"audi rs", "Audi RS", "c"}, {"audi r8", "Audi R8", "c"},
        {"audi rs6", "Audi RS6", "c"}, {"lamborghini", "Lamborghini", "c"}, {"lamborghini", "Lamborghini", "c"},
        {"lamborghini huracan", "Lamborghini Huracán", "c"}, {"lamborghini aventador", "Lamborghini Aventador", "c"},
        {"mclaren", "McLaren", "c"}, {"ferrari", "Ferrari", "c"}, {"porsche 911 gt3", "Porsche 911 GT3", "c"},
        {"nissan gt-r", "Nissan GT-R", "c"}, {"koenigsegg", "Koenigsegg", "c"}, {"pagani", "Pagani", "c"},
        {"bugatti", "Bugatti", "c"}, {"mansory", "Mansory", "c"}, {"supercar", null, "c"}, {"hypercar", null, "c"}
    };
    /**
     * Auta po przeróbkach w stylu youtubera Buddy (modele z jego garażu według artykułów Eski i Auto Świata), tylko na
     * wygaszaczu i tylko ciemne kadry: na wygaszaczu widać całe auto, a na ekranie głównym dół zasłaniają kafelki i dok.
     */
    private static final String[][] AUTA_TUNING = {
        {"lamborghini huracan sto", "Lamborghini Huracán", "c"}, {"liberty walk", "Liberty Walk", "c"},
        {"widebody", null, "c"}, {"tuned car", null, "c"}, {"mclaren 720s", "McLaren 720S", "c"},
        {"pagani huayra", "Pagani Huayra", "c"}, {"toyota supra", "Toyota Supra", "c"}, {"mazda rx-7", "Mazda RX-7", "c"},
        {"nissan silvia", "Nissan Silvia", "c"}, {"nissan skyline gt-r", "Nissan Skyline GT-R", "c"},
        {"mitsubishi lancer evolution", "Mitsubishi Lancer Evolution", "c"}, {"subaru wrx sti", "Subaru WRX STI", "c"}
    };
    /** Lista wygaszacza: wszystkie auta i dwa razy auta po przeróbkach. */
    private static final String[][] AUTA_WYGASZACZ = polacz(AUTA, AUTA_TUNING, AUTA_TUNING);

    private static String[][] polacz(String[][]... listy) {
        List<String[]> wynik = new ArrayList<String[]>();
        for (String[][] lista : listy) wynik.addAll(java.util.Arrays.asList(lista));
        return wynik.toArray(new String[0][]);
    }
    /**
     * Miasta: futurystyczne miasta nocą (wybór użytkownika z kanwy 12.11.2026, zestaw 5, ok. 190 zdjęć). Bez kadrów
     * z gry Cyberpunk 2077 z postaciami, które użytkownik uznał za dziwne.
     */
    private static final String[][] NEONY = {
        {"futuristic city night" + BEZ_RYSUNKOW + " -\"Cyberpunk 2077\"", null}
    };
    private static final String[] RYNKI = {"pl-PL", "en-US", "en-GB", "de-DE", "fr-FR", "ja-JP", "en-CA", "en-AU", "it-IT", "es-ES"};
    private static final String[][] SCENY = {
        {"backgrounds/scene-night.jpg", "backgrounds/scene-night-fg.webp"},
        {"backgrounds/scene-golden.jpg", "backgrounds/scene-golden-fg.webp"},
        {"backgrounds/scene-dawn.jpg", "backgrounds/scene-dawn-fg.webp"},
        {"backgrounds/scene-fog.jpg", "backgrounds/scene-fog-fg.webp"}
    };

    // ------------------------------------------------------------------ ustawienia (wołane z SettingsActivity)

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String skladnik(Context c) {
        return c.getPackageName() + "/" + ModPlWygaszacz.class.getName();
    }

    /** Czy systemowym wygaszaczem telewizora jest ten wygaszacz. */
    static boolean aktywny(Context c) {
        try {
            String s = Settings.Secure.getString(c.getContentResolver(), "screensaver_components");
            return s != null && s.contains("ModPlWygaszacz");
        } catch (Throwable e) {
            return false;
        }
    }

    /** Zapamiętuje dotychczasowy wygaszacz telewizora, żeby dało się do niego wrócić. */
    static void zapamietajPoprzedni(Context c) {
        try {
            String s = Settings.Secure.getString(c.getContentResolver(), "screensaver_components");
            if (s != null && !s.isEmpty() && !s.contains("ModPlWygaszacz")) prefs(c).edit().putString(K_POPRZEDNI, s).apply();
        } catch (Throwable ignored) {
        }
    }

    static String poprzedni(Context c) {
        return prefs(c).getString(K_POPRZEDNI, "");
    }

    /** Czy wygaszacz był już raz włączony automatycznie (potem szanujemy wybór użytkownika). */
    static boolean autoZrobione(Context c) {
        return prefs(c).getBoolean(K_AUTO, false);
    }

    /** Ustawia wygaszacz telewizora bez ADB. Działa tylko, gdy launcher ma zgodę WRITE_SECURE_SETTINGS. */
    static boolean ustawBezposrednio(Context c, String skladnik) {
        if (!LocalAdb.hasWriteSecureSettings(c)) return false;
        try {
            zapamietajPoprzedni(c);
            Settings.Secure.putString(c.getContentResolver(), "screensaver_components", skladnik);
            Settings.Secure.putInt(c.getContentResolver(), "screensaver_enabled", 1);
            return skladnik.contains("ModPlWygaszacz") == aktywny(c);
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Po instalacji: gdy launcher dostał zgodę WRITE_SECURE_SETTINGS (kreator w trybie ADB), sam ustawia się jako
     * wygaszacz telewizora. Robi to tylko raz, żeby nie nadpisywać późniejszego wyboru użytkownika.
     */
    static void wlaczJesliMozna(Context c) {
        try {
            if (autoZrobione(c)) return;
            if (aktywny(c) || ustawBezposrednio(c, skladnik(c))) prefs(c).edit().putBoolean(K_AUTO, true).apply();
        } catch (Throwable ignored) {
        }
    }

    static int coIle(Context c) {
        int m = prefs(c).getInt(K_CO_ILE, 5);
        for (int x : CO_ILE) if (x == m) return m;
        return 5;
    }

    static void nastepneCoIle(Context c) {
        int m = coIle(c), i = 0;
        for (int k = 0; k < CO_ILE.length; k++) if (CO_ILE[k] == m) i = k;
        prefs(c).edit().putInt(K_CO_ILE, CO_ILE[(i + 1) % CO_ILE.length]).apply();
    }

    static String opisCoIle(Context c) {
        int m = coIle(c);
        return I18n.t(c, m + (m == 1 ? " minute" : " minutes"));
    }

    static boolean temperatura(Context c) {
        return prefs(c).getBoolean(K_TEMP, true);
    }

    static void przelaczTemperature(Context c) {
        prefs(c).edit().putBoolean(K_TEMP, !temperatura(c)).apply();
    }

    static boolean tylkoChowane(Context c) {
        return prefs(c).getBoolean(K_TYLKO, true);
    }

    static void przelaczTylkoChowane(Context c) {
        prefs(c).edit().putBoolean(K_TYLKO, !tylkoChowane(c)).apply();
    }


    static boolean temat(Context c, int bit) {
        return (prefs(c).getInt(K_TEMATY, DOMYSLNE) & bit) != 0;
    }

    /** Włącza albo wyłącza temat zdjęć; ostatniego włączonego nie da się wyłączyć. */
    static void przelaczTemat(Context c, int bit) {
        int tematy = (prefs(c).getInt(K_TEMATY, DOMYSLNE) ^ bit) & WSZYSTKIE;
        if (tematy != 0) prefs(c).edit().putInt(K_TEMATY, tematy).apply();
    }

    static boolean podpis(Context c) {
        return prefs(c).getBoolean(K_PODPIS, true);
    }

    static void przelaczPodpis(Context c) {
        prefs(c).edit().putBoolean(K_PODPIS, !podpis(c)).apply();
    }

    // ------------------------------------------------------------------ tapety ekranu głównego (te same źródła co wygaszacz)

    private static final String K_TAPETY = "tap_tematy_";
    private static final int TAPET_Z_TEMATU = 12;      // tyle adresów z każdego włączonego tematu na jedną talię

    static boolean tematTapet(Context c, String zakres, int bit) {
        return (prefs(c).getInt(K_TAPETY + zakres, DOMYSLNE) & bit) != 0;
    }

    /** Włącza albo wyłącza temat tapet; ostatniego włączonego nie da się wyłączyć. */
    static void przelaczTematTapet(Context c, String zakres, int bit) {
        int tematy = (prefs(c).getInt(K_TAPETY + zakres, DOMYSLNE) ^ bit) & WSZYSTKIE;
        if (tematy != 0) prefs(c).edit().putInt(K_TAPETY + zakres, tematy).apply();
    }

    /**
     * Adresy tapet do silnika „tapet z internetu” launchera (CustomMedia, wątek w tle): po kilkanaście z każdego
     * włączonego tematu, wymieszane. Pusta lista, gdy brak sieci; launcher sięga wtedy po swoje zapasowe zdjęcia.
     */
    static List<String> adresyTapet(Context c, String zakres) {
        List<String> adresy = new ArrayList<String>();
        Random los = new Random();
        int tematy = prefs(c).getInt(K_TAPETY + zakres, DOMYSLNE) & WSZYSTKIE;
        if (tematy == 0) tematy = DOMYSLNE;
        for (int bit : new int[]{KRAJOBRAZY, MIASTA, SAMOCHODY, CYBERPUNK}) {
            if ((tematy & bit) == 0) continue;
            try {
                List<String[]> lista = bit == SAMOCHODY ? zWallhaven(los, AUTA, "s")
                        : bit == CYBERPUNK ? zWallhaven(los, NEONY, "p")
                        : bit == KRAJOBRAZY && los.nextInt(10) >= 7 ? zBinga(los)
                        : zCommons(los, bit == MIASTA ? KAT_MIASTA : KAT_KRAJOBRAZY, "x");
                Collections.shuffle(lista, los);
                for (int i = 0; i < lista.size() && i < TAPET_Z_TEMATU; i++) adresy.add(lista.get(i)[1]);
            } catch (Throwable e) {
                ModPlDziennik.i(TAG, "tapety, temat " + bit + ": " + e);
            }
        }
        Collections.shuffle(adresy, los);
        return adresy;
    }

    /** Uruchamia wygaszacz od razu (działa, gdy jest ustawiony jako wygaszacz telewizora). */
    static void podglad(Activity a) {
        if (a == null || a.isFinishing()) return;
        if (!aktywny(a)) {
            Toast.makeText(a, I18n.t(a, "Turn the screen saver on first."), Toast.LENGTH_LONG).show();
            return;
        }
        try {
            a.startActivity(new Intent(Intent.ACTION_MAIN)
                    .setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable e) {
            Toast.makeText(a, I18n.t(a, "The preview is not available on this TV. The screen saver starts by itself when the TV is idle."),
                    Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------ usługa

    private Widok widok;
    private Uklad uklad;
    private Uklad ukladKrawedzi;         // ten sam zegar z ostrożniejszymi limitami dla drugiej reguły
    private int zdjW, zdjH;
    private volatile boolean dziala;
    private Thread watek;
    private final Handler glowny = new Handler(Looper.getMainLooper());
    private final Set<String> odrzucone = new HashSet<String>();
    private final List<String[]> kandydaci = new ArrayList<String[]>();
    private String plikBiezacy = "";

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        int w = Math.max(dm.widthPixels, dm.heightPixels), h = Math.min(dm.widthPixels, dm.heightPixels);
        uklad = new Uklad(this, w, h);
        // druga reguła (krawędź pod zegarem) jest mniej pewna niż linia nieba, więc chowa ostrożniej
        ukladKrawedzi = new Uklad(this, w, h);
        ukladKrawedzi.maksCyfry = 0.28f;
        ukladKrawedzi.maksRazem = 0.14f;
        zdjW = Math.min(w, 1920);
        zdjH = Math.round(zdjW * (h / (float) w));
        widok = new Widok(this);
        setContentView(widok);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        dziala = true;
        Set<String> zapisane = prefs(this).getStringSet(K_ODRZUCONE, null);
        if (zapisane != null) odrzucone.addAll(zapisane);
        Weather.refresh(this, false, null);
        watek = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    petla();
                } catch (Throwable e) {
                    ModPlDziennik.w(TAG, "pętla przerwana", e);
                }
            }
        }, "mod-pl-wygaszacz");
        watek.setPriority(Thread.MIN_PRIORITY + 1);
        watek.start();
        if (widok != null) widok.invalidate();
    }

    @Override
    public void onDreamingStopped() {
        dziala = false;
        if (watek != null) watek.interrupt();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        dziala = false;
        if (widok != null) widok.zwolnij();
        super.onDetachedFromWindow();
    }

    private void petla() {
        Random los = new Random();
        Slajd pierwszy = zZapasu(los);
        boolean pusto = pierwszy == null;
        if (pierwszy == null) pierwszy = zeSceny(los);
        pokaz(pierwszy);
        // bez zapasu albo przy małym zapasie pierwsze nowe zdjęcie z internetu przychodzi od razu (użytkownik 12.11.2026:
        // po zmianie filtrów start wygaszacza pokazywał w kółko te same zdjęcia)
        long czekaj = pusto ? 12000L : ileWZapasie() < MALY_ZAPAS ? 15000L : coIle(this) * 60000L;
        while (dziala) {
            if (!spijIDociagaj(czekaj, los)) return;
            Slajd s = zInternetu(los, true);
            if (s == null) s = zZapasu(los);
            if (s == null) s = zeSceny(los);
            pokaz(s);
            if (Weather.stale(this)) Weather.refresh(this, false, null);
            czekaj = coIle(this) * 60000L;
        }
    }

    private boolean spij(long ms) {
        long koniec = SystemClock.uptimeMillis() + ms;
        while (dziala && SystemClock.uptimeMillis() < koniec) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return dziala;
    }

    private void pokaz(final Slajd s) {
        if (s == null) return;
        glowny.post(new Runnable() {
            @Override
            public void run() {
                if (widok != null && dziala) widok.ustaw(s);
                else s.zwolnij();
            }
        });
    }

    // ------------------------------------------------------------------ skąd zdjęcia

    private File katalog() {
        File k = new File(getFilesDir(), "wygaszacz10");
        if (!k.isDirectory()) {
            k.mkdirs();
            for (String nazwa : new String[]{"wygaszacz", "wygaszacz2", "wygaszacz3", "wygaszacz4", "wygaszacz5", "wygaszacz6", "wygaszacz7", "wygaszacz8", "wygaszacz9"}) {     // zapasy z wersji próbnych (inne źródła zdjęć)
                File stary = new File(getFilesDir(), nazwa);
                File[] stare = stary.listFiles();
                if (stare != null) for (File f : stare) f.delete();
                stary.delete();
            }
        }
        return k;
    }

    private static String skrot(String id) {
        return Integer.toHexString(id.hashCode());
    }

    /** Nowe zdjęcie z internetu, na którym zegar da się schować. Null, gdy brak sieci albo nic nie pasowało. */
    /** Co tyle przy małym zapasie wygaszacz dociąga w tle jedno zdjęcie do zapasu. */
    private static final long DOCIAGANIE_MS = 30000L;

    /**
     * Śpi „ms”, a gdy zapas jest mały, co 30 s dokłada w tle jedno zdjęcie do zapasu (bez pokazywania), żeby szybko
     * było z czego losować (użytkownik 12.11.2026: „większość z wygaszacza się powtarza”).
     */
    private boolean spijIDociagaj(long ms, Random los) {
        long koniec = SystemClock.uptimeMillis() + ms;
        while (dziala) {
            long zostalo = koniec - SystemClock.uptimeMillis();
            if (zostalo <= 0) return true;
            if (!spij(Math.min(zostalo, DOCIAGANIE_MS))) return false;
            if (koniec - SystemClock.uptimeMillis() > 5000L && ileWZapasie() < MALY_ZAPAS) {
                Slajd s = zInternetu(los, false);
                if (s != null) s.zwolnij();
            }
        }
        return false;
    }

    /** Nowe zdjęcie z internetu (zapisane w zapasie); pokazane = false: tylko dociągnięte, nie liczy się jako pokazane. */
    private Slajd zInternetu(Random los, boolean pokazane) {
        for (int proba = 0; proba < PROB_NA_ZMIANE && dziala; proba++) {
            if (kandydaci.isEmpty()) {
                try {
                    kandydaci.addAll(nowiKandydaci(los));
                } catch (Throwable e) {
                    ModPlDziennik.i(TAG, "lista zdjęć niedostępna: " + e);
                }
                if (kandydaci.isEmpty() && temat(this, KRAJOBRAZY)) {
                    try {
                        kandydaci.addAll(zBinga(los));
                    } catch (Throwable e) {
                        return null;
                    }
                }
                if (kandydaci.isEmpty()) return null;
                Collections.shuffle(kandydaci, los);
                while (kandydaci.size() > 6) kandydaci.remove(kandydaci.size() - 1);   // krótka kolejka: tematy się przeplatają
            }
            String[] k = kandydaci.remove(kandydaci.size() - 1);
            String znak = k[3] + "_" + skrot(k[0]);     // pierwsza litera nazwy pliku = temat
            if (odrzucone.contains(znak) || new File(katalog(), znak + ".jpg").exists()) continue;
            Bitmap ekranowe = null;
            try {
                byte[] dane = pobierz(k[1], 9000000);
                Bitmap surowe = dekoduj(dane, zdjW, zdjH);
                if (surowe == null) throw new IllegalStateException("nie obraz");
                ekranowe = doEkranu(surowe, zdjW, zdjH);
                if (surowe != ekranowe) surowe.recycle();
            } catch (Throwable e) {
                ModPlDziennik.i(TAG, "pobranie nieudane: " + e);
                continue;
            }
            if (("s".equals(k[3]) || "p".equals(k[3])) && !nocne(ekranowe)) {
                // auta i miasta są w stylu RS7 (nocą, w neonach): zdjęcia za dnia i o zmierzchu odpadają
                ModPlDziennik.i(TAG, k[0] + ": za jasne, nie nocne");
                ekranowe.recycle();
                odrzuc(znak);
                continue;
            }
            Slajd s = zbuduj(ekranowe, null, k[0], k[2]);
            if (s == null) {
                ekranowe.recycle();
                odrzuc(znak);
                continue;
            }
            zapiszZapas(ekranowe, znak, k[2]);
            if (pokazane) {
                plikBiezacy = znak + ".jpg";
                zapamietaj(plikBiezacy);
            }
            return s;
        }
        return null;
    }

    private void odrzuc(String znak) {
        if (odrzucone.size() > 800) odrzucone.clear();
        odrzucone.add(znak);
        prefs(this).edit().putStringSet(K_ODRZUCONE, new HashSet<String>(odrzucone)).apply();
    }

    /** Lista kandydatów z losowego włączonego tematu. */
    private List<String[]> nowiKandydaci(Random los) throws Exception {
        int tematy = prefs(this).getInt(K_TEMATY, DOMYSLNE) & WSZYSTKIE;
        if (tematy == 0) tematy = DOMYSLNE;
        List<Integer> wlaczone = new ArrayList<Integer>();
        for (int bit : new int[]{KRAJOBRAZY, MIASTA, SAMOCHODY, CYBERPUNK}) if ((tematy & bit) != 0) wlaczone.add(bit);
        int temat = wlaczone.get(los.nextInt(wlaczone.size()));
        if (temat == KRAJOBRAZY && los.nextInt(10) >= 7) return zBinga(los);
        if (temat == SAMOCHODY) return zWallhaven(los, AUTA_WYGASZACZ, "s");
        if (temat == CYBERPUNK) return zWallhaven(los, NEONY, "p");
        return zCommons(los, temat == MIASTA ? KAT_MIASTA : KAT_KRAJOBRAZY, temat == MIASTA ? "m" : "k");
    }

    private void zapiszZapas(Bitmap b, String znak, String podpis) {
        try {
            File k = katalog();
            FileOutputStream out = new FileOutputStream(new File(k, znak + ".jpg"));
            try {
                b.compress(Bitmap.CompressFormat.JPEG, 90, out);
            } finally {
                out.close();
            }
            if (podpis != null && !podpis.isEmpty()) {
                FileOutputStream opis = new FileOutputStream(new File(k, znak + ".txt"));
                try {
                    opis.write(podpis.getBytes(StandardCharsets.UTF_8));
                } finally {
                    opis.close();
                }
            }
            File[] pliki = k.listFiles((d, n) -> n.endsWith(".jpg"));
            if (pliki != null && pliki.length > MAKS_ZAPAS) {
                Arrays.sort(pliki, (x, y) -> Long.compare(x.lastModified(), y.lastModified()));
                for (int i = 0; i < pliki.length - MAKS_ZAPAS; i++) usunZapas(pliki[i]);
            }
        } catch (Throwable e) {
            ModPlDziennik.i(TAG, "zapas: " + e);
        }
    }

    /** Czy plik zapasu należy do włączonego tematu (pierwsza litera nazwy: k, m albo s). */
    private static boolean zTematu(String nazwa, int tematy) {
        char c = nazwa.isEmpty() ? ' ' : nazwa.charAt(0);
        int bit = c == 'k' ? KRAJOBRAZY : c == 'm' ? MIASTA : c == 's' ? SAMOCHODY : c == 'p' ? CYBERPUNK : 0;
        return bit == 0 || tematy == 0 || (tematy & bit) != 0;
    }

    private static void usunZapas(File zdjecie) {
        zdjecie.delete();
        new File(zdjecie.getPath().replace(".jpg", ".txt")).delete();
    }

    private static String podpisZapasu(File zdjecie) {
        File f = new File(zdjecie.getPath().replace(".jpg", ".txt"));
        if (!f.isFile() || f.length() > 400) return null;
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            try {
                byte[] buf = new byte[(int) f.length()];
                int n = in.read(buf);
                return n > 0 ? new String(buf, 0, n, StandardCharsets.UTF_8) : null;
            } finally {
                in.close();
            }
        } catch (Throwable e) {
            return null;
        }
    }

    /** Z nazwy pliku albo opisu robi krótki podpis; null, gdy nazwa nic nie mówi (np. "DSC 0123"). */
    private static String oczysc(String tytul) {
        if (tytul == null) return null;
        String s = tytul.startsWith("File:") ? tytul.substring(5) : tytul;
        int kropka = s.lastIndexOf('.');
        if (kropka > 0 && s.length() - kropka <= 5) s = s.substring(0, kropka);
        int prawa = s.indexOf(" (©");
        if (prawa > 0) s = s.substring(0, prawa);
        s = s.replace('_', ' ').replaceAll("\\s*\\(\\d+\\)$", "").replaceAll("\\s+", " ").trim();
        if (s.length() < 4) return null;
        int cyfry = 0;
        for (int i = 0; i < s.length(); i++) if (Character.isDigit(s.charAt(i))) cyfry++;
        if (cyfry * 2 > s.length()) return null;
        if (s.matches("(?i)^(img|dsc|dscn|dscf|pict|image|p\\d)[ _-]?\\d.*")) return null;
        if (s.length() > 64) s = s.substring(0, 63).trim() + "…";
        return s;
    }

    /** Ostatnio pokazane zdjęcia (nazwy plików z zapasu, od najstarszego), pamiętane także między startami wygaszacza. */
    private static final String K_OSTATNIE = "wyg_ostatnie";
    private static final int PAMIETANE = 12;
    /** Przy mniejszym zapasie pierwsze nowe zdjęcie z internetu przychodzi po 15 s, a nie po kilku minutach. */
    private static final int MALY_ZAPAS = 12;

    private List<String> ostatnie() {
        List<String> lista = new ArrayList<String>();
        for (String n : prefs(this).getString(K_OSTATNIE, "").split("\\|")) {
            if (!n.isEmpty()) lista.add(n);
        }
        return lista;
    }

    private void zapamietaj(String nazwa) {
        List<String> lista = ostatnie();
        lista.remove(nazwa);
        lista.add(nazwa);
        while (lista.size() > PAMIETANE) lista.remove(0);
        StringBuilder sb = new StringBuilder();
        for (String n : lista) {
            if (sb.length() > 0) sb.append('|');
            sb.append(n);
        }
        prefs(this).edit().putString(K_OSTATNIE, sb.toString()).apply();
    }

    /** Ile zdjęć włączonych tematów jest w zapasie. */
    private int ileWZapasie() {
        final int tematy = prefs(this).getInt(K_TEMATY, DOMYSLNE) & WSZYSTKIE;
        File[] pliki = katalog().listFiles((d, n) -> n.endsWith(".jpg") && zTematu(n, tematy));
        return pliki == null ? 0 : pliki.length;
    }

    /**
     * Zdjęcie z zapasu na dysku: najpierw takie, których ostatnio nie było (losowo), potem pokazane najdawniej.
     * Użytkownik 12.11.2026: „czy wygaszacz, jak się włącza, często tę samą tapetę pokazuje?”.
     */
    private Slajd zZapasu(Random los) {
        final int tematy = prefs(this).getInt(K_TEMATY, DOMYSLNE) & WSZYSTKIE;
        File[] pliki = katalog().listFiles((d, n) -> n.endsWith(".jpg") && zTematu(n, tematy));
        if (pliki == null || pliki.length == 0) return null;
        List<File> lista = new ArrayList<File>(Arrays.asList(pliki));
        Collections.shuffle(lista, los);
        final List<String> ostatnio = ostatnie();      // -1 (nie było) przed pokazanymi; wśród pokazanych najstarsze pierwsze
        Collections.sort(lista, (a, b) -> Integer.compare(ostatnio.indexOf(a.getName()), ostatnio.indexOf(b.getName())));
        int proby = 0;
        for (File f : lista) {
            if (!dziala || proby++ >= 5) break;
            if (f.getName().equals(plikBiezacy) && lista.size() > 1) continue;
            Bitmap surowe = BitmapFactory.decodeFile(f.getPath());
            if (surowe == null) {
                usunZapas(f);
                continue;
            }
            Bitmap ekranowe = doEkranu(surowe, zdjW, zdjH);
            if (surowe != ekranowe) surowe.recycle();
            if ((f.getName().startsWith("s_") || f.getName().startsWith("p_")) && !nocne(ekranowe)) {
                // auta i miasta zapisane, zanim doszło sito nocnych zdjęć
                ekranowe.recycle();
                usunZapas(f);
                continue;
            }
            Slajd s = zbuduj(ekranowe, null, "zapas " + f.getName(), podpisZapasu(f));
            if (s == null) {
                ekranowe.recycle();
                usunZapas(f);
                continue;
            }
            plikBiezacy = f.getName();
            zapamietaj(plikBiezacy);
            return s;
        }
        return null;
    }

    /** Scena launchera z ręcznie wyciętym pierwszym planem: gdy nie ma sieci ani zapasu. */
    private Slajd zeSceny(Random los) {
        String[] scena = SCENY[los.nextInt(SCENY.length)];
        try {
            Bitmap tlo = zAssetow(scena[0]), przod = zAssetow(scena[1]);
            if (tlo == null) return null;
            Bitmap ekranowe = doEkranu(tlo, zdjW, zdjH);
            if (tlo != ekranowe) tlo.recycle();
            byte[] maska = null;
            if (przod != null) {
                Bitmap m = Bitmap.createScaledBitmap(przod, AW, AH, true);
                int[] px = new int[AW * AH];
                m.getPixels(px, 0, AW, 0, 0, AW, AH);
                maska = new byte[AW * AH];
                for (int i = 0; i < px.length; i++) maska[i] = (byte) ((px[i] >>> 24) > 127 ? 1 : 0);
                if (m != przod) m.recycle();
                przod.recycle();
            }
            plikBiezacy = "";
            Slajd s = zbuduj(ekranowe, maska, "scena " + scena[0], null);
            if (s == null) ekranowe.recycle();
            return s;
        } catch (Throwable e) {
            ModPlDziennik.i(TAG, "scena: " + e);
            return null;
        }
    }

    private Bitmap zAssetow(String sciezka) {
        try {
            InputStream in = getAssets().open(sciezka);
            try {
                return BitmapFactory.decodeStream(in);
            } finally {
                in.close();
            }
        } catch (Throwable e) {
            return null;
        }
    }

    private static List<String[]> zCommons(Random los, String[] kategorie, String litera) throws Exception {
        List<String[]> lista = new ArrayList<String[]>();
        String kat = kategorie[los.nextInt(kategorie.length)];
        int rok = Calendar.getInstance().get(Calendar.YEAR) - 6 + los.nextInt(7);     // tylko zdjęcia dodane w ostatnich latach
        String start = String.format(Locale.US, "%04d-%02d-01T00:00:00Z", rok, 1 + los.nextInt(12));
        for (String kierunek : new String[]{"asc", "desc"}) {
            String adres = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=categorymembers"
                    + "&gcmtitle=Category:" + kat + "&gcmtype=file&gcmlimit=30&gcmsort=timestamp&gcmdir=" + kierunek
                    + "&gcmstart=" + start + "&prop=imageinfo&iiprop=url%7Csize%7Cmime&iiurlwidth=1920";
            JSONObject korzen = new JSONObject(new String(pobierz(adres, 2000000), StandardCharsets.UTF_8));
            JSONObject zapytanie = korzen.optJSONObject("query");
            JSONObject strony = zapytanie == null ? null : zapytanie.optJSONObject("pages");
            if (strony == null) continue;
            for (Iterator<String> it = strony.keys(); it.hasNext(); ) {
                String klucz = it.next();
                JSONObject strona = strony.optJSONObject(klucz);
                JSONArray ii = strona == null ? null : strona.optJSONArray("imageinfo");
                JSONObject info = ii == null ? null : ii.optJSONObject(0);
                if (info == null || !"image/jpeg".equals(info.optString("mime"))) continue;
                int w = info.optInt("width"), h = info.optInt("height");
                String miniatura = info.optString("thumburl", "");
                if (w < 1900 || h <= 0 || miniatura.isEmpty()) continue;
                float proporcje = w / (float) h;
                if (proporcje < 1.45f || proporcje > 2.05f) continue;     // bez pionowych i bardzo szerokich panoram
                lista.add(new String[]{"c" + klucz, miniatura, oczysc(strona.optString("title", "")), litera});
            }
            if (!lista.isEmpty()) break;
        }
        return lista;
    }

    /** Sportowe auta i miasta cyberpunk: najbardziej lubiane tapety z wyszukiwarki Wallhaven (kategoria ogólna, treści bezpieczne). */
    /**
     * Czy zdjęcie jest ciemne (nocne): dwa główne kolory dominujące z wyników Wallhaven mają jasność poniżej 70 na 255.
     * Dla zapytań marek i tuningu z flagą „c” (styl RS7: auta nocą albo w ciemnym studiu, nie za dnia).
     */
    private static boolean ciemne(JSONArray kolory) {
        if (kolory == null || kolory.length() < 2) return false;
        for (int i = 0; i < 2; i++) {
            String k = kolory.optString(i, "");
            if (k.length() != 7 || k.charAt(0) != '#') return false;
            try {
                int rgb = Integer.parseInt(k.substring(1), 16);
                double jasnosc = 0.299 * ((rgb >> 16) & 255) + 0.587 * ((rgb >> 8) & 255) + 0.114 * (rgb & 255);
                if (jasnosc >= 70) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * Czy zdjęcie wygląda na nocne: średnia jasność całości poniżej 75, a górnej jednej trzeciej (niebo) poniżej 90.
     * Drugie sito po kolorach dominujących z Wallhaven, które przepuszczały np. auto przy szarym budynku za dnia albo
     * na górskiej drodze o zmierzchu (sprawdzone 12.11.2026 na zrzutach: odpadły 84/136 i 90/97, zostały 59/81 i 21/6).
     */
    static boolean nocne(Bitmap b) {
        if (b == null || b.isRecycled()) return false;
        Bitmap m = Bitmap.createScaledBitmap(b, 64, 36, true);
        int[] px = new int[64 * 36];
        m.getPixels(px, 0, 64, 0, 0, 64, 36);
        if (m != b) m.recycle();
        double suma = 0, gora = 0;
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            double j = 0.299 * ((p >> 16) & 255) + 0.587 * ((p >> 8) & 255) + 0.114 * (p & 255);
            suma += j;
            if (i < 64 * 12) gora += j;
        }
        return suma / px.length < 75 && gora / (64 * 12) < 90;
    }

    private static List<String[]> zWallhaven(Random los, String[][] zapytania, String litera) throws Exception {
        List<String[]> lista = new ArrayList<String[]>();
        // do 3 losowych zapytań: przy filtrze ciemnych zdjęć wylosowana strona bywa pusta
        for (int proba = 0; proba < 3 && lista.isEmpty(); proba++) {
            String[] auto = zapytania[los.nextInt(zapytania.length)];
            boolean tylkoCiemne = auto.length > 2 && "c".equals(auto[2]);
            // strony 1-8 (do ok. 190 najlepszych zdjęć zapytania); gdy wylosowana jest pusta, strona 1
            for (int strona : new int[]{1 + los.nextInt(8), 1}) {
                String adres = "https://wallhaven.cc/api/v1/search?q=" + java.net.URLEncoder.encode(auto[0] + (zapytania != NEONY ? BEZ_AUT : ""), "UTF-8")
                        + "&categories=100&purity=100&atleast=1920x1080&ratios=16x9%2C16x10&sorting=favorites&order=desc&page=" + strona;
                JSONObject korzen = new JSONObject(new String(pobierz(adres, 1000000), StandardCharsets.UTF_8));
                JSONArray dane = korzen.optJSONArray("data");
                if (dane == null) continue;
                for (int i = 0; i < dane.length(); i++) {
                    JSONObject o = dane.getJSONObject(i);
                    String id = o.optString("id", ""), plik = o.optString("path", ""), typ = o.optString("file_type", "");
                    if (id.isEmpty() || plik.isEmpty() || o.optLong("file_size") > 8000000L) continue;
                    if (!"image/jpeg".equals(typ) && !"image/png".equals(typ)) continue;
                    if (tylkoCiemne && !ciemne(o.optJSONArray("colors"))) continue;
                    lista.add(new String[]{"w" + id, plik, auto[1], litera});
                }
                if (!lista.isEmpty()) break;
            }
        }
        return lista;
    }

    private static List<String[]> zBinga(Random los) throws Exception {
        List<String[]> lista = new ArrayList<String[]>();
        String adres = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=" + (los.nextBoolean() ? 0 : 8)
                + "&n=8&mkt=" + RYNKI[los.nextInt(RYNKI.length)];
        JSONObject korzen = new JSONObject(new String(pobierz(adres, 500000), StandardCharsets.UTF_8));
        JSONArray obrazy = korzen.optJSONArray("images");
        if (obrazy == null) return lista;
        for (int i = 0; i < obrazy.length(); i++) {
            JSONObject obraz = obrazy.getJSONObject(i);
            String baza = obraz.optString("urlbase", "");
            if (!baza.isEmpty()) {
                lista.add(new String[]{"b" + baza, "https://www.bing.com" + baza + "_1920x1080.jpg", oczysc(obraz.optString("copyright", "")), "k"});
            }
        }
        return lista;
    }

    private static byte[] pobierz(String adres, int maks) throws Exception {
        HttpURLConnection pol = (HttpURLConnection) new URL(adres).openConnection();
        try {
            pol.setInstanceFollowRedirects(true);
            pol.setConnectTimeout(8000);
            pol.setReadTimeout(15000);
            pol.setRequestProperty("User-Agent", UA);
            if (pol.getResponseCode() != 200) throw new IllegalStateException("HTTP " + pol.getResponseCode());
            InputStream in = pol.getInputStream();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > maks) throw new IllegalStateException("za duży plik");
                }
                return out.toByteArray();
            } finally {
                in.close();
            }
        } finally {
            pol.disconnect();
        }
    }

    private static Bitmap dekoduj(byte[] dane, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(dane, 0, dane.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int probka = 1;
        while (o.outWidth / (probka * 2) >= w && o.outHeight / (probka * 2) >= h) probka *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = probka;
        return BitmapFactory.decodeByteArray(dane, 0, dane.length, o);
    }

    /** Skaluje i przycina zdjęcie do rozmiaru ekranu (wypełnienie całego kadru). */
    private static Bitmap doEkranu(Bitmap src, int w, int h) {
        if (src.getWidth() == w && src.getHeight() == h && src.getConfig() == Bitmap.Config.ARGB_8888) return src;
        float s = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        Bitmap wynik = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Matrix m = new Matrix();
        m.setScale(s, s);
        m.postTranslate((w - src.getWidth() * s) / 2f, (h - src.getHeight() * s) / 2f);
        new Canvas(wynik).drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        return wynik;
    }

    // ------------------------------------------------------------------ slajd i układ zegara

    private static final class Slajd {
        Bitmap zdjecie, maska, szklo;
        float gora;              // górny brzeg cyfr jako ułamek wysokości ekranu
        boolean ciemne;          // jasne tło za zegarem: cyfry ciemne
        String podpis;           // co jest na zdjęciu (pokazywane przez kilka sekund)
        Shader cieniowanieZdjecia, cieniowanieSzkla, mlecznaAutora, obwodkaAutora;

        void zwolnij() {
            if (zdjecie != null && !zdjecie.isRecycled()) zdjecie.recycle();
            if (maska != null && !maska.isRecycled()) maska.recycle();
            if (szklo != null && !szklo.isRecycled()) szklo.recycle();
            zdjecie = maska = szklo = null;
        }
    }

    /** Położenie cyfr zegara na ekranie: potrzebne i do rysowania, i do sprawdzania, ile zakrywa pierwszy plan. */
    static final class Uklad {
        final int w, h;
        final float rozmiar, wysCyfr, podniesienie;
        final float tMin, tMax, tDomyslne;     // dozwolony górny brzeg cyfr jako ułamek wysokości ekranu
        float maksCyfry = 0.34f, maksDwukropka = 0.22f;   // najwyżej tyle jednej cyfry i dwukropka może zniknąć
        float maksRazem = 0f;                  // najwyżej tyle wszystkich cyfr naraz; 0 = domyślnie (22%, płaska linia 14%)
        float dosc = 1f, karaZaDol = 0f;       // ocena położenia: schowane liczy się do „dosc”, zejście zegara w dół kosztuje
        float czystaGora = 1f;                 // najwyżej tyle górnych 60% cyfry może zasłaniać pierwszy plan (1 = bez wymogu)
        float minRazem = 0.03f;                // co najmniej tyle wszystkich cyfr ma zniknąć, żeby efekt było widać
        int minCyfr = 0;                       // co najmniej tyle cyfr ma być wyraźnie przyciętych (każda o progCyfry)
        float progCyfry = 0.04f;
        final float[] x0 = new float[4], x1 = new float[4];
        final float dx0, dx1;

        Uklad(Context c, int w, int h) {
            this.w = w;
            this.h = h;
            rozmiar = h * 330f / 1080f;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setTypeface(Ui.font(c, "clock_rounded"));
            p.setTextSize(rozmiar);
            p.setLetterSpacing(-0.012f);
            Rect b = new Rect();
            p.getTextBounds("0", 0, 1, b);
            wysCyfr = b.height();
            float sz = p.measureText("0"), sd = p.measureText(":");
            float lewo = (w - (4 * sz + sd)) / 2f, m = sz * 0.08f;
            x0[0] = lewo + m;
            x1[0] = lewo + sz - m;
            x0[1] = lewo + sz + m;
            x1[1] = lewo + 2 * sz - m;
            dx0 = lewo + 2 * sz;
            dx1 = dx0 + sd;
            x0[2] = dx1 + m;
            x1[2] = dx1 + sz - m;
            x0[3] = dx1 + sz + m;
            x1[3] = dx1 + 2 * sz - m;
            podniesienie = rozmiar * 0.121f;      // dwukropek rysujemy wyżej, żeby dolna kropka nie ginęła
            tMin = T_MIN;
            tMax = T_MAX;
            tDomyslne = T_DOMYSLNE;
        }

        /** Układ dla zegara ekranu głównego: znane są tylko skrajne x cyfr, ich wysokość i zakres położenia. */
        Uklad(int w, int h, float lewo, float prawo, float wysCyfr, float tMin, float tMax, float tDomyslne) {
            this.w = w;
            this.h = h;
            this.wysCyfr = wysCyfr;
            this.tMin = tMin;
            this.tMax = tMax;
            this.tDomyslne = tDomyslne;
            rozmiar = wysCyfr / 0.72f;
            podniesienie = 0f;
            float sz = (prawo - lewo) / 4.42f, sd = sz * 0.42f, m = sz * 0.08f;   // cztery cyfry i węższy dwukropek
            x0[0] = lewo + m;
            x1[0] = lewo + sz - m;
            x0[1] = lewo + sz + m;
            x1[1] = lewo + 2 * sz - m;
            dx0 = lewo + 2 * sz;
            dx1 = dx0 + sd;
            x0[2] = dx1 + m;
            x1[2] = dx1 + sz - m;
            x0[3] = dx1 + sz + m;
            x1[3] = dx1 + 2 * sz - m;
        }
    }

    private Slajd zbuduj(Bitmap ekranowe, byte[] gotowaMaska, String skad, String podpis) {
        long t0 = SystemClock.uptimeMillis();
        Baza bz = baza(ekranowe, gotowaMaska);
        // Wykrywacz aut jak na ekranie głównym (12.11.2026: w wygaszaczu zegar stanął na środku Ferrari, dół „4” za
        // oponą). Zegar ma stać nad autem i chować się tylko za dachem; sceny z wyciętym pierwszym planem pomijamy.
        List<ModPlDetektor.Pojazd> pojazdy = gotowaMaska == null ? ModPlDetektor.pojazdy(this, ekranowe) : null;
        float[] g = {uklad.x0[0] / uklad.w, uklad.x1[3] / uklad.w, uklad.wysCyfr / uklad.h, uklad.tDomyslne};
        ModPlDetektor.Pojazd auto = ModPlTapety.autoPodZegarem(pojazdy, g);
        Analiza a = umiesc(bz, uklad);
        if (a.efekt && ModPlTapety.naAucie(pojazdy, g, a.gora)) {
            String opis = a.opis + ", zegar leżałby na aucie";
            a = bezEfektu(bz, uklad);
            a.opis = opis;
        }
        if (!a.efekt && gotowaMaska == null) {
            // linia nieba nie wystarczyła (np. za cyframi stoją drzewa): krawędź tuż pod zegarem, np. dach auta;
            // gdy pod zegarem stoi auto, szukamy jej tylko przy dachu, nie niżej przy szybach, kołach i masce
            Analiza druga = podZegarem(ekranowe, ukladKrawedzi != null ? ukladKrawedzi : uklad, bz.jasnosc,
                    auto != null ? auto.gora + ModPlTapety.ZAPAS_DACHU : 0f);
            if (druga != null && ModPlTapety.naAucie(pojazdy, g, druga.gora)) {
                a.opis += " | krawędź pod zegarem: zegar leżałby na aucie";
            } else if (druga != null) {
                a = druga;
            }
        }
        if (auto != null) a.opis += String.format(Locale.US, ", dach auta %.0f%%", auto.gora * 100f);
        ModPlDziennik.i(TAG, skad + ": " + (a.efekt ? "chowa" : "bez efektu") + ", góra cyfr " + Math.round(a.gora * 1080)
                + ", " + a.opis + ", " + (SystemClock.uptimeMillis() - t0) + " ms");
        if (!a.efekt && tylkoChowane(this)) return null;
        Slajd s = new Slajd();
        s.zdjecie = ekranowe;
        s.gora = a.gora;
        s.ciemne = a.ciemne;
        s.podpis = podpis;
        // szkło cyfr jak w scenach autora, tak samo jak na tapetach ekranu głównego (użytkownik 12.11.2026)
        s.szklo = ModPlTapety.szkloSceny(ekranowe, a.ciemne);
        if (a.efekt && a.maska != null) {
            s.maska = Bitmap.createBitmap(AW, AH, Bitmap.Config.ALPHA_8);
            s.maska.copyPixelsFromBuffer(ByteBuffer.wrap(a.maska));
        }
        return s;
    }

    // ------------------------------------------------------------------ analiza zdjęcia (linia nieba)

    static final int AW = 640, AH = 360;
    private static final int OKNO = 6, SKOK = 4, PAS = 12;
    private static final float KARA = 5f;
    private static final float T_MIN = 150f / 1080f, T_MAX = 470f / 1080f, T_KROK = 6f / 1080f, T_DOMYSLNE = 157f / 1080f;

    /** Część analizy wspólna dla każdego położenia zegara. Liczona raz na zdjęcie (to ona kosztuje czas). */
    static final class Baza {
        byte[] maska;            // AW*AH, 0/1: pierwszy plan
        byte[] jasnosc;          // AW*AH, 0..255: jasność rozmytego zdjęcia
        byte[] miekka;           // maska z miękką krawędzią, liczona przy pierwszym użyciu
        int[] c;                 // suma narastająca maski: podręczna, gdy porównujemy kilka układów zegara
        boolean pewne, plaska, gotowa;
        String opis = "";
    }

    static final class Analiza {
        boolean efekt, ciemne;
        boolean ciemnaData;      // jasne tło nad zegarem: data i powitanie ciemne
        float gora;
        float schowane;          // jaka część cyfr wchodzi za pierwszy plan (0..1); 0, gdy bez efektu
        float jasnoscTla;        // średnia jasność zdjęcia za cyframi (0..255)
        byte[] maska;            // AW*AH, 0..255: pierwszy plan; null, gdy bez chowania
        String opis = "";
    }

    static Analiza analizuj(Bitmap zdjecie, Uklad u, byte[] gotowa) {
        return umiesc(baza(zdjecie, gotowa), u);
    }

    /** Wynik jak dla zdjęcia bez efektu: zegar w domyślnym miejscu, kolor cyfr dobrany do tła w tym miejscu. */
    static Analiza bezEfektu(Baza bz, Uklad u) {
        Baza bez = new Baza();
        bez.maska = bz.maska;
        bez.jasnosc = bz.jasnosc;
        synchronized (bz) {
            bez.c = bz.c;
        }
        bez.plaska = bz.plaska;
        bez.pewne = false;      // bez pewnej linii nieba umiesc() nie chowa zegara i stawia go w domyślnym miejscu
        return umiesc(bez, u);
    }

    /** Linia nieba, maska pierwszego planu i jasność zdjęcia: wszystko, co nie zależy od położenia zegara. */
    static Baza baza(Bitmap zdjecie, byte[] gotowa) {
        Baza wynik = new Baza();
        Bitmap mala = Bitmap.createScaledBitmap(zdjecie, AW, AH, true);
        int n = AW * AH;
        int[] px = new int[n];
        mala.getPixels(px, 0, AW, 0, 0, AW, AH);
        if (mala != zdjecie) mala.recycle();
        float[] r = new float[n], g = new float[n], b = new float[n];
        for (int i = 0; i < n; i++) {
            int p = px[i];
            r[i] = (p >> 16) & 255;
            g[i] = (p >> 8) & 255;
            b[i] = p & 255;
        }
        rozmyj(r);
        rozmyj(g);
        rozmyj(b);

        byte[] maska;
        boolean pewne, plaska = false;
        if (gotowa != null) {
            maska = gotowa;
            pewne = true;
            wynik.opis = "pierwszy plan ze sceny";
        } else {
            float[] sr = narastajaco(r), sg = narastajaco(g), sb = narastajaco(b);
            int y0 = (int) (AH * 0.10f), y1 = (int) (AH * 0.60f), wiersze = y1 - y0;
            float[] e = new float[wiersze * AW], dl = new float[wiersze * AW];
            int[] hist = new int[1024];
            for (int yy = 0; yy < wiersze; yy++) {
                int y = y0 + yy;
                for (int x = 0; x < AW; x++) {
                    float nr = (sr[y * AW + x] - sr[(y - OKNO) * AW + x]) / OKNO, pr = (sr[(y + OKNO) * AW + x] - sr[y * AW + x]) / OKNO;
                    float ng = (sg[y * AW + x] - sg[(y - OKNO) * AW + x]) / OKNO, pg = (sg[(y + OKNO) * AW + x] - sg[y * AW + x]) / OKNO;
                    float nb = (sb[y * AW + x] - sb[(y - OKNO) * AW + x]) / OKNO, pb = (sb[(y + OKNO) * AW + x] - sb[y * AW + x]) / OKNO;
                    float d = (float) Math.sqrt((nr - pr) * (nr - pr) + (ng - pg) * (ng - pg) + (nb - pb) * (nb - pb));
                    float roznica = (0.299f * nr + 0.587f * ng + 0.114f * nb) - (0.299f * pr + 0.587f * pg + 0.114f * pb);
                    float premia = Math.max(-1f, Math.min(1f, roznica / 80f));
                    float sila = d * (1f + 0.4f * premia);            // niebo zwykle jaśniejsze od ziemi
                    e[yy * AW + x] = sila;
                    dl[yy * AW + x] = roznica;
                    hist[Math.max(0, Math.min(1023, (int) sila))]++;
                }
            }
            float mediana = 0;
            for (int i = 0, licz = 0; i < hist.length; i++) {
                licz += hist[i];
                if (licz * 2 >= wiersze * AW) {
                    mediana = i + 0.5f;
                    break;
                }
            }
            // najsilniejsza gładka granica od lewej do prawej (programowanie dynamiczne)
            float[] razem = new float[wiersze], nast = new float[wiersze];
            byte[] skad = new byte[wiersze * AW];
            for (int yy = 0; yy < wiersze; yy++) razem[yy] = e[yy * AW];
            for (int x = 1; x < AW; x++) {
                for (int yy = 0; yy < wiersze; yy++) {
                    float naj = -1e9f;
                    int wybor = 0;
                    for (int d = -SKOK; d <= SKOK; d++) {
                        int yp = yy - d;
                        if (yp < 0 || yp >= wiersze) continue;
                        float v = razem[yp] - KARA * Math.abs(d);
                        if (v > naj) {
                            naj = v;
                            wybor = d;
                        }
                    }
                    skad[yy * AW + x] = (byte) wybor;
                    nast[yy] = naj + e[yy * AW + x];
                }
                float[] t = razem;
                razem = nast;
                nast = t;
            }
            int yb = 0;
            for (int yy = 1; yy < wiersze; yy++) if (razem[yy] > razem[yb]) yb = yy;
            int[] linia = new int[AW];
            float silaSr = 0, dlSr = 0;
            int jasniej = 0, min = AH, maks = 0;
            float[] silaLinii = new float[AW];
            for (int x = AW - 1; x >= 0; x--) {
                linia[x] = yb + y0;
                silaLinii[x] = e[yb * AW + x];
                silaSr += silaLinii[x];
                dlSr += dl[yb * AW + x];
                if (dl[yb * AW + x] > 0) jasniej++;
                min = Math.min(min, linia[x]);
                maks = Math.max(maks, linia[x]);
                yb = yb - skad[yb * AW + x];
            }
            silaSr /= AW;
            dlSr /= AW;
            int slabe = 0;
            for (int x = 0; x < AW; x++) if (silaLinii[x] < 0.5f * silaSr) slabe++;
            float pewnosc = silaSr / (mediana + 1e-3f), czescSlaba = slabe / (float) AW, czescJasniej = jasniej / (float) AW;
            plaska = (maks - min) / (float) AH < 0.09f;
            // niebo ma być wyraźnie jaśniejsze od ziemi (dzień) albo wyraźnie ciemniejsze (noc w mieście); mieszane = chmury
            boolean dzien = czescJasniej >= 0.7f && dlSr >= 8f, noc = czescJasniej <= 0.3f && dlSr <= -8f;
            pewne = silaSr >= 28f && pewnosc >= 3f && czescSlaba <= 0.30f && (dzien || noc);
            wynik.opis = String.format(Locale.US, "siła %.0f, pewność %.1f, słabe %.2f, jaśniej %.2f, linia %d-%d%%",
                    silaSr, pewnosc, czescSlaba, czescJasniej, min * 100 / AH, maks * 100 / AH);

            // przy samej linii każdy piksel idzie do nieba albo do ziemi po kolorze (ostre szczyty, drzewa)
            byte[] surowa = new byte[n];
            for (int x = 0; x < AW; x++) {
                int l = linia[x];
                int g1 = ogranicz(l - 3 * OKNO, 0, AH), g2 = ogranicz(l - OKNO / 2, 1, AH);
                int d1 = ogranicz(l + OKNO / 2, 0, AH - 1), d2 = ogranicz(l + 3 * OKNO, 1, AH);
                float ig = 1f / Math.max(g2 - g1, 1), id = 1f / Math.max(d2 - d1, 1);
                float nr = (sr[g2 * AW + x] - sr[g1 * AW + x]) * ig, ng = (sg[g2 * AW + x] - sg[g1 * AW + x]) * ig, nb = (sb[g2 * AW + x] - sb[g1 * AW + x]) * ig;
                float zr = (sr[d2 * AW + x] - sr[d1 * AW + x]) * id, zg = (sg[d2 * AW + x] - sg[d1 * AW + x]) * id, zb = (sb[d2 * AW + x] - sb[d1 * AW + x]) * id;
                for (int y = 0; y < AH; y++) {
                    int i = y * AW + x;
                    if (y < l - PAS) surowa[i] = 0;
                    else if (y > l + PAS) surowa[i] = 1;
                    else {
                        float doNieba = (r[i] - nr) * (r[i] - nr) + (g[i] - ng) * (g[i] - ng) + (b[i] - nb) * (b[i] - nb);
                        float doZiemi = (r[i] - zr) * (r[i] - zr) + (g[i] - zg) * (g[i] - zg) + (b[i] - zb) * (b[i] - zb);
                        surowa[i] = (byte) (doZiemi < doNieba ? 1 : 0);
                    }
                }
            }
            wynik.opis += ", wystające " + wystajace(r, g, b, linia, surowa);
            // filtr większościowy 5x5 usuwa pojedyncze pomyłki
            int[] c = calka(surowa);
            maska = new byte[n];
            for (int y = 0; y < AH; y++) {
                for (int x = 0; x < AW; x++) {
                    int xa = Math.max(0, x - 2), xb = Math.min(AW, x + 3), ya = Math.max(0, y - 2), yb2 = Math.min(AH, y + 3);
                    int suma = c[yb2 * (AW + 1) + xb] - c[ya * (AW + 1) + xb] - c[yb2 * (AW + 1) + xa] + c[ya * (AW + 1) + xa];
                    maska[y * AW + x] = (byte) (suma * 2 > (xb - xa) * (yb2 - ya) ? 1 : 0);
                }
            }
        }

        wynik.maska = maska;
        wynik.pewne = pewne;
        wynik.plaska = plaska;
        wynik.gotowa = gotowa != null;
        wynik.jasnosc = new byte[n];
        for (int i = 0; i < n; i++) {
            wynik.jasnosc[i] = (byte) Math.min(255, Math.round(0.299f * r[i] + 0.587f * g[i] + 0.114f * b[i]));
        }
        return wynik;
    }

    // ------------------------------------------------------------------ druga reguła: krawędź pod zegarem

    private static final float BOK = 0.03f, WYS_PASA = 0.16f, KROK_PASA = 0.06f;
    private static final int OKNO2 = 6, SKOK2 = 2, PAS2 = 8;
    /** Krawędź słabsza niż 70 bywa przypadkowa (mgła, cień): zegar wyglądał wtedy na ucięty bez powodu. */
    private static final float KARA2 = 4f, SILA_MIN = 70f, SLABE_MAX = 0.35f, PRZY_GORZE_MAX = 0.03f;

    /**
     * Druga reguła chowania, gdy linia nieba nie daje efektu (np. za cyframi stoją drzewa): najwyraźniejsza ciągła
     * krawędź tuż pod zegarem (dach auta, mur, horyzont), szukana tylko w kolumnach zegara. Pierwszy plan to wszystko
     * poniżej tej krawędzi; to, co wyżej, zostaje tłem za zegarem. Pasy sprawdzamy od góry i bierzemy pierwszy,
     * w którym zegar się chowa: liczy się górny brzeg obiektu (dach), a nie silniejsza krawędź niżej (maska silnika).
     * Używają jej wygaszacz i ekran główny (ModPlTapety). Zwraca analizę z efektem albo null.
     * Ten sam pomysł w Pythonie: kanwa-kafelki/narzedzia/proba_druga_linia.py.
     */
    static Analiza podZegarem(Bitmap zdjecie, Uklad u, byte[] jasnosc) {
        return podZegarem(zdjecie, u, jasnosc, 0f);
    }

    /**
     * To samo z ograniczeniem od dołu: krawędzi szukamy najwyżej do wysokości dolPasa (ułamek wysokości; 0 = bez
     * ograniczenia). Ekran główny podaje tu dach wykrytego auta z małym zapasem, żeby nie złapać linii maski albo szyb.
     */
    static Analiza podZegarem(Bitmap zdjecie, Uklad u, byte[] jasnosc, float dolPasa) {
        long t0 = SystemClock.uptimeMillis();
        Bitmap mala = Bitmap.createScaledBitmap(zdjecie, AW, AH, true);
        int n = AW * AH;
        int[] px = new int[n];
        mala.getPixels(px, 0, AW, 0, 0, AW, AH);
        if (mala != zdjecie) mala.recycle();
        float[] r = new float[n], g = new float[n], b = new float[n];
        for (int i = 0; i < n; i++) {
            int p = px[i];
            r[i] = (p >> 16) & 255;
            g[i] = (p >> 8) & 255;
            b[i] = p & 255;
        }
        rozmyj(r);
        rozmyj(g);
        rozmyj(b);
        float[][] kolory = {r, g, b, narastajaco(r), narastajaco(g), narastajaco(b)};
        float hg = u.wysCyfr / u.h;
        float koniec = Math.min(u.tMax + hg + 0.025f, (AH - OKNO2 - 1) / (float) AH);
        if (dolPasa > 0f) koniec = Math.min(koniec, dolPasa);
        for (float od = u.tMin + 0.85f * hg; od < koniec - 0.05f; od += KROK_PASA) {
            Baza bz = krawedz(kolory, u, od, Math.min(od + WYS_PASA, koniec), jasnosc);
            if (bz == null || !bz.pewne) continue;
            Analiza a = umiesc(bz, u);
            if (a.efekt) {
                a.opis += ", " + (SystemClock.uptimeMillis() - t0) + " ms";
                return a;
            }
        }
        return null;
    }

    /** Najwyraźniejsza ciągła krawędź w jednym pasie (ułamki wysokości) w kolumnach zegara i maska „wszystko pod nią”. */
    private static Baza krawedz(float[][] kolory, Uklad u, float pasOd, float pasDo, byte[] jasnosc) {
        float[] r = kolory[0], g = kolory[1], b = kolory[2], sr = kolory[3], sg = kolory[4], sb = kolory[5];
        int n = AW * AH;
        int r0 = (int) (pasOd * AH), r1 = (int) (pasDo * AH);
        int x0 = Math.max(0, (int) ((u.x0[0] / u.w - BOK) * AW)), x1 = Math.min(AW, (int) ((u.x1[3] / u.w + BOK) * AW));
        int wiersze = r1 - r0, kolumny = x1 - x0;
        if (wiersze < 8 || kolumny < 32 || r0 < OKNO2 || r1 + OKNO2 > AH) return null;

        // siła granicy: różnica koloru między oknem nad wierszem i pod nim
        float[] e = new float[wiersze * kolumny];
        for (int yy = 0; yy < wiersze; yy++) {
            int y = r0 + yy;
            for (int xx = 0; xx < kolumny; xx++) {
                int x = x0 + xx;
                float dr = (sr[y * AW + x] - sr[(y - OKNO2) * AW + x]) - (sr[(y + OKNO2) * AW + x] - sr[y * AW + x]);
                float dg = (sg[y * AW + x] - sg[(y - OKNO2) * AW + x]) - (sg[(y + OKNO2) * AW + x] - sg[y * AW + x]);
                float db = (sb[y * AW + x] - sb[(y - OKNO2) * AW + x]) - (sb[(y + OKNO2) * AW + x] - sb[y * AW + x]);
                e[yy * kolumny + xx] = (float) Math.sqrt(dr * dr + dg * dg + db * db) / OKNO2;
            }
        }
        // najsilniejsza gładka linia od lewej do prawej (programowanie dynamiczne)
        float[] razem = new float[wiersze], nast = new float[wiersze];
        byte[] skad = new byte[wiersze * kolumny];
        for (int yy = 0; yy < wiersze; yy++) razem[yy] = e[yy * kolumny];
        for (int xx = 1; xx < kolumny; xx++) {
            for (int yy = 0; yy < wiersze; yy++) {
                float naj = -1e9f;
                int wybor = 0;
                for (int d = -SKOK2; d <= SKOK2; d++) {
                    int yp = yy - d;
                    if (yp < 0 || yp >= wiersze) continue;
                    float v = razem[yp] - KARA2 * Math.abs(d);
                    if (v > naj) {
                        naj = v;
                        wybor = d;
                    }
                }
                skad[yy * kolumny + xx] = (byte) wybor;
                nast[yy] = naj + e[yy * kolumny + xx];
            }
            float[] tmp = razem;
            razem = nast;
            nast = tmp;
        }
        int yb = 0;
        for (int yy = 1; yy < wiersze; yy++) if (razem[yy] > razem[yb]) yb = yy;
        int[] linia = new int[kolumny];
        float[] sila = new float[kolumny];
        float silaSr = 0;
        int min = AH, maks = 0;
        for (int xx = kolumny - 1; xx >= 0; xx--) {
            linia[xx] = yb + r0;
            sila[xx] = e[yb * kolumny + xx];
            silaSr += sila[xx];
            min = Math.min(min, linia[xx]);
            maks = Math.max(maks, linia[xx]);
            yb = yb - skad[yb * kolumny + xx];
        }
        silaSr /= kolumny;
        int slabe = 0;
        for (int xx = 0; xx < kolumny; xx++) if (sila[xx] < 0.5f * silaSr) slabe++;
        float czescSlaba = slabe / (float) kolumny;
        // linia dochodząca do górnego brzegu pasa znaczy, że brzeg obiektu ucieka w górę przez zegar (zbliżenie auta):
        // zegar leżałby wtedy na aucie, a nie chował się za nim
        int uGory = 0;
        for (int xx = 0; xx < kolumny; xx++) if (linia[xx] - r0 <= 1) uGory++;
        float przyGorze = uGory / (float) kolumny;

        // przy samej krawędzi każdy piksel idzie na górę albo na dół po kolorze; niżej wszystko jest pierwszym planem
        byte[] surowa = new byte[n];
        for (int xx = 0; xx < kolumny; xx++) {
            int x = x0 + xx, l = linia[xx];
            int g1 = Math.max(0, l - 12), g2 = Math.max(1, l - 3), d1 = Math.min(AH - 1, l + 3), d2 = Math.min(AH, l + 12);
            float ig = 1f / Math.max(g2 - g1, 1), id = 1f / Math.max(d2 - d1, 1);
            float nr = (sr[g2 * AW + x] - sr[g1 * AW + x]) * ig, ng = (sg[g2 * AW + x] - sg[g1 * AW + x]) * ig, nb = (sb[g2 * AW + x] - sb[g1 * AW + x]) * ig;
            float zr = (sr[d2 * AW + x] - sr[d1 * AW + x]) * id, zg = (sg[d2 * AW + x] - sg[d1 * AW + x]) * id, zb = (sb[d2 * AW + x] - sb[d1 * AW + x]) * id;
            for (int y = Math.max(0, l - PAS2); y < AH; y++) {
                int i = y * AW + x;
                if (y > l + PAS2) {
                    surowa[i] = 1;
                } else {
                    float doGory = (r[i] - nr) * (r[i] - nr) + (g[i] - ng) * (g[i] - ng) + (b[i] - nb) * (b[i] - nb);
                    float doDolu = (r[i] - zr) * (r[i] - zr) + (g[i] - zg) * (g[i] - zg) + (b[i] - zb) * (b[i] - zb);
                    surowa[i] = (byte) (doDolu < doGory ? 1 : 0);
                }
            }
        }
        // filtr większościowy 5x5 usuwa pojedyncze pomyłki
        int[] c = calka(surowa);
        byte[] maska = new byte[n];
        for (int y = 0; y < AH; y++) {
            for (int x = 0; x < AW; x++) {
                int xa = Math.max(0, x - 2), xb = Math.min(AW, x + 3), ya = Math.max(0, y - 2), yb2 = Math.min(AH, y + 3);
                int suma = c[yb2 * (AW + 1) + xb] - c[ya * (AW + 1) + xb] - c[yb2 * (AW + 1) + xa] + c[ya * (AW + 1) + xa];
                maska[y * AW + x] = (byte) (suma * 2 > (xb - xa) * (yb2 - ya) ? 1 : 0);
            }
        }
        Baza bz = new Baza();
        bz.maska = maska;
        bz.jasnosc = jasnosc;
        bz.pewne = silaSr >= SILA_MIN && czescSlaba <= SLABE_MAX && przyGorze <= PRZY_GORZE_MAX;
        bz.opis = String.format(Locale.US, "krawędź pod zegarem: siła %.0f, słabe %.2f, przy górze %.2f, linia %d-%d%%",
                silaSr, czescSlaba, przyGorze, min * 100 / AH, maks * 100 / AH);
        return bz;
    }

    private static final float ODEJSCIE = 70f, POWROT = 45f;
    private static final int WIERSZY_POWROTU = 3, SZER_OBIEKTU = 16, SKOK_OBIEKTU = 8;

    /**
     * Obiekt wystający ponad linię nieba, np. dach i szyba auta, gdy linia poszła niżej, po krawędzi maski silnika
     * (ciemna szyba kontra jaskrawa maska bywa silniejszą granicą niż niebo kontra dach). W każdej kolumnie idziemy
     * od góry i pilnujemy koloru nieba (średnia krocząca); gdy kolor odskoczy i nie wróci aż do linii, od tego miejsca
     * w dół może być pierwszy plan. Liczy się tylko szeroki, ciągły obiekt: słupy, banery i pojedyncze kolumny
     * odpadają. Zwraca liczbę podniesionych kolumn. Ten sam kod w Pythonie: kanwa-kafelki/narzedzia/proba_kadr.py.
     */
    private static int wystajace(float[] r, float[] g, float[] b, int[] linia, byte[] surowa) {
        int[] od = new int[AW];
        for (int x = 0; x < AW; x++) {
            od[x] = -1;
            int koniec = Math.max(0, linia[x] - PAS);
            if (koniec <= 12) continue;
            float wr = 0, wg = 0, wb = 0;
            for (int y = 0; y < 8; y++) {
                int i = y * AW + x;
                wr += r[i];
                wg += g[i];
                wb += b[i];
            }
            wr /= 8f;
            wg /= 8f;
            wb /= 8f;
            int odejscie = -1, wraca = 0;
            for (int y = 8; y < koniec; y++) {
                int i = y * AW + x;
                float dr = r[i] - wr, dg = g[i] - wg, db = b[i] - wb, d2 = dr * dr + dg * dg + db * db;
                if (odejscie < 0) {
                    if (d2 > ODEJSCIE * ODEJSCIE) {
                        odejscie = y;
                        wraca = 0;
                    } else {
                        wr = wr * 0.9f + r[i] * 0.1f;
                        wg = wg * 0.9f + g[i] * 0.1f;
                        wb = wb * 0.9f + b[i] * 0.1f;
                    }
                } else if (d2 < POWROT * POWROT) {
                    if (++wraca >= WIERSZY_POWROTU) odejscie = -1;
                } else {
                    wraca = 0;
                }
            }
            od[x] = odejscie;
        }
        int ile = 0;
        for (int x = 0; x < AW; ) {
            if (od[x] < 0) {
                x++;
                continue;
            }
            int k = x + 1;
            while (k < AW && od[k] >= 0 && Math.abs(od[k] - od[k - 1]) <= SKOK_OBIEKTU) k++;
            if (k - x >= SZER_OBIEKTU) {
                for (int i = x; i < k; i++) {
                    int koniec = Math.max(0, linia[i] - PAS);
                    for (int y = od[i]; y < koniec; y++) surowa[y * AW + i] = 1;
                }
                ile += k - x;
            }
            x = k;
        }
        return ile;
    }

    /** Położenie zegara dla danego układu cyfr: jak wysoko go postawić, żeby dół cyfr wszedł za pierwszy plan. */
    static Analiza umiesc(Baza bz, Uklad u) {
        Analiza wynik = new Analiza();
        int n = AW * AH;
        wynik.opis = bz.opis;
        int[] c;
        synchronized (bz) {
            if (bz.c == null) bz.c = calka(bz.maska);
            c = bz.c;
        }
        float limit = bz.plaska ? 0.14f : 0.22f;
        if (u.maksRazem > 0f) limit = Math.min(limit, u.maksRazem);
        float[] zegar = dobierz(c, u, limit);
        boolean schowany = zegar != null && (bz.gotowa || zegar[1] >= u.minRazem);
        wynik.efekt = bz.pewne && (schowany || bz.gotowa);
        wynik.gora = bz.pewne && schowany ? zegar[0] : u.tDomyslne;
        wynik.schowane = bz.pewne && schowany ? zegar[1] : 0f;
        if (zegar != null) wynik.opis += String.format(Locale.US, ", schowane %.0f%%", zegar[1] * 100f);
        if (wynik.efekt) {
            synchronized (bz) {
                if (bz.miekka == null) {
                    // miękka krawędź: średnia 3x3
                    byte[] miekka = new byte[n];
                    for (int y = 0; y < AH; y++) {
                        for (int x = 0; x < AW; x++) {
                            int xa = Math.max(0, x - 1), xb = Math.min(AW, x + 2), ya = Math.max(0, y - 1), yb2 = Math.min(AH, y + 2);
                            int suma = c[yb2 * (AW + 1) + xb] - c[ya * (AW + 1) + xb] - c[yb2 * (AW + 1) + xa] + c[ya * (AW + 1) + xa];
                            miekka[y * AW + x] = (byte) (suma * 255 / ((xb - xa) * (yb2 - ya)));
                        }
                    }
                    bz.miekka = miekka;
                }
                wynik.maska = bz.miekka;
            }
        }
        // jasność tła za cyframi decyduje o kolorze cyfr
        int xa = ogranicz(Math.round(u.x0[0] / u.w * AW), 0, AW - 1), xb = ogranicz(Math.round(u.x1[3] / u.w * AW), xa + 1, AW);
        int ya = ogranicz(Math.round(wynik.gora * AH), 0, AH - 1), yb2 = ogranicz(Math.round((wynik.gora + u.wysCyfr / u.h) * AH), ya + 1, AH);
        double jasnosc = 0;
        for (int y = ya; y < yb2; y++) {
            for (int x = xa; x < xb; x++) jasnosc += bz.jasnosc[y * AW + x] & 255;
        }
        jasnosc /= Math.max(1, (xb - xa) * (yb2 - ya));
        wynik.ciemne = jasnosc > 150;
        wynik.jasnoscTla = (float) jasnosc;
        // to samo dla pasa tuż nad zegarem, gdzie stoją data i powitanie
        int yc = ogranicz(Math.round((wynik.gora - 0.085f) * AH), 0, AH - 1), yd = ogranicz(Math.round((wynik.gora - 0.012f) * AH), yc + 1, AH);
        double nad = 0;
        for (int y = yc; y < yd; y++) {
            for (int x = xa; x < xb; x++) nad += bz.jasnosc[y * AW + x] & 255;
        }
        nad /= Math.max(1, (xb - xa) * (yd - yc));
        wynik.ciemnaData = nad > 150;
        wynik.opis += String.format(Locale.US, ", jasność tła %.0f, nad zegarem %.0f", jasnosc, nad);
        return wynik;
    }

    /** Górny brzeg cyfr: jak najwięcej schowane, ale każda cyfra czytelna. Zwraca {góra, schowana część} albo null. */
    private static float[] dobierz(int[] c, Uklad u, float limit) {
        float hg = u.wysCyfr / u.h, pod = u.podniesienie / u.h;
        float[] naj = null;
        float najOcena = 0f;
        for (float t = u.tMin; t <= u.tMax + 1e-4f; t += T_KROK) {
            float suma = 0, maks = 0, gora = 0;
            int dotkniete = 0;
            for (int i = 0; i < 4; i++) {
                float f = srednia(c, u.x0[i] / u.w, t, u.x1[i] / u.w, t + hg);
                suma += f;
                if (f > maks) maks = f;
                if (f >= u.progCyfry) dotkniete++;
                if (u.czystaGora < 1f) gora = Math.max(gora, srednia(c, u.x0[i] / u.w, t, u.x1[i] / u.w, t + hg * 0.6f));
            }
            float dwukropek = srednia(c, u.dx0 / u.w, t - pod, u.dx1 / u.w, t - pod + hg);
            float razem = suma / 4f;
            if (maks > u.maksCyfry || dwukropek > u.maksDwukropka || razem > limit || gora > u.czystaGora) continue;
            if (dotkniete < u.minCyfr) continue;      // jedna cyfra przycięta lusterkiem albo słupem wygląda na przypadek
            float ocena = Math.min(razem, u.dosc) - u.karaZaDol * (t - u.tMin);
            if (naj == null || ocena > najOcena) {
                naj = new float[]{t, razem};
                najOcena = ocena;
            }
        }
        return naj;
    }

    private static int ogranicz(int v, int a, int b) {
        return Math.max(a, Math.min(b, v));
    }

    /** Lekkie rozmycie 3x3 (wagi 1-2-1) tłumi szum przed szukaniem krawędzi. */
    static void rozmyj(float[] a) {
        float[] t = new float[a.length];
        for (int y = 0; y < AH; y++) {
            for (int x = 0; x < AW; x++) {
                int i = y * AW + x;
                float l = a[x > 0 ? i - 1 : i], p = a[x < AW - 1 ? i + 1 : i];
                t[i] = (l + 2f * a[i] + p) * 0.25f;
            }
        }
        for (int y = 0; y < AH; y++) {
            for (int x = 0; x < AW; x++) {
                int i = y * AW + x;
                float gora = t[y > 0 ? i - AW : i], dol = t[y < AH - 1 ? i + AW : i];
                a[i] = (gora + 2f * t[i] + dol) * 0.25f;
            }
        }
    }

    /** Sumy narastające w pionie: s[y*AW+x] = suma wierszy powyżej y. */
    static float[] narastajaco(float[] a) {
        float[] s = new float[(AH + 1) * AW];
        for (int y = 0; y < AH; y++) {
            for (int x = 0; x < AW; x++) s[(y + 1) * AW + x] = s[y * AW + x] + a[y * AW + x];
        }
        return s;
    }

    /** Obraz całkowy maski 0/1: szybkie liczenie, jaka część prostokąta jest pierwszym planem. */
    static int[] calka(byte[] m) {
        int[] c = new int[(AH + 1) * (AW + 1)];
        for (int y = 0; y < AH; y++) {
            int wiersz = 0;
            for (int x = 0; x < AW; x++) {
                wiersz += m[y * AW + x];
                c[(y + 1) * (AW + 1) + x + 1] = c[y * (AW + 1) + x + 1] + wiersz;
            }
        }
        return c;
    }

    static float srednia(int[] c, float fx0, float fy0, float fx1, float fy1) {
        int xa = ogranicz(Math.round(fx0 * AW), 0, AW), xb = ogranicz(Math.round(fx1 * AW), 0, AW);
        int ya = ogranicz(Math.round(fy0 * AH), 0, AH), yb = ogranicz(Math.round(fy1 * AH), 0, AH);
        if (xb <= xa || yb <= ya) return 0f;
        int suma = c[yb * (AW + 1) + xb] - c[ya * (AW + 1) + xb] - c[yb * (AW + 1) + xa] + c[ya * (AW + 1) + xa];
        return suma / (float) ((xb - xa) * (yb - ya));
    }

    // ------------------------------------------------------------------ rysowanie

    private final class Widok extends View {
        private final Paint zdj = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Paint przod = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Paint zaslona = new Paint();
        private final Paint cyfry = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint napis = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint maly = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF caly = new RectF();
        private final Matrix macierz = new Matrix();
        private Slajd teraz, przedtem;
        private long startTeraz, startPrzedtem;

        Widok(Context c) {
            super(c);
            cyfry.setTypeface(Ui.font(c, "clock_rounded"));
            cyfry.setLetterSpacing(-0.012f);
            napis.setTypeface(Ui.font(c, "semibold"));
            maly.setTypeface(Ui.font(c, "semibold"));          // Inter, jak data nad zegarem
        }

        void ustaw(Slajd s) {
            if (przedtem != null) przedtem.zwolnij();
            przedtem = teraz;
            startPrzedtem = startTeraz;
            teraz = s;
            startTeraz = SystemClock.uptimeMillis();
            invalidate();
        }

        void zwolnij() {
            if (teraz != null) teraz.zwolnij();
            if (przedtem != null) przedtem.zwolnij();
            teraz = przedtem = null;
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            caly.set(0, 0, w, h);
            zaslona.setShader(new LinearGradient(0, 0, 0, h,
                    new int[]{0x29000000, 0x00000000, 0x00000000, 0x38000000}, new float[]{0f, 0.26f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas c) {
            long t = SystemClock.uptimeMillis();
            c.drawColor(0xFF000000);
            float a = teraz == null ? 0f : Math.min(1f, (t - startTeraz) / (float) PRZEJSCIE_MS);
            if (przedtem != null) {
                if (a >= 1f) {
                    przedtem.zwolnij();
                    przedtem = null;
                } else {
                    rysuj(c, przedtem, t - startPrzedtem);
                }
            }
            if (teraz != null && teraz.zdjecie != null) {
                if (a < 1f) {
                    int warstwa = c.saveLayerAlpha(caly, (int) (a * 255));
                    rysuj(c, teraz, t - startTeraz);
                    c.restoreToCount(warstwa);
                } else {
                    rysuj(c, teraz, t - startTeraz);
                }
            }
            if (dziala) postInvalidateDelayed(a < 1f ? 16 : (t - startTeraz < 15000L ? 40 : 200));
        }

        private void rysuj(Canvas c, Slajd s, long wiek) {
            if (s.zdjecie == null || s.zdjecie.isRecycled()) return;
            float w = getWidth(), h = getHeight();
            float okres = coIle(ModPlWygaszacz.this) * 60000f;
            float skala = 1f + POWIEKSZENIE * Math.min(1f, wiek / okres);
            c.save();
            c.scale(skala, skala, w / 2f, h / 2f);
            c.drawBitmap(s.zdjecie, null, caly, zdj);
            c.restore();
            c.drawRect(caly, zaslona);
            rysujZegar(c, s, w, h);
            if (s.maska != null && !s.maska.isRecycled()) {
                if (s.cieniowanieZdjecia == null) {
                    s.cieniowanieZdjecia = new BitmapShader(s.zdjecie, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    macierz.setScale(w / s.zdjecie.getWidth(), h / s.zdjecie.getHeight());
                    s.cieniowanieZdjecia.setLocalMatrix(macierz);
                }
                przod.setShader(s.cieniowanieZdjecia);
                c.save();
                c.scale(skala, skala, w / 2f, h / 2f);
                c.drawBitmap(s.maska, null, caly, przod);      // maska ALPHA_8 + zdjęcie jako kolor = pierwszy plan
                c.restore();
            }
            rysujDate(c, s, w, h);
            rysujPodpis(c, s, w, h, wiek);
        }

        /** Podpis zdjęcia w lewym dolnym rogu: pojawia się po zmianie zdjęcia i po kilku sekundach znika. */
        private void rysujPodpis(Canvas c, Slajd s, float w, float h, long wiek) {
            if (s.podpis == null || s.podpis.isEmpty() || !podpis(ModPlWygaszacz.this)) return;
            float widac = wiek < 1800 ? 0f : wiek < 2600 ? (wiek - 1800) / 800f : wiek < 12000 ? 1f : wiek < 13200 ? 1f - (wiek - 12000) / 1200f : 0f;
            if (widac <= 0f) return;
            float sk = h / 1080f;
            maly.setTextSize(26f * sk);
            maly.setColor(0xFFFFFF | ((int) (widac * 224) << 24));
            maly.setShadowLayer(6f * sk, 0, 1.5f * sk, ((int) (widac * 170) << 24));
            c.drawText(s.podpis, 64f * sk, h - 56f * sk, maly);
        }

        private void rysujZegar(Canvas c, Slajd s, float w, float h) {
            Context ctx = ModPlWygaszacz.this;
            String czas = new SimpleDateFormat(Scenes.isClock24h(ctx) ? "HH:mm" : "h:mm", Locale.US).format(new Date());
            int dw = czas.indexOf(':');
            String godz = dw > 0 ? czas.substring(0, dw) : czas, min = dw > 0 ? czas.substring(dw + 1) : "";
            cyfry.setTextSize(uklad.rozmiar * (h / uklad.h));
            float sg = cyfry.measureText(godz), sd = cyfry.measureText(":"), sm = cyfry.measureText(min);
            float x = (w - (sg + sd + sm)) / 2f;
            float gora = s.gora * h, podstawa = gora + uklad.wysCyfr * (h / uklad.h), pod = uklad.podniesienie * (h / uklad.h);
            rysujZegarAutora(c, s, godz, min, x, sg, sd, gora, podstawa, pod, w, h);
        }

        /**
         * Cyfry jak w scenach autora (Scenes.drawStylizedClock, „iPhone zaokrąglony”): twardy cień, wypełnienie szkłem
         * (ModPlTapety.szkloSceny), mleczna warstwa tylko bez pierwszego planu i cienka obwódka z gradientem.
         * Wartości (przezroczystości, kolory, grubości) są przepisane z kodu autora.
         */
        private void rysujZegarAutora(Canvas c, Slajd s, String godz, String min, float x, float sg, float sd,
                                      float gora, float podstawa, float pod, float w, float h) {
            float u = h / 540f;
            boolean przodek = s.maska != null;
            float gornyGradient = gora - (podstawa - gora) * 0.24f;     // u autora gradient zaczyna się nad cyframi
            if (s.obwodkaAutora == null) {
                s.mlecznaAutora = new LinearGradient(0, gornyGradient, 0, podstawa, s.ciemne
                        ? new int[]{Color.argb(38, 255, 255, 255), Color.argb(68, 255, 255, 255), Color.argb(118, 255, 255, 255)}
                        : new int[]{Color.argb(52, 218, 224, 255), Color.argb(86, 236, 232, 255), Color.argb(138, 255, 242, 252)},
                        new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP);
                s.obwodkaAutora = new LinearGradient(0, gornyGradient, 0, podstawa, s.ciemne
                        ? new int[]{Color.argb(210, 255, 255, 255), Color.argb(160, 255, 255, 255), Color.argb(230, 255, 255, 255)}
                        : new int[]{Color.argb(228, 244, 248, 255), Color.argb(185, 232, 236, 255), Color.argb(250, 255, 255, 255)},
                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            }
            // 1. cień
            cyfry.setStyle(Paint.Style.FILL);
            cyfry.setShader(null);
            cyfry.setColor(Color.argb(przodek ? 46 : 70, 0, 0, 0));
            tekst(c, godz, min, x, sg, sd, podstawa + 3.5f * u, pod);
            // 2. szkło: przerobione zdjęcie w cyfrach
            if (s.szklo != null && !s.szklo.isRecycled()) {
                if (s.cieniowanieSzkla == null) {
                    s.cieniowanieSzkla = new BitmapShader(s.szklo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    Matrix m = new Matrix();
                    m.setScale(w / s.szklo.getWidth(), h / s.szklo.getHeight());
                    s.cieniowanieSzkla.setLocalMatrix(m);
                }
                cyfry.setColor(0xFFFFFFFF);
                cyfry.setShader(s.cieniowanieSzkla);
                tekst(c, godz, min, x, sg, sd, podstawa, pod);
            }
            // 3. mleczna warstwa: u autora tylko w scenach bez wyciętego pierwszego planu
            if (!przodek) {
                cyfry.setShader(s.mlecznaAutora);
                cyfry.setColor(0xFFFFFFFF);
                cyfry.setAlpha(120);
                tekst(c, godz, min, x, sg, sd, podstawa, pod);
            }
            // 4. obwódka
            cyfry.setStyle(Paint.Style.STROKE);
            cyfry.setStrokeWidth(Math.max(1.5f, 1.5f * u));
            cyfry.setShader(s.obwodkaAutora);
            cyfry.setColor(0xFFFFFFFF);
            cyfry.setAlpha(przodek ? 165 : 155);
            tekst(c, godz, min, x, sg, sd, podstawa, pod);
            cyfry.setAlpha(255);
            cyfry.setShader(null);
            cyfry.setStyle(Paint.Style.FILL);
        }

        private void tekst(Canvas c, String godz, String min, float x, float sg, float sd, float y, float pod) {
            c.drawText(godz, x, y, cyfry);
            c.drawText(":", x + sg, y - pod, cyfry);
            c.drawText(min, x + sg + sd, y, cyfry);
        }

        private void rysujDate(Canvas c, Slajd s, float w, float h) {
            Context ctx = ModPlWygaszacz.this;
            float sk = h / 1080f;
            String data;
            try {
                data = new SimpleDateFormat("EEEE, d MMMM", new Locale(I18n.getLang(ctx))).format(new Date());
            } catch (Throwable e) {
                data = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date());
            }
            if (!data.isEmpty()) data = data.substring(0, 1).toUpperCase() + data.substring(1);
            napis.setTextSize(34f * sk);
            napis.setColor(s.ciemne ? 0xE60E121C : 0xFFFFFFFF);
            napis.setShadowLayer(s.ciemne ? 1f : 8f * sk, 0, s.ciemne ? 1f : 2f * sk, s.ciemne ? 0x66FFFFFF : 0x80000000);
            Weather.Data pogoda = temperatura(ctx) && Weather.enabled(ctx) ? Weather.cached(ctx) : null;
            String temp = pogoda == null ? null : Weather.formatTemp(ctx, pogoda.temp);
            float ikona = 44f * sk, odstep = 14f * sk;
            float sData = napis.measureText(data), sKropka = napis.measureText("·"), sTemp = temp == null ? 0 : napis.measureText(temp);
            float razem = sData + (temp == null ? 0 : odstep + sKropka + odstep + ikona + odstep * 0.6f + sTemp);
            float x = (w - razem) / 2f, y = s.gora * h - 51f * sk;
            c.drawText(data, x, y, napis);
            if (temp != null) {
                x += sData + odstep;
                c.drawText("·", x, y, napis);
                x += sKropka + odstep;
                Bitmap ik = WeatherIcons.get(pogoda.code, pogoda.day, Math.max(16, Math.round(ikona)));
                if (ik != null) c.drawBitmap(ik, x, y - ikona * 0.82f, zdj);
                x += ikona + odstep * 0.6f;
                c.drawText(temp, x, y, napis);
            }
        }
    }
}
