package com.techgenius.tvoslauncher;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

/** Lokalny test tłumaczeń moda PL (bez Androida): mapa stała i napisy sklejane w locie. */
public final class TestPl {
    static int bledy = 0;
    static PrintStream out;

    static void dyn(String en) {
        String pl = I18nPl.dynamic(en);
        String jedna = en.replace("\n", "\\n");
        if (jedna.length() > 70) jedna = jedna.substring(0, 70) + "…";
        if (pl == null) {
            bledy++;
            out.println("BRAK  " + jedna);
            return;
        }
        String plJedna = pl.replace("\n", "\\n");
        if (plJedna.length() > 110) plJedna = plJedna.substring(0, 110) + "…";
        out.println("OK    " + jedna + "\n   -> " + plJedna);
    }

    static void brak(String en) {
        String pl = I18nPl.dynamic(en);
        if (pl != null) {
            bledy++;
            out.println("NIEPOTRZEBNE DOPASOWANIE  " + en + " -> " + pl);
        }
    }

    public static void main(String[] a) throws Exception {
        out = new PrintStream(System.out, true, "UTF-8");
        Map<String, String> m = new HashMap<>();
        I18nPl.fill(m);
        out.println("Tekstów stałych w mapie: " + m.size());
        String[] stale = {"Settings", "Clear", "H:", "L:", "Humidity ", "Wind ", "Most Watched", "dash_latest_releases",
                "home_settings", "Names under icons", "Show app names", "Action", "Aurora", "Today", "Now",
                "HOURLY FORECAST", "5-DAY FORECAST", "My Location", "Continue watching", "Other apps", "Fewer apps",
                "Open", "Move", "Rename", "Weather", "Language", "On", "Off"};
        for (String s : stale) {
            String v = m.get(s);
            if (v == null) {
                bledy++;
                out.println("BRAK STAŁEGO  [" + s + "]");
            } else {
                out.println("  [" + s + "] -> [" + v + "]");
            }
        }

        out.println("--- napisy sklejane w locie ---");
        dyn("You are up to date (v2.1.5-pl1)");
        dyn("A new version of tvOS Launcher is available on GitHub.\nCurrent version: v2.1.5 → New version: v2.1.6\n\nUwaga testowa");
        dyn("Changelog — v2.1.6");
        dyn("Downloading update v2.1.6…");
        dyn("Installing update v2.1.6 automatically via ADB…");
        dyn("Removed remap for Guide.");
        dyn("Done (12 apps disabled)");
        dyn("Not done (3/14 disabled)");
        dyn("Model: HY4001");
        dyn("Brand/OEM: Skyworth");
        dyn("Exact TV model found and selected automatically:\n\n• Canal+ Box (canal-box)\nmodel match");
        dyn("Search: \"sky\" (4)");
        dyn("Select Apps to Disable — Canal+ Box (17 found)");
        dyn("Disabling (3/16):\ncom.example.app");
        dyn("Successfully disabled 12 packages for Canal+ Box.\n\n• Saved disabled app list (12 apps) for one-tap Restore.\n• Flushed package state to disk (sync).");
        dyn("Found saved disabled-app data (12 packages).\n\nRe-enable all 12 saved packages over ADB?");
        dyn("No saved disabled-app list was found.\n\nRestore will use your selected TV model (Canal+ Box) and re-enable its 14 packages over ADB.");
        dyn("Closed 3 apps · freed 120 MB");
        dyn("Closed 1 app · freed 40 MB");
        dyn("Closed 2 apps");
        dyn("Closed 1 app");
        dyn("No image files (jpg, png, webp) in this folder");
        dyn("Select this folder & shuffle (14 images)");
        dyn("Your current tvOS Launcher settings have been saved to:\n\n/storage/emulated/0/Download/tvos-launcher-settings.json");
        dyn("Could not write to Download/tvos-launcher-settings.json\n\nPlease allow storage access and try again.");
        dyn("Could not write to Download/tvos-launcher-settings.json\n\nEACCES\n\nPlease allow storage access and try again.");
        dyn("Restore from Download/tvos-launcher-settings.json");
        dyn("Restore from this folder (tvos-launcher-settings.json)");
        dyn("Settings were successfully restored from:\n/storage/emulated/0/Download/tvos-launcher-settings.json");
        dyn("Could not restore settings from:\n/sdcard/x.json\n\nbad json");
        dyn("Can't open Netflix");
        dyn("Can't switch to HDMI 1");
        dyn("Delete “Osoba 2”?");
        dyn("RAM 1.2 GB used of 1.9 GB");
        dyn("Move TiviMate · Left/Right, OK when done");
        dyn("Move TiviMate  ·  Left/Right, OK when done");
        dyn("Off in 25 min");
        dyn("▶ Open in Netflix");
        dyn("Get Netflix");
        dyn("To sign in, focus the Netflix tile on the Home screen and scan the QR code.");
        dyn("Sign out of Netflix?");
        dyn("Continue watching on the dock will stop showing your Netflix titles. The Netflix app itself stays signed in.");
        dyn("Signed out of Netflix.");
        dyn("This runs 'settings put global adb_enabled 0' over Local ADB and verifies that port 5555 is closed on your home network.\n\nIf WRITE_SECURE_SETTINGS…");
        dyn("Keep Alive monitors the Accessibility Service and automatically restores it if your TV turns it off.\n\n✓ Because…");
        dyn("⚠️ Security Warning:\n\nKeep Alive monitors…");
        dyn("WARNING: Manual Debloat lists ALL system apps installed on your TV.\n\n• Only disable…");
        dyn("Dock size set to 120%.");
        dyn("Added favorite team: Legia Warszawa");
        dyn("Added custom wallpaper #2.");
        dyn("Switch to Dzieci");
        dyn("PAGE 1 OF 3 (1–20 OF 55)");
        dyn("Page 2 of 3");
        dyn("MODELS (55)");
        dyn("All (131)");
        dyn("All disabled (4)");
        dyn("All TV Models (55)");
        dyn("✅ Disable Selected Packages (5)");
        dyn("Restore Selected Model Profile (Canal+ Box — 14 packages)");
        dyn("Custom 2");
        dyn("Folder shuffle enabled: Tapety");
        dyn("No place found for “Wrocalw”.");
        dyn("Location set to Wrocław.");
        dyn("WRITE_SECURE_SETTINGS and all launcher permissions are now active, and ADB (port 5555) has been closed for your security.\n\nTip: …");
        dyn("Sign in to Netflix");
        dyn("Couldn't reach Netflix");
        dyn("Couldn't reach Netflix. Focus the tile again to retry.");
        dyn("63% used · Recent Apps");
        dyn("63% used · Clean");
        dyn("12 min left");

        out.println("--- teksty, których szablony NIE powinny łapać ---");
        brak("TiviMate");
        brak("Projectivy Launcher");
        brak("Netflix");
        brak("12:45");
        brak("Wtorek, 10 listopada");

        out.println(bledy == 0 ? "WYNIK: wszystko w porządku" : "WYNIK: błędów " + bledy);
        System.exit(bledy == 0 ? 0 : 1);
    }
}
