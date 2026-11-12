# tvOS Launcher PL 2.1.5-pl30

Polska wersja [tvOS Launcher autorstwa techgenius1](https://github.com/techgenius1/tvOS-Launcher), oparta na wersji 2.1.5.

## Zmiany moda PL

- Pełne spolszczenie interfejsu, kreatora i pogody.
- Kafelki i okna Liquid Glass: mecze z LiveScore i wynikami na żywo, „Najczęściej oglądane” z JustWatch dla Polski oraz pogoda.
- Powiadomienia o golach ulubionych drużyn nad innymi aplikacjami.
- Włączanie uprawnienia „Oglądaj dalej” przez lokalne ADB.
- Nocne tapety z internetu: supercary, auta po tuningu i futurystyczne miasta; pomijanie zdjęć za dnia.
- Szklany zegar chowający się za elementami zdjęcia oraz wykrywanie aut na telewizorze przez TensorFlow Lite.
- Ulubione tapety wracające domyślnie co piątą zmianę.
- Wygaszacz z tymi samymi zdjęciami i zegarem.
- Zalecany tryb kreatora zachowuje debugowanie USB.
- Wersja wydaniowa ma wyłączony dziennik moda.

## Plik wydania

Załącznik do **GitHub Releases**, poza repozytorium źródłowym:

`tvOS Launcher PL 2.1.5-pl30.apk`

- Wersja w APK: `2.1.5-pl30`, kod wersji `44`.
- Android 7.0 lub nowszy (API 24); docelowe API 34.
- Rozmiar: 14 148 060 bajtów (13,49 MiB).
- SHA-256: `25610c0532133f73827d12043695cbafd735b8692bc92730fa24e9a03b1dd667`.
- Sprawdzono podpis za pomocą `apksigner verify` i kod DEX: metody dziennika `ModPlDziennik` nie wywołują `android.util.Log`. Dotyczy to dziennika moda, nie wszystkich komunikatów Androida i oryginalnego launchera.

## Instalacja i aktualizacja

Przenieś APK na telewizor pendrive'em lub przez Send files to TV, otwórz w menedżerze plików i przejdź polski kreator. Jeśli system poprosi, zezwól temu menedżerowi na instalowanie nieznanych aplikacji. Aktualizuj na poprzednią wersję moda z tym samym podpisem, aby zachować ustawienia.

## Budowanie

W Git Bash, ze skonfigurowanym JDK, Pythonem 3 i Android SDK (platforma 34, Build Tools 34.0.0 i 35.0.0):

```bash
# Do testów: z dziennikiem moda
bash zbuduj-pl.sh

# Do wydania: bez dziennika moda
DZIENNIK=0 bash zbuduj-pl.sh
```

Wynik: `build/tvos-launcher-pl.apk`. Klucz i hasło powstają przy pierwszym budowaniu w `mod-pl.keystore` oraz `mod-pl.keystore.haslo` i nie trafiają do repozytorium. Skrypt odtwarza `src/` oraz manifest z Gita; buduj w osobnej kopii roboczej, jeśli masz niezapisane zmiany w tych plikach.

## Zdjęcia scen i źródła

APK zawiera zdjęcia scen „Czerwone Lamborghini” i „Audi RS7 nocą”. Zdjęcia i warstwy Audi są wyłączone z repozytorium źródłowego i nie są objęte licencją Apache-2.0 kodu. Nie potwierdzono autora ani licencji konkretnego zdjęcia Lamborghini z Wallhaven. Właściciel moda zdecydował o pozostawieniu obu scen w APK.

Kod źródłowy: [premiumsmart/tvOS-Launcher-PL](https://github.com/premiumsmart/tvOS-Launcher-PL). Autor oryginału: [techgenius1](https://github.com/techgenius1/tvOS-Launcher). Informacje o autorstwie i dołączonych bibliotekach: [NOTICE](NOTICE).
