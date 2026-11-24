package com.techgenius.tvoslauncher;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

/**
 * Mod PL: wiersze list (ustawienia, kreator) jak w tvOS 27 — wybór użytkownika z 24.11.2026 (kanwa „Zaznaczenie
 * w listach”, wariant A „biała pigułka”). Wzorem jest lista wyjść dźwięku w Centrum sterowania tvOS 27: wiersze
 * mają pełne zaokrąglenie (pigułka) zamiast rogów 12–13 dp, zaznaczony wiersz jest biały z ciemnym tekstem
 * (kolory ustawia kod oryginału), a niezaznaczone są szklane z cienką jasną krawędzią. Zaznaczony wiersz listy
 * ustawień lekko rośnie, jak elementy z fokusem w tvOS.
 * Plik źródłowy leży w pl/java/, a pl/naloz.py kopiuje go do src/ przy każdym buildzie.
 */
final class ModPlListy {
    private ModPlListy() { }

    /** Tło niezaznaczonego wiersza ustawień w oryginale (8% bieli): przy nim rysujemy krawędź szkła. */
    private static final int SZKLO = 0x14FFFFFF;
    /** Krawędź szkła: biel 8%, jak obrys niezaznaczonych kapsuł w tvOS. */
    private static final int KRAWEDZ = 0x14FFFFFF;
    /** Zaznaczony wiersz ustawień rośnie o 1,5% (szeroki wiersz: ok. 8 px z każdej strony przy 1080p). */
    private static final float POWIEKSZENIE = 1.015f;
    /** Najwyższe wiersze (kilka linii tekstu) dostają ten promień zamiast połowy wysokości, żeby rogi nie wchodziły w tekst. */
    private static final float NAJWIEKSZY_PROMIEN_DP = 28f;

    /** Tło wiersza listy ustawień: pigułka, krawędź szkła i powiększenie przy zaznaczeniu. */
    static GradientDrawable wiersz(Context c) {
        return new Pigulka(c, true);
    }

    /** Pigułka bez powiększenia: boczne menu, tło ikon, podpowiedź i wiersze kreatora (te rosną same). */
    static GradientDrawable pigulka(Context c) {
        return new Pigulka(c, false);
    }

    private static final class Pigulka extends GradientDrawable {
        private final boolean rosnie;
        private final int grubosc;
        private final float najwiekszyPromien;
        private boolean zaznaczony;

        Pigulka(Context c, boolean rosnie) {
            this.rosnie = rosnie;
            float gestosc = c.getResources().getDisplayMetrics().density;
            grubosc = Math.max(1, Math.round(gestosc * 0.6f));
            najwiekszyPromien = gestosc * NAJWIEKSZY_PROMIEN_DP;
        }

        @Override
        protected void onBoundsChange(Rect r) {
            super.onBoundsChange(r);
            setCornerRadius(Math.min(r.height() / 2f, najwiekszyPromien));
            // Poza telewizorami Android kładzie na zaznaczonym widoku własny szary prostokąt (widać go za
            // zaokrągleniem pigułki); na telewizorach go nie ma, ale wyłączamy go wszędzie.
            Callback cb = getCallback();
            if (Build.VERSION.SDK_INT >= 26 && cb instanceof View && ((View) cb).getDefaultFocusHighlightEnabled()) {
                ((View) cb).setDefaultFocusHighlightEnabled(false);
            }
        }

        @Override
        public void setColor(int kolor) {
            super.setColor(kolor);
            if (kolor == SZKLO) setStroke(grubosc, KRAWEDZ);
            else setStroke(0, 0);
        }

        @Override
        public boolean isStateful() {
            return rosnie || super.isStateful();
        }

        @Override
        protected boolean onStateChange(int[] stan) {
            boolean zmiana = super.onStateChange(stan);
            if (!rosnie) return zmiana;
            boolean teraz = false;
            for (int s : stan) {
                if (s == android.R.attr.state_focused) {
                    teraz = true;
                    break;
                }
            }
            if (teraz == zaznaczony) return zmiana;
            zaznaczony = teraz;
            Callback cb = getCallback();
            if (cb instanceof View) {
                View v = (View) cb;
                if (teraz) bezPrzycinania(v);
                v.animate().scaleX(teraz ? POWIEKSZENIE : 1f).scaleY(teraz ? POWIEKSZENIE : 1f)
                        .setDuration(160).start();
            }
            return zmiana;
        }
    }

    /** Powiększony wiersz wystaje kilka pikseli poza listę: rodzice nie mogą go przycinać. */
    private static void bezPrzycinania(View v) {
        ViewParent p = v.getParent();
        for (int i = 0; i < 3 && p instanceof ViewGroup; i++) {
            ((ViewGroup) p).setClipChildren(false);
            p = p.getParent();
        }
    }
}
