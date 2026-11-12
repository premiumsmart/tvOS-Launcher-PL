# Zbiera wszystkie angielskie klucze tekstów interfejsu tvOS Launcher:
# z wywołań I18n.s / I18n.t w kodzie oraz z map tłumaczeń w I18n.java.
import re, sys, glob, io

sys.stdout.reconfigure(encoding="utf-8")
root = sys.argv[1]
wyjscie = sys.argv[2]

STR = r'"((?:[^"\\]|\\.)*)"'

keys_s, keys_t, keys_map = set(), set(), {}
pliki = glob.glob(root + "/src/com/techgenius/tvoslauncher/**/*.java", recursive=True)
dyn = 0
for f in pliki:
    t = io.open(f, encoding="utf-8").read()
    for m in re.finditer(r'I18n\.s\(\s*[A-Za-z_.()]+\s*,\s*' + STR + r'\s*([,+)])', t):
        if m.group(2) == '+':
            dyn += 1
        else:
            keys_s.add(m.group(1))
    for m in re.finditer(r'I18n\.t\(\s*[A-Za-z_.()]+\s*,\s*' + STR + r'\s*([,+)])', t):
        if m.group(2) == '+':
            dyn += 1
        else:
            keys_t.add(m.group(1))
    # lokalne skróty: tr("en", "tr") oraz tr(ctx, "en", "tr")
    for m in re.finditer(r'(?<![A-Za-z0-9_.])tr\(\s*(?:[A-Za-z_.()]+\s*,\s*)?' + STR + r'\s*([,+)])', t):
        if m.group(2) == '+':
            dyn += 1
        else:
            keys_s.add(m.group(1))

i18n = io.open(root + "/src/com/techgenius/tvoslauncher/I18n.java", encoding="utf-8").read()
for name, pat in (("TR_MAP", r'TR_MAP\.put\(\s*' + STR), ("EN_MAP", r'EN_MAP\.put\(\s*' + STR),
                  ("add", r'\badd\(\s*' + STR), ("sv", r'\bsv\(\s*' + STR),
                  ("ko", r'\bko\(\s*' + STR), ("az", r'\baz\(\s*' + STR)):
    keys_map[name] = set(m.group(1) for m in re.finditer(pat, i18n))

print("wywołania I18n.s (stałe klucze):", len(keys_s))
print("wywołania I18n.t (stałe klucze):", len(keys_t))
for k, v in keys_map.items():
    print("mapa " + k + ":", len(v))

uzywane = keys_s | keys_t
wszystkie = set(uzywane)
for v in keys_map.values():
    wszystkie |= v
print("RAZEM unikalnych kluczy:", len(wszystkie))
print("używane w kodzie, brak w koreańskim:", len(uzywane - keys_map["ko"]))
print("używane w kodzie, brak w szwedzkim:", len(uzywane - keys_map["sv"]))
print("wywołania ze sklejanym napisem (dynamiczne):", dyn)

io.open(wyjscie, "w", encoding="utf-8", newline="\n").write("\n".join(sorted(wszystkie)) + "\n")
io.open(wyjscie + ".uzywane", "w", encoding="utf-8", newline="\n").write("\n".join(sorted(uzywane)) + "\n")
