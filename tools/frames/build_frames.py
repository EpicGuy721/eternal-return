"""Generates the resources for dyed item frames and glass item frames (com.eternalreturn.blocks.Frames), from vanilla's
item frame models and textures (read from the Minecraft jars in the Loom cache, so run a Gradle build first) and the
hand-made glass frame textures in tools/frames/textures.

Run from anywhere: python tools/frames/build_frames.py

Dye colours a frame's leather back only, as on a dyed frame of the Fast Item Frames mod: the wood (glass on a glass
frame) and the picture on the item icon keep their own colours. A dyed frame shows grey copies of the leather (their
shading kept, brightened) under the dye, so the dye shows true; an undyed vanilla frame keeps vanilla's look.

Writes (all under src/main/resources/assets):
- block models: eternalreturn:block/template_tinted_item_frame(_map), vanilla's frame templates with the back
  marked for the dye; dyed_item_frame(_map) and dyed_glow_item_frame(_map), on them, with the grey backs; and the
  glass frames, glass_item_frame and glowing_glass_item_frame, glass in place of the birch (facing out from the
  glass's own edge, the rest of it being see-through);
- block textures: dyed_item_frame and dyed_glow_item_frame (vanilla's backs in grey), glass_item_frame_back (the
  hand-made one) and glowing_glass_item_frame_back (that one warmed to the glow item frame's colour);
- item models: vanilla's item_frame and glow_item_frame, with an eternalreturn:dyed override to
  eternalreturn:item/dyed_item_frame and dyed_glow_item_frame; glass_item_frame and glowing_glass_item_frame (the
  glowing one with its dyed override, dyed_glowing_glass_item_frame);
- item textures, each icon as two layers, the second the one dyed: <frame>_border (the frame, its nail and the
  picture) and <frame>_inside (the leather: grey on vanilla's frames, the hand-made glass frame's own grey, the
  glowing glass frame's on the glow item frame's colours, and grey again when dyed).

Do not hand-edit the generated files; change this script or the textures and rerun it.
"""
import glob
import io
import json
import os
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', '..')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets')
NS = 'eternalreturn'
LOOM = os.path.join(ROOT, '.gradle', 'loom-cache', 'minecraftMaven', 'net', 'minecraft')
CLIENT = [j for j in glob.glob(os.path.join(LOOM, 'minecraft-clientOnly-*', '*', '*.jar')) if not j.endswith('-sources.jar')]
if not CLIENT:
    raise SystemExit('Minecraft jars not found in the Loom cache; run ./gradlew build first')
JAR = zipfile.ZipFile(CLIENT[0])

# The grey a dyed back's darkest and lightest leather becomes (the dye times grey/255).
GREY_LOW, GREY_HIGH = 150, 245
GLOW_GREY_LOW, GLOW_GREY_HIGH = 120, 255
# The glowing glass frame's back: the glass frame's, warmed to the glow item frame's light leather.
GLOW = (0xFF, 0xD1, 0x80)


def vanilla_image(path):
    return Image.open(io.BytesIO(JAR.read(f'assets/minecraft/textures/{path}.png'))).convert('RGBA')


def vanilla_json(path):
    return json.loads(JAR.read(f'assets/minecraft/{path}.json').decode('utf-8'))


def write_json(rel, data):
    full = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def write_png(rel, image):
    full = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    image.save(full)


def luma(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


def saturation(p):
    return max(p[:3]) - min(p[:3])


def is_leather(p):
    """Vanilla's leather (brown, or the glow frame's orange) against the picture on it (greys, white, near black)."""
    return p[3] > 0 and saturation(p) >= 0x30


def to_grey(image, pixels, low, high, basis=None):
    """
    The given pixels in grey: the basis pixels' (by default all of them) lightness spread from low to high, the darkest
    of them low, the rest on the same scale.
    """
    lumas = [luma(image.getpixel(xy)) for xy in (basis or pixels)]
    lo, hi = min(lumas), max(lumas)
    out = Image.new('RGBA', image.size, (0, 0, 0, 0))
    for xy in pixels:
        t = (luma(image.getpixel(xy)) - lo) / (hi - lo) if hi > lo else 1.0
        v = max(0, min(255, round(low + t * (high - low))))
        out.putpixel(xy, (v, v, v, image.getpixel(xy)[3]))
    return out


def onto_ramp(image, pixels, ramp):
    """The given pixels recoloured onto the ramp (darkest to lightest), by their lightness."""
    lumas = [luma(image.getpixel(xy)) for xy in pixels]
    lo, hi = min(lumas), max(lumas)
    out = image.copy()
    for xy in pixels:
        t = (luma(image.getpixel(xy)) - lo) / (hi - lo) if hi > lo else 1.0
        r, g, b = ramp[min(len(ramp) - 1, int(t * len(ramp)))]
        out.putpixel(xy, (r, g, b, image.getpixel(xy)[3]))
    return out


def warmed(image, color):
    """The (grey) image times the colour, its lightest pixel the colour itself: its own shading, warmed."""
    top = max(luma(image.getpixel(xy)) for xy in all_pixels(image))
    out = image.copy()
    for xy in all_pixels(image):
        p = image.getpixel(xy)
        t = luma(p) / top
        out.putpixel(xy, (round(color[0] * t), round(color[1] * t), round(color[2] * t), p[3]))
    return out


def ramp_of(image, pixels):
    """The distinct colours among the pixels, darkest first."""
    return sorted({image.getpixel(xy)[:3] for xy in pixels}, key=luma)


def all_pixels(image, test=lambda p: p[3] > 0):
    return [(x, y) for y in range(image.height) for x in range(image.width) if test(image.getpixel((x, y)))]


def split(image, inside):
    """The icon as its frame (all but the inside pixels) and its inside."""
    border, rest = image.copy(), Image.new('RGBA', image.size, (0, 0, 0, 0))
    for xy in inside:
        rest.putpixel(xy, image.getpixel(xy))
        border.putpixel(xy, (0, 0, 0, 0))
    return border, rest


def tinted_template(name):
    model = vanilla_json(f'models/block/{name}')
    for element in model['elements']:
        for face in element['faces'].values():
            if face['texture'] == '#back':
                face['tintindex'] = 0
            else:
                face.pop('tintindex', None)
    return model


def glass_template(template):
    """The tinted template with its frame facing out from the glass's opaque edge (the inside of glass is clear)."""
    model = json.loads(json.dumps(template))
    for element in model['elements']:
        (x0, y0, _), (x1, y1, _) = element['from'], element['to']
        for side, face in element['faces'].items():
            if face['texture'] != '#wood' or side not in ('north', 'south'):
                continue
            if x1 - x0 > y1 - y0:
                # a bar across: the top or bottom edge row of the glass
                row = 0 if y0 >= 8 else 15
                face['uv'] = [16 - x1, row, 16 - x0, row + 1] if side == 'north' else [x0, row, x1, row + 1]
            else:
                col = 0 if (x0 < 8) == (side == 'south') else 15
                face['uv'] = [col, 16 - y1, col + 1, 16 - y0]
    return model


def frame_model(parent, wood, back):
    return {'parent': f'{NS}:block/{parent}', 'textures': {'particle': wood, 'wood': wood, 'back': back}}


def item_model(border, inside, override=None):
    model = {'parent': 'minecraft:item/generated', 'textures': {'layer0': border, 'layer1': inside}}
    if override:
        model['overrides'] = [{'predicate': {f'{NS}:dyed': 1.0}, 'model': override}]
    return model


def main():
    # Block models: the templates, the dyed vanilla frames, the glass frames.
    template = tinted_template('template_item_frame')
    write_json(f'{NS}/models/block/template_tinted_item_frame.json', template)
    write_json(f'{NS}/models/block/template_tinted_item_frame_map.json', tinted_template('template_item_frame_map'))
    write_json(f'{NS}/models/block/template_glass_item_frame.json', glass_template(template))
    birch = 'minecraft:block/birch_planks'
    for frame in ('item_frame', 'glow_item_frame'):
        back = f'{NS}:block/dyed_{frame}'
        write_json(f'{NS}/models/block/dyed_{frame}.json', frame_model('template_tinted_item_frame', birch, back))
        write_json(f'{NS}/models/block/dyed_{frame}_map.json', frame_model('template_tinted_item_frame_map', birch, back))
    glass = 'minecraft:block/glass'
    for frame in ('glass_item_frame', 'glowing_glass_item_frame'):
        write_json(f'{NS}/models/block/{frame}.json', frame_model('template_glass_item_frame', glass, f'{NS}:block/{frame}_back'))

    # Block textures: vanilla's backs in grey; the glass back, and it on the glow frame's colours.
    for frame, low, high in (('item_frame', GREY_LOW, GREY_HIGH), ('glow_item_frame', GLOW_GREY_LOW, GLOW_GREY_HIGH)):
        image = vanilla_image(f'block/{frame}')
        # (the dark edge, under the frame, as much darker than the leather as vanilla's)
        leather = all_pixels(image, is_leather)
        write_png(f'{NS}/textures/block/dyed_{frame}.png', to_grey(image, all_pixels(image), low, high, leather))
    glass_back = Image.open(os.path.join(HERE, 'textures', 'glass_item_frame_back.png')).convert('RGBA')
    write_png(f'{NS}/textures/block/glass_item_frame_back.png', glass_back)
    write_png(f'{NS}/textures/block/glowing_glass_item_frame_back.png', warmed(glass_back, GLOW))

    # Item icons: vanilla's split into frame and leather, the leather grey; the glass ones, their grey the inside.
    for frame, low, high in (('item_frame', GREY_LOW, GREY_HIGH), ('glow_item_frame', GLOW_GREY_LOW, GLOW_GREY_HIGH)):
        icon = vanilla_image(f'item/{frame}')
        leather = [(x, y) for (x, y) in all_pixels(icon, is_leather) if 3 <= x <= 12 and 5 <= y <= 12]
        border, inside = split(icon, leather)
        write_png(f'{NS}/textures/item/{frame}_border.png', border)
        write_png(f'{NS}/textures/item/{frame}_inside.png', to_grey(inside, leather, low, high))
        write_json(f'minecraft/models/item/{frame}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'minecraft:item/{frame}'},
                'overrides': [{'predicate': {f'{NS}:dyed': 1.0}, 'model': f'{NS}:item/dyed_{frame}'}]})
        write_json(f'{NS}/models/item/dyed_{frame}.json', item_model(f'{NS}:item/{frame}_border', f'{NS}:item/{frame}_inside'))
    glass_icon = Image.open(os.path.join(HERE, 'textures', 'glass_item_frame.png')).convert('RGBA')
    grey = all_pixels(glass_icon, lambda p: p[3] > 0 and saturation(p) < 8)
    border, inside = split(glass_icon, grey)
    write_png(f'{NS}/textures/item/glass_item_frame_border.png', border)
    write_png(f'{NS}/textures/item/glass_item_frame_inside.png', inside)
    glow_icon = vanilla_image('item/glow_item_frame')
    icon_ramp = ramp_of(glow_icon, [xy for xy in all_pixels(glow_icon, is_leather) if 3 <= xy[0] <= 12 and 5 <= xy[1] <= 12])
    write_png(f'{NS}/textures/item/glowing_glass_item_frame_inside.png', onto_ramp(inside, grey, icon_ramp))
    write_json(f'{NS}/models/item/glass_item_frame.json', item_model(f'{NS}:item/glass_item_frame_border', f'{NS}:item/glass_item_frame_inside'))
    write_json(f'{NS}/models/item/glowing_glass_item_frame.json', item_model(f'{NS}:item/glass_item_frame_border',
            f'{NS}:item/glowing_glass_item_frame_inside', f'{NS}:item/dyed_glowing_glass_item_frame'))
    write_json(f'{NS}/models/item/dyed_glowing_glass_item_frame.json', item_model(f'{NS}:item/glass_item_frame_border',
            f'{NS}:item/glass_item_frame_inside'))


if __name__ == '__main__':
    main()
