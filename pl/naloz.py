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

# Od 2.2.5 oryginał ma własny język polski (LANG_PL, initPolish z ok. 500 wpisami, locale pl-PL). Zostawiamy go
# jako podkład i nakładamy na wierzch nasz pełny słownik (I18nPl): nasze tłumaczenia wygrywają, a napisy, których
# nie mamy, biorą się z oryginału zamiast zostać po angielsku.
zmiana("I18n.java", "Polski na liście języków: bez wpisu oryginału w środku listy",
       '        {LANG_PL, "Polski", "Użyj tvOS Launcher w języku polskim"},\n',
       '')

zmiana("I18n.java", "Polski na liście języków",
       '        {LANG_EN, "English", "Use tvOS Launcher in English"},\n',
       '        {LANG_EN, "English", "Use tvOS Launcher in English"},\n'
       '        {LANG_PL, "Polski", "Używaj tvOS Launchera po polsku"},\n')

# słowniki kończy hebrajski (I18nHe.init), polski oryginału (initPolish) jest już wtedy wczytany
zmiana("I18n.java", "wczytanie polskiej mapy",
       '        I18nHe.init(LANG_MAPS.get(LANG_HE));\n',
       '        I18nHe.init(LANG_MAPS.get(LANG_HE));\n'
       '\n'
       '        I18nPl.fill(LANG_MAPS.get(LANG_PL));   // Mod PL: nasz słownik na wierzch polskiego z oryginału\n')

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

# oryginał od 2.2.5 ma polski we wspólnej gałęzi „EEEE d MMMM” (mała litera, bez przecinka); u nas osobna gałąź
zmiana("I18n.java", "polski format daty: bez gałęzi oryginału",
       '            case LANG_PT:\n'
       '            case LANG_PL:\n'
       '            case LANG_RU:\n',
       '            case LANG_PT:\n'
       '            case LANG_RU:\n')

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

# (kraj PL dla języka polskiego ma od 2.2.5 sam oryginał: defaultCountryForUiLang)

zmiana("HomeDashboard.java", "widżet godzin modlitw domyślnie wyłączony",
       '        return prefs(c).getBoolean(KEY_W_PRAYER, true);\n',
       '        return prefs(c).getBoolean(KEY_W_PRAYER, false);\n')

# Od 2.2.0 oryginał ma jeszcze krykiet, wiadomości (angielskie źródła) i „Top Movies & Series” (angielskie
# tytuły; po polsku mamy „Najczęściej oglądane” z JustWatch). Domyślnie wyłączone, włącza się je w ustawieniach.
zmiana("HomeDashboard.java", "widżet krykieta domyślnie wyłączony",
       '        return prefs(c).getBoolean(KEY_W_CRICKET, true);\n',
       '        return prefs(c).getBoolean(KEY_W_CRICKET, false);\n')

zmiana("HomeDashboard.java", "widżet wiadomości domyślnie wyłączony",
       '        return prefs(c).getBoolean(KEY_W_NEWS, true);\n',
       '        return prefs(c).getBoolean(KEY_W_NEWS, false);\n')

zmiana("HomeDashboard.java", "widżet Top Movies & Series domyślnie wyłączony",
       '        return prefs(c).getBoolean(KEY_W_TOP_MEDIA, true);\n',
       '        return prefs(c).getBoolean(KEY_W_TOP_MEDIA, false);\n')

# 2.2.0 przy pierwszym odczycie ustawień na siłę włącza te trzy widżety (znacznik widgets_all_default_v2);
# u nas znacznik się zapisuje, ale niczego nie włącza.
zmiana("HomeDashboard.java", "bez wymuszonego włączenia nowych widżetów",
       '                    .putBoolean(KEY_ALL_DEFAULT_V2, true)\n'
       '                    .putBoolean(KEY_W_CRICKET, true)\n'
       '                    .putBoolean(KEY_W_NEWS, true)\n'
       '                    .putBoolean(KEY_W_TOP_MEDIA, true)\n'
       '                    .apply();\n',
       '                    .putBoolean(KEY_ALL_DEFAULT_V2, true)\n'
       '                    .apply();\n')

# Widżet meczów zostaje domyślnie WŁĄCZONY (nowe instalacje, np. u znajomego kibica); kto go wyłączył,
# ma to zapisane w ustawieniach. Poniżej ligi i drużyny pod polskiego kibica. Główne źródło to LiveScore
# (pl/java/ModPlMecze.java: Ekstraklasa, puchary UEFA, 5 lig); pozostałe kody idą przez ESPN (zapas oryginału).
# Od 2.2.0 oryginał ma długą listę lig; bierzemy z niej rozgrywki reprezentacji, bez lig tureckiej i azerskiej.
zmiana("HomeDashboard.java", "ligi: Ekstraklasa, puchary i reprezentacje, bez tureckiej",
       '        { "all", "All Leagues & Cups (Worldwide)" },\n'
       '        { "uefa.champions", "UEFA Champions League" },\n'
       '        { "uefa.europa", "UEFA Europa League" },\n'
       '        { "uefa.europa.conf", "UEFA Conference League" },\n'
       '        { "uefa.nations", "UEFA Nations League" },\n'
       '        { "fifa.world", "FIFA World Cup / Qualifiers" },\n'
       '        { "eng.1", "Premier League (England)" },\n'
       '        { "eng.fa", "FA Cup / EFL Cup" },\n'
       '        { "esp.1", "LaLiga (Spain)" },\n'
       '        { "esp.copa_del_rey", "Copa del Rey" },\n'
       '        { "ita.1", "Serie A (Italy)" },\n'
       '        { "ita.coppa_italia", "Coppa Italia" },\n'
       '        { "ger.1", "Bundesliga (Germany)" },\n'
       '        { "ger.dfb_pokal", "DFB-Pokal" },\n'
       '        { "tur.1", "Trendyol Süper Lig (Turkey)" },\n'
       '        { "tur.cup", "Ziraat Türkiye Kupası" },\n'
       '        { "fra.1", "Ligue 1 (France)" },\n'
       '        { "aze.1", "Misli Premyer Liqası (Azerbaijan)" },\n'
       '        { "ned.1", "Eredivisie (Netherlands)" },\n'
       '        { "por.1", "Liga Portugal" },\n'
       '        { "ksa.1", "Saudi Pro League" },\n'
       '        { "usa.1", "MLS" },\n'
       '        { "bra.1", "Brasileirão" },\n'
       '        { "conmebol.libertadores", "Copa Libertadores" }\n',
       '        { "all", "All Top Leagues" },\n'
       '        { "pol.1", "Ekstraklasa" },\n'
       '        { "uefa.champions", "UEFA Champions League" },\n'
       '        { "uefa.europa", "UEFA Europa League" },\n'
       '        { "uefa.europa.conf", "UEFA Conference League" },\n'
       '        { "uefa.nations", "UEFA Nations League" },\n'
       '        { "fifa.world", "FIFA World Cup / Qualifiers" },\n'
       '        { "eng.1", "Premier League" },\n'
       '        { "esp.1", "LaLiga" },\n'
       '        { "ita.1", "Serie A" },\n'
       '        { "ger.1", "Bundesliga" },\n'
       '        { "fra.1", "Ligue 1" }\n')

# Zapas ESPN dla „Najlepszych lig” (gdy LiveScore nie odpowiada): bez całego świata ("all") i lig
# tureckich, azerskiej i saudyjskiej z listy oryginału.
zmiana("HomeDashboard.java", "Najlepsze ligi: puchary UEFA i reprezentacje zamiast ligi tureckiej",
       '                    String[] codes = {\n'
       '                        "all",\n'
       '                        "uefa.champions", "uefa.europa", "uefa.europa.conf", "uefa.nations",\n'
       '                        "fifa.world", "eng.1", "eng.fa", "esp.1", "esp.copa_del_rey",\n'
       '                        "tur.1", "tur.cup", "ita.1", "ger.1", "fra.1",\n'
       '                        "aze.1", "ned.1", "por.1", "ksa.1"\n'
       '                    };\n',
       '                    String[] codes = {\n'
       '                        "uefa.champions", "uefa.europa", "uefa.europa.conf", "uefa.nations",\n'
       '                        "fifa.world", "eng.1", "esp.1", "ita.1", "ger.1", "fra.1"\n'
       '                    };\n')

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

zmiana("HomeDashboard.java", "lista drużyn do wyboru: polskie kluby zamiast azerskich i tureckich",
       '        "Qarabağ", "Neftçi", "Sabah", "Zirə", "Turan Tovuz",\n'
       '        "Galatasaray", "Fenerbahçe", "Beşiktaş", "Trabzonspor", "Başakşehir",\n',
       '        "Polska", "Legia", "Lech Poznań", "Jagiellonia", "Raków", "Pogoń", "Widzew", "Górnik", "Cracovia",\n')

# od 2.2.0 przycisk ligi w oknie oryginału ma samą nazwę ligi (bez „League:”)
zmiana("HomeDashboard.java", "przycisk ligi po polsku",
       'createActionPill(host, curLeagueLabel);\n',
       'createActionPill(host, I18n.t(host, curLeagueLabel));\n')

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

# od 2.2.0 wiersze doku są w osobnej sekcji DOCK (mniejsze wcięcie, doszły „Dock rows”)
zmiana("SettingsActivity.java", "przełącznik podpisów w ustawieniach",
       '                    Scenes.setDockNamesShown(this, !Scenes.isDockNamesShown(this));\n'
       '                    refreshAll();\n'
       '                });\n',
       '                    Scenes.setDockNamesShown(this, !Scenes.isDockNamesShown(this));\n'
       '                    refreshAll();\n'
       '                });\n'
       '        row(tr("Names under icons", "Simgelerin altında adlar"),\n'
       '                () -> Scenes.isDockNamesAlways(this) ? tr("On", "Açık") : tr("Off", "Kapalı"),\n'
       '                () -> {\n'
       '                    Scenes.setDockNamesAlways(this, !Scenes.isDockNamesAlways(this));\n'
       '                    refreshAll();\n'
       '                });\n')

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

# Aktualizacje z naszego repozytorium (decyzja użytkownika 17.11.2026: fork zostaje na premiumsmart). Wydania mają tag
# w postaci 2.2.0-pl31 (porównanie wersji po liczbach) i jeden plik APK o stałej nazwie tvOS-Launcher-PL.apk
# (podpisany naszym kluczem, bez dziennika, bez zdjęć Lambo i RS7). Automatyczne aktualizacje zostają domyślnie
# włączone jak w oryginale: wcześniej je wyłączaliśmy, bo wskazywały oryginał z innym podpisem.
REPO_AKTUALIZACJI = "premiumsmart/tvOS-Launcher-PL"
zmiana("AppUpdater.java", "aktualizacje z repozytorium moda (API wydań)",
       '            "https://api.github.com/repos/techgenius1/tvOS-Launcher/releases/latest";\n',
       '            "https://api.github.com/repos/' + REPO_AKTUALIZACJI + '/releases/latest";\n')

zmiana("AppUpdater.java", "aktualizacje z repozytorium moda (zapasowy adres APK)",
       '            "https://github.com/techgenius1/tvOS-Launcher/releases/latest/download/tvos-home.apk";\n',
       '            "https://github.com/' + REPO_AKTUALIZACJI + '/releases/latest/download/tvOS-Launcher-PL.apk";\n')

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

# „Internal Storage” w wyborze folderu: od 2.2.0 oryginał tłumaczy sam (I18n.t), łatka niepotrzebna.

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
       '        /** Mod PL: czas trwania w minutach (film albo odcinek) i oznaczenie wieku ze źródła, np. 16 albo PG-13. */\n'
       '        int czas;\n'
       '        String wiek = "";\n'
       '        /** Continue Watching entries')

zmiana("Suggestions.java", "zapis nazwy serwisu",
       '.put("link", link).put("pub", published)',
       '.put("link", link).put("prov", prov == null ? "" : prov).put("trailer", trailer == null ? "" : trailer)'
       '.put("czas", czas).put("wiek", wiek == null ? "" : wiek).put("pub", published)')

zmiana("Suggestions.java", "odczyt nazwy serwisu",
       '            i.link = o.optString("link");\n',
       '            i.link = o.optString("link");\n'
       '            i.prov = o.optString("prov", "");\n'
       '            i.trailer = o.optString("trailer", "");\n'
       '            i.czas = o.optInt("czas", 0);\n'
       '            i.wiek = o.optString("wiek", "");\n')

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
       'return "s4:" + I18n.getLang(c) + ":" + TextUtils.join(",", getEffectiveReleasesCountries(c));')

zmiana("Suggestions.java", "JustWatch: zwiastuny, czas trwania i oznaczenie wieku w zapytaniu",
       'scoring { imdbScore } }"',
       'scoring { imdbScore } runtime ageCertification clips { externalId provider } }"')

zmiana("Suggestions.java", "Najczęściej oglądane: zapamiętanie zwiastuna, czasu trwania i wieku",
       '                            it.genres = jwGenres(ct.optJSONArray("genres"));\n'
       '                            JSONArray offers = n.optJSONArray("offers");\n',
       '                            it.genres = jwGenres(ct.optJSONArray("genres"));\n'
       '                            it.czas = ct.optInt("runtime", 0);\n'
       '                            it.wiek = ct.isNull("ageCertification") ? "" : ct.optString("ageCertification", "").trim();\n'
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
       '                if ("all".equals(league)) {\n',
       '                List<MatchItem> zLiveScore = ModPlMecze.pobierz(c, league);\n'
       '                if (zLiveScore != null) {\n'
       '                    all.addAll(zLiveScore);\n'
       '                } else if (ModPlEkstraklasa.KOD.equals(league)) {\n'
       '                    all.addAll(ModPlEkstraklasa.pobierz(c));\n'
       '                } else if ("all".equals(league)) {\n'
       '                    all.addAll(ModPlEkstraklasa.pobierz(c));\n')

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

# Od 2.2.0 oryginał porównuje ulubione przez normalizeTeamName (zna znaki tureckie i azerskie);
# dokładamy polskie: „Lech Poznan” z ESPN = „Lech Poznań” z listy.
zmiana("HomeDashboard.java", "ulubione bez polskich znaków (Lech Poznan = Lech Poznań)",
       '    private static String normalizeTeamName(String s) {\n'
       '        if (s == null) return "";\n'
       '        return s.toLowerCase(Locale.ROOT)\n',
       '    private static String normalizeTeamName(String s) {\n'
       '        if (s == null) return "";\n'
       '        return ModPlOkna.bezOgonkow(s)\n')

# Od 2.2.0 oryginał sam ma w meczu id i herby (homeLogo, awayLogo) i czyta je z ESPN razem z listą logos;
# dokładamy tylko własne pole.
zmiana("HomeDashboard.java", "pole główna liga dla meczów LiveScore",
       '        public String leagueName = "";\n',
       '        public String leagueName = "";\n'
       '        /** Mod PL: główna liga albo polskie rozgrywki - ulubione dopasowujemy też po fragmencie nazwy. */\n'
       '        public boolean glowna = true;\n')

zmiana("HomeDashboard.java", "okno Najczęściej oglądane: wariant C",
       'private static void showReleasesDialog(final MainActivity host, final List<Suggestions.Item> items, final Runnable onCycle) {\n',
       'private static void showReleasesDialog(final MainActivity host, final List<Suggestions.Item> items, final Runnable onCycle) {\n'
       '        if (ModPlOkna.pokazNajczesciej(host, items)) return;\n')

# od 2.2.0 okno meczów oryginału ma trzeci parametr (filtr „tylko ulubione”); nasze okno ma własny filtr
zmiana("HomeDashboard.java", "okno meczów: wariant C",
       'private static void showFootballDialog(final MainActivity host, final Runnable onUpdated, final boolean favOnlyFilter) {\n',
       'private static void showFootballDialog(final MainActivity host, final Runnable onUpdated, final boolean favOnlyFilter) {\n'
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

# (dotyczy zapasowego kafelka oryginału, ModPlKafelki.NOWE = false; nasz kafelek pilnuje meczów sam)
zmiana("HomeDashboard.java", "kafelek: odświeżanie na żywo",
       '        card.setOnClickListener(new View.OnClickListener() {\n'
       '            @Override\n'
       '            public void onClick(View v) {\n'
       '                showFootballDialog(host, bindMatches, false);\n',
       '        ModPlOkna.pilnujMeczow(card, host, bindMatches);\n'
       '        card.setOnClickListener(new View.OnClickListener() {\n'
       '            @Override\n'
       '            public void onClick(View v) {\n'
       '                showFootballDialog(host, bindMatches, false);\n')

zmiana("HomeDashboard.java", "pusta lista z LiveScore zastępuje starą",
       '            if (!all.isEmpty()) {\n'
       '                sMemMatches = all;\n',
       '            if (!all.isEmpty() || ModPlMecze.dziala) {\n'
       '                sMemMatches = all;\n')

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

# Od 2.2.0 przełączniki widżetów w ustawieniach, rząd na ekranie i menu przytrzymania OK biorą się
# z DEFAULT_WIDGET_IDS; bez modlitw na tej liście widżet znika wszędzie naraz.
zmiana("HomeDashboard.java", "ustawienia: bez przełącznika godzin modlitw",
       '        WIDGET_PRAYER, WIDGET_FOOTBALL, WIDGET_RELEASES, WIDGET_WEATHER,\n',
       '        WIDGET_FOOTBALL, WIDGET_RELEASES, WIDGET_WEATHER,\n')

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
       '        row(I18n.t(this, "Clock style"),\n'
       '                () -> ModPlWygaszacz.opisStyluZegara(this),\n'
       '                () -> {\n'
       '                    ModPlWygaszacz.nastepnyStylZegara(this);\n'
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

zmiana('SetupActivity.java', 'kreator: tryb bezpieczny bez zamykania ADB (nazwa)',
       '            option(l, tr("Mode: Secure Auto Setup & Close ADB (Recommended)", "Mod: Güvenli Otomatik Kurulum ve ADB\'yi Kapat (Önerilen)"),\n',
       '            option(l, tr("Mode: Secure Auto Setup", "Mod: Güvenli Otomatik Kurulum"),\n')

zmiana('SetupActivity.java', 'kreator: tryb zalecany bez zamykania ADB (opis)',
       '                    tr("Grants WRITE_SECURE_SETTINGS once to self-repair Accessibility, then closes port 5555", "Erişilebilirliği korumak için bir kez WRITE_SECURE_SETTINGS verir, ardından port 5555\'i kapatır"),\n',
       '                    tr("No recent apps or updates via ADB", "Erişilebilirliği korumak için bir kez WRITE_SECURE_SETTINGS verir; USB hata ayıklama açık kalır"),\n')

# Prośba użytkownika 24.11.2026: „adb ma być włączony … zalecane daj z włączonym adb”. W trybie bezpiecznym nie
# działają ostatnie aplikacje, zamykanie aplikacji ani aktualizacje przez ADB, więc zalecany jest tryb stałego ADB:
# idzie na pierwsze miejsce kreatora (dostaje zaznaczenie), a tryb bezpieczny na drugie.
zmiana('SetupActivity.java', 'kreator: tryb stałego ADB pierwszy i zalecany',
       '            option(l, tr("Mode: Secure Auto Setup", "Mod: Güvenli Otomatik Kurulum"),\n'
       '                    tr("No recent apps or updates via ADB", "Erişilebilirliği korumak için bir kez WRITE_SECURE_SETTINGS verir; USB hata ayıklama açık kalır"),\n'
       '                    false,\n'
       '                    () -> {\n'
       '                        LocalAdb.setAdbMode(this, LocalAdb.ADB_MODE_SECURE_CLOSE);\n'
       '                        proceedToEasyAdbOrDirections();\n'
       '                    });\n'
       '            option(l, tr("Mode: Persistent ADB Setup (Keep ADB Open)", "Mod: Kalıcı ADB Kurulumu (ADB\'yi Açık Tut)"),\n'
       '                    tr("Keeps network ADB enabled for CleanYourTV debloating (Note: leaves port 5555 open on LAN)", "CleanYourTV debloat özellikleri için ağ ADB\'sini açık tutar (Not: yerel ağda port 5555 açık kalır)"),\n'
       '                    false,\n'
       '                    () -> {\n'
       '                        LocalAdb.setAdbMode(this, LocalAdb.ADB_MODE_PERSISTENT);\n'
       '                        proceedToEasyAdbOrDirections();\n'
       '                    });\n',
       '            option(l, tr("Mode: Persistent ADB Setup (Recommended)", "Mod: Kalıcı ADB Kurulumu (Önerilen)"),\n'
       '                    tr("Recent apps, updates and the volume indicator work", "CleanYourTV debloat özellikleri için ağ ADB\'sini açık tutar (Not: yerel ağda port 5555 açık kalır)"),\n'
       '                    false,\n'
       '                    () -> {\n'
       '                        LocalAdb.setAdbMode(this, LocalAdb.ADB_MODE_PERSISTENT);\n'
       '                        proceedToEasyAdbOrDirections();\n'
       '                    });\n'
       '            option(l, tr("Mode: Secure Auto Setup", "Mod: Güvenli Otomatik Kurulum"),\n'
       '                    tr("No recent apps or updates via ADB", "Erişilebilirliği korumak için bir kez WRITE_SECURE_SETTINGS verir; USB hata ayıklama açık kalır"),\n'
       '                    false,\n'
       '                    () -> {\n'
       '                        LocalAdb.setAdbMode(this, LocalAdb.ADB_MODE_SECURE_CLOSE);\n'
       '                        proceedToEasyAdbOrDirections();\n'
       '                    });\n')


zmiana('SetupActivity.java', 'kreator: podpowiedź bez zamykania ADB',
       '                    + (secureClose ? " (ADB port 5555 will be closed automatically right after setup)." : " (ADB will remain enabled for CleanYourTV)."),\n',
       '                    + (secureClose ? " (USB debugging stays on)." : " (ADB stays on)."),\n')

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

# Od 2.2.0 każda automatyczna konfiguracja (kreator, Perms, ustawienia) idzie przez grantAllPermissions,
# a domyślny tryb to SECURE_CLOSE, więc ta jedna łatka pilnuje, żeby nic samo nie wyłączyło ADB.
# Przycisk „Zamknij ADB” w ustawieniach (closeAdbPort) działa tylko po świadomym potwierdzeniu.
zmiana('LocalAdb.java', 'automatyczna konfiguracja: nie wyłączaj debugowania USB',
       '                if (closeAdbAfter) {\n                    cmds.add("settings put global adb_enabled 0 2>/dev/null");\n',
       '                if (closeAdbAfter && ModPlAdb.ZAMYKAJ_ADB) {\n                    cmds.add("settings put global adb_enabled 0 2>/dev/null");\n')

zmiana('LocalAdb.java', 'automatyczna konfiguracja: wynik bez „ADB zamknięte”',
       'cb.onResult(true, closeAdbAfter ? "CLOSED_ADB" : null);',
       'cb.onResult(true, closeAdbAfter && ModPlAdb.ZAMYKAJ_ADB ? "CLOSED_ADB" : null);')

zmiana('LocalAdb.java', 'nazwa trybu bezpiecznego bez zamykania ADB',
       '                "Secure Mode — WRITE_SECURE_SETTINGS & Close ADB (Recommended)",\n',
       '                "Secure Mode — WRITE_SECURE_SETTINGS, ADB stays on (limited)",\n')

zmiana('LocalAdb.java', 'nazwa trybu bezpiecznego bez zamykania ADB (tr)',
       '                "Güvenli Mod — WRITE_SECURE_SETTINGS ve ADB Kapat (Önerilen)");\n',
       '                "Güvenli Mod — WRITE_SECURE_SETTINGS, ADB açık kalır (Önerilen)");\n')

zmiana('SettingsActivity.java', 'ustawienia: tryb bezpieczny bez zamykania ADB',
       '                    + tr("Secure Mode — Grant WRITE_SECURE_SETTINGS & Close ADB (Recommended)",\n',
       '                    + tr("Secure Mode — Grant WRITE_SECURE_SETTINGS, ADB stays on (limited)",\n')

zmiana('SettingsActivity.java', 'ustawienia: tryb stałego ADB zalecany',
       '                    + tr("Persistent ADB Mode — Keep Network ADB Open (Optional)",\n',
       '                    + tr("Persistent ADB Mode — Keep Network ADB Open (Recommended)",\n')

zmiana('SettingsActivity.java', 'ustawienia: komunikat trybu stałego ADB',
       '                        showStatusMsg(tr("Persistent ADB Mode selected — Port 5555 stays open for CleanYourTV & ADB tools.",\n',
       '                        showStatusMsg(tr("Persistent ADB Mode selected — ADB stays on for recent apps, updates and ADB tools.",\n')

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

# Sceny aut moda są w każdej wersji, ale ich zdjęć nie ma w APK (decyzja użytkownika 17.11.2026): na jego telewizorze
# leżą w pamięci aplikacji (Android/data/<pakiet>/files/mod_pl/, wgrywa je pl/zdjecia-na-box.sh), u innych ich nie ma,
# więc sceny są tam ukryte (ModPlTapety.scenaDostepna) i nie wchodzą do rotacji ani na listy.
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
       '            final Scenes.Spec[] modSceny = ModPlTapety.najpierwAuta(this, Scenes.ALL);   // Mod PL: auta moda na początku (gdy są ich zdjęcia)\n            for (int i = 0; i < modSceny.length; i++) {\n                final Scenes.Spec spec = modSceny[i];\n')

zmiana('Scenes.java', 'ustawienia: auta moda na początku kolejności scen (także przy zapisanej kolejności)',
       '        for (Spec s : ALL) {\n            if (!out.contains(s.id)) out.add(s.id);\n        }\n',
       '        int modPrzod = 0;   // Mod PL: auta moda, których nie było w zapisanej kolejności, idą na początek listy\n        for (Spec s : ALL) {\n            if (out.contains(s.id)) continue;\n            if (ModPlTapety.autoModa(s.id)) {\n                if (ModPlTapety.scenaDostepna(c, s.id)) out.add(modPrzod++, s.id);\n            } else out.add(s.id);\n        }\n')

# zdjęcia aut moda spoza APK: wczytywanie z pamięci aplikacji, a bez nich sceny znikają z rotacji i list
zmiana('Scenes.java', 'zdjęcia aut moda: zapas z pamięci aplikacji',
       '            return c.getAssets().open(assetPath.replace(\'/\', \'\\\\\'));\n',
       '            java.io.File modPlik = ModPlTapety.plikPozaApk(c, assetPath);   // Mod PL: zdjęcia aut moda spoza APK\n'
       '            if (modPlik != null) return new java.io.FileInputStream(modPlik);\n'
       '            return c.getAssets().open(assetPath.replace(\'/\', \'\\\\\'));\n')

zmiana('Scenes.java', 'zdjęcia aut moda: bez zdjęć scena nie wchodzi do rotacji',
       '            int cSlot = customSlotFromId(p);\n'
       '            if (cSlot > 0) {\n'
       '                if (!customModeOn || customSubMode != CustomMedia.CUSTOM_MODE_SINGLE_MULTI\n',
       '            if (!ModPlTapety.scenaDostepna(c, p)) continue;   // Mod PL: auto moda bez zdjęć na tym telewizorze\n'
       '            int cSlot = customSlotFromId(p);\n'
       '            if (cSlot > 0) {\n'
       '                if (!customModeOn || customSubMode != CustomMedia.CUSTOM_MODE_SINGLE_MULTI\n')

zmiana('SettingsActivity.java', 'zdjęcia aut moda: lista scen ekranu blokady bez niedostępnych',
       '        for (Scenes.Spec spec : Scenes.ALL) {\n'
       '            lockSceneCheckRow(spec.id, spec.title(this), spec.bgAsset);\n',
       '        for (Scenes.Spec spec : Scenes.ALL) {\n'
       '            if (!ModPlTapety.scenaDostepna(this, spec.id)) continue;   // Mod PL: auto moda bez zdjęć\n'
       '            lockSceneCheckRow(spec.id, spec.title(this), spec.bgAsset);\n')

zmiana('Scenes.java', 'kreator: przełączenie sceny przy tapetach z internetu nie kasuje pozostałych scen',
       '        List<String> enabled = new ArrayList<>(getEnabledSceneIds(c));\n        if (enabled.contains(id)) {\n            if (enabled.size() <= 1) return false;\n',
       '        // Mod PL: przełączamy zapisaną listę scen. getEnabledSceneIds przy włączonej własnej tapecie zwraca tylko ją\n        // („reddit_wall”), więc przełączenie sceny w kreatorze zapisywało listę od nowa i gubiło pozostałe sceny.\n        List<String> enabled = new ArrayList<>(getEnabledSceneIdsRaw(c));\n        if (enabled.contains(id)) {\n            if (enabled.size() <= 1) return false;\n')

zmiana('CustomMedia.java', 'tapety z internetu: dołączona ulubiona z pamięci podręcznej zachowuje ręczne ustawienie zegara',
       '                Bitmap disk = decodeBitmapFromFile(f, Math.max(1280, reqW), Math.max(720, reqH));\n',
       '                Bitmap disk = decodeBitmapFromFile(f, Math.max(1280, reqW), Math.max(720, reqH));\n                ModPlTapety.oznacz(c, scope, disk);   // Mod PL: dołączona ulubiona (np. RS7) zachowuje ręczne ustawienie\n')

# ---------------------------------------------------------------- nowe widżety oryginału 2.2.0 w naszym stylu (17.11.2026)
# Użytkownik: „te widgety co doszły nowe to musisz je zrobić tak jak są mecze i najczęściej oglądane”.
# Kafelki: ModPlKafelki.wiadomosci / top, okna: ModPlOkna.pokazWiadomosci / pokazTop.
# Od 2.2.5 wiadomości mają w oryginale wybór kraju (domyślnie według języka: polski -> Polska), więc własna łatka
# z polskimi nagłówkami Google News nie jest już potrzebna.

# ---------------------------------------------------------------- bez widżetu krykieta (2.2.5, 23.11.2026)
# Decyzja użytkownika z 23.11.2026: z dodatkowych kafelków zostają Wiadomości i Top filmy i seriale, krykiet
# wylatuje tak jak wcześniej godziny modlitw. Kafelek nigdy się nie buduje, a bez wpisu w DEFAULT_WIDGET_IDS znika
# z ustawień, z rzędu na ekranie i z menu przytrzymania OK. Kod krykieta oryginału zostaje nieużywany.
zmiana("HomeDashboard.java", "widżet krykieta usunięty",
       '        return prefs(c).getBoolean(KEY_W_CRICKET, false);\n',
       '        return false;\n')

zmiana("HomeDashboard.java", "ustawienia: bez przełącznika krykieta",
       '        WIDGET_CRICKET, WIDGET_NEWS, WIDGET_TOP_MEDIA\n',
       '        WIDGET_NEWS, WIDGET_TOP_MEDIA\n')

# Od 2.2.5 zmiana miasta pogody od razu pobiera też godziny modlitw (api.aladhan.com) dla tego miejsca.
# Modlitw u nas nie ma, więc tego zapytania nie wysyłamy.
zmiana("Weather.java", "bez pobierania godzin modlitw przy zmianie miasta pogody",
       '        if (HomeDashboard.isPrayerLocationAuto(c)) {\n',
       '        if (HomeDashboard.isWidgetPrayerEnabled(c) && HomeDashboard.isPrayerLocationAuto(c)) {\n')

# Od 2.2.5 oryginał wczytuje zdjęcia scen i rysuje kadr ekranu głównego w RGB_565 zamiast ARGB_8888 (mniej pamięci).
# U nas zostają pełne kolory jak w 2.2.0 (ModPlTapety.PELNE_KOLORY): nocne niebo i szkło cyfr bez pasów.
zmiana("Scenes.java", "sceny w pełnych kolorach: zdjęcia",
       '            opts.inPreferredConfig = isOpaqueJpeg ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;\n',
       '            opts.inPreferredConfig = (isOpaqueJpeg && !ModPlTapety.PELNE_KOLORY)\n'
       '                    ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;\n')

zmiana("Scenes.java", "sceny w pełnych kolorach: rysowany kadr",
       '            return classicClockOnlyMode ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;\n',
       '            return (classicClockOnlyMode || ModPlTapety.PELNE_KOLORY) ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;\n')

# Od 2.2.5 własna scena może mieć ręcznie wgraną warstwę pierwszego planu (PNG), za którą chowa się zegar.
# Decyzja użytkownika z 23.11.2026: tej funkcji nie pokazujemy (ModPlKafelki.WARSTWA_PNG).
zmiana("SettingsActivity.java", "własna scena: bez wiersza warstwy pierwszego planu PNG",
       '                if (isScene) {\n'
       '                    final boolean hasFg = CustomMedia.hasCustomSceneForeground(this, slot);\n',
       '                if (isScene && ModPlKafelki.WARSTWA_PNG) {\n'
       '                    final boolean hasFg = CustomMedia.hasCustomSceneForeground(this, slot);\n')

# Od 2.2.5 w ustawieniach widżetów jest wybór miasta dla godzin modlitw; bez modlitw wiersz jest zbędny.
zmiana("SettingsActivity.java", "ustawienia: bez wyboru miasta godzin modlitw",
       '        row("🕌  " + tr("Prayer times location", "Namaz vakti konumu") + "…",\n'
       '                () -> HomeDashboard.getPrayerLocationSummary(this),\n'
       '                () -> openSubMenu("prayer_location"));\n',
       '')

zmiana("AppUpdater.java", "lista zmian bez znaczników Markdownu (opis wydania z GitHuba)",
       '        bodyTv.setText(bodyText);\n',
       '        bodyTv.setText(ModPlOkna.bezMarkdown(bodyText));   // Mod PL\n')

zmiana("HomeDashboard.java", "wiadomości: odnośnik do artykułu",
       '        public String pubDate = "";\n'
       '    }\n',
       '        public String pubDate = "";\n'
       '        /** Mod PL: adres artykułu (przycisk „Czytaj w przeglądarce”). */\n'
       '        public String link = "";\n'
       '    }\n')

zmiana("HomeDashboard.java", "wiadomości: data i odnośnik z kanału RSS",
       '                        nh.source = source != null && !source.isEmpty() ? decodeXmlEntities(source) : "LIVE";\n',
       '                        nh.source = source != null && !source.isEmpty() ? decodeXmlEntities(source) : "LIVE";\n'
       '                        nh.pubDate = extractXmlTag(block, "pubDate");\n'
       '                        nh.link = decodeXmlEntities(extractXmlTag(block, "link"));\n')

zmiana("HomeDashboard.java", "wiadomości: dostęp do listy",
       '    private static List<NewsHeadline> sMemNews = new ArrayList<NewsHeadline>();\n',
       '    private static List<NewsHeadline> sMemNews = new ArrayList<NewsHeadline>();\n'
       '\n'
       '    /** Mod PL: kopia ostatnio pobranych nagłówków (kafelek i okno moda). */\n'
       '    static List<NewsHeadline> modPlWiadomosci() {\n'
       '        return new ArrayList<NewsHeadline>(sMemNews);\n'
       '    }\n')

zmiana("HomeDashboard.java", "Top filmy i seriale: dostęp do listy",
       '    private static List<Suggestions.Item> sMemTopMedia = new ArrayList<Suggestions.Item>();\n',
       '    private static List<Suggestions.Item> sMemTopMedia = new ArrayList<Suggestions.Item>();\n'
       '\n'
       '    /** Mod PL: kopia ostatnio pobranego rankingu (kafelek i okno moda). */\n'
       '    static List<Suggestions.Item> modPlTop() {\n'
       '        return new ArrayList<Suggestions.Item>(sMemTopMedia);\n'
       '    }\n')

zmiana("HomeDashboard.java", "Top filmy i seriale: poziome tło, plakat, gatunki i zwiastun",
       '                            it.backdrop = m.optString("poster", m.optString("background", ""));\n',
       '                            it.backdrop = m.optString("poster", m.optString("background", ""));\n'
       '                            ModPlKafelki.uzupelnijTop(it, m);   // Mod PL: kadr poziomy, plakat, gatunki, zwiastun\n')

# Od 2.2.5 powrót na ekran główny nie buduje kafelków od nowa: oryginał woła zarejestrowane dla kafelka odświeżanie
# (sCardRefreshers, refreshCardsInPlace). Kafelki moda zgłaszają tu własne (ModPlKafelki: mecze, filmy, Top,
# pogoda, wiadomości), inaczej po powrocie z aplikacji pokazywałyby dane sprzed wyjścia.
zmiana("HomeDashboard.java", "odświeżanie w miejscu także dla kafelków moda",
       '    public static void refreshCardsInPlace(List<View> cards) {\n',
       '    /** Mod PL: kafelek moda zgłasza własne odświeżanie w miejscu. */\n'
       '    static void modPlOdswiezanie(View card, Runnable r) {\n'
       '        if (card != null && r != null) sCardRefreshers.put(card, r);\n'
       '    }\n'
       '\n'
       '    public static void refreshCardsInPlace(List<View> cards) {\n')

# Od 2.2.5 klawisz HOME zamyka otwarte okno widżetu (dismissActiveDialog). Okna moda (ModPlOkna.okno) zgłaszają
# się jako aktywne tą samą drogą.
zmiana("HomeDashboard.java", "klawisz HOME zamyka też okna moda",
       '    private static Dialog sActiveDialog;\n',
       '    private static Dialog sActiveDialog;\n'
       '\n'
       '    /** Mod PL: okno moda zgłasza się jako aktywne (zamykane klawiszem HOME jak okna oryginału). */\n'
       '    static void modPlOkno(Dialog d) {\n'
       '        sActiveDialog = d;\n'
       '    }\n')

zmiana("HomeDashboard.java", "nowy kafelek wiadomości",
       '    private static View createNewsWidgetCard(final MainActivity host) {\n',
       '    private static View createNewsWidgetCard(final MainActivity host) {\n'
       '        if (ModPlKafelki.NOWE) return ModPlKafelki.wiadomosci(host);\n')

zmiana("HomeDashboard.java", "nowy kafelek Top filmy i seriale",
       '    private static View createTopMediaWidgetCard(final MainActivity host) {\n',
       '    private static View createTopMediaWidgetCard(final MainActivity host) {\n'
       '        if (ModPlKafelki.NOWE) return ModPlKafelki.top(host);\n')

zmiana("HomeDashboard.java", "okno wiadomości w naszym stylu",
       '    private static void showNewsDialog(final MainActivity host, final Runnable onCycle) {\n',
       '    private static void showNewsDialog(final MainActivity host, final Runnable onCycle) {\n'
       '        if (ModPlOkna.pokazWiadomosci(host, onCycle)) return;\n')

zmiana("HomeDashboard.java", "okno Top filmy i seriale w naszym stylu",
       '    private static void showTopMediaDialog(final MainActivity host, final Runnable onCycle) {\n',
       '    private static void showTopMediaDialog(final MainActivity host, final Runnable onCycle) {\n'
       '        if (ModPlOkna.pokazTop(host, ModPlKafelki.przeplec(modPlTop()))) return;\n')

# ---------------------------------------------------------------- widżety i półka nad dokiem (2.2.0, 17.11.2026)
# Oryginał liczy miejsce nad dokiem na sztywno (dok 74 dp na rząd). Nasz dok bywa wyższy (podpisy pod ikonami pod
# każdym rzędem, większy rozmiar doku), więc widżety wchodziły pod dok (użytkownik 17.11: „nawet na 1 rząd te widgety
# nachodzą na ten dok”). Przesuwamy je o tyle, o ile prawdziwy dok jest wyższy od założonego; odstęp nad dokiem
# zostaje taki jak w oryginale.

# Od 2.2.5 oryginał sam liczy górę doku z rzędów, skali i dymka z nazwą, ale nadal bez naszych podpisów pod ikonami;
# gdy dok jest już zmierzony, bierzemy jego prawdziwą wysokość.
zmiana("SceneHome.java", "widżety i półka zawsze nad dokiem (prawdziwa wysokość doku)",
       '        int dockPadAbove = dp(4);\n'
       '        return Math.max(dp(190), Math.min(dp(SHELF_TOP_DP), dockTop - dockPadAbove - cardAreaH));\n',
       '        int dockPadAbove = dp(4);\n'
       '        if (dockBox != null && dockBox.getHeight() > 0) {   // Mod PL: prawdziwa wysokość doku\n'
       '            dockTop = Math.min(dockTop, host.screenH - dp(14) - dockBox.getHeight() - tooltipH);\n'
       '        }\n'
       '        return Math.max(dp(96), Math.min(dp(SHELF_TOP_DP), dockTop - dockPadAbove - cardAreaH));\n')

zmiana("SceneHome.java", "widżety i półka: przeliczenie po zmianie wysokości doku",
       '        dockBox = new LinearLayout(host);\n',
       '        dockBox = new LinearLayout(host);\n'
       '        // Mod PL: po każdej zmianie wysokości doku (rzędy, podpisy, rozmiar) widżety i półka siadają nad nim\n'
       '        dockBox.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {\n'
       '            if (b - t != ob - ot) v.post(this::updateShelfAndDashboardTopMargin);\n'
       '        });\n')

# ---------------------------------------------------------------- SceneHome.java: kafelki po zmianie zestawu widżetów

# Od 2.2.5 kafelki odświeżają się w miejscu, więc widżet włączony albo wyłączony w ustawieniach nie pojawiał się
# (ani nie znikał) na ekranie głównym aż do restartu launchera. Gdy zestaw się zmienił, rząd budujemy od nowa.
zmiana("SceneHome.java", "kafelki po zmianie zestawu widżetów: pole",
       '    final List<View> dashboardCards = new ArrayList<>();\n',
       '    final List<View> dashboardCards = new ArrayList<>();\n'
       '    /** Mod PL: zestaw widżetów, z którego zbudowano rząd kafelków. */\n'
       '    private String modPlZestawWidzetow = "";\n')
zmiana("SceneHome.java", "kafelki po zmianie zestawu widżetów: zapis zestawu",
       '        dashboardBox.removeAllViews();\n        dashboardCards.clear();\n        View row = HomeDashboard.buildDashboardView(',
       '        dashboardBox.removeAllViews();\n        dashboardCards.clear();\n'
       '        modPlZestawWidzetow = android.text.TextUtils.join(",", HomeDashboard.getEnabledWidgetOrder(host));\n'
       '        View row = HomeDashboard.buildDashboardView(')
zmiana("SceneHome.java", "kafelki po zmianie zestawu widżetów: porównanie",
       '        if (dashboardBox != null && dashboardBox.getChildCount() > 0 && !dashboardCards.isEmpty()) {\n'
       '            HomeDashboard.refreshCardsInPlace(dashboardCards);\n',
       '        if (dashboardBox != null && dashboardBox.getChildCount() > 0 && !dashboardCards.isEmpty()) {\n'
       '            if (!android.text.TextUtils.join(",", HomeDashboard.getEnabledWidgetOrder(host)).equals(modPlZestawWidzetow)) {\n'
       '                populateDashboard(true);   // Mod PL: zmienił się zestaw widżetów\n'
       '                return;\n'
       '            }\n'
       '            HomeDashboard.refreshCardsInPlace(dashboardCards);\n')

# ---------------------------------------------------------------- panel pogody w stylu tvOS 27 (kanwa 24.11.2026)
# Panel zostaje widokiem WeatherView oryginału (otwieranie, fokus i zamykanie bez zmian), ale ma wymiary z kanwy
# (620×960 px przy 1080p zamiast 760×912) i rysuje go ModPlOknaTv.rysujPogode: nagłówek i dwie szklane karty.
zmiana("WeatherView.java", "panel pogody: wymiary z kanwy",
       'FULL_W = 380, FULL_H = 456;',
       'FULL_W = 310, FULL_H = 480;')

zmiana("WeatherView.java", "panel pogody: rysowanie w układzie z kanwy",
       '        if (full) drawFull(c);\n',
       '        if (full) ModPlOknaTv.rysujPogode(this, c, data);   // Mod PL: układ z kanwy zamiast drawFull\n')

zmiana("MainActivity.java", "panel pogody: zaokrąglenie 48 px i marginesy 60 / 80 px",
       '        LiquidGlass glass = new LiquidGlass(dp(26), 0x33000000, glassSource, screenW, screenH, unit).adaptive();\n'
       '        glass.lens = dp(7);\n'
       '        panel.setBackground(glass);\n'
       '        panel.setFocusable(true);\n'
       '        panel.setData(d);\n'
       '        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(WeatherView.FULL_W), dp(WeatherView.FULL_H),\n'
       '                Gravity.TOP | (rightSide ? Gravity.END : Gravity.START));\n'
       '        plp.topMargin = dp(24);\n'
       '        if (rightSide) plp.rightMargin = dockOuter;\n'
       '        else plp.leftMargin = dockOuter;\n',
       '        LiquidGlass glass = new LiquidGlass(dp(24), 0x33000000, glassSource, screenW, screenH, unit).adaptive();\n'
       '        glass.lens = dp(7);\n'
       '        panel.setBackground(glass);\n'
       '        panel.setFocusable(true);\n'
       '        panel.setData(d);\n'
       '        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(WeatherView.FULL_W), dp(WeatherView.FULL_H),\n'
       '                Gravity.TOP | (rightSide ? Gravity.END : Gravity.START));\n'
       '        plp.topMargin = dp(30);\n'
       '        if (rightSide) plp.rightMargin = dp(40);\n'
       '        else plp.leftMargin = dp(40);\n')

# ---------------------------------------------------------------- zaznaczenie ikon doku w stylu tvOS 27 (24.11.2026)
# Kod: pl/java/ModPlFokus.java. Tryb w ustawieniach doku: obwódka oryginału, styl tvOS (cień i połysk, bez obwódki,
# domyślny) albo styl tvOS z cienką obwódką. Ikona czyta tryb przy każdym zaznaczeniu, więc dok nie wymaga przebudowy.
zmiana("Scenes.java", "zaznaczenie ikon doku: zapis trybu",
       '    public static void setDockNamesAlways(Context c, boolean on) {\n'
       '        prefs(c).edit().putBoolean("dock_names_always", on).apply();\n'
       '    }\n',
       '    public static void setDockNamesAlways(Context c, boolean on) {\n'
       '        prefs(c).edit().putBoolean("dock_names_always", on).apply();\n'
       '    }\n'
       '\n'
       '    /** Mod PL: zaznaczenie ikon doku: 0 obwódka oryginału, 1 styl tvOS, 2 styl tvOS z cienką obwódką. */\n'
       '    public static int getModPlDockFocus(Context c) {\n'
       '        return Math.max(0, Math.min(2, prefs(c).getInt("mod_pl_fokus_doku", 1)));\n'
       '    }\n'
       '\n'
       '    public static void setModPlDockFocus(Context c, int tryb) {\n'
       '        prefs(c).edit().putInt("mod_pl_fokus_doku", tryb).apply();\n'
       '    }\n')

zmiana("SettingsActivity.java", "zaznaczenie ikon doku: wiersz w ustawieniach",
       '                    Scenes.setDockNamesAlways(this, !Scenes.isDockNamesAlways(this));\n'
       '                    refreshAll();\n'
       '                });\n',
       '                    Scenes.setDockNamesAlways(this, !Scenes.isDockNamesAlways(this));\n'
       '                    refreshAll();\n'
       '                });\n'
       '        row(tr("Dock icon highlight", "Dock simge vurgusu"),\n'
       '                () -> I18n.t(this, ModPlFokus.nazwa(this)),\n'
       '                () -> {\n'
       '                    ModPlFokus.nastepny(this);\n'
       '                    refreshAll();\n'
       '                });\n')

zmiana("SceneHome.java", "zaznaczenie ikon doku: połysk i cień zamiast obwódki",
       '            v.setForeground(has ? focusRing : null);\n'
       '            if (index >= 0 && index < dockNames.size()) {\n',
       '            v.setForeground(ModPlFokus.zaznaczenie(v, has, focusRing, rad));   // Mod PL: styl tvOS albo obwódka\n'
       '            if (index >= 0 && index < dockNames.size()) {\n')

# W tvOS taca doku się nie wybrzusza: jest na tyle wysoka, że powiększona ikona mieści się w niej z równym odstępem
# (pomiar zrzutów tvOS 26/27). W stylu tvOS dok dostaje więc większe odstępy od krawędzi (ModPlFokus.odstepDoku).
zmiana("SceneHome.java", "zaznaczenie ikon doku: odstępy tacy mieszczące powiększoną ikonę",
       '        int padH = Math.max(dp(8), Math.round(dp(13) * scale));\n'
       '        int padV = Math.max(dp(6), Math.round(dp(10) * scale));\n'
       '        dockBox.setPadding(padH, padV, padH, padV);\n',
       '        int padH = ModPlFokus.odstepDoku(host, Math.max(dp(8), Math.round(dp(13) * scale)), tileSize);\n'
       '        int padV = ModPlFokus.odstepDoku(host, Math.max(dp(6), Math.round(dp(10) * scale)), tileSize);\n'
       '        dockBox.setPadding(padH, padV, padH, padV);\n')

# To samo zaznaczenie w oknie „Inne aplikacje” (oryginał: obwódka i powiększenie 1,11).
zmiana("SceneHome.java", "zaznaczenie ikon w oknie Inne aplikacje: przygotowanie",
       '            final Drawable focusRing = createIconFocusRingDrawable(iconRad, dp(2.2f), sq != null && hasWhiteCorners(sq));\n',
       '            final Drawable focusRing = createIconFocusRingDrawable(iconRad, dp(2.2f), sq != null && hasWhiteCorners(sq));\n'
       '            if (android.os.Build.VERSION.SDK_INT >= 26) iconBox.setDefaultFocusHighlightEnabled(false);   // Mod PL\n'
       '            ModPlFokus.przygotuj(this, iconBox, app, iconRad, sq != null && hasWhiteCorners(sq));   // Mod PL: cień i dane do głębi ikony\n')

zmiana("SceneHome.java", "zaznaczenie ikon w oknie Inne aplikacje: styl tvOS",
       '                iconBox.setForeground(has ? focusRing : null);\n'
       '                lbl.setTextColor(has ? 0xFFFFFFFF : 0xD9F4F4F6);\n'
       '                iconBox.animate().scaleX(has ? 1.11f : 1f).scaleY(has ? 1.11f : 1f)\n',
       '                iconBox.setForeground(ModPlFokus.zaznaczenie(iconBox, has, focusRing, iconRad));   // Mod PL\n'
       '                lbl.setTextColor(has ? 0xFFFFFFFF : 0xD9F4F4F6);\n'
       '                lbl.animate().translationY(has ? ModPlFokus.podpisNizej(host, iconBox) : 0f).setDuration(170).start();\n'
       '                iconBox.animate().scaleX(has ? ModPlFokus.skalaSiatki(host) : 1f).scaleY(has ? ModPlFokus.skalaSiatki(host) : 1f)\n')

# Zaznaczona ikona rośnie od środka, więc jej dół nachodziłby na stały podpis: podpis zjeżdża o tyle w dół.
zmiana("SceneHome.java", "zaznaczenie ikon doku: podpis zaznaczonej aplikacji niżej",
       '                dockNames.get(index).setTextColor(has ? 0xFFFFFFFF : 0xD9F5F5F7);\n',
       '                dockNames.get(index).setTextColor(has ? 0xFFFFFFFF : 0xD9F5F5F7);\n'
       '                dockNames.get(index).animate().translationY(has ? ModPlFokus.podpisNizej(host, v) : 0f)\n'
       '                        .setDuration(170).start();\n')

zmiana("SceneHome.java", "zaznaczenie ikon doku: przygotowanie przy budowie ikony",
       '        Drawable focusRing = createIconFocusRingDrawable(rad, dp(2.2f), whiteCorners);\n',
       '        Drawable focusRing = createIconFocusRingDrawable(rad, dp(2.2f), whiteCorners);\n'
       '        ModPlFokus.przygotuj(this, tile, a, rad, whiteCorners);   // Mod PL: cień styku i dane do głębi ikony\n')

# Poza telewizorami system kładzie na zaznaczonym widoku własny jasny prostokąt, i to zanim zadziała nasłuch
# zaznaczenia, więc wyłączamy go już przy budowaniu ikony (na telewizorach system go nie rysuje).
zmiana("SceneHome.java", "zaznaczenie ikon doku: bez systemowego podświetlenia",
       '        tile.setLongClickable(true);\n'
       '        tile.setPivotX(size / 2f);\n',
       '        tile.setLongClickable(true);\n'
       '        if (android.os.Build.VERSION.SDK_INT >= 26) tile.setDefaultFocusHighlightEnabled(false);   // Mod PL\n'
       '        tile.setPivotX(size / 2f);\n')

zmiana("SceneHome.java", "zaznaczenie ikon doku: powiększenie zależne od trybu",
       '                v.animate().scaleX(1.13f).scaleY(1.13f)\n',
       '                v.animate().scaleX(ModPlFokus.skala(host)).scaleY(ModPlFokus.skala(host))\n')

# ---------------------------------------------------------------- własny wskaźnik głośności (wariant B, 24.11.2026)
# Kod: pl/java/ModPlGlosnosc.java. Opcję („Wskaźnik głośności” w ustawieniach doku) wybiera użytkownik w kreatorze
# albo w jednorazowym pytaniu po aktualizacji (25.11.2026); do czasu wyboru zostaje pasek systemowy. Gdy jest
# włączona, PowerService zmienia głośność bez FLAG_SHOW_UI (system nie pokazuje białego paska) i nie oddaje systemowi
# klawiszy głośności, bo ten zmieniłby ją drugi raz i pokazał swój pasek.
zmiana("Scenes.java", "wskaźnik głośności: zapis opcji",
       '    public static void setModPlDockFocus(Context c, int tryb) {\n'
       '        prefs(c).edit().putInt("mod_pl_fokus_doku", tryb).apply();\n'
       '    }\n',
       '    public static void setModPlDockFocus(Context c, int tryb) {\n'
       '        prefs(c).edit().putInt("mod_pl_fokus_doku", tryb).apply();\n'
       '    }\n'
       '\n'
       '    /** Mod PL: własny wskaźnik głośności zamiast paska systemowego. Do wyboru użytkownika pasek systemowy. */\n'
       '    public static boolean isModPlVolumeIndicator(Context c) {\n'
       '        return prefs(c).getBoolean("mod_pl_wskaznik_glosnosci", false);\n'
       '    }\n'
       '\n'
       '    /** Mod PL: czy użytkownik wybrał już wskaźnik (kreator, pytanie po aktualizacji albo ustawienia). */\n'
       '    public static boolean isModPlVolumeIndicatorChosen(Context c) {\n'
       '        return prefs(c).contains("mod_pl_wskaznik_glosnosci");\n'
       '    }\n'
       '\n'
       '    public static void setModPlVolumeIndicator(Context c, boolean on) {\n'
       '        prefs(c).edit().putBoolean("mod_pl_wskaznik_glosnosci", on).apply();\n'
       '    }\n'
       '\n'
       '    /** Mod PL: krawędź zaznaczonej jasnej ikony: 0 jak w tvOS, 1 delikatna ciemna, 2 wyraźna ciemna. */\n'
       '    public static int getModPlBrightEdge(Context c) {\n'
       '        return prefs(c).getInt("mod_pl_krawedz_jasnych", 0);\n'
       '    }\n'
       '\n'
       '    public static void setModPlBrightEdge(Context c, int rodzaj) {\n'
       '        prefs(c).edit().putInt("mod_pl_krawedz_jasnych", rodzaj).apply();\n'
       '    }\n')

zmiana("SettingsActivity.java", "wskaźnik głośności: wiersz w ustawieniach",
       '                    ModPlFokus.nastepny(this);\n'
       '                    refreshAll();\n'
       '                });\n',
       '                    ModPlFokus.nastepny(this);\n'
       '                    refreshAll();\n'
       '                });\n'
       '        row(tr("Edge of bright icons", "Açık simge kenarı"),\n'
       '                () -> I18n.t(this, ModPlFokus.nazwaKrawedzi(this)),\n'
       '                () -> {\n'
       '                    ModPlFokus.nastepnaKrawedz(this);\n'
       '                    refreshAll();\n'
       '                });\n'
       '        row(tr("Volume indicator", "Ses göstergesi"),\n'
       '                () -> I18n.t(this, Scenes.isModPlVolumeIndicator(this) ? "Glass pill" : "System bar"),\n'
       '                () -> {\n'
       '                    Scenes.setModPlVolumeIndicator(this, !Scenes.isModPlVolumeIndicator(this));\n'
       '                    if (Scenes.isModPlVolumeIndicator(this)) ModPlGlosnosc.przyklad(this);\n'
       '                    else ModPlGlosnosc.przywrocPasek(this);\n'
       '                    refreshAll();\n'
       '                });\n')

# Wybór wskaźnika (prośba użytkownika 25.11.2026: „zrób tak że mogą wybrać i przy pierwszej instalacji i przy
# aktualizacji”): krok kreatora przed „Gotowe” (tylko tam, gdzie pigułka może działać; numery kroków 5–7, do których
# odwołuje się kreator, się nie zmieniają) i jednorazowe pytanie przy powrocie na ekran główny.
zmiana("SetupActivity.java", "wskaźnik głośności: krok kreatora przed „Gotowe”",
       '        steps.add(this::buttonMapperSetup);\n'
       '        steps.add(this::finish_);\n',
       '        steps.add(this::buttonMapperSetup);\n'
       '        if (ModPlGlosnosc.mozliwyNaUrzadzeniu(this)) steps.add(this::modPlGlosnosc);   // Mod PL: wybór wskaźnika głośności\n'
       '        steps.add(this::finish_);\n')

zmiana("SetupActivity.java", "wskaźnik głośności: strona kroku kreatora",
       '    void finish_() {\n',
       '    /** Mod PL: krok „Wskaźnik głośności” (szklana pigułka albo pasek systemowy); treść w ModPlGlosnosc. */\n'
       '    void modPlGlosnosc() {\n'
       '        ModPlGlosnosc.krokKreatora(this);\n'
       '    }\n'
       '\n'
       '    void finish_() {\n')

zmiana("MainActivity.java", "wskaźnik głośności: pytanie po aktualizacji przy powrocie na ekran główny",
       '        ModPlWygaszacz.wlaczJesliMozna(this);\n',
       '        ModPlWygaszacz.wlaczJesliMozna(this);\n'
       '        ModPlGlosnosc.zapytajPoAktualizacji(this);\n')

zmiana("MainActivity.java", "wskaźnik głośności: hak ADB do zrzutów pytania (--es panel glosnosc)",
       '            else if (panel.equals("search")) showSearch(intent.getStringExtra("q"));\n',
       '            else if (panel.equals("search")) showSearch(intent.getStringExtra("q"));\n'
       '            else if (panel.equals("glosnosc")) ModPlOknaTv.pytanieGlosnosci(this);   // Mod PL: pytanie o wskaźnik\n')

# Obserwator zdarzeń AudioService oryginału widzi każdy klawisz głośności obsłużony przez system, także na krańcach
# skali, gdzie poziom się nie zmienia i nie przychodzi rozgłoszenie zmiany: wtedy też pokazujemy pigułkę.
zmiana("PowerService.java", "wskaźnik głośności: krańce skali także przy obserwatorze getevent (stałe ADB)",
       '                    (dir, down, isRepeat) -> {\n',
       '                    (dir, down, isRepeat) -> {\n'
       '                        if (down && !isRepeat) ModPlGlosnosc.poKlawiszuSystemu(dir);   // Mod PL\n')

zmiana("PowerService.java", "wskaźnik głośności: pigułka także na krańcach skali",
       '                    } else if (!lastWmLine.equals(prevLastWmLine)) {\n',
       '                    } else if (!lastWmLine.equals(prevLastWmLine)) {\n'
       '                        ModPlGlosnosc.poKlawiszuSystemu(lastWmLine.contains("dir:ADJUST_RAISE") ? 1\n'
       '                                : lastWmLine.contains("dir:ADJUST_LOWER") ? -1 : 0);   // Mod PL: pigułka także przy zerze i maksimum\n')

# Bez usługi dostępności nikt nie pokaże pigułki: przy jej zatrzymaniu pasek głośności wraca do systemu.
zmiana("PowerService.java", "wskaźnik głośności: pasek systemu wraca przy zatrzymaniu usługi",
       '        stopAudioDumpVolumeWatcher();\n'
       '        if (instance == this) instance = null;\n'
       '        return super.onUnbind(intent);\n',
       '        stopAudioDumpVolumeWatcher();\n'
       '        if (instance == this) instance = null;\n'
       '        ModPlGlosnosc.poZatrzymaniuUslugi(this);   // Mod PL\n'
       '        return super.onUnbind(intent);\n')

# Na Androidzie TV klawisze głośności nie docierają do usługi dostępności, więc przy starcie usługi odłączamy pasek
# systemu przez ADB launchera i słuchamy rozgłoszeń zmiany głośności (zob. ModPlGlosnosc.start).
zmiana("PowerService.java", "wskaźnik głośności: start razem z usługą",
       '        instance = this;\n'
       '        serviceConnectedElapsedMs = SystemClock.elapsedRealtime();\n',
       '        instance = this;\n'
       '        serviceConnectedElapsedMs = SystemClock.elapsedRealtime();\n'
       '        ModPlGlosnosc.start(this);   // Mod PL: własny wskaźnik głośności\n')

zmiana("PowerService.java", "wskaźnik głośności: zmiana bez paska systemowego",
       'AudioManager.FLAG_SHOW_UI);',
       'ModPlGlosnosc.flaga(this));', ile=3)

zmiana("PowerService.java", "wskaźnik głośności: pokazanie po każdym kroku",
       '            } catch (Throwable ignored) {\n'
       '            }\n'
       '        }\n'
       '    }\n'
       '\n'
       '    private final Runnable volRepeatRunnable = new Runnable() {\n',
       '            } catch (Throwable ignored) {\n'
       '            }\n'
       '        }\n'
       '        ModPlGlosnosc.poKroku(this, dir);   // Mod PL: własny wskaźnik głośności\n'
       '    }\n'
       '\n'
       '    private final Runnable volRepeatRunnable = new Runnable() {\n')

# Poprawka przytrzymania oryginału zostawia pierwszy krok systemowi („return false”), a sama robi tylko powtórzenia.
# Z własnym wskaźnikiem klawisz nie trafia do systemu, więc pierwszy krok robimy tutaj. Gdy poprawka jest wyłączona,
# kroki i powtarzanie przy przytrzymaniu robi ModPlGlosnosc.klawisz (usługa nie dostaje powtórzeń klawisza).
zmiana("PowerService.java", "wskaźnik głośności: klawisze głośności i wyciszenia",
       '                onVolumeKeyState(dir, false, false);\n'
       '            }\n'
       '            return false;\n'
       '        }\n'
       '        if (ButtonMapper.handleKeyEvent(this, event)) {\n',
       '                onVolumeKeyState(dir, false, false);\n'
       '            }\n'
       '            if (ModPlGlosnosc.przyKlawiszu(this)) {   // Mod PL: system nie dostaje klawisza, pierwszy krok robimy sami\n'
       '                if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) stepVolume(dir);\n'
       '                return true;\n'
       '            }\n'
       '            return false;\n'
       '        }\n'
       '        if ((code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN\n'
       '                || code == KeyEvent.KEYCODE_VOLUME_MUTE)\n'
       '                && ModPlGlosnosc.przyKlawiszu(this) && !ButtonMapper.isKeyCustomMapped(this, code)) {\n'
       '            // Mod PL: własny wskaźnik głośności, gdy poprawka przytrzymania jest wyłączona, oraz klawisz wyciszenia\n'
       '            ModPlGlosnosc.klawisz(this, event, LocalAdb.volWatchConnected && ButtonMapper.isVolumeHoldFixEnabled(this));\n'
       '            return true;\n'
       '        }\n'
       '        if (ButtonMapper.handleKeyEvent(this, event)) {\n')

# ---------------------------------------------------------------- listy jak w tvOS 27 (ModPlListy)
# Wybór użytkownika z 24.11.2026 (kanwa „Zaznaczenie w listach”, wariant A): wiersze ustawień i kreatora jako
# pigułki (pełne zaokrąglenie), zaznaczony biały jak dotąd, niezaznaczone z krawędzią szkła, zaznaczony wiersz
# ustawień lekko rośnie. Wszystkie wiersze ustawień budują tło tak samo, stąd jedna zmiana na 13 miejsc.
zmiana("SettingsActivity.java", "listy tvOS 27: wiersze ustawień jako pigułki",
       '        GradientDrawable bg = new GradientDrawable();\n'
       '        bg.setCornerRadius(u(13));\n',
       '        GradientDrawable bg = ModPlListy.wiersz(this);   // Mod PL: pigułka jak w tvOS 27\n', ile=13)

zmiana("SettingsActivity.java", "listy tvOS 27: boczne menu jako pigułki",
       '            GradientDrawable pillBg = new GradientDrawable();\n'
       '            pillBg.setCornerRadius(u(12));\n',
       '            GradientDrawable pillBg = ModPlListy.pigulka(this);   // Mod PL: pigułka jak w tvOS 27\n')

zmiana("SettingsActivity.java", "listy tvOS 27: okrągłe tło ikon bocznego menu",
       '            GradientDrawable iconBg = new GradientDrawable();\n'
       '            iconBg.setCornerRadius(iconRad);\n',
       '            GradientDrawable iconBg = ModPlListy.pigulka(this);   // Mod PL: koło (pigułka z kwadratu)\n')

zmiana("SettingsActivity.java", "listy tvOS 27: podpowiedź klawiszy jako pigułka",
       '        GradientDrawable hintBg = new GradientDrawable();\n'
       '        hintBg.setCornerRadius(u(12));\n',
       '        GradientDrawable hintBg = ModPlListy.pigulka(this);   // Mod PL: pigułka jak w tvOS 27\n')

zmiana("SetupActivity.java", "listy tvOS 27: wiersze kreatora jako pigułki",
       '        GradientDrawable bg = new GradientDrawable();\n'
       '        bg.setCornerRadius(u(12));\n'
       '        bg.setColor(0x26FFFFFF);\n',
       '        GradientDrawable bg = ModPlListy.pigulka(this);   // Mod PL: pigułka jak w tvOS 27\n'
       '        bg.setColor(0x26FFFFFF);\n')

# ---------------------------------------------------------------- centrum sterowania jak w tvOS 27 (ModPlCentrum)
# Układ do wyboru w ustawieniach (Centrum sterowania → Układ): siatka oryginału, tvOS 27 w wymiarach Apple,
# tvOS 27 w większych wymiarach oryginału. Opis układu i pomiary: pl/java/ModPlCentrum.java.
zmiana("Scenes.java", "centrum sterowania: ustawienie układu",
       '    public static void setModPlBrightEdge(Context c, int rodzaj) {\n'
       '        prefs(c).edit().putInt("mod_pl_krawedz_jasnych", rodzaj).apply();\n'
       '    }\n',
       '    public static void setModPlBrightEdge(Context c, int rodzaj) {\n'
       '        prefs(c).edit().putInt("mod_pl_krawedz_jasnych", rodzaj).apply();\n'
       '    }\n'
       '\n'
       '    /** Mod PL: układ centrum sterowania: 0 siatka oryginału, 1 tvOS 27, 2 tvOS 27 większy (domyślny, wybór użytkownika 24.11.2026). */\n'
       '    public static int getModPlCcLayout(Context c) {\n'
       '        return prefs(c).getInt("mod_pl_uklad_centrum", 2);\n'
       '    }\n'
       '\n'
       '    public static void setModPlCcLayout(Context c, int uklad) {\n'
       '        prefs(c).edit().putInt("mod_pl_uklad_centrum", uklad).apply();\n'
       '    }\n')

zmiana("SettingsActivity.java", "centrum sterowania: wiersz układu w ustawieniach",
       '    void pageControlPanel() {\n'
       '        section(tr("BACKGROUND MODE", "ARKA PLAN MODU"));\n',
       '    void pageControlPanel() {\n'
       '        section(tr("LAYOUT", "DÜZEN"));   // Mod PL\n'
       '        row(tr("Control Center layout", "Denetim Merkezi düzeni"),\n'
       '                () -> I18n.t(this, ModPlCentrum.nazwa(this)),\n'
       '                () -> {\n'
       '                    ModPlCentrum.nastepny(this);\n'
       '                    refreshAll();\n'
       '                });\n'
       '        section(tr("BACKGROUND MODE", "ARKA PLAN MODU"));\n')

zmiana("ControlPanelOverlay.java", "centrum sterowania: wymiary kapsuł jak w tvOS",
       '        int col = dp(unit, 141);\n'
       '        int gap = dp(unit, 11);\n'
       '        int cap = dp(unit, 56);\n'
       '        int small = (2 * col + gap - 3 * gap) / 4;\n',
       '        int col = ModPlCentrum.wymiar(c, unit, 141f, 102.5f);   // Mod PL: w układzie tvOS 27 wymiary Apple\n'
       '        int gap = ModPlCentrum.wymiar(c, unit, 11f, 8.5f);\n'
       '        int cap = ModPlCentrum.wymiar(c, unit, 56f, 47f);\n'
       '        int small = (2 * col + gap - 3 * gap) / 4;\n')

zmiana("ControlPanelOverlay.java", "centrum sterowania: duży kafel zasilania i kolejność kapsuł jak w tvOS",
       '        for (int i = 0; i < capsules.size(); i++) {\n'
       '            CapsuleSpec cs = capsules.get(i);\n'
       '            int colIdx = i % 2;\n'
       '            int rowIdx = i / 2;\n'
       '            int x = pad + colIdx * (col + gap);\n'
       '            int y = pad + rowIdx * (cap + gap);\n'
       '            View tile = makeTile(c, panel, x, y, col, cap, unit, screenW, screenH, glassSource,\n'
       '                    cs.glyph, cs.title, cs.status, cs.click);\n'
       '            if (cs.isMemory) {\n'
       '                attachMemoryBar(c, tile, unit);\n'
       '            }\n'
       '            outCells.add(tile);\n'
       '        }\n'
       '\n'
       '        int capsuleRows = (capsules.size() + 1) / 2;\n',
       '        // Mod PL: układ tvOS 27 (duży kafel zasilania po lewej, kapsuły w kolejności Apple)\n'
       '        boolean modTv = ModPlCentrum.uklad(c) != ModPlCentrum.ORYGINAL;\n'
       '        int modZasilanie = -1;\n'
       '        if (modTv) {\n'
       '            java.util.Collections.sort(capsules,\n'
       '                    (a, b) -> ModPlCentrum.kolejnosc(a.glyph) - ModPlCentrum.kolejnosc(b.glyph));\n'
       '            for (int i = 0; i < capsules.size(); i++) {\n'
       '                if ("power".equals(capsules.get(i).glyph)) modZasilanie = i;\n'
       '            }\n'
       '        }\n'
       '        int modInne = capsules.size() - (modZasilanie >= 0 ? 1 : 0);\n'
       '        int modNr = 0;\n'
       '        for (int i = 0; i < capsules.size(); i++) {\n'
       '            CapsuleSpec cs = capsules.get(i);\n'
       '            int colIdx = i % 2;\n'
       '            int rowIdx = i / 2;\n'
       '            int x = pad + colIdx * (col + gap);\n'
       '            int y = pad + rowIdx * (cap + gap);\n'
       '            int tw = col, th = cap;\n'
       '            if (modTv) {\n'
       '                int[] m = ModPlCentrum.miejsce(i == modZasilanie, modZasilanie >= 0, modNr, modInne, col, cap, gap);\n'
       '                if (i != modZasilanie) modNr++;\n'
       '                x = pad + m[0];\n'
       '                y = pad + m[1];\n'
       '                tw = m[2];\n'
       '                th = m[3];\n'
       '            }\n'
       '            View tile = makeTile(c, panel, x, y, tw, th, unit, screenW, screenH, glassSource,\n'
       '                    cs.glyph, cs.title, cs.status, cs.click);\n'
       '            if (cs.isMemory) {\n'
       '                attachMemoryBar(c, tile, unit);\n'
       '            }\n'
       '            outCells.add(tile);\n'
       '        }\n'
       '\n'
       '        int capsuleRows = modTv ? ModPlCentrum.rzedy(modZasilanie >= 0, modInne) : (capsules.size() + 1) / 2;\n')

zmiana("ControlPanelOverlay.java", "centrum sterowania: zaokrąglenia kafli jak w tvOS",
       '        float radius = iconOnly ? dp(unit, 16) : h / 2f;\n',
       '        float radius = ModPlCentrum.promien(c, iconOnly ? dp(unit, 16) : h / 2f, w, h, iconOnly);   // Mod PL\n')

zmiana("ControlPanelOverlay.java", "centrum sterowania: rozmiar ikon jak w tvOS",
       '        int gs = iconOnly ? dp(unit, 26) : dp(unit, 25);\n',
       '        int gs = ModPlCentrum.ikona(c, iconOnly ? dp(unit, 26) : dp(unit, 25), w, h, unit, iconOnly);   // Mod PL\n')

zmiana("ControlPanelOverlay.java", "centrum sterowania: białe ikony zasilania i wygaszacza w układzie tvOS",
       '        int baseIconColor = ("wifi".equals(glyph) || "bluetooth".equals(glyph)) ? 0xFF0A84FF\n'
       '                : "ambient".equals(glyph) ? 0xFFFFD60A\n'
       '                : "power".equals(glyph) ? 0xFFFF453A\n'
       '                : Color.WHITE;\n',
       '        int baseIconColor = ModPlCentrum.kolorIkony(c, glyph,\n'
       '                ("wifi".equals(glyph) || "bluetooth".equals(glyph)) ? 0xFF0A84FF\n'
       '                : "ambient".equals(glyph) ? 0xFFFFD60A\n'
       '                : "power".equals(glyph) ? 0xFFFF453A\n'
       '                : Color.WHITE, Color.WHITE);   // Mod PL\n')

zmiana("ControlPanelOverlay.java", "centrum sterowania: ciemne ikony zasilania i wygaszacza w zaznaczeniu",
       '        final int focusIconColor = ("wifi".equals(glyph) || "bluetooth".equals(glyph)) ? 0xFF0A84FF\n'
       '                : "ambient".equals(glyph) ? 0xFFD97706\n'
       '                : "power".equals(glyph) ? 0xFFD7372A\n'
       '                : 0xFF111114;\n',
       '        final int focusIconColor = ModPlCentrum.kolorIkony(c, glyph,\n'
       '                ("wifi".equals(glyph) || "bluetooth".equals(glyph)) ? 0xFF0A84FF\n'
       '                : "ambient".equals(glyph) ? 0xFFD97706\n'
       '                : "power".equals(glyph) ? 0xFFD7372A\n'
       '                : 0xFF111114, 0xFF111114);   // Mod PL\n')

zmiana("ControlPanelOverlay.java", "centrum sterowania: nazwy w dwóch liniach i duży kafel zasilania",
       '        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);\n'
       '        lp.leftMargin = x;\n'
       '        lp.topMargin = y;\n'
       '        panel.addView(t, lp);\n'
       '        return t;\n',
       '        ModPlCentrum.dopasuj(c, t, gi, titleRef[0], subRef, w, h, unit, gs);   // Mod PL\n'
       '        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);\n'
       '        lp.leftMargin = x;\n'
       '        lp.topMargin = y;\n'
       '        panel.addView(t, lp);\n'
       '        return t;\n')

zmiana("ControlPanelOverlay.java", "centrum sterowania: róg kafla pogody jak dużego kafla",
       '        float radius = dp(unit, 18);\n',
       '        float radius = ModPlCentrum.promienPogody(c, dp(unit, 18), unit, h);   // Mod PL\n')

zmiana("ControlPanelOverlay.java", "centrum sterowania: pasek pamięci w węższej kapsule",
       '        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(unit, 78), dp(unit, 4));\n',
       '        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ModPlCentrum.pasekPamieci(c, unit, 78f), dp(unit, 4));   // Mod PL\n')

zmiana("ControlPanelOverlay.java", "centrum sterowania: wyższy panel mieści się na ekranie także nad aplikacjami",
       '        plp.topMargin = dp(unit, 58);\n'
       '        plp.rightMargin = dp(unit, 24);\n',
       '        plp.topMargin = ModPlCentrum.gora(dp(unit, 58), plp.height, screenH, unit);   // Mod PL\n'
       '        plp.rightMargin = dp(unit, 24);\n')

# W tvOS centrum sterowania leży na widocznej treści, bez rozmywania ekranu: domyślne tło to „Przezroczyste”
# (wybór użytkownika 24.11.2026 razem z układem A); kto przełączył tło sam, zachowuje swoje ustawienie.
zmiana("ControlPanelOverlay.java", "centrum sterowania: domyślnie tło przezroczyste jak w tvOS",
       '        String m = cpPrefs(c).getString("cp_bg_mode", BG_MODE_BLURRY);\n',
       '        String m = cpPrefs(c).getString("cp_bg_mode", BG_MODE_TRANSPARENT);   // Mod PL\n')

zmiana("MainActivity.java", "centrum sterowania: wyższy panel mieści się na ekranie głównym",
       '        plp.topMargin = dp(58);\n'
       '        plp.rightMargin = dockOuter;\n',
       '        plp.topMargin = ModPlCentrum.gora(dp(58), plp.height, screenH, unit);   // Mod PL\n'
       '        plp.rightMargin = dockOuter;\n')

PLIKI_MODA = ["ModPlOkna.java", "ModPlOknaTv.java", "ModPlFokus.java", "ModPlGlosnosc.java", "ModPlPowloka.java", "ModPlEkstraklasa.java", "ModPlMecze.java", "ModPlAdb.java", "ModPlKafelki.java", "ModPlGole.java",
              "ModPlWygaszacz.java", "ModPlTapety.java", "ModPlDetektor.java", "ModPlDziennik.java", "ModPlListy.java", "ModPlCentrum.java"]
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
        # Pusta nowa treść (łatka usuwająca) jest „w” każdym tekście, więc nie świadczy o nałożeniu:
        # bez tego łatka usuwająca po zmianie oryginału przechodziłaby po cichu bez trafienia.
        if nowy and nowy in tekst and (stary not in tekst
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
