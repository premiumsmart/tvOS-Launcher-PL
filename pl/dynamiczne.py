# Wypisuje wywołania tłumaczeń, w których angielski tekst jest sklejany w locie
# (np. "Dock size set to " + pct + "%."), z pełnym wyrażeniem pierwszego argumentu.
import re, sys, glob, io, os

sys.stdout.reconfigure(encoding="utf-8")
root = sys.argv[1]


def argument(t, i):
    """Zwraca wyrażenie od pozycji i do przecinka/nawiasu na poziomie 0."""
    depth = 0
    j = i
    in_str = False
    while j < len(t):
        ch = t[j]
        if in_str:
            if ch == '\\':
                j += 2
                continue
            if ch == '"':
                in_str = False
        else:
            if ch == '"':
                in_str = True
            elif ch in '([{':
                depth += 1
            elif ch in ')]}':
                if depth == 0:
                    break
                depth -= 1
            elif ch == ',' and depth == 0:
                break
        j += 1
    return t[i:j]


wzorzec = re.compile(r'(?:I18n\.[st]|(?<![A-Za-z0-9_.])tr)\(\s*')
widziane = set()
for f in sorted(glob.glob(root + "/src/com/techgenius/tvoslauncher/**/*.java", recursive=True)):
    if f.endswith("I18n.java"):
        continue
    t = io.open(f, encoding="utf-8").read()
    for m in wzorzec.finditer(t):
        i = m.end()
        a1 = argument(t, i)
        # pierwszy argument bywa kontekstem (c, this, host...) - wtedy bierz drugi
        if not a1.lstrip().startswith('"'):
            i2 = i + len(a1) + 1
            a1 = argument(t, i2)
        a1 = " ".join(a1.split())
        if not a1.startswith('"'):
            continue
        if '+' not in re.sub(r'"(?:[^"\\]|\\.)*"', '""', a1):
            continue
        if a1 in widziane:
            continue
        widziane.add(a1)
        nr = t.count("\n", 0, m.start()) + 1
        print("%s:%d  %s" % (os.path.basename(f), nr, a1))
print("razem:", len(widziane))
