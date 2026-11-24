package com.techgenius.tvoslauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.SystemClock;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mod PL: wykrywacz pojazdów na tapecie. Mówi, gdzie na zdjęciu jest auto, żeby zegar ekranu głównego chował się
 * za jego dachem, a nie leżał na kabinie (użytkownik 12.11.2026: „zegar jest dosłownie na autach a to tak nie miało być”).
 * Reguły oparte na samych krawędziach i kolorach nie odróżniały dachu od linii maski czy szyb.
 *
 * Działa w telewizorze, bez internetu: TensorFlow Lite 2.14.0 (pl/biblioteki, z Maven Central) i model
 * SSD MobileNet v1 COCO w wersji 8-bitowej (assets/mod_pl/wykrywacz.tflite, 4 MB, od Google, licencja Apache 2.0).
 * Wejście 300x300 RGB, wyjścia: ramki [1][10][4] jako (góra, lewo, dół, prawo) w ułamkach, klasy, pewności, liczba.
 * Numer klasy to numer wiersza w labelmap.txt minus 1: 2 = auto, 3 = motocykl, 5 = autobus, 6 = pociąg, 7 = ciężarówka.
 *
 * Poprawki z 24.11.2026 (sprawdzone na boxie narzędziem narzedzia-testow/wykrywacz na 15 zdjęciach z Wallhaven):
 * - Duże auto z bliska, zwłaszcza od tyłu albo od przodu, model często bierze za pociąg: GT-R od tyłu w deszczu dostał
 *   72% „pociąg” i tylko 25% „auto”, więc duży zegar wygaszacza stanął na nim. „Pociąg” liczy się jako auto, gdy ma
 *   proporcje auta i model widzi w tej samej ramce także auto, autobus albo ciężarówkę (pociagJakoAuto).
 * - Zdjęcie 16:9 ściśnięte do kwadratu zniekształca szerokie auta: białe Lamborghini i Porsche od przodu nie były
 *   wykrywane wcale. Oprócz całego zdjęcia sprawdzamy więc lewy i prawy kwadratowy wycinek (razem cała szerokość);
 *   z wycinków dochodzą tylko pojazdy, których nie ma w wynikach całego zdjęcia (szczegóły przy pojazdy(Bitmap)).
 *
 * Gdy biblioteka albo model się nie wczytają, pojazdy() zwraca null i dobór tapet działa jak bez wykrywacza.
 */
final class ModPlDetektor {
    private ModPlDetektor() { }

    private static final String TAG = "ModPlDetektor";
    private static final String MODEL = "mod_pl/wykrywacz.tflite";
    private static final int BOK = 300, MAKS = 10;
    private static final float MIN_PEWNOSC = 0.40f;
    private static final int AUTO = 2, MOTOCYKL = 3, AUTOBUS = 5, POCIAG = 6, CIEZAROWKA = 7;
    /** Od tylu procent słabe „auto” potwierdza, że „pociąg” w tej samej ramce to auto widziane z bliska. */
    private static final float MIN_POTWIERDZENIA = 0.15f;
    /** Najmniejsza ramka (część całego kadru) motocykla, autobusu albo ciężarówki z wycinka (duzyIPoziomy). */
    private static final float MIN_POLE_INNYCH_Z_WYCINKA = 0.15f;

    /** Wykryty pojazd: ramka w ułamkach kadru (0..1) i pewność wykrycia. */
    static final class Pojazd {
        float lewo, gora, prawo, dol, pewnosc;
        int klasa;

        float pole() {
            return Math.max(0f, prawo - lewo) * Math.max(0f, dol - gora);
        }
    }

    private static Interpreter interpreter;
    private static ByteBuffer wejscie;
    private static boolean nieDziala;
    private static final int[] PIKSELE = new int[BOK * BOK];

    private static boolean pojazd(int klasa) {
        return klasa == AUTO || klasa == MOTOCYKL || klasa == AUTOBUS || klasa == CIEZAROWKA;
    }

    private static String nazwa(int klasa) {
        switch (klasa) {
            case AUTO: return "auto";
            case MOTOCYKL: return "motocykl";
            case AUTOBUS: return "autobus";
            case POCIAG: return "pociąg";
            case CIEZAROWKA: return "ciężarówka";
            default: return "klasa " + klasa;
        }
    }

    private static void wczytaj(Context c) throws Exception {
        InputStream we = c.getAssets().open(MODEL);
        ByteArrayOutputStream calosc = new ByteArrayOutputStream(4500000);
        try {
            byte[] bufor = new byte[65536];
            for (int ile; (ile = we.read(bufor)) > 0; ) calosc.write(bufor, 0, ile);
        } finally {
            we.close();
        }
        ByteBuffer model = ByteBuffer.allocateDirect(calosc.size()).order(ByteOrder.nativeOrder());
        model.put(calosc.toByteArray());
        model.rewind();
        wczytaj(model);
    }

    /** Model już w pamięci. Osobno, bo z pliku podaje go też narzędzie testów na boxie (narzedzia-testow/wykrywacz). */
    static synchronized void wczytaj(ByteBuffer model) {
        Interpreter.Options opcje = new Interpreter.Options();
        opcje.setNumThreads(2);
        interpreter = new Interpreter(model, opcje);
        wejscie = ByteBuffer.allocateDirect(BOK * BOK * 3).order(ByteOrder.nativeOrder());
    }

    /**
     * Pojazdy na zdjęciu (zdjęcie w kadrze ekranu, dowolnej wielkości). Pusta lista, gdy żadnego nie ma;
     * null, gdy wykrywacz nie działa na tym urządzeniu.
     */
    static synchronized List<Pojazd> pojazdy(Context c, Bitmap obraz) {
        if (nieDziala || c == null || obraz == null || obraz.isRecycled()) return null;
        try {
            if (interpreter == null) wczytaj(c.getApplicationContext());
            return pojazdy(obraz);
        } catch (Throwable e) {
            // brak biblioteki dla tego procesora, zły model itp.: dalej bez wykrywacza
            nieDziala = true;
            ModPlDziennik.w(TAG, "wykrywacz nie działa", e);
            return null;
        }
    }

    /**
     * Właściwe wykrywanie, przy wczytanym modelu: całe zdjęcie i, przy kadrze poziomym, dwa kwadratowe wycinki
     * bez zniekształceń. Z wycinków dochodzą tylko pojazdy, których nie ma już w wynikach całego zdjęcia: wycinek
     * widzi auto ucięte albo z luźniejszą ramką (np. górą wyżej niż dach), a ramka z całego zdjęcia jest pełniejsza.
     * Z wycinka „auto” liczy się zawsze, motocykl, autobus i ciężarówka tylko duże i poziome (duzyIPoziomy),
     * „pociąg” nigdy. Na boxie jeden przebieg modelu trwa ok. 180 ms, całość ok. 0,6 s.
     */
    static synchronized List<Pojazd> pojazdy(Bitmap obraz) {
        long t0 = SystemClock.uptimeMillis();
        int w = obraz.getWidth(), h = obraz.getHeight();
        List<Pojazd> lista = new ArrayList<Pojazd>();
        StringBuilder opis = new StringBuilder();
        wykryj(obraz, 0, 0, w, h, lista, opis, "całe", -1);
        if (w > h * 1.2f) {
            int zCalego = lista.size();
            wykryj(obraz, 0, 0, h, h, lista, opis, "lewy", zCalego);
            wykryj(obraz, w - h, 0, h, h, lista, opis, "prawy", zCalego);
        }
        ModPlDziennik.i(TAG, "pojazdy: " + lista.size() + opis + ", " + (SystemClock.uptimeMillis() - t0) + " ms");
        return lista;
    }

    /**
     * Czy pojazd z wycinka to ten sam, który jest już wśród pierwszych „ile” pozycji listy (wyniki całego zdjęcia):
     * ramki pokrywają się w co najmniej 30% (część wspólna do sumy) albo ramka z wycinka leży w 80% w tamtej.
     */
    private static boolean juzJest(Pojazd p, List<Pojazd> lista, int ile) {
        for (int i = 0; i < ile; i++) {
            Pojazd f = lista.get(i);
            float wspolne = Math.max(0f, Math.min(p.prawo, f.prawo) - Math.max(p.lewo, f.lewo))
                    * Math.max(0f, Math.min(p.dol, f.dol) - Math.max(p.gora, f.gora));
            float suma = p.pole() + f.pole() - wspolne;
            if (suma > 0f && wspolne >= 0.3f * suma || wspolne >= 0.8f * p.pole()) return true;
        }
        return false;
    }

    /**
     * Wycinek zdjęcia przeskalowany do 300x300 jednym krokiem, bez kopii wycinka w pełnej wielkości. Dla całego
     * zdjęcia to dokładnie to, co Bitmap.createScaledBitmap(obraz, 300, 300, true) (ta sama macierz i to samo filtrowanie).
     */
    private static Bitmap kwadrat(Bitmap obraz, int x, int y, int sw, int sh) {
        Matrix m = new Matrix();
        if (sw != BOK || sh != BOK) m.setScale(BOK / (float) sw, BOK / (float) sh);
        Bitmap b = Bitmap.createBitmap(obraz, x, y, sw, sh, m, true);
        if (b.getWidth() != BOK || b.getHeight() != BOK) {      // zaokrąglenie rozmiaru, na wszelki wypadek
            Bitmap p = Bitmap.createScaledBitmap(b, BOK, BOK, true);
            if (b != obraz && b != p) b.recycle();
            b = p;
        }
        return b;
    }

    /**
     * Jeden przebieg modelu na wycinku (x, y, szerokość, wysokość w pikselach zdjęcia). Znalezione pojazdy trafiają
     * do listy w ułamkach CAŁEGO zdjęcia; do opisu idą one i najlepszy odrzucony pojazd (pewność od 15%).
     * zCalego = -1: przebieg na całym zdjęciu. zCalego >= 0: przebieg na wycinku, tyle pierwszych pozycji listy to
     * wyniki całego zdjęcia; z wycinka dochodzą tylko pojazdy, których tam jeszcze nie ma.
     */
    private static void wykryj(Bitmap obraz, int x, int y, int sw, int sh, List<Pojazd> lista, StringBuilder opis,
                               String wycinek, int zCalego) {
        Bitmap mala = kwadrat(obraz, x, y, sw, sh);
        mala.getPixels(PIKSELE, 0, BOK, 0, 0, BOK, BOK);
        if (mala != obraz) mala.recycle();
        wejscie.rewind();
        for (int p : PIKSELE) {
            wejscie.put((byte) (p >> 16));
            wejscie.put((byte) (p >> 8));
            wejscie.put((byte) p);
        }
        wejscie.rewind();
        float[][][] ramki = new float[1][MAKS][4];
        float[][] klasy = new float[1][MAKS], pewnosci = new float[1][MAKS];
        float[] ile = new float[1];
        Map<Integer, Object> wyjscia = new HashMap<Integer, Object>();
        wyjscia.put(0, ramki);
        wyjscia.put(1, klasy);
        wyjscia.put(2, pewnosci);
        wyjscia.put(3, ile);
        interpreter.runForMultipleInputsOutputs(new Object[]{wejscie}, wyjscia);
        float W = obraz.getWidth(), H = obraz.getHeight();
        int n = Math.min(MAKS, Math.max(0, Math.round(ile[0])));
        int znalezione = 0, powtorzone = 0, odrzuconaKlasa = -1;
        float odrzucona = 0f;
        StringBuilder tu = new StringBuilder();
        for (int i = 0; i < n; i++) {
            int klasa = Math.round(klasy[0][i]);
            float pew = pewnosci[0][i];
            boolean przyjety = pew >= MIN_PEWNOSC && (zCalego >= 0
                    ? klasa == AUTO || pojazd(klasa) && duzyIPoziomy(ramki[0][i], sw, sh, W, H)
                    : pojazd(klasa) || klasa == POCIAG && pociagJakoAuto(i, ramki[0], klasy[0], pewnosci[0], n, sw, sh));
            if (!przyjety) {
                if ((pojazd(klasa) || klasa == POCIAG) && pew > odrzucona) {
                    odrzucona = pew;
                    odrzuconaKlasa = klasa;
                }
                continue;
            }
            Pojazd p = new Pojazd();
            p.gora = Math.max(0f, Math.min(1f, (y + ramki[0][i][0] * sh) / H));
            p.lewo = Math.max(0f, Math.min(1f, (x + ramki[0][i][1] * sw) / W));
            p.dol = Math.max(0f, Math.min(1f, (y + ramki[0][i][2] * sh) / H));
            p.prawo = Math.max(0f, Math.min(1f, (x + ramki[0][i][3] * sw) / W));
            p.pewnosc = pew;
            p.klasa = klasa;
            if (zCalego > 0 && juzJest(p, lista, zCalego)) {
                powtorzone++;
                continue;
            }
            lista.add(p);
            znalezione++;
            tu.append(String.format(Locale.US, " %s %.0f%% x %.2f-%.2f y %.2f-%.2f", nazwa(klasa), pew * 100f,
                    p.lewo, p.prawo, p.gora, p.dol));
        }
        opis.append(" [").append(wycinek).append(':').append(znalezione == 0 ? " -" : tu.toString());
        if (powtorzone > 0) opis.append(", jak w całym: ").append(powtorzone);
        if (odrzucona >= 0.15f) {
            opis.append(String.format(Locale.US, ", odrzucony %s %.0f%%", nazwa(odrzuconaKlasa), odrzucona * 100f));
        }
        opis.append(']');
    }

    /**
     * Motocykl, autobus albo ciężarówka z wycinka liczą się tylko jako duża ramka (co najmniej 15% całego kadru),
     * szersza niż wysoka. W wycinku model bierze czasem auto z bliska za inny pojazd (czarne Aventador wypełniające
     * kadr: „motocykl” 55%, 37% kadru), ale widzi też „ciężarówkę” w budynkach neonowego miasta (50%, tylko 7% kadru,
     * wyższa niż szeroka).
     */
    private static boolean duzyIPoziomy(float[] r, int sw, int sh, float W, float H) {
        float szer = (r[3] - r[1]) * sw, wys = (r[2] - r[0]) * sh;
        return szer >= wys && szer * wys >= MIN_POLE_INNYCH_Z_WYCINKA * W * H;
    }

    /**
     * Czy „pociąg” nr i to w rzeczywistości auto widziane z bliska. Ramka ma proporcje auta (szersza niż wysoka,
     * w pikselach wycinka) i leży w niej słabe „auto”, „autobus” albo „ciężarówka” (od 15%), w co najmniej 60%
     * wewnątrz ramki i zajmujące co najmniej 10% jej pola. Bez tego warunku liczyłyby się też „pociągi”, które model
     * widzi w neonowych budynkach (na zdjęciach miast bez aut: 40–49%).
     */
    private static boolean pociagJakoAuto(int i, float[][] ramki, float[] klasy, float[] pewnosci, int n, int sw, int sh) {
        float[] r = ramki[i];
        float szer = (r[3] - r[1]) * sw, wys = (r[2] - r[0]) * sh;
        if (wys <= 0f || szer < wys || szer > 4f * wys) return false;
        float pole = (r[3] - r[1]) * (r[2] - r[0]);
        for (int j = 0; j < n; j++) {
            int k = Math.round(klasy[j]);
            if (j == i || pewnosci[j] < MIN_POTWIERDZENIA || (k != AUTO && k != AUTOBUS && k != CIEZAROWKA)) continue;
            float[] a = ramki[j];
            float poleA = Math.max(0f, a[3] - a[1]) * Math.max(0f, a[2] - a[0]);
            float wspolne = Math.max(0f, Math.min(a[3], r[3]) - Math.max(a[1], r[1]))
                    * Math.max(0f, Math.min(a[2], r[2]) - Math.max(a[0], r[0]));
            if (poleA > 0f && wspolne >= 0.6f * poleA && poleA >= 0.1f * pole) return true;
        }
        return false;
    }
}
