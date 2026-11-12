"""Składa planszę wyłącznie z gotowych, zanonimizowanych zrzutów."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageOps, ImageChops
import sys
sys.stdout.reconfigure(encoding='utf-8')
R = Path(__file__).resolve().parents[2]
O = R / 'zrzuty-github/gotowe'

def font(n: int, w: str='regular') -> ImageFont.FreeTypeFont:
    f = {'regular': 'Inter-Regular.ttf', 'medium': 'Inter-Medium.ttf', 'semi': 'Inter-SemiBold.ttf', 'bold': 'InterDisplay-Bold.ttf'}[w]
    return ImageFont.truetype(str(R / 'assets/fonts' / f), n)

def rounded(size: tuple[int, int], box: tuple[int, int, int, int], r: int) -> Image.Image:
    m = Image.new('L', (size[0] * 3, size[1] * 3))
    ImageDraw.Draw(m).rounded_rectangle(tuple((round(v * 3) for v in box)), radius=round(r * 3), fill=255)
    return m.resize(size, Image.Resampling.LANCZOS)

def tafla(im: Image.Image, w: int, b: int=2, r: int=28) -> Image.Image:
    h = round(w * im.height / im.width)
    size = (w + 2 * b, h + 2 * b)
    p = Image.new('RGBA', size, (0, 0, 0, 0))
    outer = rounded(size, (0, 0, size[0] - 1, size[1] - 1), r + b)
    inner = rounded(size, (b, b, size[0] - 1 - b, size[1] - 1 - b), r)
    base = Image.new('RGBA', size, (21, 9, 34, 255))
    base.putalpha(outer)
    p.alpha_composite(base)
    p.paste(im.resize((w, h), Image.Resampling.LANCZOS), (b, b), rounded((w, h), (0, 0, w - 1, h - 1), r))
    edge = ImageChops.subtract(outer, inner)
    shine = Image.new('RGBA', size)
    sd = ImageDraw.Draw(shine)
    for y in range(size[1]):
        t = y / max(1, size[1] - 1)
        sd.line((0, y, size[0], y), fill=(round(225 - 48 * t), round(199 - 72 * t), round(253 - 24 * t), round(195 - 120 * t)))
    shine.putalpha(ImageChops.multiply(shine.getchannel('A'), edge))
    p.alpha_composite(shine)
    return p
na = ImageOps.fit(Image.open(R / 'zrzuty-github/oprawa/liquid-violet.webp').convert('RGBA'), (1600, 1250), method=Image.Resampling.LANCZOS)
shade = Image.new('RGBA', na.size)
d = ImageDraw.Draw(shade)
for y in range(1250):
    d.line((0, y, 1600, y), fill=(13, 3, 23, round(40 + 45 * max(0, (y - 780) / 470))))
na.alpha_composite(shade)

def umiesc(p: Image.Image, xy: tuple[int, int], kat: float=0) -> tuple[int, int, int, int]:
    p = p.rotate(kat, resample=Image.Resampling.BICUBIC, expand=True)
    x, y = xy
    assert x >= 0 and y >= 0 and (x + p.width <= 1600) and (y + p.height <= 1250), (xy, p.size)
    alpha = Image.new('L', na.size)
    alpha.paste(p.getchannel('A'), (x, y + 15))
    shadow = Image.new('RGBA', na.size)
    shadow.putalpha(alpha.filter(ImageFilter.GaussianBlur(18)).point(lambda v: round(v * 0.68)))
    na.alpha_composite(shadow)
    halo = Image.new('RGBA', na.size, (170, 71, 229, 0))
    halo.putalpha(alpha.filter(ImageFilter.GaussianBlur(22)).point(lambda v: round(v * 0.055)))
    na.alpha_composite(halo)
    na.alpha_composite(p, (x, y))
    return (x, y, x + p.width, y + p.height)
d = ImageDraw.Draw(na)
d.text((54, 28), 'tvOS Launcher', font=font(64, 'bold'), fill=(250, 242, 255))
tw = d.textlength('tvOS Launcher', font=font(64, 'bold'))
bx = round(54 + tw + 22)
d.rounded_rectangle((bx, 42, bx + 84, 99), radius=20, fill=(55, 24, 78, 190), outline=(166, 112, 201), width=1)
d.text((bx + 16, 46), 'PL', font=font(39, 'semi'), fill=(230, 189, 255))
d.text((58, 117), 'Po polsku. W stylu Liquid Glass.', font=font(22), fill=(212, 186, 230))
home = Image.open(O / 'home.webp').convert('RGB')
mecze = Image.open(O / 'matches.webp').convert('RGB').crop((67, 59, 1533, 841))
filmy = Image.open(O / 'most-watched.webp').convert('RGB').crop((67, 59, 1533, 841))
pogoda = Image.open(O / 'weather.webp').convert('RGB').crop((938, 42, 1569, 799))
gol = Image.open(O / 'goal-badge.webp').convert('RGB')
bounds = {}
bounds['home'] = umiesc(tafla(home, 1060, 3, 30), (43, 181), -1.2)
bounds['weather'] = umiesc(tafla(pogoda, 290, 2, 28), (1225, 205), 1.2)
bounds['goal'] = umiesc(tafla(gol, 326, 2, 40), (1192, 676), 0)
bounds['matches'] = umiesc(tafla(mecze, 545, 2, 25), (52, 858), 1.2)
bounds['movies'] = umiesc(tafla(filmy, 650, 2, 26), (750, 814), -1.2)
d = ImageDraw.Draw(na)

def opis(x: int, y: int, nazwa: str, zrodlo: str | None=None) -> None:
    d.text((x, y), nazwa, font=font(18, 'semi'), fill=(237, 218, 250), stroke_width=1, stroke_fill=(28, 6, 41))
    if zrodlo:
        xx = x + d.textlength(nazwa, font=font(18, 'semi')) + 14
        d.text((xx, y + 4), zrodlo, font=font(12, 'medium'), fill=(189, 160, 207), stroke_width=1, stroke_fill=(28, 6, 41))
opis(1234, 168, 'Pogoda', 'Open-Meteo')
opis(1199, 636, 'Powiadomienia o golach')
opis(70, 817, 'Mecze', 'LiveScore · ulubione drużyny')
opis(770, 1208, 'Najczęściej oglądane', 'JustWatch PL')
wynik = O / 'showcase.webp'
for q in (92, 89, 86, 83, 80, 77, 73):
    na.convert('RGB').save(wynik, 'WEBP', quality=q, method=6)
    if wynik.stat().st_size < 400000:
        break
if wynik.stat().st_size >= 400000:
    raise RuntimeError('Plansza przekracza limit 400 000 bajtow')
print('Plansza:', na.size, wynik.stat().st_size, 'B')
