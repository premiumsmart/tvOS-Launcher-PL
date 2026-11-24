# tvOS Launcher PL 2.2.5-pl2

Polska wersja [tvOS Launcher autorstwa techgenius1](https://github.com/techgenius1/tvOS-Launcher), oparta na wersji 2.2.5.

## Nowe w tej wersji

- **Okna widżetów w stylu tvOS 27**: „Najczęściej oglądane” i „Top filmy i seriale” z dużym kadrem na cały ekran i półką plakatów (czas trwania i kategoria wiekowa), mecze w kolorach drużyn, wiadomości w kolorze tapety, pogoda na przezroczystym szkle.
- **Zaznaczenie ikon w doku w stylu tvOS 27**: powiększenie, miękki cień, jasna krawędź szkła i połysk, który przesuwa się za ruchem pilota. Nowa opcja **Krawędź jasnych ikon** (Ustawienia → Wygląd i sceny → Dok).
- **Centrum sterowania w układzie tvOS 27**: duży kafel zasilania, kapsuły Wi-Fi, Nie przeszkadzać, Dźwięk i Usypianie, okrągłe przyciski, domyślnie przezroczyste tło. Dawny układ: **Ustawienia → Centrum sterowania → Układ centrum sterowania → Siatka (oryginał)**.
- **Szklany wskaźnik głośności** (do wyboru): przy zmianie głośności pilotem zamiast paska Androida przy prawej krawędzi ekranu pokazuje się szklana pigułka w stylu tvOS. Wybierasz go w kreatorze, a po aktualizacji launcher raz o to zapyta. Działa na przystawkach z Androidem 10 lub nowszym w trybie stałego ADB; telewizory zostają przy swoim wskaźniku. Zmiana: **Ustawienia → Wygląd i sceny → Dok → Wskaźnik głośności**.
- **Listy w ustawieniach i w kreatorze jak w tvOS 27**: zaznaczony wiersz to biała pigułka.
- **Słowa jak w tvOS**: Centrum sterowania, Usypianie, Wyłącz zasilanie, Wł./Wył. Akcja kafelka zasilania nazywa się teraz **Czarny ekran** (to nie jest wygaszacz).
- **Kreator zaleca tryb stałego ADB**: działają wtedy ostatnie aplikacje, aktualizacje przez ADB i wskaźnik głośności.
- **Nowy styl zegara wygaszacza „Duży, w stylu »Portrety«”**: duże cyfry z mlecznego szkła, a rozmiar zegara jest dobierany do zdjęcia, żeby schował się za autem albo budynkiem (Ustawienia wygaszacza → Styl zegara).
- **Lepsze wykrywanie aut** na tapetach i w wygaszaczu: zegar nie staje na aucie także wtedy, gdy auto widać z bliska od tyłu albo od przodu.
- Poprawka błędu oryginału: po włączeniu albo wyłączeniu widżetu kafelek zmienia się od razu, bez ponownego uruchomienia.

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

[Pobierz tvOS-Launcher-PL.apk](https://github.com/premiumsmart/tvOS-Launcher-PL/releases/download/2.2.5-pl2/tvOS-Launcher-PL.apk)

Android TV / Google TV z Androidem 7.0 lub nowszym. Suma SHA-256 jest w załączonym pliku `SHA256SUMS.txt`.

## Instalacja i aktualizacja

- **Masz 2.2.5-pl albo 2.2.0-pl:** launcher sam zaproponuje tę wersję (**Ustawienia → Aktualizacje**). Ustawienia zostają. Po aktualizacji launcher raz zapyta, czy włączyć szklany wskaźnik głośności (tylko tam, gdzie zadziała).
- **Masz starszy mod PL (2.1.5-pl30):** zainstaluj ten plik raz ręcznie, na poprzednią wersję. Kolejne wersje launcher pobierze już sam.
- **Nowa instalacja:** przenieś APK na telewizor pendrive'em lub przez Send files to TV, otwórz w menedżerze plików i przejdź polski kreator. Jeśli system poprosi, zezwól temu menedżerowi na instalowanie nieznanych aplikacji.

## Licencja

Autor oryginału: [techgenius1](https://github.com/techgenius1/tvOS-Launcher). Kod jest na licencji Apache-2.0 (`LICENSE.txt`); informacje o autorstwie, zmianach moda i bibliotekach zawiera `NOTICE.txt`.
