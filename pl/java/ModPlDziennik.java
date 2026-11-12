package com.techgenius.tvoslauncher;

import android.util.Log;

/**
 * Mod PL: dziennik moda (logcat). Na boxie użytkownika włączony, w instalce dla innych wyłączony
 * (użytkownik 12.11.2026: „dziennik lepiej, jak będzie tylko u nas, nie na instalce, bo niepotrzebnie
 * będzie się zbierać komuś”).
 *
 * Instalkę buduje się poleceniem `DZIENNIK=0 bash zbuduj-pl.sh`: pl/naloz.py przy kopiowaniu tego pliku ustawia
 * WLACZONY na false, a javac wycina wtedy wywołania Log z ciał metod (stała znana przy kompilacji).
 */
final class ModPlDziennik {
    private ModPlDziennik() { }

    static final boolean WLACZONY = true;

    static void i(String tag, String tekst) {
        if (WLACZONY) Log.i(tag, tekst);
    }

    static void w(String tag, String tekst) {
        if (WLACZONY) Log.w(tag, tekst);
    }

    static void w(String tag, String tekst, Throwable wyjatek) {
        if (WLACZONY) Log.w(tag, tekst, wyjatek);
    }
}
