package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Mod PL: włączenie "Oglądaj dalej" bez komputera. Większość telewizorów nie ma w ustawieniach
 * przełącznika dostępu do powiadomień, więc launcher nadaje go sobie sam przez debugowanie USB
 * (lokalne ADB, to samo połączenie co w oryginalnym kreatorze), ale wykonuje TYLKO trzy polecenia
 * potrzebne do "Oglądaj dalej". Niczego nie wyłącza, nie zmienia ekranu głównego i nie zamyka ADB.
 * Plik źródłowy leży w pl/java/, a pl/naloz.py kopiuje go do src/ przy każdym buildzie.
 */
final class ModPlAdb {
    private ModPlAdb() { }

    /** Automatyczna konfiguracja launchera nie wyłącza debugowania USB (prośba użytkownika 11.11.2026). */
    static final boolean ZAMYKAJ_ADB = false;

    private static AlertDialog.Builder okno(Activity a, String tytul) {
        return new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(tytul);
    }

    private static String t(Activity a, String en) {
        return I18n.t(a, en);
    }

    /**
     * Gdy debugowanie USB jest włączone, pyta o zgodę i nadaje dostęp sam; gdy nie, uruchamia
     * {@code gdyZamkniete} (ekrany systemowe, a na końcu instrukcja).
     */
    static void wlaczOgladajDalej(final Activity a, final Runnable gdyZamkniete) {
        if (a == null || a.isFinishing()) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean otwarte = LocalAdb.isPortOpen("127.0.0.1", 5555, 600);
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (a.isFinishing()) return;
                        if (otwarte) zapytaj(a);
                        else gdyZamkniete.run();
                    }
                });
            }
        }, "mod-pl-adb").start();
    }

    private static void zapytaj(final Activity a) {
        okno(a, t(a, "Continue watching"))
                .setMessage(t(a, "The launcher can turn this on by itself through USB debugging.\n\n"
                        + "In a moment the TV will ask \"Allow USB debugging?\". Choose Allow.\n\n"
                        + "Only access to playback notifications is granted. Nothing else on the TV is changed."))
                .setPositiveButton(t(a, "Turn on"), (d, w) -> nadaj(a))
                .setNegativeButton(t(a, "Cancel"), null)
                .show();
    }

    private static void nadaj(final Activity a) {
        Toast.makeText(a, t(a, "Waiting for your consent on the TV screen…"), Toast.LENGTH_LONG).show();
        final String pkg = a.getPackageName();
        final String usluga = pkg + "/" + pkg + ".MediaWatchService";
        String teraz = "";
        try {
            teraz = Settings.Secure.getString(a.getContentResolver(), "enabled_notification_listeners");
        } catch (Throwable ignored) {
            // bez odczytu wpisujemy samą naszą usługę
        }
        final String nowe = teraz == null || teraz.trim().isEmpty() || "null".equalsIgnoreCase(teraz.trim())
                ? usluga : (teraz.contains(usluga) ? teraz : teraz + ":" + usluga);
        final List<String> polecenia = new ArrayList<String>();
        polecenia.add("cmd notification allow_listener " + usluga + " 2>/dev/null");
        polecenia.add("settings put secure enabled_notification_listeners '" + nowe + "'");
        polecenia.add("pm grant " + pkg + " android.permission.MEDIA_CONTENT_CONTROL 2>/dev/null");
        // przy tej samej zgodzie: plakietka z wynikiem meczu nad aplikacjami i wygaszacz launchera (jeśli jeszcze nie był włączany)
        polecenia.add("appops set " + pkg + " SYSTEM_ALERT_WINDOW allow 2>/dev/null");
        if (!ModPlWygaszacz.aktywny(a) && !ModPlWygaszacz.autoZrobione(a)) {
            ModPlWygaszacz.zapamietajPoprzedni(a);
            polecenia.add("settings put secure screensaver_components " + ModPlWygaszacz.skladnik(a) + " 2>/dev/null");
            polecenia.add("settings put secure screensaver_enabled 1 2>/dev/null");
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean wykonane;
                try {
                    wykonane = LocalAdb.execShellSequential(a, polecenia, null);
                } catch (Throwable e) {
                    wykonane = false;
                }
                final boolean ok = wykonane;
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (a.isFinishing()) return;
                        boolean dziala = false;
                        try {
                            dziala = ok && MediaWatchService.granted(a);
                            if (dziala) {
                                MediaWatchService.ensureSessionTracking(a);
                                odswiez(a);
                            }
                        } catch (Throwable ignored) {
                            // stan pokaże komunikat niżej
                        }
                        if (dziala) {
                            okno(a, t(a, "Continue watching"))
                                    .setMessage(t(a, "Done. Continue watching is on.\n\n"
                                            + "For safety you can now turn USB debugging off in Developer options."))
                                    .setPositiveButton(t(a, "OK"), null)
                                    .show();
                        } else {
                            okno(a, t(a, "Continue watching"))
                                    .setMessage(t(a, "It didn't work. The consent on the TV screen was probably not given in time.\n\n"
                                            + "Try again and choose Allow when the TV asks about USB debugging. If a computer is connected to the TV over ADB, disconnect it for that moment."))
                                    .setPositiveButton(t(a, "Try again"), (d, w) -> nadaj(a))
                                    .setNegativeButton(t(a, "Cancel"), null)
                                    .show();
                        }
                    }
                });
            }
        }, "mod-pl-adb").start();
    }

    /** Po nadaniu dostępu odświeża wiersz w ustawieniach albo krok kreatora (jak powrót z ekranu systemowego). */
    private static void odswiez(Activity a) {
        try {
            if (a instanceof SettingsActivity) {
                ((SettingsActivity) a).refreshAll();
            } else if (a instanceof SetupActivity) {
                SetupActivity s = (SetupActivity) a;
                if (s.index == 5 || s.index == 6 || s.index == 7) s.show(s.index, 0);
            }
        } catch (Throwable ignored) {
            // odświeżenie jest tylko kosmetyczne
        }
    }

    private static boolean otworz(Activity a, Intent i) {
        try {
            ComponentName cn = i.resolveActivity(a.getPackageManager());
            if (cn == null || cn.getClassName() == null || cn.getClassName().contains("Stub")) return false;
            a.startActivity(i);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ powiadomienia nad innymi aplikacjami

    /**
     * Zgoda na wyświetlanie nad innymi aplikacjami (plakietka z wynikiem meczu, ModPlGole). Przy włączonym debugowaniu
     * USB launcher nadaje ją sobie sam jednym poleceniem; inaczej otwiera ekran systemowy, a gdy telewizor go nie ma,
     * pokazuje instrukcję.
     */
    static void wlaczNakladki(final Activity a) {
        if (a == null || a.isFinishing() || ModPlGole.mozeRysowac(a)) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean otwarte = LocalAdb.isPortOpen("127.0.0.1", 5555, 600);
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (a.isFinishing()) return;
                        if (otwarte) {
                            zapytajNakladki(a);
                            return;
                        }
                        Intent ekran = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + a.getPackageName()));
                        if (!otworz(a, ekran)) instrukcjaNakladki(a);
                    }
                });
            }
        }, "mod-pl-adb").start();
    }

    private static void zapytajNakladki(final Activity a) {
        okno(a, t(a, "Match alerts"))
                .setMessage(t(a, "To show the score over other apps the launcher needs one more permission. "
                        + "It can grant it to itself through USB debugging.\n\n"
                        + "In a moment the TV will ask \"Allow USB debugging?\". Choose Allow."))
                .setPositiveButton(t(a, "Turn on"), (d, w) -> nadajNakladki(a))
                .setNegativeButton(t(a, "Cancel"), null)
                .show();
    }

    private static void nadajNakladki(final Activity a) {
        Toast.makeText(a, t(a, "Waiting for your consent on the TV screen…"), Toast.LENGTH_LONG).show();
        final List<String> polecenia = new ArrayList<String>();
        polecenia.add("appops set " + a.getPackageName() + " SYSTEM_ALERT_WINDOW allow");
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean wykonane;
                try {
                    wykonane = LocalAdb.execShellSequential(a, polecenia, null);
                } catch (Throwable e) {
                    wykonane = false;
                }
                final boolean ok = wykonane;
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (a.isFinishing()) return;
                        if (ok && ModPlGole.mozeRysowac(a)) {
                            odswiez(a);
                            ModPlGole.przyklad(a);
                            okno(a, t(a, "Match alerts"))
                                    .setMessage(t(a, "Done. Alerts will show in the corner of the screen, like the sample one now.\n\n"
                                            + "For safety you can now turn USB debugging off in Developer options."))
                                    .setPositiveButton(t(a, "OK"), null)
                                    .show();
                        } else {
                            okno(a, t(a, "Match alerts"))
                                    .setMessage(t(a, "It didn't work. The consent on the TV screen was probably not given in time.\n\n"
                                            + "Try again and choose Allow when the TV asks about USB debugging. If a computer is connected to the TV over ADB, disconnect it for that moment."))
                                    .setPositiveButton(t(a, "Try again"), (d, w) -> nadajNakladki(a))
                                    .setNegativeButton(t(a, "Cancel"), null)
                                    .show();
                        }
                    }
                });
            }
        }, "mod-pl-adb").start();
    }

    private static void instrukcjaNakladki(final Activity a) {
        okno(a, t(a, "Match alerts"))
                .setMessage(t(a, "Alerts are on. Without one more permission they appear as a small system message at the bottom of the screen. "
                        + "To get the badge in the corner:\n\n"
                        + "1. TV Settings → System → About: press OK 7 times on \"Android TV OS build\".\n"
                        + "2. Go back to System → Developer options and turn on \"USB debugging\".\n"
                        + "3. Come back here and choose \"Show a sample alert\"."))
                .setPositiveButton(t(a, "Developer options"), (d, w) -> {
                    if (!otworz(a, new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                            && !otworz(a, new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))) {
                        otworz(a, new Intent(Settings.ACTION_SETTINGS));
                    }
                })
                .setNegativeButton(t(a, "Cancel"), null)
                .show();
    }

    // ------------------------------------------------------------------ wygaszacz ekranu

    /** Ustawia wygaszacz moda jako wygaszacz telewizora (a gdy już jest ustawiony, pozwala wrócić do poprzedniego). */
    static void ustawWygaszacz(final Activity a) {
        if (a == null || a.isFinishing()) return;
        final boolean jest = ModPlWygaszacz.aktywny(a);
        if (!jest && LocalAdb.hasWriteSecureSettings(a)) {
            zmienWygaszacz(a, ModPlWygaszacz.skladnik(a));
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean otwarte = LocalAdb.isPortOpen("127.0.0.1", 5555, 600);
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (a.isFinishing()) return;
                        if (jest) {
                            wylaczWygaszacz(a, otwarte);
                        } else if (otwarte) {
                            okno(a, t(a, "Screen saver"))
                                    .setMessage(t(a, "The launcher can set its screen saver as the TV screen saver through USB debugging.\n\n"
                                            + "In a moment the TV will ask \"Allow USB debugging?\". Choose Allow."))
                                    .setPositiveButton(t(a, "Turn on"), (d, w) -> zmienWygaszacz(a, ModPlWygaszacz.skladnik(a)))
                                    .setNegativeButton(t(a, "Cancel"), null)
                                    .show();
                        } else if (otworz(a, new Intent("android.settings.DREAM_SETTINGS"))) {
                            Toast.makeText(a, t(a, "Choose tvOS Launcher on the list of screen savers."), Toast.LENGTH_LONG).show();
                        } else {
                            instrukcjaWygaszacza(a);
                        }
                    }
                });
            }
        }, "mod-pl-adb").start();
    }

    private static void wylaczWygaszacz(final Activity a, boolean otwarte) {
        final String poprzedni = ModPlWygaszacz.poprzedni(a);
        AlertDialog.Builder b = okno(a, t(a, "Screen saver"))
                .setMessage(t(a, "The launcher screen saver is on. It starts by itself when the TV is idle."))
                .setNegativeButton(t(a, "OK"), null);
        if ((otwarte || LocalAdb.hasWriteSecureSettings(a)) && !poprzedni.isEmpty()) {
            b.setPositiveButton(t(a, "Restore the previous one"), (d, w) -> zmienWygaszacz(a, poprzedni));
        } else {
            b.setPositiveButton(t(a, "TV Settings"), (d, w) -> {
                if (!otworz(a, new Intent("android.settings.DREAM_SETTINGS"))) otworz(a, new Intent(Settings.ACTION_SETTINGS));
            });
        }
        b.show();
    }

    private static void zmienWygaszacz(final Activity a, final String skladnik) {
        final boolean naMod = skladnik.contains("ModPlWygaszacz");
        if (ModPlWygaszacz.ustawBezposrednio(a, skladnik)) {      // launcher ma zgodę z kreatora: bez ADB i bez pytań
            odswiez(a);
            Toast.makeText(a, t(a, naMod ? "Done. The screen saver starts by itself when the TV is idle."
                    : "Done. The previous screen saver is back."), Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(a, t(a, "Waiting for your consent on the TV screen…"), Toast.LENGTH_LONG).show();
        ModPlWygaszacz.zapamietajPoprzedni(a);
        final List<String> polecenia = new ArrayList<String>();
        polecenia.add("settings put secure screensaver_components " + skladnik);
        polecenia.add("settings put secure screensaver_enabled 1");
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean wykonane;
                try {
                    wykonane = LocalAdb.execShellSequential(a, polecenia, null);
                } catch (Throwable e) {
                    wykonane = false;
                }
                final boolean ok = wykonane;
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (a.isFinishing()) return;
                        if (ok && ModPlWygaszacz.aktywny(a) == naMod) {
                            odswiez(a);
                            okno(a, t(a, "Screen saver"))
                                    .setMessage(t(a, naMod
                                            ? "Done. The screen saver starts by itself when the TV is idle.\n\n"
                                            + "For safety you can now turn USB debugging off in Developer options."
                                            : "Done. The previous screen saver is back."))
                                    .setPositiveButton(t(a, "OK"), null)
                                    .show();
                        } else {
                            okno(a, t(a, "Screen saver"))
                                    .setMessage(t(a, "It didn't work. The consent on the TV screen was probably not given in time.\n\n"
                                            + "Try again and choose Allow when the TV asks about USB debugging. If a computer is connected to the TV over ADB, disconnect it for that moment."))
                                    .setPositiveButton(t(a, "Try again"), (d, w) -> zmienWygaszacz(a, skladnik))
                                    .setNegativeButton(t(a, "Cancel"), null)
                                    .show();
                        }
                    }
                });
            }
        }, "mod-pl-adb").start();
    }

    private static void instrukcjaWygaszacza(final Activity a) {
        okno(a, t(a, "Screen saver"))
                .setMessage(t(a, "This TV has no list of screen savers in its settings, so the launcher will set it itself through USB debugging:\n\n"
                        + "1. TV Settings → System → About: press OK 7 times on \"Android TV OS build\".\n"
                        + "2. Go back to System → Developer options and turn on \"USB debugging\".\n"
                        + "3. Come back here and choose \"Use as the TV screen saver\" again."))
                .setPositiveButton(t(a, "Developer options"), (d, w) -> {
                    if (!otworz(a, new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                            && !otworz(a, new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))) {
                        otworz(a, new Intent(Settings.ACTION_SETTINGS));
                    }
                })
                .setNegativeButton(t(a, "Cancel"), null)
                .show();
    }

    /** Telewizor nie ma przełącznika i debugowanie USB jest wyłączone: co zrobić krok po kroku. */
    static void instrukcja(final Activity a) {
        if (a == null || a.isFinishing()) return;
        okno(a, t(a, "Continue watching"))
                .setMessage(t(a, "This TV has no switch for this permission, so the launcher will turn it on itself through USB debugging:\n\n"
                        + "1. TV Settings → System → About: press OK 7 times on \"Android TV OS build\".\n"
                        + "2. Go back to System → Developer options and turn on \"USB debugging\".\n"
                        + "3. Come back here and choose Continue watching again.\n\n"
                        + "Afterwards USB debugging can be turned off."))
                .setPositiveButton(t(a, "Developer options"), (d, w) -> {
                    if (!otworz(a, new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                            && !otworz(a, new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))) {
                        otworz(a, new Intent(Settings.ACTION_SETTINGS));
                    }
                })
                .setNeutralButton(t(a, "TV Settings"), (d, w) -> otworz(a, new Intent(Settings.ACTION_SETTINGS)))
                .setNegativeButton(t(a, "Cancel"), null)
                .show();
    }
}
