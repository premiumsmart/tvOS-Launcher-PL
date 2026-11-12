# Generuje src/com/techgenius/tvoslauncher/I18nPl.java z plików tłumaczeń w tym folderze.
#
#   en.txt             NNNN|angielski klucz (surowy literał Javy, z sekwencjami \n, \uXXXX)
#   pl_1.txt..pl_N.txt NNNN|polski tekst (te same sekwencje)
#   pl_dodatkowe.txt   pary E|/P| dla tekstów stałych spoza en.txt (opcjonalny)
#   pl_dynamiczne.txt  pary E|/P| z dziurami {1}..{9} dla napisów sklejanych w locie
#
# Uruchomienie:  python -I pl/generuj.py      (z folderu projektu albo skądkolwiek)
import glob
import io
import os
import re
import sys

sys.stdout.reconfigure(encoding="utf-8")

TU = os.path.dirname(os.path.abspath(__file__))
WYJSCIE = os.path.join(TU, "..", "src", "com", "techgenius", "tvoslauncher", "I18nPl.java")

POPRAWNE_ESCAPE = re.compile(r'\\(?:[ntrbf0"\'\\]|u[0-9a-fA-F]{4})')
ZNACZNIK_FORMATU = re.compile(r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]')
DZIURA = re.compile(r'\{(\d)\}')

bledy = []
ostrzezenia = []


def czytaj_numerowane(sciezka):
    """Zwraca słownik numer -> tekst dla pliku w formacie NNNN|tekst."""
    wynik = {}
    for nr_linii, linia in enumerate(io.open(sciezka, encoding="utf-8").read().split("\n"), 1):
        if not linia.strip() or linia.startswith("#"):
            continue
        if "|" not in linia:
            bledy.append("%s:%d brak znaku | w linii" % (os.path.basename(sciezka), nr_linii))
            continue
        nr, tekst = linia.split("|", 1)
        if nr in wynik:
            bledy.append("%s:%d powtórzony numer %s" % (os.path.basename(sciezka), nr_linii, nr))
        wynik[nr] = tekst
    return wynik


def czytaj_pary(sciezka):
    """Zwraca listę par (angielski, polski) z pliku w formacie E|... / P|..."""
    pary = []
    en = None
    if not os.path.exists(sciezka):
        return pary
    for nr_linii, linia in enumerate(io.open(sciezka, encoding="utf-8").read().split("\n"), 1):
        if not linia.strip() or linia.startswith("#"):
            continue
        if linia.startswith("E|"):
            if en is not None:
                bledy.append("%s:%d dwie linie E| pod rząd" % (os.path.basename(sciezka), nr_linii))
            en = linia[2:]
        elif linia.startswith("P|"):
            if en is None:
                bledy.append("%s:%d linia P| bez E|" % (os.path.basename(sciezka), nr_linii))
                continue
            pary.append((en, linia[2:]))
            en = None
        else:
            bledy.append("%s:%d linia nie zaczyna się od E| ani P|" % (os.path.basename(sciezka), nr_linii))
    if en is not None:
        bledy.append("%s: ostatnia linia E| bez P|" % os.path.basename(sciezka))
    return pary


def literal(tekst, opis):
    """Zamienia surowy tekst na bezpieczną zawartość literału Javy (dopisuje \\ przed gołym ")."""
    wynik = []
    i = 0
    while i < len(tekst):
        ch = tekst[i]
        if ch == "\\":
            m = POPRAWNE_ESCAPE.match(tekst, i)
            if not m:
                bledy.append("%s: niepoprawna sekwencja z ukośnikiem: %r" % (opis, tekst[i:i + 6]))
                i += 1
                continue
            wynik.append(m.group(0))
            i = m.end()
            continue
        if ch == '"':
            wynik.append('\\"')
        else:
            wynik.append(ch)
        i += 1
    return "".join(wynik)


def dopasuj_odstepy(en, pl):
    """Przenosi wiodące i końcowe spacje z klucza angielskiego na polski tekst."""
    przod = len(en) - len(en.lstrip(" "))
    tyl = len(en) - len(en.rstrip(" "))
    if en.strip(" ") == "":
        return en
    return " " * przod + pl.strip(" ") + " " * tyl


def sprawdz_zgodnosc(opis, en, pl):
    if en.count("\\n") != pl.count("\\n"):
        ostrzezenia.append("%s: inna liczba nowych linii (EN %d, PL %d)" % (opis, en.count("\\n"), pl.count("\\n")))
    f_en = sorted(ZNACZNIK_FORMATU.findall(en))
    f_pl = sorted(ZNACZNIK_FORMATU.findall(pl))
    if f_en != f_pl:
        # znak procentu po liczbie w zwykłym tekście nie jest znacznikiem formatu
        if any(z not in ("% ", "%") for z in f_en + f_pl):
            ostrzezenia.append("%s: różne znaczniki formatu (EN %s, PL %s)" % (opis, f_en, f_pl))


# --- teksty stałe ---
en = czytaj_numerowane(os.path.join(TU, "en.txt"))
pl = {}
for plik in sorted(glob.glob(os.path.join(TU, "pl_[0-9]*.txt"))):
    for nr, tekst in czytaj_numerowane(plik).items():
        if nr in pl:
            bledy.append("%s: numer %s jest już w innym pliku" % (os.path.basename(plik), nr))
        pl[nr] = tekst

for nr in sorted(en):
    if nr not in pl:
        bledy.append("brak tłumaczenia dla %s|%s" % (nr, en[nr][:50]))
for nr in sorted(pl):
    if nr not in en:
        bledy.append("tłumaczenie %s nie ma klucza w en.txt" % nr)

stale = []          # (literał EN, literał PL)
widziane = set()
bez_zmian = 0
for nr in sorted(en):
    if nr not in pl:
        continue
    e, p = en[nr], dopasuj_odstepy(en[nr], pl[nr])
    sprawdz_zgodnosc(nr, e, p)
    if e == p:
        bez_zmian += 1
    le, lp = literal(e, "en " + nr), literal(p, "pl " + nr)
    if le in widziane:
        bledy.append("powtórzony klucz angielski %s" % nr)
    widziane.add(le)
    stale.append((le, lp))

dodatkowe = czytaj_pary(os.path.join(TU, "pl_dodatkowe.txt"))
for i, (e, p) in enumerate(dodatkowe, 1):
    opis = "dodatkowe #%d" % i
    p = dopasuj_odstepy(e, p)
    sprawdz_zgodnosc(opis, e, p)
    le, lp = literal(e, opis), literal(p, opis)
    if le in widziane:
        ostrzezenia.append("%s: klucz jest już w en.txt, pomijam: %s" % (opis, e[:50]))
        continue
    widziane.add(le)
    stale.append((le, lp))

# --- szablony dynamiczne ---
szablony = []
for i, (e, p) in enumerate(czytaj_pary(os.path.join(TU, "pl_dynamiczne.txt")), 1):
    opis = "dynamiczne #%d" % i
    dz_en = DZIURA.findall(e)
    dz_pl = DZIURA.findall(p)
    if not dz_en:
        bledy.append("%s: szablon bez dziury {1}: %s" % (opis, e[:50]))
    if len(set(dz_en)) != len(dz_en):
        bledy.append("%s: ta sama dziura dwa razy w E|: %s" % (opis, e[:50]))
    for d in dz_pl:
        if d not in dz_en:
            bledy.append("%s: dziura {%s} jest w P|, a nie ma jej w E|" % (opis, d))
    if re.search(r'\}\{', e):
        bledy.append("%s: dwie dziury obok siebie w E|" % opis)
    szablony.append((literal(e, opis), literal(p, opis)))

if bledy:
    print("BŁĘDY (%d):" % len(bledy))
    for b in bledy:
        print("  " + b)
    sys.exit(1)

# --- zapis pliku Javy ---
NA_METODE = 200
czesci = [stale[i:i + NA_METODE] for i in range(0, len(stale), NA_METODE)]

j = []
j.append("package com.techgenius.tvoslauncher;")
j.append("")
j.append("import java.util.HashMap;")
j.append("import java.util.Map;")
j.append("import java.util.regex.Matcher;")
j.append("import java.util.regex.Pattern;")
j.append("")
j.append("/**")
j.append(" * Polskie tłumaczenie (mod PL).")
j.append(" * Plik GENEROWANY przez pl/generuj.py z plików pl/*.txt - nie poprawiaj go ręcznie.")
j.append(" */")
j.append("final class I18nPl {")
j.append("    private I18nPl() { }")
j.append("")
j.append("    /** Wpisuje do mapy wszystkie teksty stałe: klucz angielski -> tekst polski. */")
j.append("    static void fill(Map<String, String> m) {")
for n in range(len(czesci)):
    j.append("        fill%d(m);" % (n + 1))
j.append("    }")
for n, czesc in enumerate(czesci, 1):
    j.append("")
    j.append("    private static void fill%d(Map<String, String> m) {" % n)
    for le, lp in czesc:
        j.append('        m.put("%s", "%s");' % (le, lp))
    j.append("    }")
j.append("")
j.append("    /** Szablony napisów sklejanych w locie: {angielski, polski}, dziury {1}..{9}. */")
j.append("    private static final String[][] SZABLONY = {")
for le, lp in szablony:
    j.append('        {"%s", "%s"},' % (le, lp))
j.append("    };")
j.append("")
j.append(r'''    private static final Pattern DZIURA = Pattern.compile("\\{(\\d)\\}");
    private static final String BRAK = "\u0000";
    private static final int LIMIT_PAMIECI = 600;

    private static final class Wzorzec {
        String poczatek;   // tekst przed pierwszą dziurą (szybkie odrzucanie)
        String koniec;     // tekst po ostatniej dziurze
        Pattern wzor;
        int[] grupy;       // numer dziury -> numer grupy
        String pl;
    }

    private static Wzorzec[] wzorce;
    private static final Map<String, String> pamiec = new HashMap<>();

    private static Wzorzec zbuduj(String en, String pl) {
        Wzorzec w = new Wzorzec();
        w.grupy = new int[10];
        w.pl = pl;
        StringBuilder re = new StringBuilder();
        Matcher m = DZIURA.matcher(en);
        int od = 0;
        int grupa = 0;
        while (m.find()) {
            String lit = en.substring(od, m.start());
            if (grupa == 0) w.poczatek = lit;
            if (!lit.isEmpty()) re.append(Pattern.quote(lit));
            re.append("(.*?)");
            grupa++;
            w.grupy[m.group(1).charAt(0) - '0'] = grupa;
            od = m.end();
        }
        w.koniec = en.substring(od);
        if (w.poczatek == null) w.poczatek = w.koniec;
        if (!w.koniec.isEmpty()) re.append(Pattern.quote(w.koniec));
        w.wzor = Pattern.compile(re.toString(), Pattern.DOTALL);
        return w;
    }

    /**
     * Tłumaczy napis sklejony w locie (np. "Dock size set to 120%.").
     * Zwraca null, gdy żaden szablon nie pasuje.
     */
    static synchronized String dynamic(String en) {
        if (en == null || en.isEmpty()) return null;
        String znane = pamiec.get(en);
        if (znane != null) return znane == BRAK ? null : znane;
        if (wzorce == null) {
            Wzorzec[] w = new Wzorzec[SZABLONY.length];
            for (int i = 0; i < SZABLONY.length; i++) {
                w[i] = zbuduj(SZABLONY[i][0], SZABLONY[i][1]);
            }
            wzorce = w;
        }
        String wynik = null;
        for (Wzorzec w : wzorce) {
            if (!w.poczatek.isEmpty() && !en.startsWith(w.poczatek)) continue;
            if (!w.koniec.isEmpty() && !en.endsWith(w.koniec)) continue;
            Matcher m = w.wzor.matcher(en);
            if (!m.matches()) continue;
            StringBuilder sb = new StringBuilder();
            Matcher d = DZIURA.matcher(w.pl);
            int od = 0;
            while (d.find()) {
                sb.append(w.pl, od, d.start());
                int g = w.grupy[d.group(1).charAt(0) - '0'];
                if (g > 0 && m.group(g) != null) sb.append(m.group(g));
                od = d.end();
            }
            sb.append(w.pl, od, w.pl.length());
            wynik = sb.toString();
            break;
        }
        if (pamiec.size() >= LIMIT_PAMIECI) pamiec.clear();
        pamiec.put(en, wynik == null ? BRAK : wynik);
        return wynik;
    }
}''')

with io.open(WYJSCIE, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(j) + "\n")

print("Teksty stałe: %d (w tym dodatkowe: %d, identyczne z angielskim: %d)" % (len(stale), len(stale) - len(en), bez_zmian))
print("Szablony dynamiczne: %d" % len(szablony))
if ostrzezenia:
    print("OSTRZEŻENIA (%d):" % len(ostrzezenia))
    for o in ostrzezenia:
        print("  " + o)
print("Zapisano: " + os.path.normpath(WYJSCIE))
