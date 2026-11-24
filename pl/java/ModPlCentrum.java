package com.techgenius.tvoslauncher;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Mod PL: układ centrum sterowania jak w tvOS 26/27. Wzorem jest zrzut Apple 1920×1080 (Newsroom, czerwiec 2025)
 * i instrukcja tvOS 27: duży kwadratowy kafel „Wyłącz zasilanie” po lewej (wysokość dwóch kapsuł), obok niego Wi-Fi
 * i „Nie przeszkadzać”, niżej „Dźwięk” i „Usypianie”, pod nimi okrągłe przyciski. Zmierzone wymiary tvOS:
 * kapsuła 205×94 px, kafel 205×203 px, koło 93 px, odstępy 17 px, róg kafla ok. 48 px, nazwy w dwóch liniach.
 * Launcher ma więcej elementów (Bluetooth, pamięć, tryb Ambient, pogoda), więc kolejne kapsuły idą w następnych
 * rzędach, a ostatnia bez pary zajmuje całą szerokość.
 * Trzy układy (Scenes.getModPlCcLayout): siatka oryginału, tvOS 27 w wymiarach Apple i tvOS 27 w większych
 * wymiarach oryginału (czytelniejsze polskie nazwy). Funkcje są wołane z łatek w ControlPanelOverlay.
 * Plik źródłowy leży w pl/java/, a pl/naloz.py kopiuje go do src/ przy każdym buildzie.
 */
final class ModPlCentrum {
    private ModPlCentrum() { }

    static final int ORYGINAL = 0, TVOS = 1, TVOS_DUZY = 2;
    /** Nazwy układów w ustawieniach (klucze tłumaczeń), w kolejności wartości. */
    static final String[] NAZWY = {"Original grid", "tvOS 27", "tvOS 27, larger"};

    static int uklad(Context c) {
        return Math.max(0, Math.min(NAZWY.length - 1, Scenes.getModPlCcLayout(c)));
    }

    static String nazwa(Context c) {
        return NAZWY[uklad(c)];
    }

    /** Następny układ po kliknięciu wiersza w ustawieniach. */
    static void nastepny(Context c) {
        Scenes.setModPlCcLayout(c, (uklad(c) + 1) % NAZWY.length);
    }

    private static boolean tv(Context c) {
        return uklad(c) != ORYGINAL;
    }

    /** Wymiar w jednostkach projektu (2 px przy 1080p): oryginału albo, w układzie „tvOS 27”, zmierzony w tvOS. */
    static int wymiar(Context c, float unit, float oryginal, float tvos) {
        return Math.round(unit * (uklad(c) == TVOS ? tvos : oryginal));
    }

    /** Kolejność kapsuł jak w tvOS: Wi-Fi i „Nie przeszkadzać” przy kaflu zasilania, potem dźwięk i usypianie. */
    static int kolejnosc(String znak) {
        if ("wifi".equals(znak)) return 0;
        if ("moon".equals(znak)) return 1;
        if ("audio".equals(znak)) return 2;
        if ("timer".equals(znak)) return 3;
        if ("bluetooth".equals(znak)) return 4;
        if ("memory".equals(znak)) return 5;
        if ("ambient".equals(znak)) return 6;
        return 99;   // zasilanie na końcu listy: pierwsze zaznaczenie po otwarciu nie pada na „Wyłącz zasilanie”
    }

    /**
     * Miejsce kafla w panelu: {x, y, szerokość, wysokość}. {@code nr} to numer kapsuły wśród {@code inne} kapsuł
     * poza zasilaniem. Kafel zasilania zajmuje lewą kolumnę dwóch pierwszych rzędów; ostatnia kapsuła bez pary
     * dostaje całą szerokość.
     */
    static int[] miejsce(boolean zasilanie, boolean jestZasilanie, int nr, int inne, int col, int cap, int gap) {
        if (zasilanie) return new int[]{0, 0, col, 2 * cap + gap};
        int k = nr, rzad, kolumna;
        if (jestZasilanie) {
            if (nr < 2) return new int[]{col + gap, nr * (cap + gap), col, cap};
            k = nr - 2;
            inne -= 2;
            rzad = 2 + k / 2;
        } else {
            rzad = k / 2;
        }
        kolumna = k % 2;
        boolean samotna = kolumna == 0 && k == inne - 1;
        return new int[]{kolumna * (col + gap), rzad * (cap + gap), samotna ? 2 * col + gap : col, cap};
    }

    /** Liczba rzędów kapsuł w układzie tvOS. */
    static int rzedy(boolean jestZasilanie, int inne) {
        if (jestZasilanie) return 2 + (Math.max(0, inne - 2) + 1) / 2;
        return (inne + 1) / 2;
    }

    private static boolean duzy(int w, int h) {
        return h > w * 0.7f;
    }

    /** Zaokrąglenie kafla: koło dla przycisku z samą ikoną, róg jak w tvOS dla dużego kafla, pigułka dla kapsuły. */
    static float promien(Context c, float oryginal, int w, int h, boolean samaIkona) {
        if (!tv(c)) return oryginal;
        if (samaIkona) return Math.min(w, h) / 2f;
        return duzy(w, h) ? w * 0.235f : h / 2f;
    }

    /** Róg szerokiego kafla pogody: taki jak dużego kafla zasilania. */
    static float promienPogody(Context c, float oryginal, float unit, int h) {
        if (!tv(c)) return oryginal;
        return Math.min(h / 2f, wymiar(c, unit, 141f, 102.5f) * 0.235f);
    }

    /** Rozmiar ikony w kaflu. */
    static int ikona(Context c, int oryginal, int w, int h, float unit, boolean samaIkona) {
        if (!tv(c)) return oryginal;
        if (samaIkona) return Math.round(Math.min(w, h) * 0.42f);
        if (duzy(w, h)) return Math.round(Math.min(w, h) * 0.44f);
        return uklad(c) == TVOS ? Math.round(unit * 22f) : oryginal;
    }

    /** W tvOS ikony zasilania i wygaszacza są białe (kolor mają tylko Wi-Fi i Bluetooth). */
    static int kolorIkony(Context c, String znak, int oryginal, int neutralny) {
        if (tv(c) && ("power".equals(znak) || "ambient".equals(znak))) return neutralny;
        return oryginal;
    }

    /** Szerokość paska pamięci pod nazwą (węższa kapsuła tvOS mieści krótszy). */
    static int pasekPamieci(Context c, float unit, float oryginal) {
        return Math.round(unit * (uklad(c) == TVOS ? 50f : oryginal));
    }

    /** Górny margines panelu: gdy wyższy panel nie mieści się pod domyślnym marginesem, przesuwa go w górę. */
    static int gora(int domyslna, int wysokoscPanelu, int wysokoscEkranu, float unit) {
        int zapas = Math.round(unit * 12f);
        if (wysokoscPanelu <= 0 || domyslna + wysokoscPanelu + zapas <= wysokoscEkranu) return domyslna;
        return Math.max(Math.round(unit * 8f), wysokoscEkranu - wysokoscPanelu - zapas);
    }

    /**
     * Ostatnie poprawki zbudowanego kafla (wołane z makeTile): nazwa w dwóch liniach zamiast ucinania, mniejsza
     * czcionka, gdy najdłuższe słowo się nie mieści, a duży kafel zasilania z ikoną nad nazwą.
     */
    static void dopasuj(Context c, LinearLayout kafel, View ikona, TextView nazwa, TextView stan, int w, int h,
                        float unit, int rozmiarIkony) {
        if (Build.VERSION.SDK_INT >= 26) kafel.setDefaultFocusHighlightEnabled(false);
        if (!tv(c) || nazwa == null) return;
        boolean ciasny = uklad(c) == TVOS;
        boolean duzy = duzy(w, h);
        View teksty = (View) nazwa.getParent();
        int dostepne;
        if (duzy) {
            int odstep = Math.round(unit * (ciasny ? 6f : 9f));
            kafel.setOrientation(LinearLayout.VERTICAL);
            kafel.setGravity(Gravity.CENTER);
            kafel.setPadding(odstep, odstep, odstep, odstep);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Math.round(unit * (ciasny ? 4f : 6f));
            teksty.setLayoutParams(lp);
            if (teksty instanceof LinearLayout) ((LinearLayout) teksty).setGravity(Gravity.CENTER_HORIZONTAL);
            nazwa.setGravity(Gravity.CENTER);
            if (stan != null) stan.setGravity(Gravity.CENTER);
            dostepne = w - 2 * odstep;
        } else {
            int lewy = Math.round(unit * (ciasny ? 10f : 14f)), prawy = Math.round(unit * (ciasny ? 7f : 12f));
            int odIkony = Math.round(unit * (ciasny ? 7f : 10f));
            kafel.setPadding(lewy, 0, prawy, 0);
            ViewGroup.LayoutParams lp = teksty.getLayoutParams();
            if (lp instanceof LinearLayout.LayoutParams) {
                ((LinearLayout.LayoutParams) lp).leftMargin = odIkony;
                teksty.setLayoutParams(lp);
            }
            dostepne = w - lewy - prawy - rozmiarIkony - odIkony;
        }
        nazwa.setSingleLine(false);
        nazwa.setMaxLines(2);
        nazwa.setEllipsize(TextUtils.TruncateAt.END);
        nazwa.setLineSpacing(0f, 0.94f);
        CharSequence tekst = nazwa.getText();
        if (tekst == null || dostepne <= 0) return;
        float najdluzsze = 0f;
        for (String slowo : tekst.toString().split("\\s+")) {
            najdluzsze = Math.max(najdluzsze, nazwa.getPaint().measureText(slowo));
        }
        if (najdluzsze > dostepne) {
            // w wymiarach Apple polskie słowa są za długie (np. „przeszkadzać”), więc tam czcionka może zmaleć bardziej
            float skala = Math.max(ciasny ? 0.62f : 0.74f, dostepne / najdluzsze * 0.97f);
            nazwa.setTextSize(TypedValue.COMPLEX_UNIT_PX, nazwa.getTextSize() * skala);
        }
        if (ciasny && stan != null) {
            // kapsuła 94 px mieści dwie linie: przy nazwie w dwóch liniach stan znika, jak w tvOS („Do Not Disturb”)
            final TextView n = nazwa, s = stan;
            nazwa.post(new Runnable() {
                @Override
                public void run() {
                    if (n.getLineCount() > 1) s.setVisibility(View.GONE);
                }
            });
        }
    }
}
