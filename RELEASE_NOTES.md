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

## Kod i licencja

Kod źródłowy: [premiumsmart/tvOS-Launcher-PL](https://github.com/premiumsmart/tvOS-Launcher-PL). Autor oryginału: [techgenius1](https://github.com/techgenius1/tvOS-Launcher). Kod jest na licencji Apache-2.0; informacje o autorstwie i bibliotekach zawiera [NOTICE](https://github.com/premiumsmart/tvOS-Launcher-PL/blob/main/NOTICE).
