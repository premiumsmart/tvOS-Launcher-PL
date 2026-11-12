# Oprawa graficzna materiałów

`liquid-violet.webp` to tło planszy i galerii: 1600 × 1067 px, bez tekstu i interfejsu. Wygenerowano je narzędziem `imagegen` jako nową grafikę, bez przekazywania zrzutów, danych prywatnych ani zdjęć scen jako materiałów wejściowych. Do projektu zapisano kopię w WebP.

Na gotowe tło lokalnie nałożono prawdziwe, wcześniej zanonimizowane zrzuty. Typografia i ramki zostały złożone lokalnie z użyciem dołączonego w projekcie kroju Inter. Interfejs na zrzutach nie jest generowany przez AI.

`glass-stage.webp` to dodatkowa dekoracyjna scena: pusta szklana rama z fioletową poświatą. Wygenerowano ją odrębnie, również bez zrzutów wejściowych. Wykorzystywały ją wcześniejsze, bardziej masywne warianty oprawy; pozostaje materiałem pomocniczym. Aktualna główna plansza ma lokalnie złożone cienkie krawędzie 2–3 px, miękkie cienie i słabą poświatę. Prawdziwe kadry zachowują swoje proporcje. `liquid-violet.webp` służy jako tło kompozycji, galerii i alternatywy.

## Ponowne złożenie głównej planszy

Generator `compose-cover.py` korzysta z gotowych, zanonimizowanych obrazów i dołączonego tła. Wymaga Pythona 3 z biblioteką Pillow. Uruchomiony z katalogu projektu zapisuje tylko `zrzuty-github/gotowe/showcase.webp`; nie edytuje kodu launchera ani surowych zrzutów.

```text
python zrzuty-github/oprawa/compose-cover.py
```

## Prompt użyty do szklanej ramy

```text
Create a high-end cinematic 3D product campaign stage, WITHOUT ANY TEXT OR UI. Landscape 3:2 image. Deep black-plum / aubergine background, sophisticated violet and soft orchid-pink rim light. This is an expensive art-directed contemporary digital product hero, not a gaming wallpaper. Main subject: a single LARGE levitating ultra-wide glass display slab, seen STRAIGHT ON from the front, perfectly horizontal, occupying the lower 72% of the image, from about x=7% to x=93%, y=25% to y=91%. Its INNER DISPLAY AREA must be a perfectly flat solid near-black 16:9 rounded rectangle, straight-on and axis aligned, no perspective distortion, no reflections or objects covering that black interior, ready to receive a real screenshot later. IMPORTANT: fully visible lower corners, no crop. Around that black interior, create a beautiful physically rendered thick transparent smoked lavender crystal / liquid-glass rim with generous continuous rounded corners, subtly refracting studio lights, polished translucent edges and a layered optical depth. Frameless futuristic optical object, absolutely no TV stand, legs, buttons or hardware bezel. The top 22% of image should remain a clean dark empty space for a large editorial heading added later. Behind the glass slab a broad elegant luminous lavender arc / incomplete elliptical halo frames it, rising softly at upper right; organic sculptural light, no full sci-fi ring, no concentric rings. Floor beneath has a faint blurred violet reflection and very soft shadow to sell levitation. Dramatic sculptural depth, realistic studio material rendering, refined softbox highlights, controlled specular details, restrained rich purple palette, premium publication-quality art direction. Add a subtle brighter fuchsia glow at lower left balanced by lavender at upper right. No small cards, no small windows, no monitors in the distance, no logos, no numbers, no text, no letters, no tiny particles, no star field, no grids, no decorative ribbons crossing the main display. The black central display must be empty, clean and uninterrupted. Avoid conventional tech banner stock aesthetics.
```

## Prompt użyty do tła

```text
Create a sophisticated futuristic product launch BACKGROUND ONLY, a high-end art-directed abstract 3D scene, landscape 3:2 composition, 1600x1066. NO text, letters, logos, screens, monitors, UI, devices, cards or panels. Intended use: background behind a real Android TV launcher screenshot composed later. Palette strictly rich black plum, deep violet, electric lavender, subtle fuchsia. Look like an expensive experimental digital design studio campaign, fluid smoked glass, glossy liquid chrome, translucent purple ribbon and a beautiful sculptural flowing glass loop. Asymmetric editorial composition: keep the LEFT TOP third quiet, very dark plum empty negative space for large white typography (x=0-38%, y=0-45%). Keep CENTER and RIGHT middle mostly quiet dark purple (x=32-98%, y=15-75%) for a large screenshot later. Concentrate sculptural forms toward LOWER RIGHT and the outside LOWER edges, sweeping diagonal ribbon or glass loop from lower left across bottom to far right, with fluid luminous edge highlights and atmospheric refracted lavender light. Strong sculptural depth, professional studio reflections, soft velvet lighting, smooth anti-aliased gradients, beautiful material realism, subtle natural texture and photographic grain, refined tasteful composition, cinematic. Avoid a conventional wallpaper gradient, avoid generic sci-fi HUDs, avoid neon grids and tiny particles, avoid excessive bright magenta, no clutter. No central object that would compete with the screenshots. This is only the elegant premium abstract stage background.
```
