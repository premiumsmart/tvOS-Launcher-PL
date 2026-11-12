#!/bin/bash
# Buduje spolszczoną wersję tvOS Launcher (mod PL) na Windowsie w Git Bash, bez Gradle.
# Użycie:  bash zbuduj-pl.sh               (na box użytkownika: z dziennikiem moda w logcat)
#          DZIENNIK=0 bash zbuduj-pl.sh    (instalka dla innych: bez dziennika moda, zob. pl/java/ModPlDziennik.java)
# Wynik:   build/tvos-launcher-pl.apk  (podpisany lokalnym kluczem moda: mod-pl.keystore,
#          hasło w pliku mod-pl.keystore.haslo; oba pliki są poza repozytorium)
#
# Różnice wobec oryginalnego build.sh:
#  - d8 z build-tools 35.0.0 (wersja z 34.0.0 wywala się na klasach skompilowanych JDK 21),
#  - klasy pakowane do jar przed d8 (lista plików z "$" w nazwach psuła wywołanie),
#  - classes.dex dopisywany do APK Pythonem (Git Bash nie ma polecenia zip),
#  - własny, trwały klucz podpisu moda (ten sam przy każdej kolejnej wersji = aktualizacja bez utraty ustawień).
set -euo pipefail
cd "$(dirname "$0")"

# Systemowe JAVA_HOME wskazuje na samo JRE (bez javac), więc bierzemy JDK z Android Studio,
# chyba że podane JAVA_HOME naprawdę ma kompilator.
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ] && [ ! -x "$JAVA_HOME/bin/javac.exe" ]; then
    JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_HOME:-/c/Users/Oskar/AppData/Local/Android/Sdk}"
BT="$SDK/build-tools/34.0.0"
BT_D8="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-34/android.jar"
WERSJA_KOD="${VERSION_CODE:-44}"
WERSJA_NAZWA="${VERSION_NAME:-2.1.5-pl30}"

for f in "$BT/aapt2.exe" "$BT/zipalign.exe" "$BT_D8/lib/d8.jar" "$JAR"; do
    [ -e "$f" ] || { echo "Brak: $f" >&2; exit 1; }
done

rm -rf build && mkdir -p build/gen build/classes build/dex

echo "[1/6] zasoby (aapt2)"
echo "== Tłumaczenia i zmiany moda PL (od czystych źródeł z gita - ręczne zmiany w src/ przepadają, wszystkie zmiany idą przez pl/naloz.py)"
git checkout -- src AndroidManifest.xml
python -I pl/naloz.py
python -I pl/generuj.py
# zasoby moda (np. dołączona ulubiona tapeta) trafiają do assets/mod_pl
mkdir -p assets/mod_pl
cp pl/zasoby/* assets/mod_pl/

"$BT/aapt2.exe" compile --dir res -o build/res.zip
"$BT/aapt2.exe" link -o build/unsigned.apk -I "$JAR" --manifest AndroidManifest.xml \
    -A assets --java build/gen --min-sdk-version 24 --target-sdk-version 34 \
    --version-code "$WERSJA_KOD" --version-name "$WERSJA_NAZWA" build/res.zip

echo "[2/6] kompilacja Javy"
find src build/gen -name '*.java' > build/sources.txt
# biblioteki moda (TensorFlow Lite do wykrywania aut na tapetach): javac.exe chce ścieżek Windows rozdzielonych średnikiem
BIBLIOTEKI=""
for b in pl/biblioteki/*.jar; do BIBLIOTEKI="$BIBLIOTEKI;$(cygpath -w "$b")"; done
javac -encoding UTF-8 --release 11 -nowarn -classpath "$(cygpath -w "$JAR")$BIBLIOTEKI" -d build/classes @build/sources.txt

echo "[3/6] dex (d8 35.0.0)"
( cd build/classes && jar cf ../classes.jar . )
java -cp "$BT_D8/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 24 --lib "$JAR" \
    --output build/dex build/classes.jar pl/biblioteki/*.jar

echo "[4/6] pakowanie"
python -I -c "
import os, zipfile
z = zipfile.ZipFile('build/unsigned.apk', 'a', zipfile.ZIP_DEFLATED)
z.write('build/dex/classes.dex', 'classes.dex')
# biblioteki natywne moda: lib/<procesor>/*.so
for abi in sorted(os.listdir('pl/biblioteki/lib')):
    for n in sorted(os.listdir('pl/biblioteki/lib/' + abi)):
        z.write('pl/biblioteki/lib/%s/%s' % (abi, n), 'lib/%s/%s' % (abi, n))
z.close()
"
"$BT/zipalign.exe" -f 4 build/unsigned.apk build/aligned.apk

echo "[5/6] podpis"
KS=mod-pl.keystore
# Hasło klucza leży w osobnym pliku obok klucza (jedna linia, bez końca linii). Oba pliki są poza
# repozytorium (.gitignore), więc hasło nie trafia do kodu. Nowy klucz dostaje losowe hasło.
HASLO=mod-pl.keystore.haslo
if [ ! -f "$KS" ]; then
    if [ ! -f "$HASLO" ]; then
        printf '%s' "$(head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 32)" > "$HASLO"
    fi
    keytool -genkeypair -keystore "$KS" -storepass:file "$HASLO" -keypass:file "$HASLO" -alias modpl \
        -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=tvOS Launcher PL mod" >/dev/null 2>&1
    echo "      utworzono nowy klucz moda: $KS (hasło w $HASLO)"
elif [ ! -s "$HASLO" ]; then
    echo "Brak pliku $HASLO z hasłem do $KS (jedna linia z hasłem, obok klucza)." >&2
    exit 1
fi
java -jar "$BT_D8/lib/apksigner.jar" sign --ks "$KS" --ks-pass "file:$HASLO" \
    --ks-key-alias modpl --out build/tvos-launcher-pl.apk build/aligned.apk

echo "[6/6] weryfikacja"
java -jar "$BT_D8/lib/apksigner.jar" verify --min-sdk-version 24 build/tvos-launcher-pl.apk
ls -l build/tvos-launcher-pl.apk | awk '{printf "Zbudowano build/tvos-launcher-pl.apk (%.2f MB)\n", $5/1048576}'
