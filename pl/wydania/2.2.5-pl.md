# tvOS Launcher PL 2.2.5-pl

Polska wersja [tvOS Launcher autorstwa techgenius1](https://github.com/techgenius1/tvOS-Launcher), oparta na wersji 2.2.5.

## Nowe w tej wersji

- Zmiany oryginału 2.2.5, także po polsku:
  - **Nowa wyszukiwarka** w szklanym oknie: szuka też w zainstalowanych aplikacjach, a filmy i seriale pokazuje z serwisów, które masz na telewizorze. Na ekranie głównym jest przycisk lupy (wyłączysz go w **Ustawienia → Szybkie ustawienia**).
  - **Płynniejszy ekran główny**: powrót z aplikacji i klawisz Home nie budują kafelków od nowa, launcher zużywa mniej pamięci.
  - **„Oglądaj dalej”** pokazuje pozycje z większej liczby aplikacji.
  - **Kraj wiadomości**: kafelek Wiadomości ma wybór kraju (domyślnie Polska).
  - **Menu zasilania Androida** jako akcja kafelka zasilania w Panelu sterowania.
- Klawisz Home zamyka też okna kafelków moda (mecze, filmy, wiadomości).
- Tapety i szklany zegar zostają w pełnej jakości kolorów.
- Kafelek krykieta został usunięty. Dodatkowe kafelki to teraz **Wiadomości na żywo** i **Top filmy i seriale** (domyślnie wyłączone): **Ustawienia → Wygląd i sceny → Ustawienia widżetów ekranu głównego**.

## Zmiany moda PL (bez zmian)

- Pełne spolszczenie interfejsu, kreatora i pogody.
- Kafelki i okna Liquid Glass: mecze z LiveScore i wynikami na żywo, „Najczęściej oglądane” z JustWatch dla Polski oraz pogoda.
- Powiadomienia o golach ulubionych drużyn nad innymi aplikacjami.
- Włączanie uprawnienia „Oglądaj dalej” przez lokalne ADB.
- Nocne tapety z internetu: supercary, auta po tuningu i futurystyczne miasta; pomijanie zdjęć za dnia.
- Szklany zegar chowający się za elementami zdjęcia oraz wykrywanie aut na telewizorze przez TensorFlow Lite.
- Wygaszacz z tymi samymi zdjęciami i zegarem, z wyborem stylu cyfr.
- Mod nie wyłącza debugowania USB ani ADB.

## Pobieranie

[Pobierz tvOS-Launcher-PL.apk](https://github.com/premiumsmart/tvOS-Launcher-PL/releases/download/2.2.5-pl/tvOS-Launcher-PL.apk)

Android TV / Google TV z Androidem 7.0 lub nowszym. Suma SHA-256 jest w załączonym pliku `SHA256SUMS.txt`.

## Instalacja i aktualizacja

- **Masz 2.2.0-pl:** launcher sam zaproponuje tę wersję (**Ustawienia → Aktualizacje**). Ustawienia zostają.
- **Masz starszy mod PL (2.1.5-pl30):** zainstaluj ten plik raz ręcznie, na poprzednią wersję. Kolejne wersje launcher pobierze już sam.
- **Nowa instalacja:** przenieś APK na telewizor pendrive'em lub przez Send files to TV, otwórz w menedżerze plików i przejdź polski kreator. Jeśli system poprosi, zezwól temu menedżerowi na instalowanie nieznanych aplikacji.

## Licencja

Autor oryginału: [techgenius1](https://github.com/techgenius1/tvOS-Launcher). Kod jest na licencji Apache-2.0 (`LICENSE.txt`); informacje o autorstwie, zmianach moda i bibliotekach zawiera `NOTICE.txt`.
