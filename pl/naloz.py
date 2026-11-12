# Nakłada zmiany moda PL na źródła tvOS Launchera (czysty kod z GitHuba).
#
# Każda zmiana to: plik, fragment do znalezienia (musi wystąpić dokładnie raz), nowy fragment.
# Skrypt jest powtarzalny: zmianę już nałożoną pomija. Gdy fragmentu nie ma (np. po nowej wersji
# launchera), kończy się błędem i wypisuje, której zmiany to dotyczy.
#
# Uruchomienie:  python -I pl/naloz.py           (nakłada)
#                python -I pl/naloz.py --sprawdz (tylko sprawdza, nic nie zapisuje)
import io
import os
import sys

sys.stdout.reconfigure(encoding="utf-8")

TU = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.normpath(os.path.join(TU, "..", "src", "com", "techgenius", "tvoslauncher"))
TYLKO_SPRAWDZ = "--sprawdz" in sys.argv

ZMIANY = []


def zmiana(plik, opis, stary, nowy, ile=1):
    """ile = ile razy fragment ma wystąpić w pliku (wszystkie wystąpienia są zamieniane)."""
    ZMIANY.append((plik, opis, stary, nowy, ile))


# ---------------------------------------------------------------- I18n.java: język polski

zmiana("I18n.java", "stała LANG_PL",
       '    public static final String LANG_KO = "ko";\n',
       '    public static final String LANG_KO = "ko";\n'
       '    public static final String LANG_PL = "pl";\n')

zmiana("I18n.java", "Polski na liście języków",
       '        {LANG_EN, "English", "Use tvOS Launcher in English"},\n',
       '        {LANG_EN, "English", "Use tvOS Launcher in English"},\n'
       '        {LANG_PL, "Polski", "Używaj tvOS Launchera po polsku"},\n')

zmiana("I18n.java", "pl w obsługiwanych językach",
       '            LANG_IT, LANG_RU, LANG_PT, LANG_AR, LANG_JA, LANG_ZH, LANG_KO\n    ));',
       '            LANG_IT, LANG_RU, LANG_PT, LANG_AR, LANG_JA, LANG_ZH, LANG_KO, LANG_PL\n    ));')

zmiana("I18n.java", "wczytanie polskiej mapy",
       '        initAzerbaijani();\n    }\n',
       '        initAzerbaijani();\n'
       '\n'
       '        Map<String, String> plMap = new HashMap<String, String>();\n'
       '        I18nPl.fill(plMap);\n'
       '        LANG_MAPS.put(LANG_PL, plMap);\n'
       '    }\n')

zmiana("I18n.java", "locale pl-PL",
       '            case LANG_KO: return Locale.forLanguageTag("ko-KR");\n',
       '            case LANG_KO: return Locale.forLanguageTag("ko-KR");\n'
       '            case LANG_PL: return Locale.forLanguageTag("pl-PL");\n')

zmiana("I18n.java", "isPl, langCode, cached",
       '    public static boolean isTr(Context c) {\n'
       '        return LANG_TR.equals(getLang(c));\n'
       '    }\n',
       '    public static boolean isTr(Context c) {\n'
       '        return LANG_TR.equals(getLang(c));\n'
       '    }\n'
       '\n'
       '    public static boolean isPl(Context c) {\n'
       '        return LANG_PL.equals(getLang(c));\n'
       '    }\n'
       '\n'
       '    /** Kod ostatnio wczytanego języka interfejsu (bez kontekstu). */\n'
       '    public static String langCode() {\n'
       '        return cachedLang != null ? cachedLang : LANG_EN;\n'
       '    }\n'
       '\n'
       '    /** Tłumaczy pojedynczy tekst bez kontekstu, w ostatnio wczytanym języku. */\n'
       '    public static String cached(String en) {\n'
       '        if (en == null) return "";\n'
       '        String lang = cachedLang;\n'
       '        if (lang == null || LANG_EN.equals(lang) || LANG_TR.equals(lang)) return en;\n'
       '        return translateOther(lang, en);\n'
       '    }\n')

zmiana("I18n.java", "napisy sklejane w locie po polsku",
       '        // Dynamic pattern translations\n',
       '        if (LANG_PL.equals(lang)) {\n'
       '            String dyn = I18nPl.dynamic(en);\n'
       '            if (dyn != null) return dyn;\n'
       '        }\n'
       '        // Dynamic pattern translations\n')

zmiana("I18n.java", "polski format daty",
       '            case LANG_DE:\n'
       '                return new SimpleDateFormat("EEEE, d. MMMM", loc).format(date);\n',
       '            case LANG_PL: {\n'
       '                String d = new SimpleDateFormat("EEEE, d MMMM", loc).format(date);\n'
       '                return d.isEmpty() ? d : d.substring(0, 1).toUpperCase(loc) + d.substring(1);\n'
       '            }\n'
       '            case LANG_DE:\n'
       '                return new SimpleDateFormat("EEEE, d. MMMM", loc).format(date);\n')

zmiana("I18n.java", "polskie nazwy profili",
       '        } else if (LANG_AZ.equals(getLang(c))) {\n',
       '        } else if (LANG_PL.equals(getLang(c))) {\n'
       '            if (name.equals("Default") || name.equals("Varsayılan")) return "Domyślny";\n'
       '            if (name.startsWith("Person ")) return "Osoba " + name.substring("Person ".length());\n'
       '            if (name.startsWith("Kişi ")) return "Osoba " + name.substring("Kişi ".length());\n'
       '            if (name.startsWith("Profile ")) return "Profil " + name.substring("Profile ".length());\n'
       '            if (name.equals("Kids")) return "Dzieci";\n'
       '        } else if (LANG_AZ.equals(getLang(c))) {\n')

# ---------------------------------------------------------------- Najczęściej oglądane: Polska

zmiana("HomeDashboard.java", "Polska na liście krajów",
       '        {"TR", "Türkiye (Turkey)", "tr"},\n',
       '        {"PL", "Polska (Poland)", "pl"},\n'
       '        {"TR", "Türkiye (Turkey)", "tr"},\n')

zmiana("HomeDashboard.java", "kraj PL dla języka polskiego",
       '        if ("tr".equals(lang)) return "TR";\n',
       '        if ("pl".equals(lang)) return "PL";\n'
       '        if ("tr".equals(lang)) return "TR";\n')

zmiana("HomeDashboard.java", "widżet godzin modlitw domyślnie wyłączony",
       '        return prefs(c).getBoolean(KEY_W_PRAYER, true);\n',
       '        return prefs(c).getBoolean(KEY_W_PRAYER, false);\n')

# Widżet meczów zostaje domyślnie WŁĄCZONY (nowe instalacje, np. u znajomego kibica); kto go wyłączył,
# ma to zapisane w ustawieniach. Poniżej ligi i drużyny pod polskiego kibica (dane ESPN, bez Ekstraklasy:
# ESPN jej nie ma).
zmiana("HomeDashboard.java", "ligi: Ekstraklasa i Liga Konferencji, bez tureckiej",
       '        { "all", "All Top Leagues" },\n'
       '        { "uefa.champions", "UEFA Champions League" },\n'
       '        { "uefa.europa", "UEFA Europa League" },\n'
       '        { "eng.1", "Premier League" },\n'
       '        { "esp.1", "LaLiga" },\n'
       '        { "ita.1", "Serie A" },\n'
       '        { "ger.1", "Bundesliga" },\n'
       '        { "tur.1", "Trendyol Süper Lig" },\n'
       '        { "fra.1", "Ligue 1" }\n',
       '        { "all", "All Top Leagues" },\n'
       '        { "pol.1", "Ekstraklasa" },\n'
       '        { "uefa.champions", "UEFA Champions League" },\n'
       '        { "uefa.europa", "UEFA Europa League" },\n'
       '        { "uefa.europa.conf", "UEFA Conference League" },\n'
       '        { "eng.1", "Premier League" },\n'
       '        { "esp.1", "LaLiga" },\n'
       '        { "ita.1", "Serie A" },\n'
       '        { "ger.1", "Bundesliga" },\n'
       '        { "fra.1", "Ligue 1" }\n')

zmiana("HomeDashboard.java", "Najlepsze ligi: puchary UEFA zamiast ligi tureckiej",
       'String[] codes = { "uefa.champions", "eng.1", "esp.1", "tur.1", "ita.1", "ger.1" };\n',
       'String[] codes = { "uefa.champions", "uefa.europa", "uefa.europa.conf", "eng.1", "esp.1", "ita.1", "ger.1" };\n')

zmiana("HomeDashboard.java", "domyślne ulubione drużyny pod polskiego kibica",
       '            def.add("Qarabağ");\n'
       '            def.add("Galatasaray");\n'
       '            def.add("Real Madrid");\n',
       '            def.add("Polska");\n'
       '            def.add("Lech Poznań");\n'
       '            def.add("Jagiellonia");\n'
       '            def.add("Legia");\n'
       '            def.add("Real Madrid");\n'
       '            def.add("Barcelona");\n')

zmiana("HomeDashboard.java", "lista drużyn do wyboru: polskie kluby zamiast azerskich",
       '        "Qarabağ", "Neftçi", "Sabah", "Zirə",\n'
       '        "Galatasaray", "Fenerbahçe", "Beşiktaş", "Trabzonspor",\n',
       '        "Polska", "Legia", "Lech Poznań", "Jagiellonia", "Raków", "Pogoń", "Widzew", "Górnik", "Cracovia",\n')

zmiana("HomeDashboard.java", "przycisk ligi po polsku",
       'createActionPill(host, "League: " + curLeagueLabel);\n',
       'createActionPill(host, I18n.t(host, "League: ") + I18n.t(host, curLeagueLabel));\n')

zmiana("Suggestions.java", "polecane z JustWatch dla Polski",
       '                JSONObject vars = new JSONObject().put("country", "IN").put("language", "en")\n',
       '                JSONObject vars = new JSONObject().put("country", I18n.isPl(c) ? "PL" : "IN")\n'
       '                        .put("language", I18n.isPl(c) ? "pl" : "en")\n')

zmiana("Suggestions.java", "gatunki w języku interfejsu",
       '            String n = names.get(g.optJSONObject(i).optString("shortName"));\n'
       '            if (n != null) out.add(n);\n',
       '            String n = names.get(g.optJSONObject(i).optString("shortName"));\n'
       '            if (n != null) out.add(I18n.cached(n));\n')

# ---------------------------------------------------------------- pogoda po polsku

zmiana("Weather.java", "polskie opisy pogody",
       '        if (!I18n.isTr(c)) return I18n.t(c, describe(code, day));\n',
       '        if (I18n.isPl(c)) {\n'
       '            switch (code) {\n'
       '                case 0: return day ? "Słonecznie" : "Bezchmurnie";\n'
       '                case 1: return day ? "Przeważnie słonecznie" : "Lekkie zachmurzenie";\n'
       '                case 2: return "Częściowe zachmurzenie";\n'
       '                case 3: return "Pochmurno";\n'
       '                case 45: case 48: return "Mgła";\n'
       '                case 51: case 53: case 55: case 56: case 57: return "Mżawka";\n'
       '                case 61: case 63: case 80: case 81: return "Deszcz";\n'
       '                case 65: case 82: return "Ulewny deszcz";\n'
       '                case 66: case 67: return "Marznący deszcz";\n'
       '                case 71: case 73: case 75: case 77: case 85: case 86: return "Śnieg";\n'
       '                case 95: case 96: case 99: return "Burza";\n'
       '                default: return "";\n'
       '            }\n'
       '        }\n'
       '        if (!I18n.isTr(c)) return I18n.t(c, describe(code, day));\n')

zmiana("Weather.java", "wyszukiwanie miast w języku interfejsu",
       'search?count=8&language=en&name="\n',
       'search?count=8&language=" + I18n.langCode() + "&name="\n')

zmiana("Weather.java", "polska nazwa miasta z lokalizacji po IP",
       '                return j.optString("city", "");\n',
       '                return localName(j.optString("city", ""), out[0], out[1]);\n')

zmiana("Weather.java", "funkcja localName",
       '    static final class Place {\n',
       '    private static String lastCityEn = "", lastCityLocal = "";\n'
       '\n'
       '    /** Mod PL: nazwa miasta po polsku (np. Warsaw -> Warszawa); przy błędzie zwraca nazwę wejściową. */\n'
       '    private static String localName(String city, double lat, double lon) {\n'
       '        if (city == null || city.isEmpty() || !"pl".equals(I18n.langCode())) return city;\n'
       '        if (city.equals(lastCityEn)) return lastCityLocal;\n'
       '        String local = city;\n'
       '        try {\n'
       '            JSONObject j = new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=5&language=pl&name="\n'
       '                    + URLEncoder.encode(city, "UTF-8")));\n'
       '            JSONArray a = j.optJSONArray("results");\n'
       '            for (int i = 0; a != null && i < a.length(); i++) {\n'
       '                JSONObject r = a.getJSONObject(i);\n'
       '                if (Math.abs(r.optDouble("latitude", 999) - lat) < 0.6\n'
       '                        && Math.abs(r.optDouble("longitude", 999) - lon) < 0.9) {\n'
       '                    String n = r.optString("name", "");\n'
       '                    if (!n.isEmpty()) {\n'
       '                        local = n;\n'
       '                        break;\n'
       '                    }\n'
       '                }\n'
       '            }\n'
       '            lastCityEn = city;\n'
       '            lastCityLocal = local;\n'
       '        } catch (Exception ignored) {\n'
       '            // zostaje nazwa z usługi lokalizacji\n'
       '        }\n'
       '        return local;\n'
       '    }\n'
       '\n'
       '    static final class Place {\n')

# ---------------------------------------------------------------- dok: stałe podpisy pod ikonami

zmiana("Scenes.java", "ustawienie stałych podpisów w doku",
       '    public static void setDockNamesShown(Context c, boolean shown) {\n'
       '        prefs(c).edit().putBoolean("dock_names", shown).apply();\n'
       '    }\n',
       '    public static void setDockNamesShown(Context c, boolean shown) {\n'
       '        prefs(c).edit().putBoolean("dock_names", shown).apply();\n'
       '    }\n'
       '\n'
       '    /** Mod PL: stałe podpisy z nazwami pod ikonami doku. Domyślnie włączone. */\n'
       '    public static boolean isDockNamesAlways(Context c) {\n'
       '        return prefs(c).getBoolean("dock_names_always", true);\n'
       '    }\n'
       '\n'
       '    public static void setDockNamesAlways(Context c, boolean on) {\n'
       '        prefs(c).edit().putBoolean("dock_names_always", on).apply();\n'
       '    }\n')

zmiana("SettingsActivity.java", "przełącznik podpisów w ustawieniach",
       '                        Scenes.setDockNamesShown(this, !Scenes.isDockNamesShown(this));\n'
       '                        refreshAll();\n'
       '                    });\n',
       '                        Scenes.setDockNamesShown(this, !Scenes.isDockNamesShown(this));\n'
       '                        refreshAll();\n'
       '                    });\n'
       '            row(tr("Names under icons", "Simgelerin altında adlar"),\n'
       '                    () -> Scenes.isDockNamesAlways(this) ? tr("On", "Açık") : tr("Off", "Kapalı"),\n'
       '                    () -> {\n'
       '                        Scenes.setDockNamesAlways(this, !Scenes.isDockNamesAlways(this));\n'
       '                        refreshAll();\n'
       '                    });\n')

zmiana("SceneHome.java", "lista podpisów doku",
       '    final List<View> dockTiles = new ArrayList<>();\n',
       '    final List<View> dockTiles = new ArrayList<>();\n'
       '    final List<TextView> dockNames = new ArrayList<>();\n')

zmiana("SceneHome.java", "dymek ukryty przy stałych podpisach",
       '        if (text == null || text.isEmpty() || tile == null || !Scenes.isDockNamesShown(host)) {\n',
       '        if (text == null || text.isEmpty() || tile == null || !Scenes.isDockNamesShown(host)\n'
       '                || (Scenes.isDockNamesAlways(host) && moveDockIdx < 0)) {\n')

zmiana("SceneHome.java", "czyszczenie listy podpisów",
       '        dockBox.removeAllViews();\n'
       '        dockTiles.clear();\n',
       '        dockBox.removeAllViews();\n'
       '        dockTiles.clear();\n'
       '        dockNames.clear();\n')

zmiana("SceneHome.java", "wiersz podpisów pod wierszem ikon",
       '                if (i > 0) rlp.topMargin = tileGap;\n'
       '                dockBox.addView(row, rlp);\n'
       '            }\n',
       '                if (i > 0) rlp.topMargin = tileGap;\n'
       '                dockBox.addView(row, rlp);\n'
       '                if (namesAlways) {\n'
       '                    nameRow = new LinearLayout(host);\n'
       '                    nameRow.setOrientation(LinearLayout.HORIZONTAL);\n'
       '                    nameRow.setGravity(Gravity.TOP);\n'
       '                    nameRow.setClipChildren(false);\n'
       '                    nameRow.setClipToPadding(false);\n'
       '                    LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(\n'
       '                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);\n'
       '                    nlp.topMargin = Math.max(dp(3), Math.round(dp(5) * scale));\n'
       '                    nlp.leftMargin = -(tileGap / 2);\n'
       '                    nlp.rightMargin = -(tileGap / 2);\n'
       '                    dockBox.addView(nameRow, nlp);\n'
       '                }\n'
       '            }\n')

zmiana("SceneHome.java", "zmienne wiersza podpisów",
       '        LinearLayout row = null;\n'
       '        for (int i = 0; i < dockEntries.size(); i++) {\n',
       '        boolean namesAlways = Scenes.isDockNamesAlways(host);\n'
       '        LinearLayout nameRow = null;\n'
       '        LinearLayout row = null;\n'
       '        for (int i = 0; i < dockEntries.size(); i++) {\n')

zmiana("SceneHome.java", "podpis pod kafelkiem",
       '            row.addView(tile, tlp);\n'
       '            dockTiles.add(tile);\n',
       '            row.addView(tile, tlp);\n'
       '            dockTiles.add(tile);\n'
       '            if (namesAlways && nameRow != null) {\n'
       '                TextView nm = new TextView(host);\n'
       '                Ui.textSize(nm, Math.max(7.5f, 9.5f * scale));\n'
       '                nm.setTypeface(Ui.font(host, "medium"));\n'
       '                nm.setTextColor(0xD9F5F5F7);\n'
       '                nm.setShadowLayer(dp(3), 0, dp(1), 0x99000000);\n'
       '                nm.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);\n'
       '                nm.setMaxLines(2);\n'
       '                nm.setEllipsize(TextUtils.TruncateAt.END);\n'
       '                nm.setIncludeFontPadding(false);\n'
       '                nm.setPadding(dp(1), 0, dp(1), 0);\n'
       '                nm.setText(dockNameFit(dockDisplayName(dockEntries.get(i)), nm, tileSize + tileGap));\n'
       '                nameRow.addView(nm, new LinearLayout.LayoutParams(\n'
       '                        tileSize + tileGap, ViewGroup.LayoutParams.WRAP_CONTENT));\n'
       '                dockNames.add(nm);\n'
       '            }\n')

zmiana("SceneHome.java", "jaśniejszy podpis zaznaczonej aplikacji",
       '            v.setForeground(has ? focusRing : null);\n'
       '            if (has) {\n'
       '                focusedDockIdx = index;\n',
       '            v.setForeground(has ? focusRing : null);\n'
       '            if (index >= 0 && index < dockNames.size()) {\n'
       '                dockNames.get(index).setTextColor(has ? 0xFFFFFFFF : 0xD9F5F5F7);\n'
       '            }\n'
       '            if (has) {\n'
       '                focusedDockIdx = index;\n')

# ---------------------------------------------------------------- pozostałe

zmiana("AppUpdater.java", "auto-aktualizacja domyślnie wyłączona (mod ma inny podpis)",
       '        return prefs(c).getBoolean(KEY_AUTO_UPDATE, true);\n',
       '        return prefs(c).getBoolean(KEY_AUTO_UPDATE, false);\n')

zmiana("MainActivity.java", "nazwy tapet przez tłumaczenia",
       '        return I18n.isTr(c) ? WALL_NAMES_TR[i] : WALL_NAMES[i];\n',
       '        return I18n.isTr(c) ? WALL_NAMES_TR[i] : I18n.t(c, WALL_NAMES[i]);\n')

# komunikaty wpisane w kodzie na sztywno, z pominięciem tłumaczeń
zmiana("MainActivity.java", "komunikat: usunięto",
       '        toast("Removed " + it.title);\n',
       '        toast(I18n.t(this, "Removed ") + it.title);\n')

zmiana("MainActivity.java", "komunikat: odświeżanie",
       '        toast("Refreshing");\n',
       '        toast(I18n.t(this, "Refreshing"));\n')

zmiana("MainActivity.java", "komunikat: niedostępne",
       '            toast("Not available on this TV");\n',
       '            toast(I18n.t(this, "Not available on this TV"));\n')

zmiana("MainActivity.java", "komunikat: przesuwanie strzałkami",
       '        toast("Use arrows to move \\u00b7 OK when done");\n',
       '        toast(I18n.t(this, "Use arrows to move \\u00b7 OK when done"));\n')

# ---------------------------------------------------------------- poprawki po testach na boxie (2026-11-10)

zmiana("SceneHome.java", "podpis doku bez łamania słowa w środku",
       '    private void updateDockTooltip(View tile, String text) {\n',
       '    /** Mod PL: podpis pod ikoną bez łamania słowa w środku (np. "Kick: Streamowanie na żywo" -> "Kick"). */\n'
       '    private static String dockNameFit(String label, TextView tv, int width) {\n'
       '        if (label == null) return "";\n'
       '        String s = label.trim();\n'
       '        float avail = width - tv.getPaddingLeft() - tv.getPaddingRight() - 2;\n'
       '        android.text.TextPaint p = tv.getPaint();\n'
       '        if (dockWordsFit(s, p, avail)) return dockShrink(s, tv, avail);\n'
       '        // "PremiumSmart TV": łamanie dozwolone przed wielką literą w środku słowa (niewidoczny znak)\n'
       '        StringBuilder camel = new StringBuilder(s.length() + 4);\n'
       '        for (int i = 0; i < s.length(); i++) {\n'
       '            char ch = s.charAt(i);\n'
       '            if (i > 0 && Character.isUpperCase(ch) && Character.isLowerCase(s.charAt(i - 1))) camel.append(\'\\u200B\');\n'
       '            camel.append(ch);\n'
       '        }\n'
       '        String c = camel.toString();\n'
       '        if (c.length() != s.length() && dockWordsFit(c.replace(\'\\u200B\', \' \'), p, avail)) return dockShrink(c, tv, avail);\n'
       '        int colon = s.indexOf(\':\');\n'
       '        if (colon > 0) {\n'
       '            String brand = s.substring(0, colon).trim();\n'
       '            if (!brand.isEmpty() && dockWordsFit(brand, p, avail)) return brand;\n'
       '        }\n'
       '        // jedno długie słowo (np. "PRZEGLĄDARKA"): najpierw mniejsza czcionka, najwyżej o ok. 25%\n'
       '        float poczatek = tv.getTextSize();\n'
       '        for (int krok = 0; krok < 4 && !dockWordsFit(s, tv.getPaint(), avail); krok++) {\n'
       '            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, tv.getTextSize() * 0.93f);\n'
       '        }\n'
       '        if (dockWordsFit(s, tv.getPaint(), avail)) return dockShrink(s, tv, avail);\n'
       '        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, poczatek);\n'
       '        tv.setSingleLine(true);\n'
       '        return s;\n'
       '    }\n'
       '\n'
       '    private static boolean dockWordsFit(String s, android.text.TextPaint p, float avail) {\n'
       '        for (String w : s.split(" ")) {\n'
       '            if (!w.isEmpty() && p.measureText(w) > avail) return false;\n'
       '        }\n'
       '        return true;\n'
       '    }\n'
       '\n'
       '    /** Mod PL: gdy podpis nie mieści się w 2 liniach, zmniejsza czcionkę najwyżej o ok. 19%. */\n'
       '    private static String dockShrink(String s, TextView tv, float avail) {\n'
       '        int w = Math.max(1, (int) avail);\n'
       '        for (int krok = 0; krok < 4 && dockLines(s, tv, w) > 2; krok++) {\n'
       '            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, tv.getTextSize() * 0.95f);\n'
       '        }\n'
       '        return s;\n'
       '    }\n'
       '\n'
       '    private static int dockLines(String s, TextView tv, int w) {\n'
       '        return android.text.StaticLayout.Builder.obtain(s, 0, s.length(), tv.getPaint(), w)\n'
       '                .setIncludePad(false).build().getLineCount();\n'
       '    }\n'
       '\n'
       '    /**\n'
       '     * Mod PL: dwie aplikacje o tej samej nazwie (np. wersja zwykła i debug) dostają końcówkę pakietu,\n'
       '     * tak jak w Projectivy: eu.premiumsmart.tv.debug -> "PremiumSmart TV debug".\n'
       '     */\n'
       '    private String dockDisplayName(MainActivity.AppEntry e) {\n'
       '        String l = e.label == null ? "" : e.label;\n'
       '        if (e.pkg == null || l.isEmpty()) return l;\n'
       '        for (MainActivity.AppEntry o : dockEntries) {\n'
       '            if (o == e || o.pkg == null || !l.equals(o.label)) continue;\n'
       '            if (e.pkg.startsWith(o.pkg + ".")) return l + " " + e.pkg.substring(o.pkg.length() + 1);\n'
       '        }\n'
       '        return l;\n'
       '    }\n'
       '\n'
       '    private void updateDockTooltip(View tile, String text) {\n')

zmiana("HomeDashboard.java", "Najczęściej oglądane: Film / Serial",
       '"series".equalsIgnoreCase(it.type) ? "TV Series" : "Movie";\n',
       '"series".equalsIgnoreCase(it.type) ? I18n.t(host, "TV Series") : I18n.t(host, "Movie");\n')

zmiana("HomeDashboard.java", "widżety wyśrodkowane (przy 2-3 widżetach nie przy lewej krawędzi)",
       '        LinearLayout row = new LinearLayout(host);\n'
       '        row.setOrientation(LinearLayout.HORIZONTAL);\n'
       '        row.setGravity(Gravity.CENTER_VERTICAL);\n',
       '        LinearLayout row = new LinearLayout(host);\n'
       '        row.setOrientation(LinearLayout.HORIZONTAL);\n'
       '        row.setGravity(Gravity.CENTER);\n')

zmiana("MainActivity.java", "polecane: Film / Serial",
       '        meta.add(it.type.equals("tv") ? "Series" : "Movie");\n',
       '        meta.add(I18n.t(this, it.type.equals("tv") ? "Series" : "Movie"));\n')

zmiana("CustomMedia.java", "pamięć wewnętrzna w wyborze folderu",
       'roots.put("💾  Internal Storage (" + ext.getAbsolutePath() + ")", ext);\n',
       'roots.put("💾  " + I18n.cached("Internal Storage") + " (" + ext.getAbsolutePath() + ")", ext);\n')

zmiana("SettingsActivity.java", "krótszy tytuł ustawień po polsku",
       '        sideTitleView.setText(tr("Home settings", "Ana ekran ayarları"));\n',
       '        sideTitleView.setText(I18n.isPl(this) ? "Ustawienia" : tr("Home settings", "Ana ekran ayarları"));\n',
       ile=2)

zmiana("Weather.java", "dni tygodnia w prognozie wielką literą",
       '            x.label = d.days.isEmpty() ? I18n.s(c, "Today", "Bugün") : dayFmt.format(ymd.parse(x.date));\n',
       '            x.label = d.days.isEmpty() ? I18n.s(c, "Today", "Bugün") : dayFmt.format(ymd.parse(x.date));\n'
       '            if (I18n.isPl(c) && !d.days.isEmpty() && !x.label.isEmpty()) {\n'
       '                x.label = x.label.substring(0, 1).toUpperCase(I18n.locale(c)) + x.label.substring(1);\n'
       '            }\n')

# ---------------------------------------------------------------- nowe okna (wariant C z kanwy, 2026-11-11)
# Kod okien: pl/java/ModPlOkna.java (kopiowany do src przy każdym nałożeniu).

zmiana("Suggestions.java", "pole z nazwą serwisu",
       '        String link = "";\n'
       '        /** Continue Watching entries',
       '        String link = "";\n'
       '        /** Mod PL: nazwa serwisu z JustWatch (np. Netflix, Amazon Prime Video). */\n'
       '        String prov = "";\n'
       '        /** Mod PL: identyfikator zwiastuna na YouTube z JustWatch (gdy go podaje). */\n'
       '        String trailer = "";\n'
       '        /** Continue Watching entries')

zmiana("Suggestions.java", "zapis nazwy serwisu",
       '.put("link", link).put("pub", published)',
       '.put("link", link).put("prov", prov == null ? "" : prov).put("trailer", trailer == null ? "" : trailer).put("pub", published)')

zmiana("Suggestions.java", "odczyt nazwy serwisu",
       '            i.link = o.optString("link");\n',
       '            i.link = o.optString("link");\n'
       '            i.prov = o.optString("prov", "");\n'
       '            i.trailer = o.optString("trailer", "");\n')

zmiana("Suggestions.java", "JustWatch: nazwa serwisu w zapytaniu",
       'standardWebURL package { technicalName }',
       'standardWebURL package { technicalName clearName }')

zmiana("Suggestions.java", "Najczęściej oglądane: zapamiętanie serwisu",
       '                                it.link = offers.getJSONObject(0).optString("standardWebURL", "");\n',
       '                                it.link = offers.getJSONObject(0).optString("standardWebURL", "");\n'
       '                                JSONObject pk = offers.getJSONObject(0).optJSONObject("package");\n'
       '                                if (pk != null) it.prov = pk.optString("clearName", "");\n')

zmiana("HomeDashboard.java", "nowa pamięć podręczna (z nazwą serwisu)",
       'return I18n.getLang(c) + ":" + TextUtils.join(",", getEffectiveReleasesCountries(c));',
       'return "s3:" + I18n.getLang(c) + ":" + TextUtils.join(",", getEffectiveReleasesCountries(c));')

zmiana("Suggestions.java", "JustWatch: zwiastuny w zapytaniu",
       'scoring { imdbScore } }"',
       'scoring { imdbScore } clips { externalId provider } }"')

zmiana("Suggestions.java", "Najczęściej oglądane: zapamiętanie zwiastuna",
       '                            it.genres = jwGenres(ct.optJSONArray("genres"));\n'
       '                            JSONArray offers = n.optJSONArray("offers");\n',
       '                            it.genres = jwGenres(ct.optJSONArray("genres"));\n'
       '                            JSONArray klipy = ct.optJSONArray("clips");\n'
       '                            for (int q = 0; klipy != null && q < klipy.length(); q++) {\n'
       '                                JSONObject kl = klipy.optJSONObject(q);\n'
       '                                if (kl != null && "YOUTUBE".equalsIgnoreCase(kl.optString("provider"))) {\n'
       '                                    it.trailer = kl.optString("externalId", "");\n'
       '                                    break;\n'
       '                                }\n'
       '                            }\n'
       '                            JSONArray offers = n.optJSONArray("offers");\n')

zmiana("HomeDashboard.java", "Ekstraklasa: pobieranie (osobno i w Najlepszych ligach)",
       '                    if ("all".equals(league)) {\n',
       '                    List<MatchItem> zLiveScore = ModPlMecze.pobierz(c, league);\n'
       '                    if (zLiveScore != null) {\n'
       '                        all.addAll(zLiveScore);\n'
       '                    } else if (ModPlEkstraklasa.KOD.equals(league)) {\n'
       '                        all.addAll(ModPlEkstraklasa.pobierz(c));\n'
       '                    } else if ("all".equals(league)) {\n'
       '                        all.addAll(ModPlEkstraklasa.pobierz(c));\n')

zmiana("HomeDashboard.java", "Ekstraklasa: pamięć podręczna przy wybranej Ekstraklasie",
       '        if (!sMemMatches.isEmpty()) return new ArrayList<MatchItem>(sMemMatches);\n',
       '        if (!sMemMatches.isEmpty()) return new ArrayList<MatchItem>(sMemMatches);\n'
       '        List<MatchItem> migawka = ModPlMecze.migawka(c);\n'
       '        if (migawka != null) {            // LiveScore działa: jego lista jest prawdą, także pusta\n'
       '            if (!migawka.isEmpty()) sMemMatches = migawka;\n'
       '            return new ArrayList<MatchItem>(migawka);\n'
       '        }\n'
       '        if (ModPlEkstraklasa.KOD.equals(getSelectedLeague(c))) return ModPlEkstraklasa.zPamieci(c);\n')

zmiana("HomeDashboard.java", "Ekstraklasa: pamięć podręczna w Najlepszych ligach",
       '            List<MatchItem> parsed = parseEspnEvents(c, raw, "");\n',
       '            List<MatchItem> parsed = parseEspnEvents(c, raw, "");\n'
       '            if ("all".equals(getSelectedLeague(c))) {\n'
       '                parsed.addAll(ModPlEkstraklasa.zPamieci(c));\n'
       '                sortMatches(c, parsed);\n'
       '            }\n')

zmiana("HomeDashboard.java", "ulubione bez polskich znaków (Lech Poznan = Lech Poznań)",
       '        String lower = teamName.toLowerCase(Locale.ROOT);\n',
       '        String lower = ModPlOkna.bezOgonkow(teamName);\n')

zmiana("HomeDashboard.java", "ulubione bez polskich znaków (porównanie)",
       'lower.contains(f.toLowerCase(Locale.ROOT))',
       'lower.contains(ModPlOkna.bezOgonkow(f))')

zmiana("HomeDashboard.java", "herby drużyn: pola",
       '        public String leagueName = "";\n',
       '        public String leagueName = "";\n'
       '        public String homeLogo = "";\n'
       '        public String awayLogo = "";\n'
       '        /** Mod PL: identyfikator meczu w LiveScore (pusty dla starych źródeł). */\n'
       '        public String id = "";\n'
       '        /** Mod PL: główna liga albo polskie rozgrywki - ulubione dopasowujemy też po fragmencie nazwy. */\n'
       '        public boolean glowna = true;\n')

zmiana("HomeDashboard.java", "herby drużyn: odczyt z ESPN",
       '                    String score = teamEntry.optString("score", "0");\n',
       '                    String score = teamEntry.optString("score", "0");\n'
       '                    String logo = teamObj != null ? teamObj.optString("logo", "") : "";\n')

zmiana("HomeDashboard.java", "herb gospodarzy",
       '                        item.homeScore = score;\n',
       '                        item.homeScore = score;\n'
       '                        item.homeLogo = logo;\n')

zmiana("HomeDashboard.java", "herb gości",
       '                        item.awayScore = score;\n',
       '                        item.awayScore = score;\n'
       '                        item.awayLogo = logo;\n')

zmiana("HomeDashboard.java", "okno Najczęściej oglądane: wariant C",
       'private static void showReleasesDialog(final MainActivity host, final List<Suggestions.Item> items, final Runnable onCycle) {\n',
       'private static void showReleasesDialog(final MainActivity host, final List<Suggestions.Item> items, final Runnable onCycle) {\n'
       '        if (ModPlOkna.pokazNajczesciej(host, items)) return;\n')

zmiana("HomeDashboard.java", "okno meczów: wariant C",
       'private static void showFootballDialog(final MainActivity host, final Runnable onUpdated) {\n',
       'private static void showFootballDialog(final MainActivity host, final Runnable onUpdated) {\n'
       '        if (ModPlOkna.pokazMecze(host, onUpdated)) return;\n')

zmiana("HomeDashboard.java", "ulubione drużyny dostępne dla nowego okna",
       '    private static void showFavoriteTeamsDialog(final MainActivity host',
       '    static void showFavoriteTeamsDialog(final MainActivity host')

# ---------------------------------------------------------------- LiveScore i mecze na żywo (2026-11-11)
# Źródło: pl/java/ModPlMecze.java. Stare źródła (ESPN, TheSportsDB) zostają jako zapas.

zmiana("HomeDashboard.java", "jedna kolejność meczów dla wszystkich źródeł",
       '    private static void sortMatches(Context c, List<MatchItem> list) {\n',
       '    private static void sortMatches(Context c, List<MatchItem> list) {\n'
       '        if (list != null) {\n'
       '            ModPlMecze.sortuj(c, list);\n'
       '            return;\n'
       '        }\n')

zmiana("HomeDashboard.java", "kafelek: licznik meczów na żywo po polsku",
       'badgeTv.setText(liveCount > 0 ? ("● " + liveCount + " LIVE") : list.get(0).leagueName);',
       'badgeTv.setText(liveCount > 0 ? ("● " + I18n.t(host, "Live") + ": " + liveCount) : list.get(0).leagueName);')

zmiana("HomeDashboard.java", "kafelek: wynik z minutą meczu",
       'right.setText(m.homeScore + ":" + m.awayScore + (m.isLive ? " \'" : ""));',
       'right.setText(ModPlOkna.wynikKafelka(host, m));')

zmiana("HomeDashboard.java", "kafelek: termin meczu po polsku",
       'right.setText(m.statusText != null && !m.statusText.isEmpty() ? m.statusText : "VS");',
       'right.setText(ModPlOkna.terminKafelka(host, m));')

zmiana("HomeDashboard.java", "kafelek: odświeżanie na żywo",
       '        card.setOnClickListener(new View.OnClickListener() {\n'
       '            @Override\n'
       '            public void onClick(View v) {\n'
       '                showFootballDialog(host, bindMatches);\n',
       '        ModPlOkna.pilnujMeczow(card, host, bindMatches);\n'
       '        card.setOnClickListener(new View.OnClickListener() {\n'
       '            @Override\n'
       '            public void onClick(View v) {\n'
       '                showFootballDialog(host, bindMatches);\n')

zmiana("HomeDashboard.java", "pusta lista z LiveScore zastępuje starą",
       '                if (!all.isEmpty()) {\n'
       '                    sMemMatches = all;\n',
       '                if (!all.isEmpty() || ModPlMecze.dziala) {\n'
       '                    sMemMatches = all;\n')

zmiana("HomeDashboard.java", "kafelek: brak meczów zamiast wiecznego ładowania",
       '                    empty.setText(I18n.t(host, "football_loading"));\n',
       '                    empty.setText(I18n.t(host, ModPlMecze.dziala ? "football_no_matches" : "football_loading"));\n')

# ---------------------------------------------------------------- "Oglądaj dalej" bez komputera (2026-11-11)
# Kod: pl/java/ModPlAdb.java. Przy włączonym debugowaniu USB launcher sam nadaje sobie dostęp.

zmiana("Perms.java", "Oglądaj dalej: najpierw samodzielnie przez debugowanie USB",
       '        if (Build.VERSION.SDK_INT >= 30) {\n'
       '            Intent detail = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)\n',
       '        ModPlAdb.wlaczOgladajDalej(a, new Runnable() {\n'
       '            @Override\n'
       '            public void run() {\n'
       '                requestListenerSystem(a);\n'
       '            }\n'
       '        });\n'
       '    }\n'
       '\n'
       '    /** Mod PL: dawna ścieżka - ekrany systemowe, a gdy ich nie ma, instrukcja krok po kroku. */\n'
       '    static void requestListenerSystem(final Activity a) {\n'
       '        if (Build.VERSION.SDK_INT >= 30) {\n'
       '            Intent detail = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)\n')

zmiana("Perms.java", "Oglądaj dalej: parametr final dla wywołania zwrotnego",
       '    static void requestListener(Activity a) {\n',
       '    static void requestListener(final Activity a) {\n')

zmiana("Perms.java", "Oglądaj dalej: instrukcja zamiast polecenia dla komputera",
       '        lastResort(a, I18n.s(a, "Continue Watching & Do Not Disturb", "\\u0130zlemeye Devam Et ve Rahats\\u0131z Etmeyin"),\n'
       '                "adb shell cmd notification allow_listener com.techgenius.tvoslauncher/com.techgenius.tvoslauncher.MediaWatchService");\n',
       '        ModPlAdb.instrukcja(a);\n')

zmiana("SettingsActivity.java", "podpowiedź nazwy drużyny po polsku",
       '        in.setHint("e.g. Qarabağ, Galatasaray, Real Madrid");\n',
       '        in.setHint(I18n.t(this, "e.g. Arsenal, Legia, Poland"));\n')

# ---------------------------------------------------------------- nowe kafelki ekranu głównego (2026-11-11)
# Kod: pl/java/ModPlKafelki.java (mecze z wariantu A, filmy z C, pogoda z A, wersja przezroczysta).
# Stare ciała metod zostają jako zapas: ModPlKafelki.NOWE = false przywraca kafelki oryginału.

zmiana("HomeDashboard.java", "nowy kafelek meczów",
       '    private static View createFootballWidgetCard(final MainActivity host) {\n',
       '    private static View createFootballWidgetCard(final MainActivity host) {\n'
       '        if (ModPlKafelki.NOWE) return ModPlKafelki.mecze(host);\n')

zmiana("HomeDashboard.java", "nowy kafelek najczęściej oglądanych",
       '    private static View createReleasesWidgetCard(final MainActivity host) {\n',
       '    private static View createReleasesWidgetCard(final MainActivity host) {\n'
       '        if (ModPlKafelki.NOWE) return ModPlKafelki.filmy(host);\n')

zmiana("HomeDashboard.java", "nowy kafelek pogody",
       '    private static View createWeatherWidgetCard(final MainActivity host, final NavBridge nav) {\n',
       '    private static View createWeatherWidgetCard(final MainActivity host, final NavBridge nav) {\n'
       '        if (ModPlKafelki.NOWE) return ModPlKafelki.pogoda(host, nav);\n')

# ---------------------------------------------------------------- bez widżetu godzin modlitw (2026-11-11)
# Prośba użytkownika: "usuń ten widget godziny modlitw". Kafelek nigdy się nie buduje, a z ustawień
# znikają jego przełącznik i wybór metody obliczania.

zmiana("HomeDashboard.java", "widżet godzin modlitw usunięty",
       '        return prefs(c).getBoolean(KEY_W_PRAYER, false);\n',
       '        return false;\n')

zmiana("SettingsActivity.java", "ustawienia: bez przełącznika godzin modlitw",
       '        row("🕌  " + I18n.t(this, "dash_prayer_times"),\n'
       '                () -> HomeDashboard.isWidgetPrayerEnabled(this) ? tr("On", "Açık") : tr("Off", "Kapalı"),\n'
       '                () -> {\n'
       '                    HomeDashboard.setWidgetPrayerEnabled(this, !HomeDashboard.isWidgetPrayerEnabled(this));\n'
       '                    refreshAll();\n'
       '                });\n',
       '')

zmiana("SettingsActivity.java", "ustawienia: bez metody obliczania godzin modlitw",
       '        row(tr("Prayer calculation method", "Namaz vakti hesaplama yöntemi"),\n'
       '                () -> HomeDashboard.getPrayerMethodName(this),\n'
       '                this::promptPrayerMethodDialog);\n',
       '')

# ---------------------------------------------------------------- powiadomienia o meczach (2026-11-11)
# Kod: pl/java/ModPlGole.java. Manifest leży poza src/, dlatego ścieżka wychodzi w górę; zbuduj-pl.sh
# przywraca go z gita razem z src/ przed nałożeniem łatek.

zmiana("../../../../AndroidManifest.xml", "manifest: uprawnienia powiadomień o meczach",
       '    <uses-permission android:name="android.permission.DUMP" />\n',
       '    <uses-permission android:name="android.permission.DUMP" />\n'
       '    <!-- Mod PL: powiadomienia o meczach nad innymi aplikacjami -->\n'
       '    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />\n'
       '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />\n'
       '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />\n'
       '    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />\n'
       '    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />\n')

zmiana("../../../../AndroidManifest.xml", "manifest: usługa pilnująca meczu",
       '        <!-- Continue Watching: reads the media sessions apps publish while playing. -->\n',
       '        <!-- Mod PL: pilnuje meczu obserwowanej drużyny i pokazuje powiadomienia o golach -->\n'
       '        <service\n'
       '            android:name=".ModPlGole$Usluga"\n'
       '            android:exported="false"\n'
       '            android:foregroundServiceType="dataSync" />\n'
       '\n'
       '        <!-- Continue Watching: reads the media sessions apps publish while playing. -->\n')

zmiana("MainActivity.java", "powrót na ekran główny: sprawdź, czy trzeba pilnować meczu",
       '        resumed = true;\n'
       '        activeInstance = this;\n',
       '        resumed = true;\n'
       '        activeInstance = this;\n'
       '        ModPlGole.pilnuj(this);\n')

zmiana("SettingsActivity.java", "ustawienia: powiadomienia o meczach",
       '        row(tr("Add favorite football team…", "Favori futbol takımı ekle…"),\n'
       '                () -> "＋",\n'
       '                this::promptAddFavoriteTeamDialog);\n',
       '        row(tr("Add favorite football team…", "Favori futbol takımı ekle…"),\n'
       '                () -> "＋",\n'
       '                this::promptAddFavoriteTeamDialog);\n'
       '        row(I18n.t(this, "Match alerts over other apps"),\n'
       '                () -> ModPlGole.opisTrybu(this),\n'
       '                () -> {\n'
       '                    ModPlGole.nastepnyTryb(this);\n'
       '                    refreshAll();\n'
       '                });\n'
       '        row(I18n.t(this, "Alert position"),\n'
       '                () -> ModPlGole.opisRogu(this),\n'
       '                () -> {\n'
       '                    ModPlGole.nastepnyRog(this);\n'
       '                    refreshAll();\n'
       '                });\n'
       '        row(I18n.t(this, "Show a sample alert"),\n'
       '                () -> "›",\n'
       '                () -> ModPlGole.przyklad(this));\n')

# ---------------------------------------------------------------- wygląd klasyczny schowany (2026-11-11)
# Prośba użytkownika: „możesz ten wygląd klasyczny wyłączyć póki co żeby w instalce nie był widoczny do wyboru”.
# ModPlKafelki.KLASYCZNY = true przywraca wybór wyglądu w ustawieniach i w kreatorze.

zmiana("Scenes.java", "wygląd: klasyczny schowany, zawsze nocne sceny",
       '    public static String getLook(Context c) {\n'
       '        return prefs(c).getString("look_mode", LOOK_SCENES);\n',
       '    public static String getLook(Context c) {\n'
       '        if (!ModPlKafelki.KLASYCZNY) return LOOK_SCENES;\n'
       '        return prefs(c).getString("look_mode", LOOK_SCENES);\n')

zmiana("SettingsActivity.java", "ustawienia: bez wyboru wyglądu (początek)",
       '        section(tr("LOOK", "GÖRÜNÜM"));\n'
       '        choiceRow(\n'
       '                tr("Night scenes", "Gece sahneleri"),\n',
       '        if (ModPlKafelki.KLASYCZNY) {\n'
       '        section(tr("LOOK", "GÖRÜNÜM"));\n'
       '        choiceRow(\n'
       '                tr("Night scenes", "Gece sahneleri"),\n')

zmiana("SettingsActivity.java", "ustawienia: bez wyboru wyglądu (koniec)",
       '                    selectSection(LOOK_SCENES, true, 1);\n'
       '                });\n',
       '                    selectSection(LOOK_SCENES, true, 1);\n'
       '                });\n'
       '        }\n')

zmiana("SetupActivity.java", "kreator: bez wyboru wyglądu, od razu wybór scen",
       '        boolean scenesMode = Scenes.isScenesLook(this);\n'
       '        String curWall = Profiles.homePrefs(this).getString("wallpaper", "photo:tessellated-pavement.jpg");\n'
       '        if (lookSubStep == 0) {\n',
       '        if (!ModPlKafelki.KLASYCZNY) lookSubStep = 1;\n'
       '        boolean scenesMode = Scenes.isScenesLook(this);\n'
       '        String curWall = Profiles.homePrefs(this).getString("wallpaper", "photo:tessellated-pavement.jpg");\n'
       '        if (lookSubStep == 0) {\n')

zmiana("SetupActivity.java", "kreator: Wstecz z wyboru scen wraca do poprzedniego kroku",
       '        if (index == 2 && lookSubStep != 0) {\n',
       '        if (ModPlKafelki.KLASYCZNY && index == 2 && lookSubStep != 0) {\n')

# ---------------------------------------------------------------- wygaszacz ekranu (2026-11-11)
# Kod: pl/java/ModPlWygaszacz.java (usługa DreamService) i ModPlAdb.ustawWygaszacz (ustawienie jako wygaszacz telewizora).
# Wybór użytkownika z kanwy: wariant B przezroczysty, zegar chowa się za pierwszym planem, zdjęcia bez efektu są pomijane.

zmiana("../../../../AndroidManifest.xml", "manifest: wygaszacz ekranu",
       '        <!-- Continue Watching: reads the media sessions apps publish while playing. -->\n',
       '        <!-- Mod PL: wygaszacz ekranu (losowe zdjęcia, szklany zegar chowający się za pierwszym planem) -->\n'
       '        <service\n'
       '            android:name=".ModPlWygaszacz"\n'
       '            android:exported="true"\n'
       '            android:label="tvOS Launcher"\n'
       '            android:permission="android.permission.BIND_DREAM_SERVICE">\n'
       '            <intent-filter>\n'
       '                <action android:name="android.service.dreams.DreamService" />\n'
       '                <category android:name="android.intent.category.DEFAULT" />\n'
       '            </intent-filter>\n'
       '        </service>\n'
       '\n'
       '        <!-- Continue Watching: reads the media sessions apps publish while playing. -->\n')

zmiana("SettingsActivity.java", "ustawienia: wejście do ustawień wygaszacza",
       '        row(tr("Home screen widget settings", "Ana ekran widget ayarları"),\n'
       '                () -> (HomeDashboard.isEnabled(this) ? tr("On", "Açık") : tr("Off", "Kapalı")) + "  ›",\n'
       '                () -> openSubMenu("home_dashboard"));\n',
       '        row(tr("Home screen widget settings", "Ana ekran widget ayarları"),\n'
       '                () -> (HomeDashboard.isEnabled(this) ? tr("On", "Açık") : tr("Off", "Kapalı")) + "  ›",\n'
       '                () -> openSubMenu("home_dashboard"));\n'
       '\n'
       '        section(I18n.t(this, "SCREEN SAVER"));\n'
       '        row(I18n.t(this, "Screen saver settings"),\n'
       '                () -> (ModPlWygaszacz.aktywny(this) ? tr("On", "Açık") : tr("Off", "Kapalı")) + "  ›",\n'
       '                () -> openSubMenu("mod_pl_wygaszacz"));\n')

zmiana("SettingsActivity.java", "ustawienia: podmenu wygaszacza w spisie podmenu",
       '        } else if ("home_dashboard".equals(subId)) {\n'
       '            subMenuHomeDashboard();\n',
       '        } else if ("home_dashboard".equals(subId)) {\n'
       '            subMenuHomeDashboard();\n'
       '        } else if ("mod_pl_wygaszacz".equals(subId)) {\n'
       '            subMenuModPlWygaszacz();\n')

zmiana("SettingsActivity.java", "ustawienia: podmenu wygaszacza",
       '    private void renderSubMenu(String subId) {\n',
       '    private void subMenuModPlWygaszacz() {\n'
       '        mainHeadView.setText(I18n.t(this, "Screen saver settings"));\n'
       '        backSubMenuRow();\n'
       '\n'
       '        section(I18n.t(this, "SCREEN SAVER"));\n'
       '        row(I18n.t(this, "Use as the TV screen saver"),\n'
       '                () -> ModPlWygaszacz.aktywny(this) ? tr("On", "Açık") : tr("Off", "Kapalı"),\n'
       '                () -> ModPlAdb.ustawWygaszacz(this));\n'
       '        row(I18n.t(this, "Preview the screen saver now"),\n'
       '                () -> "›",\n'
       '                () -> ModPlWygaszacz.podglad(this));\n'
       '        row(I18n.t(this, "New photo every"),\n'
       '                () -> ModPlWygaszacz.opisCoIle(this),\n'
       '                () -> {\n'
       '                    ModPlWygaszacz.nastepneCoIle(this);\n'
       '                    refreshAll();\n'
       '                });\n'
       '        row(I18n.t(this, "Temperature next to the date"),\n'
       '                () -> ModPlWygaszacz.temperatura(this) ? tr("On", "Açık") : tr("Off", "Kapalı"),\n'
       '                () -> {\n'
       '                    ModPlWygaszacz.przelaczTemperature(this);\n'
       '                    refreshAll();\n'
       '                });\n'
       '        row(I18n.t(this, "Photo caption for a few seconds"),\n'
       '                () -> ModPlWygaszacz.podpis(this) ? tr("On", "Açık") : tr("Off", "Kapalı"),\n'
       '                () -> {\n'
       '                    ModPlWygaszacz.przelaczPodpis(this);\n'
       '                    refreshAll();\n'
       '                });\n'
       '        row(I18n.t(this, "Skip photos the clock cannot hide behind"),\n'
       '                () -> ModPlWygaszacz.tylkoChowane(this) ? tr("On", "Açık") : tr("Off", "Kapalı"),\n'
       '                () -> {\n'
       '                    ModPlWygaszacz.przelaczTylkoChowane(this);\n'
       '                    refreshAll();\n'
       '                });\n'
       '\n'
       '        section(I18n.t(this, "PHOTO THEMES"));\n'
       '        row(I18n.t(this, "Landscapes"),\n'
       '                () -> ModPlWygaszacz.temat(this, ModPlWygaszacz.KRAJOBRAZY) ? tr("On", "Açık") : tr("Off", "Kapalı"),\n'
       '                () -> {\n'
       '                    ModPlWygaszacz.przelaczTemat(this, ModPlWygaszacz.KRAJOBRAZY);\n'
       '                    refreshAll();\n'
       '                });\n'
       '        row(I18n.t(this, "Cities"),\n'
       '                () -> ModPlWygaszacz.temat(this, ModPlWygaszacz.MIASTA) ? tr("On", "Açık") : tr("Off", "Kapalı"),\n'
       '                () -> {\n'
       '                    ModPlWygaszacz.przelaczTemat(this, ModPlWygaszacz.MIASTA);\n'
       '                    refreshAll();\n'
       '                });\n'
       '        row(I18n.t(this, "Sports cars"),\n'
       '                () -> ModPlWygaszacz.temat(this, ModPlWygaszacz.SAMOCHODY) ? tr("On", "Açık") : tr("Off", "Kapalı"),\n'
       '                () -> {\n'
       '                    ModPlWygaszacz.przelaczTemat(this, ModPlWygaszacz.SAMOCHODY);\n'
       '                    refreshAll();\n'
       '                });\n'
       '        row(I18n.t(this, "Cyberpunk cities"),\n'
       '                () -> ModPlWygaszacz.temat(this, ModPlWygaszacz.CYBERPUNK) ? tr("On", "Açık") : tr("Off", "Kapalı"),\n'
       '                () -> {\n'
       '                    ModPlWygaszacz.przelaczTemat(this, ModPlWygaszacz.CYBERPUNK);\n'
       '                    refreshAll();\n'
       '                });\n'
       '        infoNote(I18n.t(this, "Random photos from the internet with a big glass clock that hides behind mountains, trees and buildings. Without internet it shows photos saved earlier and the launcher scenes."));\n'
       '    }\n'
       '\n'
       '    private void renderSubMenu(String subId) {\n')

# ---------------------------------------------------------------- wygaszacz od razu po instalacji, bezpieczniejsze odchudzanie (2026-11-11)
# Prośby użytkownika: „po instalacji launchera jest aktywny ten wygaszacz” oraz „żeby domyślnie w auto nie było że usuwa wszystko”.

zmiana("MainActivity.java", "powrót na ekran główny: włącz wygaszacz, gdy launcher ma już zgodę",
       '        ModPlGole.pilnuj(this);\n',
       '        ModPlGole.pilnuj(this);\n'
       '        ModPlWygaszacz.wlaczJesliMozna(this);\n')

zmiana("LocalAdb.java", "automatyczna konfiguracja ADB: plakietka meczowa i wygaszacz launchera",
       '            cmds.add("appops set " + pkg + " ACCESS_RESTRICTED_SETTINGS allow 2>/dev/null");\n',
       '            cmds.add("appops set " + pkg + " ACCESS_RESTRICTED_SETTINGS allow 2>/dev/null");\n'
       '            // Mod PL: plakietka z wynikiem meczu nad innymi aplikacjami i wygaszacz ekranu launchera\n'
       '            cmds.add("appops set " + pkg + " SYSTEM_ALERT_WINDOW allow 2>/dev/null");\n'
       '            if (!ModPlWygaszacz.aktywny(c) && !ModPlWygaszacz.autoZrobione(c)) {\n'
       '                ModPlWygaszacz.zapamietajPoprzedni(c);\n'
       '                cmds.add("settings put secure screensaver_components " + ModPlWygaszacz.skladnik(c) + " 2>/dev/null");\n'
       '                cmds.add("settings put secure screensaver_enabled 1 2>/dev/null");\n'
       '            }\n')

zmiana("CleanYourTv.java", "odchudzanie: lista pakietów startuje bez zaznaczeń (nic nie wyłącza się hurtem przez pomyłkę)",
       '                primaryItems.add(new DebloatItem(pkg, label, desc, false, true));\n',
       '                primaryItems.add(new DebloatItem(pkg, label, desc, false, false));\n')

# ---------------------------------------------------------------- ADB zostaje włączone, tapety z tematów wygaszacza (2026-11-11)
# Prośby użytkownika: „nie ma wyłączać adb” oraz „te tapety też mogą być tak jak z wygaszacza … cyberpunk samochody sportowe”.

zmiana('SetupActivity.java', 'kreator: tryb zalecany bez zamykania ADB (nazwa)',
       '            option(l, tr("Mode: Secure Auto Setup & Close ADB (Recommended)", "Mod: Güvenli Otomatik Kurulum ve ADB\'yi Kapat (Önerilen)"),\n',
       '            option(l, tr("Mode: Secure Auto Setup (Recommended)", "Mod: Güvenli Otomatik Kurulum (Önerilen)"),\n')

zmiana('SetupActivity.java', 'kreator: tryb zalecany bez zamykania ADB (opis)',
       '                    tr("Grants WRITE_SECURE_SETTINGS once to self-repair Accessibility, then closes port 5555", "Erişilebilirliği korumak için bir kez WRITE_SECURE_SETTINGS verir, ardından port 5555\'i kapatır"),\n',
       '                    tr("Grants WRITE_SECURE_SETTINGS once to self-repair Accessibility; USB debugging stays on", "Erişilebilirliği korumak için bir kez WRITE_SECURE_SETTINGS verir; USB hata ayıklama açık kalır"),\n')

zmiana('SetupActivity.java', 'kreator: podpowiedź bez zamykania ADB',
       '                    + (secureClose ? " (ADB port 5555 will be closed automatically right after setup)." : " (ADB will remain enabled for CleanYourTV)."),\n',
       '                    + (secureClose ? " (USB debugging stays on)." : " (ADB will remain enabled for CleanYourTV)."),\n')

zmiana('SetupActivity.java', 'kreator: podpowiedź bez zamykania ADB (tr)',
       '                    + (secureClose ? " (Kurulumdan hemen sonra ADB port 5555 otomatik olarak kapatılacaktır)." : " (CleanYourTV için ADB açık kalacaktır).")));\n',
       '                    + (secureClose ? " (USB hata ayıklama açık kalır)." : " (CleanYourTV için ADB açık kalacaktır).")));\n')

zmiana('SetupActivity.java', 'kreator: przycisk nadania zgód bez zamykania ADB',
       '                            ? tr("Grant Permissions & Close ADB Now", "İzinleri Ver ve ADB\'yi Şimdi Kapat")\n',
       '                            ? tr("Grant Permissions Now", "İzinleri Şimdi Ver")\n')

zmiana('SetupActivity.java', 'kreator: opis przycisku bez zamykania ADB',
       '                            ? tr("Grants WRITE_SECURE_SETTINGS & closes port 5555 when done", "WRITE_SECURE_SETTINGS verir ve bittiğinde port 5555\'i kapatır")\n',
       '                            ? tr("Grants WRITE_SECURE_SETTINGS; USB debugging stays on", "WRITE_SECURE_SETTINGS verir; USB hata ayıklama açık kalır")\n')

zmiana('SetupActivity.java', 'kreator: okno „ADB zamknięte” tylko, gdy naprawdę je zamknięto',
       '            if (success) {\n                if (closeAfter) {\n',
       '            if (success) {\n                if (closeAfter && "CLOSED_ADB".equals(msg)) {\n')

zmiana('LocalAdb.java', 'automatyczna konfiguracja: nie wyłączaj debugowania USB',
       '            if (closeAdbAfter) {\n                cmds.add("settings put global adb_enabled 0 2>/dev/null");\n',
       '            if (closeAdbAfter && ModPlAdb.ZAMYKAJ_ADB) {\n                cmds.add("settings put global adb_enabled 0 2>/dev/null");\n')

zmiana('LocalAdb.java', 'automatyczna konfiguracja: wynik bez „ADB zamknięte”',
       'cb.onResult(true, closeAdbAfter ? "CLOSED_ADB" : null);',
       'cb.onResult(true, closeAdbAfter && ModPlAdb.ZAMYKAJ_ADB ? "CLOSED_ADB" : null);')

zmiana('LocalAdb.java', 'nazwa trybu bezpiecznego bez zamykania ADB',
       '                "Secure Mode — WRITE_SECURE_SETTINGS & Close ADB (Recommended)",\n',
       '                "Secure Mode — WRITE_SECURE_SETTINGS, ADB stays on (Recommended)",\n')

zmiana('LocalAdb.java', 'nazwa trybu bezpiecznego bez zamykania ADB (tr)',
       '                "Güvenli Mod — WRITE_SECURE_SETTINGS ve ADB Kapat (Önerilen)");\n',
       '                "Güvenli Mod — WRITE_SECURE_SETTINGS, ADB açık kalır (Önerilen)");\n')

zmiana('SettingsActivity.java', 'ustawienia: tryb bezpieczny bez zamykania ADB',
       '                    + tr("Secure Mode — Grant WRITE_SECURE_SETTINGS & Close ADB (Recommended)",\n',
       '                    + tr("Secure Mode — Grant WRITE_SECURE_SETTINGS, ADB stays on (Recommended)",\n')

zmiana('SettingsActivity.java', 'ustawienia: tryb bezpieczny bez zamykania ADB (tr)',
       '                         "Güvenli Mod — WRITE_SECURE_SETTINGS Ver ve ADB\'yi Kapat (Önerilen)"),\n',
       '                         "Güvenli Mod — WRITE_SECURE_SETTINGS Ver, ADB açık kalır (Önerilen)"),\n')

zmiana('SettingsActivity.java', 'ustawienia: komunikat trybu bezpiecznego',
       '                        showStatusMsg(tr("Secure Mode selected — ADB will grant WRITE_SECURE_SETTINGS and close port 5555 when paired.",\n',
       '                        showStatusMsg(tr("Secure Mode selected — ADB grants WRITE_SECURE_SETTINGS once; USB debugging stays on.",\n')

zmiana('SettingsActivity.java', 'ustawienia: komunikat trybu bezpiecznego (tr)',
       '                                "Güvenli Mod seçildi — Eşleştirildiğinde WRITE_SECURE_SETTINGS verilir ve port 5555 kapatılır."));\n',
       '                                "Güvenli Mod seçildi — WRITE_SECURE_SETTINGS bir kez verilir; USB hata ayıklama açık kalır."));\n')

zmiana('SettingsActivity.java', 'tapety: nazwa trybu',
       '        choiceRow(tr("Reddit Wallpapers", "Reddit Duvar Kağıtları"),\n',
       '        choiceRow(tr("Wallpapers from the internet", "İnternetten Duvar Kağıtları"),\n')

zmiana('SettingsActivity.java', 'tapety: opis trybu',
       '                tr("Auto-changing 1080p/4K wallpapers from your selected subreddits",\n',
       '                tr("Auto-changing wallpapers: sports cars, cyberpunk cities, landscapes or cities",\n')

zmiana('SettingsActivity.java', 'tapety: opis trybu (tr)',
       '                   "Seçtiğiniz subreddit\'lerden otomatik değişen 1080p/4K duvar kağıtları"),\n',
       '                   "Otomatik değişen duvar kağıtları: spor arabalar, siberpunk şehirler, manzaralar veya şehirler"),\n')

zmiana('SettingsActivity.java', 'tapety: nagłówek opcji',
       '            section(tr("REDDIT WALLPAPER OPTIONS", "REDDIT DUVAR KAĞIDI SEÇENEKLERİ"));\n',
       '            section(tr("WALLPAPER OPTIONS", "DUVAR KAĞIDI SEÇENEKLERİ"));\n')

zmiana('SettingsActivity.java', 'tapety: komunikat pobierania',
       '                        showStatusMsg(tr("Fetching next Reddit wallpaper…", "Sonraki Reddit duvar kağıdı indiriliyor…"));\n',
       '                        showStatusMsg(tr("Fetching the next wallpaper…", "Sonraki duvar kağıdı indiriliyor…"));\n')

zmiana('SettingsActivity.java', 'tapety: komunikat gotowe',
       '                                showStatusMsg(tr("Reddit wallpaper updated.", "Reddit duvar kağıdı güncellendi.")));\n',
       '                                showStatusMsg(tr("Wallpaper updated.", "Duvar kağıdı güncellendi.")));\n')

zmiana('CustomMedia.java', 'tapety: nazwa trybu w podsumowaniu',
       '            return I18n.s(c, "Reddit Wallpapers", "Reddit Duvar Kağıtları");\n',
       '            return I18n.s(c, "Wallpapers from the internet", "İnternetten Duvar Kağıtları");\n')

zmiana('Scenes.java', 'tapety: nazwa sceny',
       '            REDDIT_WALLPAPER_ID, "Reddit Wallpapers", "Reddit Duvar Kağıtları",\n',
       '            REDDIT_WALLPAPER_ID, "Wallpapers from the internet", "İnternetten Duvar Kağıtları",\n')

zmiana('ControlPanelOverlay.java', 'tapety: następna tapeta (ControlPanelOverlay.java)',
       '                    I18n.s(svc, "Skip to the next Reddit wallpaper", "Sonraki Reddit duvar kağıdına geç"),\n',
       '                    I18n.s(svc, "Skip to the next wallpaper", "Sonraki duvar kağıdına geç"),\n')

zmiana('ControlPanelOverlay.java', 'tapety: ładowanie tapety (ControlPanelOverlay.java)',
       '                                I18n.s(svc, "Loading next Reddit wallpaper…", "Sonraki Reddit duvar kağıdı yükleniyor…"),\n',
       '                                I18n.s(svc, "Loading the next wallpaper…", "Sonraki duvar kağıdı yükleniyor…"),\n')

zmiana('SceneHome.java', 'tapety: następna tapeta (SceneHome.java)',
       '                    I18n.s(host, "Skip to the next Reddit wallpaper", "Sonraki Reddit duvar kağıdına geç"),\n',
       '                    I18n.s(host, "Skip to the next wallpaper", "Sonraki duvar kağıdına geç"),\n')

zmiana('SceneHome.java', 'tapety: ładowanie tapety (SceneHome.java)',
       '                        host.toast(I18n.s(host, "Loading next Reddit wallpaper…", "Sonraki Reddit duvar kağıdı yükleniyor…"));\n',
       '                        host.toast(I18n.s(host, "Loading the next wallpaper…", "Sonraki duvar kağıdı yükleniyor…"));\n')

zmiana('CustomMedia.java', 'tapety: adresy z tematów wygaszacza, Reddit tylko awaryjnie',
       '    private static List<String> fetchRedditImageUrls(Context c, String scope) {\n        List<String> urls = new ArrayList<>();\n',
       '    /** Mod PL: po zmianie tematów tapet następna tapeta ma pochodzić już z nowej listy. */\n    static void wyczyscTalieTapet(String scope) {\n        synchronized (redditUrlDeckMap) {\n            redditUrlDeckMap.remove(scope);\n            redditDeckIdxMap.remove(scope);\n        }\n    }\n\n    private static List<String> fetchRedditImageUrls(Context c, String scope) {\n        List<String> modPl = ModPlWygaszacz.adresyTapet(c, scope);   // Mod PL: tematy jak w wygaszaczu\n        if (!modPl.isEmpty()) return ModPlTapety.bezOdrzuconych(c, scope, modPl);\n        List<String> urls = new ArrayList<>();\n')

zmiana('SettingsActivity.java', 'tapety: tematy zdjęć zamiast listy subredditów',
       '            row("＋  " + tr("Add custom subreddit…", "Özel subreddit ekle…"),\n                    () -> "r/…",\n                    () -> promptAddCustomSubredditDialog(scope));\n\n            section(tr("SUBREDDITS (SELECT SINGLE OR MULTIPLE)", "SUBREDDIT\'LER (TEKLİ VEYA ÇOKLU SEÇİM)"));\n            java.util.Set<String> activeSubs = CustomMedia.getRedditSubreddits(this, scope);\n            List<String> allSubs = CustomMedia.getAllKnownRedditSubreddits(this, scope);\n            for (final String sub : allSubs) {\n                boolean on = false;\n                for (String a : activeSubs) {\n                    if (a.equalsIgnoreCase(sub)) {\n                        on = true;\n                        break;\n                    }\n                }\n                choiceRow("r/" + sub,\n                        on ? tr("Selected", "Seçili") : tr("Tap to include", "Dahil etmek için dokunun"),\n                        on,\n                        () -> {\n                            CustomMedia.toggleRedditSubreddit(this, scope, sub);\n                            CustomMedia.fetchNextRedditWallpaperAsync(this, scope, null);\n                            rebuildMainRows();\n                        });\n            }\n',
       '            section(I18n.t(this, "PHOTO THEMES"));\n            row(I18n.t(this, "Sports cars"),\n                    () -> ModPlWygaszacz.tematTapet(this, scope, ModPlWygaszacz.SAMOCHODY) ? tr("On", "Açık") : tr("Off", "Kapalı"),\n                    () -> {\n                        ModPlWygaszacz.przelaczTematTapet(this, scope, ModPlWygaszacz.SAMOCHODY);\n                        CustomMedia.wyczyscTalieTapet(scope);\n                        CustomMedia.fetchNextRedditWallpaperAsync(this, scope, null);\n                        refreshAll();\n                    });\n            row(I18n.t(this, "Cyberpunk cities"),\n                    () -> ModPlWygaszacz.tematTapet(this, scope, ModPlWygaszacz.CYBERPUNK) ? tr("On", "Açık") : tr("Off", "Kapalı"),\n                    () -> {\n                        ModPlWygaszacz.przelaczTematTapet(this, scope, ModPlWygaszacz.CYBERPUNK);\n                        CustomMedia.wyczyscTalieTapet(scope);\n                        CustomMedia.fetchNextRedditWallpaperAsync(this, scope, null);\n                        refreshAll();\n                    });\n            row(I18n.t(this, "Landscapes"),\n                    () -> ModPlWygaszacz.tematTapet(this, scope, ModPlWygaszacz.KRAJOBRAZY) ? tr("On", "Açık") : tr("Off", "Kapalı"),\n                    () -> {\n                        ModPlWygaszacz.przelaczTematTapet(this, scope, ModPlWygaszacz.KRAJOBRAZY);\n                        CustomMedia.wyczyscTalieTapet(scope);\n                        CustomMedia.fetchNextRedditWallpaperAsync(this, scope, null);\n                        refreshAll();\n                    });\n            row(I18n.t(this, "Cities"),\n                    () -> ModPlWygaszacz.tematTapet(this, scope, ModPlWygaszacz.MIASTA) ? tr("On", "Açık") : tr("Off", "Kapalı"),\n                    () -> {\n                        ModPlWygaszacz.przelaczTematTapet(this, scope, ModPlWygaszacz.MIASTA);\n                        CustomMedia.wyczyscTalieTapet(scope);\n                        CustomMedia.fetchNextRedditWallpaperAsync(this, scope, null);\n                        refreshAll();\n                    });\n')

# ---------------------------------------------------------------- zegar chowa się też na tapetach ekranu głównego (2026-11-12)
# Kod: pl/java/ModPlTapety.java (analiza wspólna z wygaszaczem). Prośby użytkownika: „ten zegar ma mieć ten efekt”,
# „kolor napisów nad zegarem … też musi się dostosować”, tapety z internetu domyślnie i pierwsze w kreatorze.

zmiana('CustomMedia.java', 'tapety z internetu: szukanie do skutku (nowe listy adresów), bo część tapet odpada',
       'while (deck != null && idx < deck.size() && attempts < 8) {',
       'long modStart = android.os.SystemClock.uptimeMillis();   // Mod PL: szukamy, aż coś będzie pasować pod zegar\n'
       '                while (downloaded == null && attempts < ModPlTapety.PROB\n'
       '                        && android.os.SystemClock.uptimeMillis() - modStart < ModPlTapety.CZAS_SZUKANIA) {\n'
       '                    if (deck == null || idx >= deck.size()) {\n'
       '                        // talia się skończyła, a nic nie pasowało: nowa lista adresów (odrzucone już nie wracają)\n'
       '                        deck = fetchRedditImageUrls(appCtx, scope);\n'
       '                        Collections.shuffle(deck);\n'
       '                        idx = 0;\n'
       '                        synchronized (redditUrlDeckMap) {\n'
       '                            redditUrlDeckMap.put(scope, deck);\n'
       '                            redditDeckIdxMap.put(scope, 0);\n'
       '                        }\n'
       '                        if (deck.isEmpty()) break;\n'
       '                    }')

zmiana('CustomMedia.java', 'tapety z internetu: tylko takie, za którymi zegar się schowa',
       '                    downloaded = downloadWallpaperUrl(imgUrl);\n',
       '                    downloaded = ModPlTapety.sprawdz(appCtx, scope, imgUrl, downloadWallpaperUrl(imgUrl));\n')

zmiana('CustomMedia.java', 'tapety z internetu: sprawdzona tapeta jest już wykadrowana',
       'Bitmap cropped = scaleCenterCropOrFit(downloaded, 1920, 1080, true);',
       'Bitmap cropped = ModPlTapety.gotowa(downloaded) ? downloaded : scaleCenterCropOrFit(downloaded, 1920, 1080, true);')

zmiana('Scenes.java', 'ekran główny: zegar chowa się za pierwszym planem własnej tapety',
       '            if (ls.glassShader != null && ls.glassBaked != null) {\n',
       '            if (!classicClockOnlyMode && !lockScreenMode && ModPlTapety.analizowana(sp.id)) {\n                // Mod PL: wysokość zegara dobrana do tapety (jak w wygaszaczu); data i powitanie idą razem z nim\n                float modSzer = measureClockWidth("00:00", clockFillPaint, u, isDuo || isRounded);\n                float modLewo = CLOCK_POS_LEFT.equals(activeClockPos) ? clockX\n                        : CLOCK_POS_RIGHT.equals(activeClockPos) ? clockX + textW - modSzer : clockX + (textW - modSzer) * 0.5f;\n                float modDy = ModPlTapety.gora(ls.bg, w, h, modLewo, modLewo + modSzer, visualCapH, clockVisualTopY) - clockVisualTopY;\n                baselineY += modDy;\n                dateBaselineY += modDy;\n                greetBaselineY += modDy;\n            }\n            if (ls.glassShader != null && ls.glassBaked != null) {\n')

zmiana('Scenes.java', 'ekran główny: pierwszy plan własnej tapety rysowany na zegarze',
       '            if (!classicClockOnlyMode && ls.fg != null) {\n                drawCenterCrop(canvas, ls.fg, w, h);\n            }\n',
       '            if (!classicClockOnlyMode && ls.fg != null) {\n                drawCenterCrop(canvas, ls.fg, w, h);\n            }\n            if (!classicClockOnlyMode && !lockScreenMode && ls.fg == null && ModPlTapety.analizowana(sp.id)) {\n                ModPlTapety.rysujPrzod(canvas, ls.bg, w, h);\n            }\n')

zmiana('Scenes.java', 'ekran główny: data i powitanie ciemne na jasnej tapecie',
       'boolean darkDt = !classicClockOnlyMode && sp.darkDate;',
       'boolean darkDt = !classicClockOnlyMode\n                    && (sp.darkDate || (!lockScreenMode && ModPlTapety.analizowana(sp.id) && ModPlTapety.ciemnaData(ls.bg)));')

zmiana('SetupActivity.java', 'kreator: tapety z internetu włączone od początku',
       '        steps.add(this::hello);\n',
       '        ModPlTapety.domyslnie(this);\n        steps.add(this::hello);\n')

zmiana('SetupActivity.java', 'kreator: tapety z internetu jako pierwsza pozycja wyboru scen',
       '            for (int i = 0; i < Scenes.ALL.length; i++) {\n',
       '            final boolean modTapety = ModPlTapety.wlaczone(this);\n            View modWiersz = option(l, I18n.t(this, "Wallpapers from the internet"), ModPlTapety.opis(this), modTapety, () -> {\n                ModPlTapety.ustaw(this, !modTapety);\n                show(index, 0);\n            });\n            modWiersz.requestFocus();\n            for (int i = 0; i < Scenes.ALL.length; i++) {\n')

zmiana('SetupActivity.java', 'kreator: zaznaczenie zaczyna od tapet z internetu',
       '                if (i == 0) r.requestFocus();\n',
       '')

zmiana('MainActivity.java', 'ekran główny: tapety z internetu domyślne w świeżej instalacji',
       '        ModPlWygaszacz.wlaczJesliMozna(this);\n',
       '        ModPlWygaszacz.wlaczJesliMozna(this);\n        ModPlTapety.domyslnie(this);\n')

# ---------------------------------------------------------------- ulubione tapety i kadrowanie tapety pod zegar (2026-11-12)
# Kod: pl/java/ModPlTapety.java. Prośby użytkownika: „dodasz opcje żeby np zapisało wybrane tapety i je częściej dawało”,
# „pod takie tapety też może przestawić np zegar”.

zmiana('CustomMedia.java', 'ulubione tapety: co którąś zmianę wraca zapisana tapeta',
       '                List<String> deck;\n                int idx;\n',
       '                final Bitmap modUlubiona = ModPlTapety.zUlubionych(appCtx, scope);   // Mod PL\n                List<String> deck;\n                int idx;\n')

zmiana('CustomMedia.java', 'ulubione tapety: przy ulubionej nie pobieramy listy adresów',
       '                if (deck == null || deck.isEmpty() || idx >= deck.size()) {\n',
       '                if (modUlubiona == null && (deck == null || deck.isEmpty() || idx >= deck.size())) {\n')

zmiana('CustomMedia.java', 'ulubione tapety: ulubiona zamiast pobierania',
       '                Bitmap downloaded = null;\n',
       '                Bitmap downloaded = modUlubiona;\n')

zmiana('CustomMedia.java', 'ulubione tapety: zapamiętanie, czy pokazana tapeta jest ulubioną',
       '                    prefs(appCtx).edit().putLong("reddit_wall_ts_" + scope, ts).apply();\n',
       '                    prefs(appCtx).edit().putLong("reddit_wall_ts_" + scope, ts).apply();\n                    ModPlTapety.pokazana(appCtx, scope);\n')

zmiana('SettingsActivity.java', 'ulubione tapety: sekcja w ustawieniach tapet z internetu',
       '            section(I18n.t(this, "PHOTO THEMES"));\n',
       '            section(I18n.t(this, "FAVOURITE WALLPAPERS"));\n            row(ModPlTapety.tytulUlubionej(this, scope),\n                    () -> I18n.t(this, "Saved favourites") + ": " + ModPlTapety.ileUlubionych(this),\n                    () -> {\n                        showStatusMsg(ModPlTapety.przelaczUlubiona(this, scope));\n                        rebuildMainRows();\n                    });\n            row(I18n.t(this, "How often favourites return"),\n                    () -> ModPlTapety.opisCoIle(this),\n                    () -> {\n                        ModPlTapety.nastepneCoIle(this);\n                        refreshAll();\n                    });\n            if (ModPlTapety.ileUlubionych(this) > 0) {\n                row(I18n.t(this, "Remove all favourites"),\n                        () -> String.valueOf(ModPlTapety.ileUlubionych(this)),\n                        () -> dialog(I18n.t(this, "Remove all favourites"))\n                                .setMessage(I18n.t(this, "Saved favourites") + ": " + ModPlTapety.ileUlubionych(this))\n                                .setPositiveButton(I18n.t(this, "Remove"), (d, w) -> {\n                                    ModPlTapety.usunWszystkie(this);\n                                    showStatusMsg(I18n.t(this, "All favourites removed"));\n                                    rebuildMainRows();\n                                })\n                                .setNegativeButton(tr("Cancel", "İptal"), null)\n                                .show());\n            }\n            section(I18n.t(this, "PHOTO THEMES"));\n')

zmiana('SceneHome.java', 'ulubione tapety: wiersz w menu ekranu głównego',
       '            card.addView(btnNextWall, nwlp);\n            rows.add(btnNextWall);\n',
       '            card.addView(btnNextWall, nwlp);\n            rows.add(btnNextWall);\n            final String modZakres = ModPlTapety.zakres(host);\n            View modUlubiona = makeGearOptionRow(\n                    "pin",\n                    ModPlTapety.tytulUlubionej(host, modZakres),\n                    ModPlTapety.opisUlubionej(host, modZakres),\n                    () -> {\n                        closeSheet();\n                        host.toast(ModPlTapety.przelaczUlubiona(host, modZakres));\n                    });\n            LinearLayout.LayoutParams modLp = new LinearLayout.LayoutParams(\n                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);\n            modLp.topMargin = dp(2);\n            card.addView(modUlubiona, modLp);\n            rows.add(modUlubiona);\n')

zmiana('ControlPanelOverlay.java', 'ulubione tapety: wiersz w panelu sterowania',
       '            card.addView(btnNextWall, nwlp);\n            rows.add(btnNextWall);\n',
       '            card.addView(btnNextWall, nwlp);\n            rows.add(btnNextWall);\n            final String modZakres = ModPlTapety.zakres(svc);\n            View modUlubiona = makeOverlayGearOptionRow(\n                    svc,\n                    u,\n                    "pin",\n                    ModPlTapety.tytulUlubionej(svc, modZakres),\n                    ModPlTapety.opisUlubionej(svc, modZakres),\n                    () -> {\n                        closeSheet.run();\n                        Toast.makeText(svc, ModPlTapety.przelaczUlubiona(svc, modZakres), Toast.LENGTH_SHORT).show();\n                    });\n            LinearLayout.LayoutParams modLp = new LinearLayout.LayoutParams(\n                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);\n            modLp.topMargin = dp(u, 2);\n            card.addView(modUlubiona, modLp);\n            rows.add(modUlubiona);\n')

zmiana('SettingsActivity.java', 'tapety z internetu: przełącznik kadrowania tapety pod zegar',
       '            section(I18n.t(this, "FAVOURITE WALLPAPERS"));\n',
       '            row(I18n.t(this, "Fit wallpaper to the clock"),\n                    () -> ModPlTapety.kadrowanie(this) ? tr("On", "Açık") : tr("Off", "Kapalı"),\n                    () -> {\n                        ModPlTapety.przelaczKadrowanie(this);\n                        refreshAll();\n                    });\n            section(I18n.t(this, "FAVOURITE WALLPAPERS"));\n')

zmiana('Scenes.java', 'własna tapeta, za którą chowa się zegar: szkło cyfr jak we wbudowanych scenach',
       '        private static void bakeLoadedGlassFromBg(LoadedScene ls, boolean darkClock) {\n',
       '        private static void bakeLoadedGlassFromBg(LoadedScene ls, boolean darkClock) {\n            if (!darkClock && ModPlTapety.chowa(ls.bg)) {\n                // Mod PL: zegar chowa się za tą tapetą, więc szkło i styl cyfr są jak we wbudowanych scenach\n                // (cień, obwódka i rozmyta tapeta w cyfrach zamiast przyciemnianego szkła doku)\n                Bitmap modKadr = ModPlTapety.kadr480(ls.bg);\n                // szkło jak w scenach autora (rozmycie, jasność 1,3, kolory 1,15): wybór użytkownika z kanwy 12.11.2026\n                Bitmap modMala = ModPlTapety.szkloAutora(modKadr);\n                if (!modKadr.isRecycled()) modKadr.recycle();\n                ls.glassBaked = bakeGlassTexture(modMala, false, false);\n                ls.glassShader = new BitmapShader(ls.glassBaked, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);\n                if (!modMala.isRecycled()) modMala.recycle();\n                ls.dockGlass = false;\n                return;\n            }\n')

zmiana('CustomMedia.java', 'tapety z internetu: zdjęcia z listy awaryjnej też muszą pasować pod zegar',
       '                        downloaded = downloadWallpaperUrl(fallbackUrls.get(i));\n',
       '                        downloaded = ModPlTapety.sprawdz(appCtx, scope, fallbackUrls.get(i), downloadWallpaperUrl(fallbackUrls.get(i)));\n')

zmiana('CustomMedia.java', 'tapety z internetu: nic nie pasowało pod zegar, zostaje obecna tapeta',
       '                    });\n                }\n            } catch (Throwable ignored) {\n            } finally {\n                synchronized (redditFetchingMap) {\n                    redditFetchingMap.put(scope, false);\n',
       '                    });\n                } else {\n                    // Mod PL: nic nie pasowało pod zegar: zostaje obecna tapeta, następna próba automatyczna po zwykłym czasie\n                    synchronized (redditBmpMap) {\n                        if (redditBmpMap.get(scope) != null) redditLastFetchMap.put(scope, System.currentTimeMillis());\n                    }\n                    final List<Runnable> modCzekali;\n                    synchronized (redditFetchingMap) {\n                        modCzekali = redditCallbacksMap.remove(scope);\n                    }\n                    if (modCzekali != null && !modCzekali.isEmpty()) ModPlTapety.nieZnaleziono(appCtx);\n                }\n            } catch (Throwable ignored) {\n            } finally {\n                synchronized (redditFetchingMap) {\n                    redditFetchingMap.put(scope, false);\n')

zmiana('Scenes.java', 'wbudowane tapety: czerwone Lamborghini i Audi RS7 nocą do wyboru jak pozostałe sceny',
       '                88f, 0f, false)\n    };',
       '                88f, 0f, false),\n        // Mod PL: czerwone Lamborghini (użytkownik 12.11.2026: „miało być w instalce do wyboru tak jak te, co już były”).\n        // Pierwszego planu nie ma w pliku: zegar ustawia i chowa za dachem auta analiza z ModPlTapety, jak przy tapetach z internetu.\n        new Spec("lambo", "Red Lamborghini", "Kırmızı Lamborghini",\n                "mod_pl/ulubiona-lamborghini.jpg", null, null,\n                false, false, false,\n                68f, 190f, 48f,\n                45f, 52f, false),\n'
       '        // Mod PL: Audi RS7 nocą, przycięte zdjęcie użytkownika, zrobione jak stałe sceny: wycięty dach auta jako pierwszy plan\n'
       '        // i postać w kapturze, stałe miejsce zegara (lewy brzeg „0” za ramieniem, dół cyfr za dachem).\n'
       '        // Pliki: kanwa-kafelki/narzedzia/scena_rs7.py (kadr 1,12).\n'
       '        new Spec("audi_rs7", "Audi RS7 at night", "Gece Audi RS7",\n'
       '                "mod_pl/ulubiona-audi-rs7.jpg", "mod_pl/scena-audi-rs7-glass.jpg", "mod_pl/scena-audi-rs7-fg.webp",\n'
       '                false, false, false,\n'
       '                89.5f, 190f, 0f,\n'
       '                66.5f, 0f, false)\n    };')

zmiana('Scenes.java', 'wbudowane tapety: auta moda domyślnie włączone (lista surowa)',
       '    private static List<String> getEnabledSceneIdsRaw(Context c) {\n        String raw = prefs(c).getString("scene_enabled", "night,golden,water,fog,dawn");\n',
       '    private static List<String> getEnabledSceneIdsRaw(Context c) {\n        String raw = prefs(c).getString("scene_enabled", "night,golden,water,fog,dawn,lambo,audi_rs7");\n')

zmiana('Scenes.java', 'wbudowane tapety: auta moda domyślnie włączone (lista scen)',
       '                return Collections.singletonList(FOLDER_SHUFFLE_ID);\n            }\n        }\n\n        String raw = prefs(c).getString("scene_enabled", "night,golden,water,fog,dawn");\n',
       '                return Collections.singletonList(FOLDER_SHUFFLE_ID);\n            }\n        }\n\n        String raw = prefs(c).getString("scene_enabled", "night,golden,water,fog,dawn,lambo,audi_rs7");\n')

zmiana('SetupActivity.java', 'kreator: auta moda na początku listy scen, zaraz pod tapetami z internetu',
       '            for (int i = 0; i < Scenes.ALL.length; i++) {\n                final Scenes.Spec spec = Scenes.ALL[i];\n',
       '            final Scenes.Spec[] modSceny = ModPlTapety.najpierwAuta(Scenes.ALL);   // Mod PL: auta moda na początku\n            for (int i = 0; i < modSceny.length; i++) {\n                final Scenes.Spec spec = modSceny[i];\n')

zmiana('Scenes.java', 'ustawienia: auta moda na początku kolejności scen (także przy zapisanej kolejności)',
       '        for (Spec s : ALL) {\n            if (!out.contains(s.id)) out.add(s.id);\n        }\n',
       '        int modPrzod = 0;   // Mod PL: auta moda, których nie było w zapisanej kolejności, idą na początek listy\n        for (Spec s : ALL) {\n            if (out.contains(s.id)) continue;\n            if (ModPlTapety.autoModa(s.id)) out.add(modPrzod++, s.id);\n            else out.add(s.id);\n        }\n')

zmiana('Scenes.java', 'kreator: przełączenie sceny przy tapetach z internetu nie kasuje pozostałych scen',
       '        List<String> enabled = new ArrayList<>(getEnabledSceneIds(c));\n        if (enabled.contains(id)) {\n            if (enabled.size() <= 1) return false;\n',
       '        // Mod PL: przełączamy zapisaną listę scen. getEnabledSceneIds przy włączonej własnej tapecie zwraca tylko ją\n        // („reddit_wall”), więc przełączenie sceny w kreatorze zapisywało listę od nowa i gubiło pozostałe sceny.\n        List<String> enabled = new ArrayList<>(getEnabledSceneIdsRaw(c));\n        if (enabled.contains(id)) {\n            if (enabled.size() <= 1) return false;\n')

zmiana('CustomMedia.java', 'tapety z internetu: dołączona ulubiona z pamięci podręcznej zachowuje ręczne ustawienie zegara',
       '                Bitmap disk = decodeBitmapFromFile(f, Math.max(1280, reqW), Math.max(720, reqH));\n',
       '                Bitmap disk = decodeBitmapFromFile(f, Math.max(1280, reqW), Math.max(720, reqH));\n                ModPlTapety.oznacz(c, scope, disk);   // Mod PL: dołączona ulubiona (np. RS7) zachowuje ręczne ustawienie\n')

PLIKI_MODA = ["ModPlOkna.java", "ModPlEkstraklasa.java", "ModPlMecze.java", "ModPlAdb.java", "ModPlKafelki.java", "ModPlGole.java",
              "ModPlWygaszacz.java", "ModPlTapety.java", "ModPlDetektor.java", "ModPlDziennik.java"]
# DZIENNIK=0 (instalka dla innych): dziennik moda wyłączony już przy kompilacji, zob. pl/java/ModPlDziennik.java
DZIENNIK_WLACZONY = "static final boolean WLACZONY = true;"
DZIENNIK_WYLACZONY = "static final boolean WLACZONY = false;"


def main() -> int:
    pliki = {}
    crlf = set()    # pliki z końcami linii CRLF (git na Windows) - zapis w tej samej postaci
    bledy = []
    nalozone = pominiete = 0
    for plik, opis, stary, nowy, oczekiwane in ZMIANY:
        if plik not in pliki:
            sciezka = os.path.join(SRC, plik)
            with io.open(sciezka, encoding="utf-8", newline="") as f:
                surowy = f.read()
            if "\r\n" in surowy:
                crlf.add(plik)
                surowy = surowy.replace("\r\n", "\n")
            pliki[plik] = surowy
        tekst = pliki[plik]
        # Już nałożona: jest nowy tekst, a stary został tylko jako jego część (łatka dopisująca)
        # albo zniknął. Sam test "nowy in tekst" myli się, gdy nowy jest fragmentem starego.
        if nowy in tekst and (stary not in tekst
                              or (stary in nowy and tekst.count(stary) == tekst.count(nowy))):
            pominiete += 1
            continue
        ile = tekst.count(stary)
        if ile != oczekiwane:
            bledy.append("%s: „%s” - fragment znaleziony %d razy (oczekiwano %d)" % (plik, opis, ile, oczekiwane))
            continue
        pliki[plik] = tekst.replace(stary, nowy)
        nalozone += 1
    if bledy:
        print("BŁĘDY (%d), niczego nie zapisano:" % len(bledy))
        for b in bledy:
            print("  " + b)
        return 1
    if not TYLKO_SPRAWDZ:
        for plik, tekst in pliki.items():
            if plik in crlf:
                tekst = tekst.replace("\n", "\r\n")
            with io.open(os.path.join(SRC, plik), "w", encoding="utf-8", newline="") as f:
                f.write(tekst)
        bez_dziennika = os.environ.get("DZIENNIK", "1") == "0"
        for nazwa in PLIKI_MODA:
            with io.open(os.path.join(TU, "java", nazwa), encoding="utf-8") as f:
                kod = f.read()
            if nazwa == "ModPlDziennik.java" and bez_dziennika:
                if kod.count(DZIENNIK_WLACZONY) != 1:
                    print("BŁĄD: w ModPlDziennik.java nie ma przełącznika dziennika")
                    return 1
                kod = kod.replace(DZIENNIK_WLACZONY, DZIENNIK_WYLACZONY)
                print("Dziennik moda: WYŁĄCZONY (wersja do instalki)")
            with io.open(os.path.join(SRC, nazwa), "w", encoding="utf-8", newline="\n") as f:
                f.write(kod)
    print("Zmian: %d, nałożono teraz: %d, były już nałożone: %d%s" % (
        len(ZMIANY), nalozone, pominiete, " (tylko sprawdzenie)" if TYLKO_SPRAWDZ else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
