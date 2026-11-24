package com.techgenius.tvoslauncher;

import android.os.IBinder;

/**
 * Mod PL: mały program pomocniczy uruchamiany z uprawnieniami powłoki ADB (nie jako część launchera):
 *   CLASSPATH=/ścieżka/do/base.apk app_process / com.techgenius.tvoslauncher.ModPlPowloka POLECENIE
 * Launcher woła go przez własnego klienta ADB (LocalAdb), bo zwykła aplikacja nie ma potrzebnych uprawnień,
 * a powłoka ADB w Androidzie 10 je ma (STATUS_BAR_SERVICE).
 * Polecenia:
 *   glosnosc-odlacz   odłącza od usługi dźwięku systemowy pasek głośności (AudioService.setVolumeController(null)).
 *                     Klawisze głośności dalej obsługuje system, tylko nie pokazuje paska; wskaźnik rysuje launcher
 *                     (ModPlGlosnosc). Pasek wraca sam po ponownym uruchomieniu SystemUI albo boxa.
 * Wynik to jedna linia zaczynająca się od „OK” albo „BLAD”.
 * Plik źródłowy leży w pl/java/, a pl/naloz.py kopiuje go do src/ przy każdym buildzie.
 */
public final class ModPlPowloka {
    private ModPlPowloka() { }

    public static void main(String[] argumenty) {
        String polecenie = argumenty != null && argumenty.length > 0 ? argumenty[0] : "";
        try {
            if ("glosnosc-odlacz".equals(polecenie)) {
                Class<?> usluga = Class.forName("android.media.IAudioService");
                Object dzwiek = interfejs("audio", "android.media.IAudioService");
                Class<?> kontroler = Class.forName("android.media.IVolumeController");
                usluga.getMethod("setVolumeController", kontroler).invoke(dzwiek, new Object[]{null});
                System.out.println("OK glosnosc-odlacz");
            } else {
                System.out.println("BLAD nieznane polecenie: " + polecenie);
            }
        } catch (Throwable e) {
            Throwable przyczyna = e.getCause() != null ? e.getCause() : e;
            System.out.println("BLAD " + polecenie + ": " + przyczyna);
        }
    }

    /** Usługa systemowa o podanej nazwie jako obiekt jej interfejsu (bez numerów transakcji, po nazwach metod). */
    private static Object interfejs(String nazwa, String typ) throws Exception {
        Object lacznik = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, nazwa);
        if (lacznik == null) throw new IllegalStateException("brak usługi " + nazwa);
        return Class.forName(typ + "$Stub").getMethod("asInterface", IBinder.class).invoke(null, lacznik);
    }
}
