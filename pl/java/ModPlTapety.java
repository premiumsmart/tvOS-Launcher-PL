package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * Mod PL: tapety ekranu głównego w wyglądzie „Nocne sceny”.
 *
 * 1. Zegar chowa się za pierwszym planem tapety, tak jak w wygaszaczu. Dotyczy tapet własnych i „tapet
 *    z internetu” (wbudowane sceny mają pierwszy plan wycięty ręcznie). Analiza zdjęcia jest wspólna
 *    z wygaszaczem: ModPlWygaszacz.baza() liczy linię nieba raz na zdjęcie, umiesc() dobiera wysokość zegara.
 *    Kolor daty i powitania zależy od jasności tła nad zegarem.
 * 2. Opcja „Powiększaj tapetę pod zegar” (domyślnie wyłączona): zegar zostaje w swoim miejscu, a tapeta z internetu
 *    jest kadrowana (lekkie powiększenie i przesunięcie), żeby dół cyfr trafił na pierwszy plan, np. na dach auta.
 *    Gdy linia nieba nie daje efektu (np. za cyframi stoją drzewa), działa druga reguła: najwyraźniejsza ciągła
 *    krawędź tuż pod zegarem (dach auta, mur, horyzont). Cyfry chowają się tylko za nią, reszta zdjęcia zostaje tłem.
 * 3. Tapety z internetu, za którymi zegar się nie schowa, są pomijane już przy pobieraniu (sprawdz()).
 * 4. Ulubione: bieżącą tapetę można zapisać, a zapisane wracają co którąś zmianę tapety.
 *
 * Zmiany w kodzie launchera (Scenes, CustomMedia, SettingsActivity, SceneHome, ControlPanelOverlay)
 * nakłada pl/naloz.py.
 */
final class ModPlTapety {
    private ModPlTapety() { }

    private static final String TAG = "ModPlTapety";
    /**
     * Zmiana tapety szuka do skutku: najwyżej tylu kandydatów i tyle czasu, potem zostaje obecna tapeta.
     * Przechodzi mniej więcej co czwarte do szóstego zdjęcie auta (użytkownik 12.11.2026: „miałem tak, że się
     * nie zmieniały”; wcześniej po 16 nieudanych próbach zmiana po cichu się kończyła).
     */
    static final int PROB = 60;
    static final long CZAS_SZUKANIA = 180000L;
    /** Dół cyfr nie schodzi niżej: nad kafelkami ekranu głównego ma zostać odstęp. */
    private static final float DOL_ZEGARA = 0.505f;
    /**
     * Chowanie ma być delikatne (użytkownik 12.11.2026: „na środku i lekko schowany”; mocne schowanie ocenił jako
     * słabe): najwyżej tyle jednej cyfry i tyle wszystkich cyfr naraz może zniknąć za pierwszym planem,
     * a górne 60% każdej cyfry ma stać na czystym niebie.
     */
    private static final float MAKS_CYFRY = 0.26f, MAKS_RAZEM = 0.12f, CZYSTA_GORA = 0.03f;
    /**
     * Efekt ma być widoczny i wyglądać na zamierzony (użytkownik 12.11.2026: tapeta „gdzie zegar był normalny bez
     * zakrycia” nie może przejść; zdjęcia mają pasować pod zegar): co najmniej 5% cyfr schowane i co najmniej dwie
     * cyfry wyraźnie przycięte. Tło za górną częścią cyfr ma być spokojne: średnia zmiana jasności między sąsiednimi
     * pikselami (w skali analizy) najwyżej FAKTURA_MAX. Zbliżenie auta z szybą i kratami pod zegarem daje ok. 16,
     * niebo z drzewami ok. 10. Wartości strojone narzędziem kanwa-kafelki/narzedzia/proba_ocena.py.
     */
    private static final float MIN_RAZEM = 0.05f, FAKTURA_MAX = 12.5f;
    private static final int MIN_CYFR = 2;
    /** Ocena położenia: schowana część liczy się tylko do tej wartości, a każde zejście zegara w dół kosztuje. */
    private static final float DOSC_SCHOWANE = 0.08f, KARA_ZA_DOL = 0.30f;
    /** Kadrowanie tapety pod zegar: dozwolone powiększenia (bez zwykłego kadru) i ich koszt w ocenie. */
    private static final float[] POWIEKSZENIA = {1.06f, 1.12f, 1.18f, 1.24f, 1.30f};
    private static final float KARA_ZA_POWIEKSZENIE = 0.25f, KROK_KADRU = 0.02f, KROK_T = 6f / 1080f;

    private static final class Wpis {
        ModPlWygaszacz.Baza baza;
        String klucz;                       // układ zegara, dla którego policzono analizę
        ModPlWygaszacz.Analiza analiza;
        Bitmap maska;
        byte[] maskaZ;                      // z której tablicy zbudowano bitmapę maski
        Float goraStala;                    // dołączone zdjęcie z ustawionym ręcznie położeniem zegara (ułamek wysokości)
        List<ModPlDetektor.Pojazd> pojazdy; // auta wykryte na tapecie; null, gdy wykrywacz nie działa
        boolean wykryte;                    // czy wykrywacz już oglądał tę tapetę
        BitmapShader cieniowanie;
    }

    /** Wycinek zdjęcia dobrany pod zegar: powiększenie i lewy górny róg jako ułamki zwykłego kadru 16:9. */
    private static final class Kadr {
        float z, ax, ay, t, schowane, ocena;
    }

    private static final WeakHashMap<Bitmap, Wpis> WPISY = new WeakHashMap<Bitmap, Wpis>();
    /** Tapety wczytane z pamięci podręcznej, które są dołączoną ulubioną: {maska, góra cyfr}. */
    private static final WeakHashMap<Bitmap, Object[]> OZNACZONE = new WeakHashMap<Bitmap, Object[]>();
    private static final Paint PEDZEL = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private static final Matrix MACIERZ = new Matrix();

    /**
     * Układ zegara z ostatniego rysowania ekranu głównego jako ułamki ekranu: lewy i prawy brzeg cyfr, ich wysokość,
     * domyślna góra. Wątek pobierania tapet ocenia nim kandydatów. Wartości startowe: zegar „iPhone zaokrąglony” na środku.
     */
    private static volatile float[] uklad = {0.298f, 0.702f, 0.245f, 0.161f};

    private static ModPlWygaszacz.Uklad zbudujUklad(int w, int h, float[] g) {
        float tMax = Math.max(g[3], DOL_ZEGARA - g[2]);
        ModPlWygaszacz.Uklad uk = new ModPlWygaszacz.Uklad(w, h, g[0] * w, g[1] * w, g[2] * h, g[3], tMax, g[3]);
        uk.maksCyfry = MAKS_CYFRY;
        uk.maksRazem = MAKS_RAZEM;
        uk.czystaGora = CZYSTA_GORA;
        uk.dosc = DOSC_SCHOWANE;
        uk.karaZaDol = KARA_ZA_DOL;
        uk.minRazem = MIN_RAZEM;
        uk.minCyfr = MIN_CYFR;
        return uk;
    }

    private static String klucz(float[] g) {
        return Math.round(g[0] * 1000) + ":" + Math.round(g[1] * 1000) + ":" + Math.round(g[2] * 1000) + ":" + Math.round(g[3] * 1000);
    }

    /** Zdjęcie w proporcjach ekranu i rozdzielczości analizy (tak samo kadrowane jak przy rysowaniu tła). */
    private static Bitmap doAnalizy(Bitmap bg, int w, int h) {
        Bitmap mala = Bitmap.createBitmap(ModPlWygaszacz.AW, ModPlWygaszacz.AH, Bitmap.Config.ARGB_8888);
        float skala = Math.max(w / (float) bg.getWidth(), h / (float) bg.getHeight());
        Matrix m = new Matrix();
        m.setScale(skala, skala);
        m.postTranslate((w - bg.getWidth() * skala) / 2f, (h - bg.getHeight() * skala) / 2f);
        m.postScale(ModPlWygaszacz.AW / (float) w, ModPlWygaszacz.AH / (float) h);
        new Canvas(mala).drawBitmap(bg, m, new Paint(Paint.FILTER_BITMAP_FLAG));
        return mala;
    }

    /** Wpis tapety z policzoną częścią wspólną analizy (linia nieba). Liczone raz na zdjęcie, ~0,15 s na boxie. */
    private static Wpis wpis(Bitmap bg, int w, int h) {
        return wpis(bg, w, h, null);
    }

    /** Jak wyżej; gotowa (AW*AH, 0/1) to wycięty ręcznie pierwszy plan zdjęcia dołączonego do aplikacji. */
    private static Wpis wpis(Bitmap bg, int w, int h, byte[] gotowa) {
        synchronized (WPISY) {
            Wpis jest = WPISY.get(bg);
            if (jest != null) return jest;
        }
        Float modGora = null;
        if (gotowa == null) {
            // tapeta oznaczona przy wczytaniu z pamięci podręcznej jako dołączona ulubiona: jej ręczne ustawienie
            Object[] oznaczona;
            synchronized (OZNACZONE) {
                oznaczona = OZNACZONE.get(bg);
            }
            if (oznaczona != null) {
                gotowa = (byte[]) oznaczona[0];
                modGora = (Float) oznaczona[1];
            }
        }
        Wpis nowy = new Wpis();
        nowy.goraStala = modGora;
        long t0 = android.os.SystemClock.uptimeMillis();
        Bitmap mala = doAnalizy(bg, w, h);
        nowy.baza = ModPlWygaszacz.baza(mala, gotowa);
        mala.recycle();
        ModPlDziennik.i(TAG, "linia nieba: " + (nowy.baza.pewne ? "pewna" : "niepewna") + ", " + nowy.baza.opis + ", "
                + (android.os.SystemClock.uptimeMillis() - t0) + " ms");
        synchronized (WPISY) {
            Wpis jest = WPISY.get(bg);
            if (jest != null) return jest;
            WPISY.put(bg, nowy);
        }
        return nowy;
    }

    /** Kontekst aplikacji do wczytania modelu wykrywacza (rysowanie sceny go nie podaje). */
    private static volatile Context app;

    private static Context kontekst() {
        Context c = app;
        if (c == null && MainActivity.activeInstance != null) c = MainActivity.activeInstance.getApplicationContext();
        return c;
    }

    /** Pojazd liczy się, gdy zajmuje co najmniej tyle kadru (małe auta w tle pomijamy). */
    private static final float MIN_POLE_AUTA = 0.04f;
    /** O tyle poniżej górnej krawędzi ramki auta wolno jeszcze szukać krawędzi dachu (niżej są szyby i maska). */
    static final float ZAPAS_DACHU = 0.07f;

    private static float wspolneX(ModPlDetektor.Pojazd p, float[] g) {
        return Math.max(0f, Math.min(p.prawo, g[1]) - Math.max(p.lewo, g[0])) / Math.max(1e-4f, g[1] - g[0]);
    }

    /**
     * Największe auto stojące pod zegarem (jego ramka pokrywa co najmniej ćwierć szerokości zegara); null, gdy brak.
     * g = {lewy brzeg cyfr, prawy brzeg, wysokość cyfr, domyślna góra} w ułamkach ekranu; używa też wygaszacz.
     */
    static ModPlDetektor.Pojazd autoPodZegarem(List<ModPlDetektor.Pojazd> pojazdy, float[] g) {
        ModPlDetektor.Pojazd naj = null;
        if (pojazdy == null) return null;
        for (ModPlDetektor.Pojazd p : pojazdy) {
            if (p.pole() < MIN_POLE_AUTA || wspolneX(p, g) < 0.25f) continue;
            if (naj == null || p.pole() > naj.pole()) naj = p;
        }
        return naj;
    }

    /**
     * Czy zegar z górą cyfr na wysokości „gora” leżałby na aucie: dach wykrytego auta wypada wyżej niż dolna 1/3 cyfr,
     * więc cyfry stałyby na kabinie zamiast chować się za dachem. Użytkownik 12.11.2026: „zegar jest dosłownie na
     * autach a to tak nie miało być”. Ta sama reguła działa w wygaszaczu (tam zegar stał na środku Ferrari).
     */
    static boolean naAucie(List<ModPlDetektor.Pojazd> pojazdy, float[] g, float gora) {
        if (pojazdy == null) return false;
        float hg = g[2];
        for (ModPlDetektor.Pojazd p : pojazdy) {
            if (p.pole() < MIN_POLE_AUTA || wspolneX(p, g) < 0.15f) continue;
            if (p.gora < gora + hg * 0.66f && p.dol > gora + hg * 0.5f) return true;
        }
        return false;
    }

    /**
     * Analiza dla jednego układu zegara; wynik zostaje we wpisie. Wołać z blokadą na wpisie.
     * Najpierw linia nieba; gdy zegar się za nią nie schowa, druga reguła: krawędź tuż pod zegarem
     * (ModPlWygaszacz.podZegarem, wspólna z wygaszaczem). Wykrywacz pojazdów pilnuje, żeby zegar nie leżał na aucie,
     * i podpowiada, gdzie jest dach.
     */
    private static ModPlWygaszacz.Analiza dlaUkladu(Wpis wp, Bitmap bg, int w, int h, float[] g) {
        String k = klucz(g);
        if (wp.analiza == null || !k.equals(wp.klucz)) {
            ModPlWygaszacz.Uklad uk = zbudujUklad(w, h, g);
            Bitmap mala = bg != null && !bg.isRecycled() ? doAnalizy(bg, w, h) : null;
            if (!wp.wykryte && mala != null && !wp.baza.gotowa) {
                Context c = kontekst();
                if (c != null) {
                    wp.pojazdy = ModPlDetektor.pojazdy(c, mala);
                    wp.wykryte = true;
                }
            }
            ModPlDetektor.Pojazd auto = autoPodZegarem(wp.pojazdy, g);
            ModPlWygaszacz.Analiza a = ModPlWygaszacz.umiesc(wp.baza, uk);
            wp.baza.c = null;
            String regula = "linia nieba";
            if (a.efekt && !wp.baza.gotowa && naAucie(wp.pojazdy, g, a.gora)) {
                a.efekt = false;
                a.opis += ", zegar leżałby na aucie";
            }
            if (!a.efekt && mala != null && !wp.baza.gotowa) {
                // gdy pod zegarem stoi auto, krawędzi szukamy tylko przy jego dachu, nie niżej przy szybach i masce
                ModPlWygaszacz.Analiza druga = ModPlWygaszacz.podZegarem(mala, uk, wp.baza.jasnosc,
                        auto != null ? auto.gora + ZAPAS_DACHU : 0f);
                if (druga != null && naAucie(wp.pojazdy, g, druga.gora)) {
                    a.opis += " | krawędź pod zegarem: zegar leżałby na aucie";
                } else if (druga != null) {
                    a = druga;
                    regula = "krawędź pod zegarem";
                }
            }
            if (mala != null) mala.recycle();
            if (a.efekt && !wp.baza.gotowa) {     // zdjęcia z wyciętym pierwszym planem są sprawdzone ręcznie
                float f = faktura(wp.baza.jasnosc, uk, a.gora);
                a.opis += String.format(Locale.US, ", tło za cyframi %.1f", f);
                if (f > FAKTURA_MAX) {
                    // zegar leżałby na czymś niespokojnym (zwykle na samym aucie): takie zdjęcie nie pasuje pod zegar
                    a.efekt = false;
                    a.opis += " (za niespokojne)";
                }
            }
            if (!a.efekt) {
                a.maska = null;
                a.gora = g[3];
            }
            if (auto != null) a.opis += String.format(Locale.US, ", dach auta %.0f%%", auto.gora * 100f);
            if (wp.goraStala != null) {
                // zdjęcie dołączone do aplikacji: zegar w miejscu ustawionym ręcznie, jak w stałej scenie
                a.gora = wp.goraStala;
                a.efekt = true;
                a.opis += ", położenie ustawione ręcznie";
            }
            wp.analiza = a;
            wp.klucz = k;
            ModPlDziennik.i(TAG, (a.efekt ? "chowa (" + regula + ")" : "bez efektu") + ", góra cyfr " + Math.round(a.gora * 1080) + ", " + a.opis);
        }
        return wp.analiza;
    }

    /**
     * Jak niespokojne jest tło za górnymi 75% cyfr: średnia zmiana jasności między sąsiednimi pikselami rozmytego
     * zdjęcia 640x360 (tablica jasności z części wspólnej analizy). Ten sam pomiar: narzedzia/proba_ocena.py.
     */
    private static float faktura(byte[] jasnosc, ModPlWygaszacz.Uklad u, float gora) {
        final int aw = ModPlWygaszacz.AW, ah = ModPlWygaszacz.AH;
        float hg = u.wysCyfr / u.h, razem = 0f;
        for (int i = 0; i < 4; i++) {
            int xa = Math.max(0, Math.round(u.x0[i] / u.w * aw)), xb = Math.min(aw - 1, Math.round(u.x1[i] / u.w * aw));
            int ya = Math.max(0, Math.round(gora * ah)), yb = Math.min(ah - 1, Math.round((gora + 0.75f * hg) * ah));
            long suma = 0;
            int ile = 0;
            for (int y = ya; y < yb; y++) {
                for (int x = xa; x < xb; x++) {
                    int v = jasnosc[y * aw + x] & 255;
                    suma += Math.abs((jasnosc[y * aw + x + 1] & 255) - v) + Math.abs((jasnosc[(y + 1) * aw + x] & 255) - v);
                    ile++;
                }
            }
            razem += ile > 0 ? suma / (float) ile : 0f;
        }
        return razem / 4f;
    }

    /**
     * Kadrowanie tapety pod zegar: szuka wycinka zdjęcia (z jak najmniejszym powiększeniem), przy którym dół cyfr
     * zegara stojącego w swoim miejscu wchodzi lekko za pierwszy plan, a reszta cyfr stoi na czystym niebie.
     * Warunki są te same co przy zwykłym doborze wysokości zegara (ModPlWygaszacz.dobierz). Zwraca najlepszy kadr
     * dla każdego powiększenia, od najlepiej ocenionego (po wycięciu maska liczy się od nowa i kadr może odpaść,
     * więc trzeba mieć następne w kolejce). Pusta lista, gdy nic nie pasuje.
     */
    private static List<Kadr> szukajKadrow(ModPlWygaszacz.Baza bz, float[] g) {
        List<Kadr> wynik = new ArrayList<Kadr>();
        if (!bz.pewne) return wynik;
        int[] c = ModPlWygaszacz.calka(bz.maska);
        ModPlWygaszacz.Uklad u = zbudujUklad(1920, 1080, g);
        float hg = g[2];
        for (float z : POWIEKSZENIA) {
            Kadr naj = null;
            float najOcena = 0f;
            float zapas = 1f - 1f / z;
            int kroki = Math.max(1, Math.round(zapas / KROK_KADRU));
            for (int ix = 0; ix <= kroki; ix++) {
                float ax = zapas * ix / kroki;
                for (int iy = 0; iy <= kroki; iy++) {
                    float ay = zapas * iy / kroki;
                    for (float t = u.tMin; t <= u.tMax + 1e-4f; t += KROK_T) {
                        float y0 = ay + t / z, y1 = ay + (t + hg) / z, yg = ay + (t + hg * 0.6f) / z;
                        float suma = 0f, maks = 0f, gora = 0f;
                        int dotkniete = 0;
                        for (int i = 0; i < 4; i++) {
                            float x0 = ax + u.x0[i] / u.w / z, x1 = ax + u.x1[i] / u.w / z;
                            float f = ModPlWygaszacz.srednia(c, x0, y0, x1, y1);
                            suma += f;
                            if (f > maks) maks = f;
                            if (f >= 0.02f) dotkniete++;
                            gora = Math.max(gora, ModPlWygaszacz.srednia(c, x0, y0, x1, yg));
                        }
                        float razem = suma / 4f;
                        if (razem < MIN_RAZEM || razem > MAKS_RAZEM || maks > MAKS_CYFRY || gora > CZYSTA_GORA || dotkniete < MIN_CYFR) continue;
                        if (ModPlWygaszacz.srednia(c, ax + u.dx0 / u.w / z, y0, ax + u.dx1 / u.w / z, y1) > u.maksDwukropka) continue;
                        // im więcej cyfr dotyka pierwszego planu, tym naturalniej to wygląda
                        float ocena = Math.min(razem, DOSC_SCHOWANE) + 0.006f * dotkniete
                                - KARA_ZA_DOL * (t - u.tMin) - KARA_ZA_POWIEKSZENIE * (z - 1f);
                        if (naj == null || ocena > najOcena) {
                            naj = new Kadr();
                            naj.z = z;
                            naj.ax = ax;
                            naj.ay = ay;
                            naj.t = t;
                            naj.schowane = razem;
                            naj.ocena = ocena;
                            najOcena = ocena;
                        }
                    }
                }
            }
            if (naj != null) wynik.add(naj);
        }
        java.util.Collections.sort(wynik, new java.util.Comparator<Kadr>() {
            @Override public int compare(Kadr a, Kadr b) {
                return Float.compare(b.ocena, a.ocena);
            }
        });
        return wynik;
    }

    /** Wycinek zdjęcia w rozdzielczości ekranu: z = 1 to zwykły kadr 16:9 (środek zdjęcia), większe z to jego część. */
    private static Bitmap wytnij(Bitmap zrodlo, float z, float ax, float ay) {
        int w = 1920, h = 1080;
        Bitmap kadr = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        float skala = Math.max(w / (float) zrodlo.getWidth(), h / (float) zrodlo.getHeight());
        Matrix m = new Matrix();
        m.setScale(skala, skala);
        m.postTranslate((w - zrodlo.getWidth() * skala) / 2f, (h - zrodlo.getHeight() * skala) / 2f);
        m.postTranslate(-ax * w, -ay * h);
        m.postScale(z, z);
        new Canvas(kadr).drawBitmap(zrodlo, m, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        return kadr;
    }

    private static void zapomnij(Bitmap b) {
        synchronized (WPISY) {
            WPISY.remove(b);
        }
        b.recycle();
    }

    /**
     * Przygotowuje tapetę ekranu głównego ze zdjęcia źródłowego (zdjęcia źródłowego nie zwalnia): zwykły kadr
     * 1920x1080, a gdy zegar się za nim nie schowa i kadrowanie jest włączone, wycinek dobrany pod zegar.
     * Zwraca null, gdy zegar nie schowa się w żadnym kadrze; ulubiona wraca wtedy w zwykłym kadrze.
     */
    private static Bitmap dopasuj(Context c, Bitmap zrodlo, boolean ulubiona) {
        return dopasuj(c, zrodlo, ulubiona, null);
    }

    private static Bitmap dopasuj(Context c, Bitmap zrodlo, boolean ulubiona, byte[] gotowa) {
        return dopasuj(c, zrodlo, ulubiona, gotowa, null);
    }

    private static Bitmap dopasuj(Context c, Bitmap zrodlo, boolean ulubiona, byte[] gotowa, Float goraStala) {
        int w = 1920, h = 1080;
        float[] g = uklad;
        Bitmap caly = wytnij(zrodlo, 1f, 0f, 0f);
        Wpis wp = wpis(caly, w, h, gotowa);
        if (goraStala != null) {
            synchronized (wp) {
                wp.goraStala = goraStala;
                wp.analiza = null;
            }
        }
        boolean pasuje;
        synchronized (wp) {
            pasuje = dlaUkladu(wp, caly, w, h, g).efekt;
        }
        if (pasuje || gotowa != null) return caly;
        if (kadrowanie(c)) {
            for (Kadr k : szukajKadrow(wp.baza, g)) {
                Bitmap wycinek = wytnij(zrodlo, k.z, k.ax, k.ay);
                Wpis wp2 = wpis(wycinek, w, h);
                boolean dobry;
                synchronized (wp2) {
                    dobry = dlaUkladu(wp2, wycinek, w, h, g).efekt;
                }
                ModPlDziennik.i(TAG, String.format(Locale.US, "kadr pod zegar: powiększenie %.2f, od lewej %.0f%%, od góry %.0f%%, schowane %.0f%% -> %s",
                        k.z, k.ax * 100f, k.ay * 100f, k.schowane * 100f, dobry ? "przyjęty" : "po przeliczeniu bez efektu"));
                if (dobry) {
                    zapomnij(caly);
                    return wycinek;
                }
                zapomnij(wycinek);
            }
        }
        if (ulubiona) return caly;
        zapomnij(caly);
        return null;
    }

    /**
     * Czy tapety z internetu wolno powiększać i przesuwać pod zegar. Domyślnie nie: użytkownik 12.11.2026 uznał, że
     * bez powiększania zdjęć jest lepiej (auto zjeżdżało pod kafelki albo traciło tył). Opcja została w ustawieniach tapet.
     */
    static boolean kadrowanie(Context c) {
        return prefs(c).getBoolean("tap_kadruj", false);
    }

    static void przelaczKadrowanie(Context c) {
        prefs(c).edit().putBoolean("tap_kadruj", !kadrowanie(c)).apply();
    }

    // ------------------------------------------------------------------ rysowanie (Scenes.drawScene)

    /**
     * Górny brzeg cyfr zegara w pikselach dla tej tapety. Gdy zegar nie ma się za czym schować, zwraca położenie
     * domyślne. Wołane przy rysowaniu sceny, więc zapamiętuje też układ zegara dla wątku pobierania.
     */
    static float gora(Bitmap bg, int w, int h, float lewo, float prawo, float wysCyfr, float goraDomyslna) {
        if (bg == null || bg.isRecycled() || w <= 0 || h <= 0 || prawo <= lewo) return goraDomyslna;
        try {
            float[] g = {lewo / w, prawo / w, wysCyfr / h, goraDomyslna / h};
            uklad = g;
            Wpis wp = wpis(bg, w, h);
            synchronized (wp) {
                ModPlWygaszacz.Analiza a = dlaUkladu(wp, bg, w, h, g);
                return a.efekt ? a.gora * h : goraDomyslna;
            }
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "analiza tapety", e);
            return goraDomyslna;
        }
    }

    /**
     * Czy zegar w obecnym układzie chowa się za tą tapetą. Wołane przy wczytywaniu sceny (Scenes.bakeLoadedGlassFromBg):
     * taka tapeta dostaje szkło i styl cyfr jak wbudowane sceny. W razie potrzeby liczy analizę (ok. 0,25 s).
     */
    static boolean chowa(Bitmap bg) {
        if (bg == null || bg.isRecycled()) return false;
        try {
            Wpis wp = wpis(bg, 1920, 1080);
            synchronized (wp) {
                return dlaUkladu(wp, bg, 1920, 1080, uklad).efekt;
            }
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "czy chowa", e);
            return false;
        }
    }

    /** Czy ta tapeta ma policzony pierwszy plan, za który wchodzi zegar (tani odczyt, bez liczenia). */
    static boolean maPrzod(Bitmap bg) {
        Wpis wp = gotowyWpis(bg);
        if (wp == null) return false;
        synchronized (wp) {
            return wp.analiza != null && wp.analiza.efekt;
        }
    }

    /**
     * Szkło cyfr „jak u autora” (wybór użytkownika z kanwy 12.11.2026, na całym ekranie głównym): rozmyta tapeta,
     * rozjaśniona ok. 1,3 raza i z mocniejszymi kolorami, tak jak autor przygotował pliki scene-*-glass.jpg
     * (zmierzone na pięciu scenach: rozmycie Gaussa 8 przy 960x540, jasność ×1,3, nasycenie +15%). Wejście 480x270
     * z kadr480(); rozmycie pudełkowe 3 px w 4 przejściach daje to samo co Gauss 8 przy 960x540. Zwraca nową bitmapę.
     */
    static Bitmap szkloAutora(Bitmap mala) {
        int w = mala.getWidth(), h = mala.getHeight();
        Bitmap rozmyta = Ui.blur(mala, w, h, 3, 4);
        Bitmap wynik = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1.15f);
        cm.postConcat(new ColorMatrix(new float[]{
                1.3f, 0, 0, 0, 0,
                0, 1.3f, 0, 0, 0,
                0, 0, 1.3f, 0, 0,
                0, 0, 0, 1f, 0}));
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        new Canvas(wynik).drawBitmap(rozmyta != null ? rozmyta : mala, 0, 0, p);
        if (rozmyta != null && rozmyta != mala) rozmyta.recycle();
        return wynik;
    }

    /**
     * Gotowa tekstura szkła cyfr wygaszacza w stylu scen autora: kadr 480x270, przeróbka autora (szkloAutora), a potem
     * to samo co Scenes.bakeGlassTexture: rozmycie pudełkowe 2x2, nasycenie 1,25 i jasność 1,08 (dla ciemnego zegara
     * nasycenie 0,9 i przyciemnienie 0,46) oraz ukośny połysk. Tak samo jak na tapetach ekranu głównego.
     */
    static Bitmap szkloSceny(Bitmap zdjecie, boolean ciemny) {
        Bitmap kadr = kadr480(zdjecie);
        Bitmap autor = szkloAutora(kadr);
        kadr.recycle();
        Bitmap baza = Ui.blur(autor, 480, 270, 2, 2);
        if (baza == null) baza = autor;
        else autor.recycle();
        Bitmap wynik = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(wynik);
        ColorMatrix cm = new ColorMatrix();
        if (ciemny) {
            cm.setSaturation(0.9f);
            cm.postConcat(new ColorMatrix(new float[]{
                    0.46f, 0, 0, 0, 0,
                    0, 0.46f, 0, 0, 0,
                    0, 0, 0.46f, 0, 0,
                    0, 0, 0, 1f, 0}));
        } else {
            cm.setSaturation(1.25f);
            cm.postConcat(new ColorMatrix(new float[]{
                    1.08f, 0, 0, 0, 10,
                    0, 1.08f, 0, 0, 8,
                    0, 0, 1.08f, 0, 10,
                    0, 0, 0, 1f, 0}));
        }
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        cv.drawBitmap(baza, 0, 0, p);
        baza.recycle();
        Paint polysk = new Paint(Paint.ANTI_ALIAS_FLAG);
        int[] kolory = ciemny
                ? new int[]{Color.argb(65, 255, 255, 255), Color.argb(28, 255, 255, 255), Color.argb(10, 255, 255, 255), Color.argb(25, 255, 255, 255)}
                : new int[]{Color.argb(68, 255, 255, 255), Color.argb(28, 255, 255, 255), Color.argb(10, 255, 255, 255), Color.argb(26, 255, 255, 255)};
        polysk.setShader(new LinearGradient(0, 0, 480 * 0.45f, 270, kolory, new float[]{0f, 0.35f, 0.65f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, 480, 270, polysk);
        return wynik;
    }

    /** Tapeta pomniejszona do 480x270 (kadr ze środka, jak tło sceny): surowiec na teksturę szkła zegara. */
    static Bitmap kadr480(Bitmap bg) {
        int w = 480, h = 270;
        Bitmap mala = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        float skala = Math.max(w / (float) bg.getWidth(), h / (float) bg.getHeight());
        Matrix m = new Matrix();
        m.setScale(skala, skala);
        m.postTranslate((w - bg.getWidth() * skala) / 2f, (h - bg.getHeight() * skala) / 2f);
        new Canvas(mala).drawBitmap(bg, m, new Paint(Paint.FILTER_BITMAP_FLAG));
        return mala;
    }

    /**
     * Sceny, przy których zegar ustawia analiza zdjęcia: własne tapety, tapety z internetu i wbudowane Lamborghini.
     * Audi RS7 nocą ma wycięty pierwszy plan i stałe miejsce zegara, jak oryginalne sceny launchera.
     */
    static boolean analizowana(String id) {
        return Scenes.isCustomId(id) || "lambo".equals(id);
    }

    /**
     * Wycięty ręcznie pierwszy plan ulubionej dołączonej do aplikacji („w-ulubiona-<nazwa>.jpg” ma obok plik
     * assets/mod_pl/scena-<nazwa>-fg.webp) jako maska analizy 640x360; null, gdy takiego pliku nie ma.
     */
    /**
     * Launcher wczytał bieżącą tapetę z internetu z pamięci podręcznej (np. po ponownym uruchomieniu). Jeśli to dołączona
     * ulubiona z ręcznym ustawieniem (np. Audi RS7 nocą), zapamiętujemy, żeby analiza użyła wyciętego pierwszego planu.
     */
    static void oznacz(Context c, String zakres, Bitmap b) {
        if (c == null || b == null) return;
        try {
            String plik = prefs(c).getString("tap_ulubiona_" + zakres, "");
            byte[] maska = maskaDolaczonej(c, plik);
            if (maska == null) return;
            synchronized (OZNACZONE) {
                OZNACZONE.put(b, new Object[]{maska, goraDolaczonej(plik)});
            }
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "oznaczanie tapety", e);
        }
    }

    /** Ręcznie ustawiona góra cyfr zegara dla dołączonych zdjęć (jak clockTopU w scenie; 1080 px wysokości ekranu). */
    private static Float goraDolaczonej(String plik) {
        if ("w-ulubiona-audi-rs7.jpg".equals(plik)) return 217f / 1080f;     // scena_rs7.py, kadr 1,12
        return null;
    }

    private static byte[] maskaDolaczonej(Context c, String plik) {
        if (plik == null || !plik.startsWith("w-ulubiona-") || !plik.endsWith(".jpg")) return null;
        String nazwa = plik.substring("w-ulubiona-".length(), plik.length() - 4);
        try {
            java.io.InputStream we = c.getAssets().open("mod_pl/scena-" + nazwa + "-fg.webp");
            Bitmap przod;
            try {
                przod = android.graphics.BitmapFactory.decodeStream(we);
            } finally {
                we.close();
            }
            if (przod == null) return null;
            Bitmap mala = Bitmap.createScaledBitmap(przod, ModPlWygaszacz.AW, ModPlWygaszacz.AH, true);
            int[] px = new int[ModPlWygaszacz.AW * ModPlWygaszacz.AH];
            mala.getPixels(px, 0, ModPlWygaszacz.AW, 0, 0, ModPlWygaszacz.AW, ModPlWygaszacz.AH);
            if (mala != przod) mala.recycle();
            przod.recycle();
            byte[] maska = new byte[px.length];
            for (int i = 0; i < px.length; i++) maska[i] = (byte) ((px[i] >>> 24) > 127 ? 1 : 0);
            ModPlDziennik.i(TAG, "wycięty pierwszy plan dla " + plik);
            return maska;
        } catch (java.io.FileNotFoundException e) {
            return null;
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "pierwszy plan dołączonej tapety", e);
            return null;
        }
    }

    /** Wbudowane tapety moda (auta), pokazywane na początku list scen, zaraz pod tapetami z internetu. */
    static final String[] AUTA_MODA = {"lambo", "audi_rs7"};

    static boolean autoModa(String id) {
        for (String a : AUTA_MODA) if (a.equals(id)) return true;
        return false;
    }

    /** Lista scen do kreatora: najpierw auta moda, potem pozostałe w dotychczasowej kolejności. */
    static Scenes.Spec[] najpierwAuta(Scenes.Spec[] wszystkie) {
        java.util.List<Scenes.Spec> wynik = new java.util.ArrayList<Scenes.Spec>();
        for (String a : AUTA_MODA) {
            for (Scenes.Spec s : wszystkie) if (a.equals(s.id)) wynik.add(s);
        }
        for (Scenes.Spec s : wszystkie) if (!autoModa(s.id)) wynik.add(s);
        return wynik.toArray(new Scenes.Spec[0]);
    }

    /** Zmiana tapety zamówiona ręcznie nic nie znalazła: krótki komunikat, obecna tapeta zostaje. */
    static void nieZnaleziono(final Context c) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                MainActivity act = MainActivity.activeInstance;
                if (act != null) act.toast(I18n.t(act, "No wallpaper fitting the clock was found, the current one stays"));
            }
        });
    }

    private static Wpis gotowyWpis(Bitmap bg) {
        if (bg == null) return null;
        synchronized (WPISY) {
            return WPISY.get(bg);
        }
    }

    /** Czy data i powitanie nad zegarem mają być ciemne (jasne tło). Po gora() dla tej samej tapety. */
    static boolean ciemnaData(Bitmap bg) {
        Wpis wp = gotowyWpis(bg);
        if (wp == null) return false;
        synchronized (wp) {
            return wp.analiza != null && wp.analiza.ciemnaData;
        }
    }

    /** Rysuje pierwszy plan tapety na zegarze: zdjęcie jeszcze raz, ale tylko tam, gdzie maska mówi „pierwszy plan”. */
    static void rysujPrzod(Canvas canvas, Bitmap bg, int w, int h) {
        if (bg == null || bg.isRecycled()) return;
        Wpis wp = gotowyWpis(bg);
        if (wp == null) return;
        try {
            synchronized (wp) {
                if (wp.analiza == null || !wp.analiza.efekt || wp.analiza.maska == null) return;
                if (wp.maska == null || wp.maskaZ != wp.analiza.maska) {
                    if (wp.maska == null) wp.maska = Bitmap.createBitmap(ModPlWygaszacz.AW, ModPlWygaszacz.AH, Bitmap.Config.ALPHA_8);
                    wp.maska.copyPixelsFromBuffer(ByteBuffer.wrap(wp.analiza.maska));
                    wp.maskaZ = wp.analiza.maska;
                }
                if (wp.cieniowanie == null) wp.cieniowanie = new BitmapShader(bg, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                // Rysujemy w układzie maski (640x360), a kanwę skalujemy do ekranu. Maska rozciągana wprost do ekranu
                // na kanwie programowej (ekran główny rysuje do bitmapy) skalowała razem z sobą zdjęcie z cieniowania.
                float sx = ModPlWygaszacz.AW / (float) w, sy = ModPlWygaszacz.AH / (float) h;
                float skala = Math.max(w / (float) bg.getWidth(), h / (float) bg.getHeight());
                MACIERZ.setScale(skala, skala);
                MACIERZ.postTranslate((w - bg.getWidth() * skala) / 2f, (h - bg.getHeight() * skala) / 2f);
                MACIERZ.postScale(sx, sy);
                wp.cieniowanie.setLocalMatrix(MACIERZ);
                PEDZEL.setShader(wp.cieniowanie);
                canvas.save();
                canvas.clipRect(0, 0, w, h * 0.6f);       // niżej launcher przyciemnia tło pod kafelki i dok: tam nic nie zmieniamy
                canvas.scale(1f / sx, 1f / sy);
                canvas.drawBitmap(wp.maska, 0f, 0f, PEDZEL);
                canvas.restore();
            }
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "pierwszy plan", e);
        }
    }

    // ------------------------------------------------------------------ pobieranie (CustomMedia, wątek w tle)

    /**
     * Świeżo pobrana tapeta z internetu: zwraca ją wykadrowaną do 1920x1080 (w razie potrzeby pod zegar) albo null,
     * gdy zegar się za nią nie schowa (launcher bierze wtedy następną z listy). Odrzucony adres zapamiętujemy,
     * żeby nie pobierać tej samej tapety przy kolejnych zmianach.
     */
    static Bitmap sprawdz(Context c, String zakres, String adres, Bitmap pobrane) {
        if (pobrane == null) return null;
        try {
            if (!CustomMedia.SCOPE_SCENE.equals(zakres)) return pobrane;      // klasyczny i ekran blokady: bez tego efektu
            app = c.getApplicationContext();
            if (adres != null && adres.contains("wallhaven.cc") && !ModPlWygaszacz.nocne(pobrane)) {
                // auta i miasta z Wallhaven są w stylu RS7 (nocą, w neonach): zdjęcia za dnia odpadają jak w wygaszaczu
                pobrane.recycle();
                zapamietajOdrzucona(c, adres);
                ModPlDziennik.i(TAG, "odrzucona (za jasna, nie nocna): " + adres);
                return null;
            }
            Bitmap wynik = dopasuj(c, pobrane, false);
            pobrane.recycle();
            if (wynik == null) zapamietajOdrzucona(c, adres);
            ModPlDziennik.i(TAG, (wynik != null ? "przyjęta: " : "odrzucona: ") + adres);
            return wynik;
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "sprawdzanie tapety", e);
            return pobrane.isRecycled() ? null : pobrane;
        }
    }

    private static final int MAKS_ODRZUCONYCH = 800;

    /** Odrzucenia są ważne tylko dla tych samych reguł: układu zegara i ustawienia kadrowania. */
    private static String regulyOdrzucen(Context c) {
        return "r3|" + klucz(uklad) + "|" + (kadrowanie(c) ? 1 : 0);
    }

    private static String skrotAdresu(String adres) {
        return Integer.toHexString(adres.hashCode());
    }

    private static String odrzucone(Context c) {
        SharedPreferences p = prefs(c);
        return regulyOdrzucen(c).equals(p.getString("tap_odrzucone_reguly", "")) ? p.getString("tap_odrzucone", "") : "";
    }

    private static void zapamietajOdrzucona(Context c, String adres) {
        if (adres == null) return;
        String lista = odrzucone(c), skrot = " " + skrotAdresu(adres);
        if ((lista + " ").contains(skrot + " ")) return;
        lista += skrot;
        int nadmiar = lista.length() - lista.replace(" ", "").length() - MAKS_ODRZUCONYCH;
        for (int i = 0; i < nadmiar; i++) lista = lista.substring(lista.indexOf(' ', 1));    // najstarsze wypadają
        prefs(c).edit().putString("tap_odrzucone", lista).putString("tap_odrzucone_reguly", regulyOdrzucen(c)).apply();
    }

    /** Lista adresów bez tapet, które już raz odpadły (gdyby odpadły wszystkie, zostaje cała lista). */
    static List<String> bezOdrzuconych(Context c, String zakres, List<String> adresy) {
        try {
            if (!CustomMedia.SCOPE_SCENE.equals(zakres)) return adresy;
            String lista = odrzucone(c) + " ";
            List<String> dobre = new ArrayList<String>();
            for (String a : adresy) {
                if (!lista.contains(" " + skrotAdresu(a) + " ")) dobre.add(a);
            }
            if (dobre.size() < adresy.size()) ModPlDziennik.i(TAG, "pominięte wcześniej odrzucone tapety: " + (adresy.size() - dobre.size()));
            return dobre.isEmpty() ? adresy : dobre;
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "lista odrzuconych", e);
            return adresy;
        }
    }

    /** Czy to zdjęcie przeszło już przez sprawdz() albo jest ulubioną (jest wykadrowane i ma gotową analizę). */
    static boolean gotowa(Bitmap b) {
        if (b == null) return false;
        synchronized (WPISY) {
            return WPISY.containsKey(b);
        }
    }

    // ------------------------------------------------------------------ ulubione tapety

    private static final String KATALOG = "mod_pl_ulubione";
    /** Co którą zmianę tapety wraca ulubiona: 0 = nigdy, 1 = same ulubione. */
    private static final int[] CO_ILE = {0, 5, 3, 2, 1};
    /** Ulubione wracają domyślnie co piątą zmianę tapety (prośba użytkownika 12.11.2026, dla wszystkich instalacji). */
    private static final int CO_ILE_DOMYSLNIE = 5;
    /** Zakres -> plik ulubionej wybranej w trwającej zmianie tapety (do chwili, gdy launcher ją pokaże). */
    private static final Map<String, String> WYBRANA = new HashMap<String, String>();

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("mod_pl", Context.MODE_PRIVATE);
    }

    private static File katalog(Context c) {
        File k = new File(c.getFilesDir(), KATALOG);
        if (!k.exists()) k.mkdirs();
        return k;
    }

    /** Plik, w którym launcher trzyma bieżącą tapetę z internetu (CustomMedia.redditCacheFile). */
    private static File biezacyPlik(Context c, String zakres) {
        return new File(new File(c.getFilesDir(), "reddit_wallpapers"), "reddit_wall_" + zakres + ".jpg");
    }

    private static File[] ulubione(Context c) {
        File[] pliki = katalog(c).listFiles();
        List<File> lista = new ArrayList<File>();
        if (pliki != null) {
            for (File f : pliki) {
                if (f.isFile() && f.getName().endsWith(".jpg") && f.length() > 0) lista.add(f);
            }
        }
        File[] wynik = lista.toArray(new File[0]);
        Arrays.sort(wynik);
        return wynik;
    }

    static int ileUlubionych(Context c) {
        return ulubione(c).length;
    }

    /** Zakres tapet bieżącego wyglądu (jak w CustomMedia.skipToNextRedditWallpaperOnCurrentLook). */
    static String zakres(Context c) {
        return Scenes.isScenesLook(c) ? CustomMedia.SCOPE_SCENE : CustomMedia.SCOPE_CLASSIC;
    }

    /** Czy tapeta, która jest teraz na ekranie, należy do ulubionych. */
    static boolean biezacaUlubiona(Context c, String zakres) {
        String n = prefs(c).getString("tap_ulubiona_" + zakres, "");
        return n.length() > 0 && new File(katalog(c), n).isFile();
    }

    static String tytulUlubionej(Context c, String zakres) {
        return I18n.t(c, biezacaUlubiona(c, zakres) ? "Remove this wallpaper from favourites" : "Add this wallpaper to favourites");
    }

    static String opisUlubionej(Context c, String zakres) {
        return I18n.t(c, biezacaUlubiona(c, zakres) ? "It will no longer come back" : "It will come back more often");
    }

    /** Dodaje bieżącą tapetę do ulubionych albo ją z nich usuwa. Zwraca komunikat dla użytkownika. */
    static String przelaczUlubiona(Context c, String zakres) {
        try {
            SharedPreferences p = prefs(c);
            String klucz = "tap_ulubiona_" + zakres;
            String n = p.getString(klucz, "");
            if (n.length() > 0 && new File(katalog(c), n).isFile()) {
                new File(katalog(c), n).delete();
                p.edit().remove(klucz).apply();
                return I18n.t(c, "Removed from favourites");
            }
            File zrodlo = biezacyPlik(c, zakres);
            if (!zrodlo.isFile() || zrodlo.length() == 0) return I18n.t(c, "Could not save this wallpaper");
            String nazwa = "u" + System.currentTimeMillis() + ".jpg";
            File tymczasowy = new File(katalog(c), nazwa + ".tmp"), cel = new File(katalog(c), nazwa);
            FileInputStream we = new FileInputStream(zrodlo);
            try {
                FileOutputStream wy = new FileOutputStream(tymczasowy);
                try {
                    byte[] bufor = new byte[65536];
                    for (int ile; (ile = we.read(bufor)) > 0; ) wy.write(bufor, 0, ile);
                    wy.flush();
                } finally {
                    wy.close();
                }
            } finally {
                we.close();
            }
            if (!tymczasowy.renameTo(cel)) {
                tymczasowy.delete();
                return I18n.t(c, "Could not save this wallpaper");
            }
            p.edit().putString(klucz, nazwa).apply();
            return I18n.t(c, "Added to favourites");
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "ulubiona", e);
            return I18n.t(c, "Could not save this wallpaper");
        }
    }

    static void usunWszystkie(Context c) {
        for (File f : ulubione(c)) f.delete();
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit();
        for (String k : p.getAll().keySet()) {
            if (k.startsWith("tap_ulubiona_")) e.remove(k);
        }
        e.apply();
    }

    static int coIle(Context c) {
        int v = prefs(c).getInt("tap_ulubione_co_ile", CO_ILE_DOMYSLNIE);
        for (int d : CO_ILE) if (d == v) return v;
        return CO_ILE_DOMYSLNIE;
    }

    static void nastepneCoIle(Context c) {
        int v = coIle(c), nast = CO_ILE[0];
        for (int i = 0; i < CO_ILE.length; i++) {
            if (CO_ILE[i] == v) nast = CO_ILE[(i + 1) % CO_ILE.length];
        }
        prefs(c).edit().putInt("tap_ulubione_co_ile", nast).apply();
    }

    static String opisCoIle(Context c) {
        switch (coIle(c)) {
            case 0: return I18n.t(c, "Never");
            case 1: return I18n.t(c, "Favourites only");
            case 2: return I18n.t(c, "Every 2nd wallpaper");
            case 3: return I18n.t(c, "Every 3rd wallpaper");
            default: return I18n.t(c, "Every 5th wallpaper");
        }
    }

    private static List<File> bez(File[] pliki, String a, String b) {
        List<File> lista = new ArrayList<File>();
        for (File f : pliki) {
            if (!f.getName().equals(a) && !f.getName().equals(b)) lista.add(f);
        }
        return lista;
    }

    /**
     * Wołane na początku każdej zmiany tapety (wątek w tle): gdy przyszła kolej ulubionej, zwraca ją jako gotowe
     * zdjęcie i launcher niczego nie pobiera. Null oznacza zwykłą, nową tapetę z internetu.
     * Ulubionych nie odrzucamy, nawet gdy zegar nie ma się za czym schować: użytkownik sam je wybrał.
     */
    static Bitmap zUlubionych(Context c, String zakres) {
        try {
            synchronized (WYBRANA) {
                WYBRANA.remove(zakres);
            }
            int co = coIle(c);
            if (co == 0) return null;
            File[] pliki = ulubione(c);
            if (pliki.length == 0) return null;
            SharedPreferences p = prefs(c);
            if (co > 1) {
                int licznik = p.getInt("tap_ulubione_licznik", 0) + 1;
                p.edit().putInt("tap_ulubione_licznik", licznik).apply();
                if (licznik % co != 0) return null;
            }
            String teraz = p.getString("tap_ulubiona_" + zakres, ""), ostatnia = p.getString("tap_ulubiona_ostatnia", "");
            List<File> wybor = bez(pliki, teraz, ostatnia);          // nie ta, która jest na ekranie, i nie ta sama co ostatnio
            if (wybor.isEmpty()) wybor = bez(pliki, teraz, null);
            if (wybor.isEmpty()) {
                if (co > 1) return null;                             // jedyna ulubiona jest właśnie na ekranie: teraz nowa tapeta
                wybor = Arrays.asList(pliki);
            }
            File f = wybor.get(new Random().nextInt(wybor.size()));
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
            if (b == null) return null;
            if (CustomMedia.SCOPE_SCENE.equals(zakres)) {
                Bitmap dopasowana = dopasuj(c, b, true, maskaDolaczonej(c, f.getName()), goraDolaczonej(f.getName()));
                b.recycle();
                b = dopasowana;
            }
            synchronized (WYBRANA) {
                WYBRANA.put(zakres, f.getName());
            }
            p.edit().putString("tap_ulubiona_ostatnia", f.getName()).apply();
            ModPlDziennik.i(TAG, "ulubiona " + f.getName() + " (z " + pliki.length + ")");
            return b;
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "wybór ulubionej", e);
            return null;
        }
    }

    /** Launcher zapisał i pokazuje nową tapetę: zapamiętujemy, czy to ulubiona (i która). */
    static void pokazana(Context c, String zakres) {
        try {
            String n;
            synchronized (WYBRANA) {
                n = WYBRANA.remove(zakres);
            }
            SharedPreferences.Editor e = prefs(c).edit();
            if (n != null) e.putString("tap_ulubiona_" + zakres, n);
            else e.remove("tap_ulubiona_" + zakres);
            e.apply();
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "pokazana tapeta", e);
        }
    }

    // ------------------------------------------------------------------ ustawienie domyślne i kreator

    /** Czy ekran główny używa tapet z internetu. */
    static boolean wlaczone(Context c) {
        return CustomMedia.isCustomWallpaperEnabled(c, CustomMedia.SCOPE_SCENE)
                && CustomMedia.getCustomWallpaperSubMode(c, CustomMedia.SCOPE_SCENE) == CustomMedia.CUSTOM_MODE_REDDIT;
    }

    static void ustaw(Context c, boolean wlacz) {
        if (wlacz) {
            CustomMedia.setCustomWallpaperSubMode(c, CustomMedia.SCOPE_SCENE, CustomMedia.CUSTOM_MODE_REDDIT);
            CustomMedia.setCustomWallpaperEnabled(c, CustomMedia.SCOPE_SCENE, true);
            CustomMedia.fetchNextRedditWallpaperAsync(c, CustomMedia.SCOPE_SCENE, null);
        } else {
            CustomMedia.setCustomWallpaperEnabled(c, CustomMedia.SCOPE_SCENE, false);
        }
    }

    /**
     * Świeża instalacja (kreator jeszcze nieukończony): tapety z internetu są włączone od razu. Robione raz;
     * w istniejącej instalacji niczego nie zmienia.
     */
    /**
     * Tapety dołączone do aplikacji (assets/mod_pl/ulubiona-*.jpg) trafiają raz do ulubionych, więc wracają co którąś
     * zmianę tapety jak każda ulubiona i można je usunąć. Użytkownik 12.11.2026: czerwone Lamborghini „możesz dodać
     * do apki na stałe”; potem Audi RS7 nocą. Każdy plik dokładany jest raz (lista nazw w „tap_wbudowane_nazwy”),
     * więc usunięta przez użytkownika nie wraca, a nowa dochodzi także w istniejącej instalacji. Tapeta zapisana
     * wcześniej ręcznie (ten sam plik) nie jest dublowana.
     */
    private static void wbudowane(Context c) {
        SharedPreferences p = prefs(c);
        String dodane = "|" + p.getString("tap_wbudowane_nazwy", "") + "|";
        if (p.getBoolean("tap_wbudowane_1", false) && !dodane.contains("|ulubiona-lamborghini.jpg|")) {
            dodane += "ulubiona-lamborghini.jpg|";        // wersja z jedną flagą dołożyła już Lamborghini
        }
        // wersja zestawu dołączonych zdjęć: przy nowej wersji podmieniamy dołożone wcześniej kopie, jeśli plik się zmienił
        // (2: Audi RS7 z wyciętym dachem; 3: RS7 w kadrze 1,12 z wyciętym dachem i postacią)
        final int wersja = 3;
        boolean odswiez = p.getInt("tap_wbudowane_wersja", 1) < wersja;
        try {
            String[] nazwy = c.getAssets().list("mod_pl");
            if (nazwy == null) return;
            File[] zapisane = ulubione(c);
            for (String nazwa : nazwy) {
                if (!nazwa.startsWith("ulubiona-") || !nazwa.endsWith(".jpg")) continue;
                boolean juzBylo = dodane.contains("|" + nazwa + "|");
                if (juzBylo && !odswiez) continue;
                java.io.InputStream we = c.getAssets().open("mod_pl/" + nazwa);
                java.io.ByteArrayOutputStream calosc = new java.io.ByteArrayOutputStream();
                try {
                    byte[] bufor = new byte[65536];
                    for (int ile; (ile = we.read(bufor)) > 0; ) calosc.write(bufor, 0, ile);
                } finally {
                    we.close();
                }
                if (juzBylo) {
                    File kopia = new File(katalog(c), "w-" + nazwa);
                    if (kopia.isFile() && kopia.length() != calosc.size()) {
                        FileOutputStream wy = new FileOutputStream(kopia);
                        try {
                            calosc.writeTo(wy);
                        } finally {
                            wy.close();
                        }
                        ModPlDziennik.i(TAG, "odświeżona ulubiona: " + nazwa);
                    }
                    continue;
                }
                dodane += nazwa + "|";
                boolean jest = false;
                for (File f : zapisane) if (f.length() == calosc.size()) jest = true;
                if (jest) continue;
                FileOutputStream wy = new FileOutputStream(new File(katalog(c), "w-" + nazwa));
                try {
                    calosc.writeTo(wy);
                } finally {
                    wy.close();
                }
                ModPlDziennik.i(TAG, "dołączona ulubiona: " + nazwa);
            }
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "dołączone tapety", e);
        }
        String zapis = dodane.replaceAll("^\\|+|\\|+$", "");
        p.edit().putString("tap_wbudowane_nazwy", zapis).putBoolean("tap_wbudowane_1", true)
                .putInt("tap_wbudowane_wersja", wersja).apply();
    }

    static void domyslnie(Context c) {
        app = c.getApplicationContext();
        wbudowane(c);
        try {
            SharedPreferences pu = prefs(c);
            if (!pu.getBoolean("tap_co_ile_5", false)) {
                // jednorazowo: wcześniejsza wartość domyślna (co trzecia) ustępuje nowej (co piąta)
                pu.edit().putBoolean("tap_co_ile_5", true).remove("tap_ulubione_co_ile").apply();
            }
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "częstość ulubionych", e);
        }
        try {
            SharedPreferences p = prefs(c);
            if (p.getBoolean("tap_domyslne", false)) return;
            p.edit().putBoolean("tap_domyslne", true).apply();
            boolean poKreatorze = c.getSharedPreferences("setup", Context.MODE_PRIVATE).getBoolean("setup_done", false);
            if (!poKreatorze) ustaw(c, true);
        } catch (Throwable e) {
            ModPlDziennik.w(TAG, "ustawienie domyślne", e);
        }
    }

    /** Opis wiersza w kreatorze. */
    static String opis(Context c) {
        return I18n.t(c, wlaczone(c) ? "On: sports cars and cyberpunk cities, the clock hides behind them"
                : "Off: the scenes below are used");
    }
}
