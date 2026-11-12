package com.techgenius.tvoslauncher;

import android.content.Context;
import android.graphics.Bitmap;
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
 * Numer klasy to numer wiersza w labelmap.txt minus 1: 2 = auto, 3 = motocykl, 5 = autobus, 7 = ciężarówka.
 *
 * Gdy biblioteka albo model się nie wczytają, pojazdy() zwraca null i dobór tapet działa jak bez wykrywacza.
 */
final class ModPlDetektor {
    private ModPlDetektor() { }

    private static final String TAG = "ModPlDetektor";
    private static final String MODEL = "mod_pl/wykrywacz.tflite";
    private static final int BOK = 300, MAKS = 10;
    private static final float MIN_PEWNOSC = 0.40f;

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

    private static boolean pojazd(int klasa) {
        return klasa == 2 || klasa == 3 || klasa == 5 || klasa == 7;
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
            long t0 = SystemClock.uptimeMillis();
            if (interpreter == null) wczytaj(c.getApplicationContext());
            Bitmap mala = Bitmap.createScaledBitmap(obraz, BOK, BOK, true);
            int[] px = new int[BOK * BOK];
            mala.getPixels(px, 0, BOK, 0, 0, BOK, BOK);
            if (mala != obraz) mala.recycle();
            wejscie.rewind();
            for (int p : px) {
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
            List<Pojazd> lista = new ArrayList<Pojazd>();
            StringBuilder opis = new StringBuilder();
            int n = Math.min(MAKS, Math.max(0, Math.round(ile[0])));
            for (int i = 0; i < n; i++) {
                int klasa = Math.round(klasy[0][i]);
                if (pewnosci[0][i] < MIN_PEWNOSC || !pojazd(klasa)) continue;
                Pojazd p = new Pojazd();
                p.gora = Math.max(0f, Math.min(1f, ramki[0][i][0]));
                p.lewo = Math.max(0f, Math.min(1f, ramki[0][i][1]));
                p.dol = Math.max(0f, Math.min(1f, ramki[0][i][2]));
                p.prawo = Math.max(0f, Math.min(1f, ramki[0][i][3]));
                p.pewnosc = pewnosci[0][i];
                p.klasa = klasa;
                lista.add(p);
                opis.append(String.format(Locale.US, " [%.0f%% x %.2f-%.2f, y %.2f-%.2f]", p.pewnosc * 100f, p.lewo, p.prawo, p.gora, p.dol));
            }
            ModPlDziennik.i(TAG, "pojazdy: " + lista.size() + opis + ", " + (SystemClock.uptimeMillis() - t0) + " ms");
            return lista;
        } catch (Throwable e) {
            // brak biblioteki dla tego procesora, zły model itp.: dalej bez wykrywacza
            nieDziala = true;
            ModPlDziennik.w(TAG, "wykrywacz nie działa", e);
            return null;
        }
    }
}
