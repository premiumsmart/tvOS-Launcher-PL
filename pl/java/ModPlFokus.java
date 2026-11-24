package com.techgenius.tvoslauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.WeakHashMap;

/**
 * Mod PL: zaznaczenie ikon doku jak w tvOS 26/27. Wzorem są zrzuty Apple (ekran główny tvOS 26 z Newsroomu,
 * instrukcja tvOS 27, zbliżenie doku z prezentacji) i opis w wytycznych Apple:
 * - zaznaczona ikona rośnie ok. 1,22-1,23× (pomiar ze zrzutów; stara tabela Apple: 300×180 → 370×222),
 * - dostaje cienką, świetlistą krawędź szkła, najjaśniejszą w lewym górnym i prawym dolnym rogu, oraz łagodne
 *   rozświetlenie górnej części powierzchni,
 * - wokół niej leży miękki cień jak otoczka, lekko przesunięty w dół; ikony bez zaznaczenia mają tylko mały cień,
 * - szklana taca doku się nie zmienia: jest na tyle wysoka, że powiększona ikona mieści się w niej z równym
 *   odstępem (dlatego w tym trybie dok ma większe odstępy od krawędzi),
 * - warstwy ikony przesuwają się względem siebie (głębia), a powierzchnia „łapie światło” w ruchu.
 * W tvOS ruch idzie za palcem na gładziku pilota; pilot ze strzałkami tego nie ma, więc ikona przechyla się
 * w kierunku, z którego przyszło zaznaczenie, i wraca z lekkim odbiciem. Potem stoi nieruchomo, żeby ekran główny
 * nie rysował się bez przerwy. Głębię mają aplikacje z ikoną adaptacyjną (osobne tło i obrazek), bez własnej ikony
 * użytkownika i bez gotowego logo launchera; pozostałe ikony są płaskie.
 * Tryb wybiera się w ustawieniach doku (Scenes.getModPlDockFocus): obwódka oryginału albo styl tvOS.
 * Plik źródłowy leży w pl/java/, a pl/naloz.py kopiuje go do src/ przy każdym buildzie.
 */
final class ModPlFokus {
    private ModPlFokus() { }

    static final int OBWODKA = 0, TVOS = 1;
    /** Nazwy trybów w ustawieniach (klucze tłumaczeń), w kolejności wartości. */
    static final String[] NAZWY = {"Ring", "tvOS style"};

    /** Największe przechylenie ikony w stopniach (w poziomie i w pionie) i czas całego ruchu. */
    private static final float KAT_X = 7f, KAT_Y = 5f;
    private static final long RUCH_MS = 720L;

    /** Stan jednej ikony doku (klucz słaby: ikony są budowane od nowa przy każdej zmianie doku). */
    private static final class Stan {
        Nakladka nakladka;
        ValueAnimator anim;
        MainActivity.AppEntry aplikacja;
        SceneHome dom;
        Drawable tloZwykle;
        Warstwy warstwy;
        boolean warstwySprawdzone;
        /** Ikona jasna przy krawędziach (np. biała): biała krawędź szkła jest na niej niewidoczna. */
        boolean jasna;
    }

    /**
     * Krawędź zaznaczonej jasnej ikony. W tvOS krawędź szkła to dodawane światło, więc na białej ikonie znika
     * (pomiar zrzutu Apple: biała ikona „Zdjęcia” ma krawędź jaśniejszą, nie ciemniejszą). Dla czytelności można
     * zamiast tego dodać ciemną krawędź, jak obwódka oryginału na ikonach z białymi rogami.
     */
    static final int KRAWEDZ_TVOS = 0, KRAWEDZ_DELIKATNA = 1, KRAWEDZ_WYRAZNA = 2;
    static final String[] NAZWY_KRAWEDZI = {"Like tvOS", "Subtle dark", "Distinct dark"};

    static int krawedzJasnych(Context c) {
        return Math.max(0, Math.min(NAZWY_KRAWEDZI.length - 1, Scenes.getModPlBrightEdge(c)));
    }

    static String nazwaKrawedzi(Context c) {
        return NAZWY_KRAWEDZI[krawedzJasnych(c)];
    }

    static void nastepnaKrawedz(Context c) {
        Scenes.setModPlBrightEdge(c, (krawedzJasnych(c) + 1) % NAZWY_KRAWEDZI.length);
    }

    private static final WeakHashMap<View, Stan> STANY = new WeakHashMap<View, Stan>();
    /** Środek ostatnio zaznaczonej ikony na ekranie i chwila zaznaczenia: z tego wynika kierunek ruchu. */
    private static float ostatniX = Float.NaN, ostatniY;
    private static long ostatnioMs;

    private static int tryb(Context c) {
        return Scenes.getModPlDockFocus(c) == OBWODKA ? OBWODKA : TVOS;
    }

    /** Powiększenie zaznaczonej ikony: oryginał 1,13; w tvOS 1,23. */
    static float skala(Context c) {
        return tryb(c) == OBWODKA ? 1.13f : 1.23f;
    }

    /** Powiększenie zaznaczonej ikony w oknie „Inne aplikacje”: oryginał 1,11; w stylu tvOS jak w doku. */
    static float skalaSiatki(Context c) {
        return tryb(c) == OBWODKA ? 1.11f : 1.22f;
    }

    /**
     * Odstęp ikon od krawędzi tacy doku. W tvOS taca się nie wybrzusza: jest na tyle wysoka, że powiększona ikona
     * mieści się w niej z równym odstępem ok. 16% wysokości ikony (pomiar: instrukcja tvOS 27 i ekran główny tvOS 26).
     */
    static int odstepDoku(Context c, int oryginalny, int bokIkony) {
        if (tryb(c) == OBWODKA) return oryginalny;
        return Math.max(oryginalny, Math.round(bokIkony * ((skala(c) - 1f) / 2f + 0.16f)));
    }

    /** O ile pikseli podpis zaznaczonej ikony zjeżdża w dół, żeby powiększona ikona na niego nie nachodziła. */
    static float podpisNizej(Context c, View ikona) {
        if (tryb(c) == OBWODKA) return 0f;
        return ikona.getHeight() * (skala(c) - 1.13f) / 2f + Ui.unit(c) * 2f;
    }

    static String nazwa(Context c) {
        return NAZWY[tryb(c)];
    }

    /** Następny tryb po kliknięciu wiersza w ustawieniach. */
    static void nastepny(Context c) {
        Scenes.setModPlDockFocus(c, (tryb(c) + 1) % NAZWY.length);
    }

    private static Stan stan(View v) {
        Stan s = STANY.get(v);
        if (s == null) {
            s = new Stan();
            STANY.put(v, s);
        }
        return s;
    }

    /**
     * Wołane z SceneHome przy budowie ikony doku: zapamiętuje aplikację i od razu kładzie mały cień. {@code jasna}
     * to ocena oryginału (SceneHome.hasWhiteCorners: jasne rogi i brzegi ikony), od której oryginał rysuje czarną
     * obwódkę zamiast białej.
     */
    static void przygotuj(SceneHome dom, View v, MainActivity.AppEntry a, float promien, boolean jasna) {
        Stan s = stan(v);
        s.dom = dom;
        s.aplikacja = a;
        s.jasna = jasna;
        Context c = v.getContext();
        if (tryb(c) == OBWODKA) return;
        s.nakladka = new Nakladka(promien, Ui.unit(c), !(v.getBackground() instanceof GradientDrawable));
        v.setForeground(s.nakladka);
    }

    /**
     * Wołane z SceneHome przy każdej zmianie zaznaczenia ikony (dok i okno „Inne aplikacje”). Ustawia przechylenie,
     * cień i głębię, a zwraca rysunek kładziony na wierzch ikony: obwódkę oryginału albo nakładkę ze szklaną krawędzią.
     */
    static Drawable zaznaczenie(final View v, final boolean ma, Drawable obwodkaOryginalu, float promien) {
        Context c = v.getContext();
        final Stan s = stan(v);
        if (s.anim != null) s.anim.cancel();
        s.anim = null;
        if (tryb(c) == OBWODKA) {
            przywrocTlo(v, s);
            v.setRotationX(0f);
            v.setRotationY(0f);
            v.setTranslationZ(0f);
            return ma ? obwodkaOryginalu : null;
        }
        if (s.nakladka == null) {
            s.nakladka = new Nakladka(promien, Ui.unit(c), !(v.getBackground() instanceof GradientDrawable));
        }
        final Nakladka n = s.nakladka;
        n.ciemnaKrawedz(s.jasna ? krawedzJasnych(c) : KRAWEDZ_TVOS);

        if (!ma) {
            przywrocTlo(v, s);
            v.setRotationX(0f);
            v.setRotationY(0f);
            v.setTranslationZ(0f);
            final float od = n.sila;
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(150);
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator an) {
                    n.ustaw(od * (1f - (Float) an.getAnimatedValue()), 0f, 0f);
                }
            });
            s.anim = a;
            a.start();
            return n;
        }

        // kierunek, z którego przyszło zaznaczenie: sąsiednia ikona doku albo (domyślnie) rząd nad dokiem
        int[] miejsce = new int[2];
        v.getLocationOnScreen(miejsce);
        float cx = miejsce[0] + v.getWidth() / 2f, cy = miejsce[1] + v.getHeight() / 2f;
        float kx = 0f, ky = 1f;
        long teraz = SystemClock.uptimeMillis();
        if (!Float.isNaN(ostatniX) && teraz - ostatnioMs < 1500L) {
            float dx = cx - ostatniX, dy = cy - ostatniY;
            if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 1f) {
                kx = Math.signum(dx);
                ky = 0f;
            } else if (Math.abs(dy) > 1f) {
                ky = Math.signum(dy);
            }
        }
        ostatniX = cx;
        ostatniY = cy;
        ostatnioMs = teraz;
        final float kierX = kx, kierY = ky;

        final Warstwy w = warstwy(v, s, promien);
        if (w != null) {
            if (s.tloZwykle == null) s.tloZwykle = v.getBackground();
            w.przesun(0f, 0f);
            v.setBackground(w);
        }
        v.setTranslationZ(Ui.unit(c) * 3f);   // zaznaczona ikona rysuje się nad sąsiadami
        v.setCameraDistance(Ui.unit(c) * 5200f);
        final float od = n.sila;
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(RUCH_MS);
        a.setInterpolator(new LinearInterpolator());
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator an) {
                float t = (Float) an.getAnimatedValue();
                // gasnące wahnięcie: pełne wychylenie na starcie, przejście przez zero, małe odbicie, spoczynek
                float f = t >= 1f ? 0f : (float) (Math.exp(-5.5 * t) * Math.cos(8.5 * t)) * (1f - t * t * t);
                float px = kierX * f, py = kierY * f;
                n.ustaw(od + (1f - od) * Math.min(1f, t / 0.22f), px, py);
                v.setRotationY(KAT_X * px);
                v.setRotationX(-KAT_Y * py);
                if (w != null) w.przesun(px, py);
            }
        });
        s.anim = a;
        a.start();
        return n;
    }

    private static void przywrocTlo(View v, Stan s) {
        if (s.tloZwykle != null && v.getBackground() == s.warstwy) v.setBackground(s.tloZwykle);
    }

    // ------------------------------------------------------------------ głębia: dwie warstwy ikony adaptacyjnej

    private static Warstwy warstwy(View v, Stan s, float promien) {
        if (s.warstwySprawdzone) return s.warstwy;
        s.warstwySprawdzone = true;
        MainActivity.AppEntry a = s.aplikacja;
        if (Build.VERSION.SDK_INT < 26 || a == null || s.dom == null || !(a.icon instanceof AdaptiveIconDrawable)) return null;
        if (v.getBackground() instanceof GradientDrawable) return null;
        try {
            Context c = v.getContext();
            if (CustomMedia.hasCustomAppIcon(c, a.key, a.pkg) || s.dom.getSquareAppLogo(a.pkg) != null) return null;
            AdaptiveIconDrawable ikona = (AdaptiveIconDrawable) a.icon;
            Drawable tloD = ikona.getBackground(), obrazD = ikona.getForeground();
            if (obrazD == null) return null;
            // ten sam rysunek co SceneHome.buildFullSquareIcon, tylko tło i obrazek na osobnych bitmapach
            int bok = 192, zapas = -Math.round(bok * 0.24f);
            Bitmap tlo = Bitmap.createBitmap(bok, bok, Bitmap.Config.ARGB_8888);
            Canvas k = new Canvas(tlo);
            boolean sklepPlay = "com.android.vending".equals(a.pkg);
            if (sklepPlay) {
                k.drawColor(0xFFFFFFFF);
            } else {
                int baza = a.color != 0 ? a.color : 0xFF24262E;
                float[] hsv = new float[3];
                Color.colorToHSV(baza, hsv);
                int gora = Color.HSVToColor(new float[]{hsv[0], Math.min(1f, hsv[1] * 0.9f),
                        Math.max(0.28f, Math.min(0.85f, hsv[2] * 1.35f))});
                int dol = Color.HSVToColor(new float[]{hsv[0], Math.min(1f, hsv[1]),
                        Math.max(0.14f, Math.min(0.55f, hsv[2] * 0.85f))});
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
                p.setShader(new LinearGradient(0, 0, 0, bok, gora, dol, Shader.TileMode.CLAMP));
                k.drawRect(0, 0, bok, bok, p);
                if (tloD != null) rysujWarstwe(tloD, k, zapas, bok);
            }
            Bitmap obraz = Bitmap.createBitmap(bok, bok, Bitmap.Config.ARGB_8888);
            rysujWarstwe(obrazD, new Canvas(obraz), zapas, bok);
            s.warstwy = new Warstwy(tlo, obraz, promien);
        } catch (Throwable e) {
            ModPlDziennik.w("ModPlFokus", "warstwy ikony: " + e);
            s.warstwy = null;
        }
        return s.warstwy;
    }

    private static void rysujWarstwe(Drawable d, Canvas k, int zapas, int bok) {
        Rect stare = d.copyBounds();
        d.setBounds(zapas, zapas, bok - zapas, bok - zapas);
        d.draw(k);
        d.setBounds(stare);
    }

    /** Tło zaznaczonej ikony z dwóch warstw: obrazek przesuwa się mocniej niż tło, co daje wrażenie głębi. */
    private static final class Warstwy extends Drawable {
        private final Bitmap tlo, obraz;
        private final Paint pTlo = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final Paint pObraz = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final BitmapShader sTlo, sObraz;
        private final Matrix m = new Matrix();
        private final RectF pole = new RectF();
        private final float promien;
        private float px, py;

        Warstwy(Bitmap tlo, Bitmap obraz, float promien) {
            this.tlo = tlo;
            this.obraz = obraz;
            this.promien = promien;
            sTlo = new BitmapShader(tlo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            sObraz = new BitmapShader(obraz, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            pTlo.setShader(sTlo);
            pObraz.setShader(sObraz);
        }

        void przesun(float x, float y) {
            px = x;
            py = y;
            invalidateSelf();
        }

        /** Ta sama skala co w płaskiej ikonie launchera (1,05), żeby przy zaznaczeniu obraz nie skakał. */
        private void uloz(BitmapShader s, Bitmap b, float ulamekX, float ulamekY) {
            float w = pole.width(), h = pole.height();
            float skala = Math.max(w / b.getWidth(), h / b.getHeight()) * 1.05f;
            m.reset();
            m.setScale(skala, skala);
            m.postTranslate(pole.left + (w - b.getWidth() * skala) / 2f + ulamekX * w,
                    pole.top + (h - b.getHeight() * skala) / 2f + ulamekY * h);
            s.setLocalMatrix(m);
        }

        @Override
        protected void onBoundsChange(Rect b) {
            pole.set(b);
        }

        @Override
        public void draw(Canvas c) {
            if (pole.isEmpty()) return;
            uloz(sTlo, tlo, -0.014f * px, -0.012f * py);
            uloz(sObraz, obraz, 0.032f * px, 0.028f * py);
            c.drawRoundRect(pole, promien, promien, pTlo);
            c.drawRoundRect(pole, promien, promien, pObraz);
        }

        @Override
        public void setAlpha(int alpha) {
            pTlo.setAlpha(alpha);
            pObraz.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            pTlo.setColorFilter(cf);
            pObraz.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    // ------------------------------------------------------------------ cień, rozświetlenie i szklana krawędź

    /**
     * Rysunek na wierzchu ikony. {@code sila} 0 to spoczynek (sam mały cień), 1 to pełne zaznaczenie (miękki cień
     * dookoła, rozświetlona górna część, świetlista krawędź szkła); {@code px}, {@code py} to chwilowe przechylenie
     * od -1 do 1, za którym przesuwa się rozświetlenie i cień.
     */
    private static final class Nakladka extends Drawable {
        private final Paint plama = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Paint krawedz = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cien = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint szklo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ciemna = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF pole = new RectF(), wewnatrz = new RectF(), wewnatrzCiemna = new RectF();
        private final Path obrys = new Path();
        private final float promien, grubosc;
        private final boolean zCieniem;
        private float sila, px, py;
        /** Krawędź jasnej ikony: 0 jak w tvOS (tylko jasna), 1 delikatna ciemna, 2 wyraźna ciemna. */
        private int rodzajCiemnej;

        void ciemnaKrawedz(int rodzaj) {
            if (rodzaj == rodzajCiemnej) return;
            rodzajCiemnej = rodzaj;
            float g = rodzaj == KRAWEDZ_WYRAZNA ? grubosc * 1.6f : grubosc;
            ciemna.setStrokeWidth(g);
            Rect b = getBounds();
            wewnatrzCiemna.set(b.left + g / 2f, b.top + g / 2f, b.right - g / 2f, b.bottom - g / 2f);
            ModPlDziennik.i("ModPlFokus", "krawędź jasnej ikony: rodzaj " + rodzaj + ", pole " + b.toShortString());
            invalidateSelf();
        }

        /** {@code zCieniem}: ikona jest nieprzezroczysta; przez półprzezroczysty kafelek cień prześwitywałby. */
        Nakladka(float promien, float jednostka, boolean zCieniem) {
            this.promien = promien;
            this.grubosc = Math.max(1.5f, jednostka);
            this.zCieniem = zCieniem && Build.VERSION.SDK_INT >= 28;
            cien.setColor(0xFF000000);
            szklo.setStyle(Paint.Style.STROKE);
            szklo.setStrokeWidth(grubosc);
            ciemna.setStyle(Paint.Style.STROKE);
            ciemna.setColor(0xFF000000);
            ciemna.setStrokeWidth(grubosc);
        }

        void ustaw(float nowaSila, float x, float y) {
            sila = Math.max(0f, Math.min(1f, nowaSila));
            px = x;
            py = y;
            invalidateSelf();
        }

        @Override
        protected void onBoundsChange(Rect b) {
            pole.set(b);
            wewnatrz.set(b.left + grubosc / 2f, b.top + grubosc / 2f, b.right - grubosc / 2f, b.bottom - grubosc / 2f);
            float g = ciemna.getStrokeWidth();
            wewnatrzCiemna.set(b.left + g / 2f, b.top + g / 2f, b.right - g / 2f, b.bottom - g / 2f);
            krawedz.setShader(new LinearGradient(0, b.top, 0, b.top + b.height() * 0.46f,
                    0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            // Krawędź szkła z pomiaru zrzutu tvOS 26: najjaśniejsza w lewym górnym rogu (ok. 55% bieli), słabnie
            // wzdłuż górnej i lewej krawędzi, znika w prawym górnym i lewym dolnym rogu, w prawym dolnym wraca na 18%.
            szklo.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom,
                    new int[]{0x8CFFFFFF, 0x52FFFFFF, 0x1FFFFFFF, 0x0AFFFFFF, 0x2EFFFFFF},
                    new float[]{0f, 0.25f, 0.5f, 0.75f, 1f}, Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(Canvas c) {
            if (pole.isEmpty()) return;
            float w = pole.width(), h = pole.height();
            if (zCieniem) {
                // Cień poza kształtem ikony (sam kształt jest wycięty). Spoczynek: mały cień u dołu, krycie 22%.
                // Zaznaczenie: rozmycie 10% boku, 7% w dół, krycie 40% (na zrzucie tvOS 26 taca pod zaznaczoną
                // ikoną jest ciemniejsza o ok. 20%, po bokach prawie wcale).
                float rozmycie = h * (0.03f + 0.07f * sila);
                float wDol = h * (0.018f + 0.052f * sila);
                int krycie = Math.round(255 * (0.22f + 0.18f * sila));
                cien.setShadowLayer(rozmycie, -px * w * 0.03f, wDol, krycie << 24);
                obrys.rewind();
                obrys.addRoundRect(pole, promien, promien, Path.Direction.CW);
                c.save();
                c.clipOutPath(obrys);
                c.drawRoundRect(pole, promien, promien, cien);
                c.restore();
            }
            if (sila <= 0.01f) return;
            // rozświetlenie górnej części i plama, która ucieka w stronę przeciwną do przechylenia
            krawedz.setAlpha(Math.round(0x14 * sila));
            c.drawRoundRect(pole, promien, promien, krawedz);
            // rozświetlenie z lewego górnego rogu: na zrzucie tvOS 26 lewa górna część ikony jest jaśniejsza o ok. 20%
            float cx = pole.left + w * (0.24f - 0.30f * px), cy = pole.top + h * (0.02f - 0.12f * py);
            int srodek = Math.round(0x3A * sila), polowa = Math.round(0x14 * sila);
            plama.setShader(new RadialGradient(cx, cy, w * 0.92f,
                    new int[]{(srodek << 24) | 0xFFFFFF, (polowa << 24) | 0xFFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP));
            c.drawRoundRect(pole, promien, promien, plama);
            szklo.setAlpha(Math.round(255 * sila));
            float r = Math.max(1f, promien - grubosc / 2f);
            c.drawRoundRect(wewnatrz, r, r, szklo);
            if (rodzajCiemnej != KRAWEDZ_TVOS) {
                // ciemna krawędź tylko na jasnej ikonie: delikatna 20% czerni, wyraźna 36% czerni i 1,6× grubsza
                ciemna.setAlpha(Math.round((rodzajCiemnej == KRAWEDZ_WYRAZNA ? 0x5C : 0x33) * sila));
                float g = ciemna.getStrokeWidth(), rc = Math.max(1f, promien - g / 2f);
                c.drawRoundRect(wewnatrzCiemna, rc, rc, ciemna);
            }
        }

        @Override
        public void setAlpha(int alpha) { }

        @Override
        public void setColorFilter(ColorFilter cf) { }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
