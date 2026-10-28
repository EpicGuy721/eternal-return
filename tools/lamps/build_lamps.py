"""Generates the resources for dyed redstone lamps and dyed water cauldrons (com.eternalreturn.blocks.Dyeing), from the
hand-made lamp textures in tools/lamps/textures (redstone_lamp.png, redstone_lamp_on.png) and their colour maps
(redstone_lamp_map.png, redstone_lamp_on_map.png: the texture with every pixel the dye leaves alone painted MASK).

Run from anywhere: python tools/lamps/build_lamps.py

A dyed lamp is drawn as two layers: the lamp texture itself, and over it the part its map colours, in grey (the rest
left clear), tinted with the lamp's colour, so the dye shows true (as a dyed item frame's back does).

Writes (all under src/main/resources):
- textures eternalreturn:block/dyed_redstone_lamp(_on) (the lamp) and dyed_redstone_lamp(_on)_glass (its glass, grey);
- block models dyed_redstone_lamp(_on) (the two layers, the glass marked for the tint), its blockstate (lit or not),
  vanilla's redstone_lamp item model with an eternalreturn:dyed override to eternalreturn:item/dyed_redstone_lamp;
- the dyed water cauldron's models (on vanilla's cauldron templates, the water tinted with the dye) and blockstate;
- loot tables: the dyed lamp drops a redstone lamp of its colour, the dyed cauldron a cauldron.

Do not hand-edit the generated files; change this script or the textures and rerun it.
"""
import json
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', '..', 'src', 'main', 'resources')
NS = 'eternalreturn'
# The grey the glass's darkest and lightest pixels become, unlit and lit.
GREYS = {'redstone_lamp': (40, 200), 'redstone_lamp_on': (110, 255)}


def write_json(rel, data):
    full = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def luma(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


# The colour maps' mark for a pixel the dye leaves alone.
MASK = (0xFF, 0x00, 0x6E)


def glass_layer(image, colour_map, low, high):
    """The pixels the map colours (not MASK), in grey: their lightness spread from low to high."""
    pixels = [(x, y) for y in range(image.height) for x in range(image.width) if colour_map.getpixel((x, y))[:3] != MASK]
    lumas = [luma(image.getpixel(xy)) for xy in pixels]
    lo, hi = min(lumas), max(lumas)
    out = Image.new('RGBA', image.size, (0, 0, 0, 0))
    for xy in pixels:
        t = (luma(image.getpixel(xy)) - lo) / (hi - lo) if hi > lo else 1.0
        v = round(low + t * (high - low))
        out.putpixel(xy, (v, v, v, 255))
    return out


def cube(texture, tint):
    faces = {}
    for side in ('down', 'up', 'north', 'south', 'west', 'east'):
        f = {'texture': texture, 'cullface': side}
        if tint:
            f['tintindex'] = 0
        faces[side] = f
    return {'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': faces}


def main():
    for name, (low, high) in GREYS.items():
        image = Image.open(os.path.join(HERE, 'textures', name + '.png')).convert('RGBA')
        colour_map = Image.open(os.path.join(HERE, 'textures', name + '_map.png')).convert('RGBA')
        dyed = 'dyed_' + name
        image.save(os.path.join(RES, 'assets', NS, 'textures', 'block', dyed + '.png'))
        glass_layer(image, colour_map, low, high).save(os.path.join(RES, 'assets', NS, 'textures', 'block', dyed + '_glass.png'))
        write_json(f'assets/{NS}/models/block/{dyed}.json', {
            'parent': 'minecraft:block/block',
            'textures': {'particle': f'{NS}:block/{dyed}', 'lamp': f'{NS}:block/{dyed}', 'glass': f'{NS}:block/{dyed}_glass'},
            'elements': [cube('#lamp', False), cube('#glass', True)]})
    write_json(f'assets/{NS}/blockstates/dyed_redstone_lamp.json', {'variants': {
        'lit=false': {'model': f'{NS}:block/dyed_redstone_lamp'},
        'lit=true': {'model': f'{NS}:block/dyed_redstone_lamp_on'}}})
    write_json(f'assets/{NS}/models/item/dyed_redstone_lamp.json', {'parent': f'{NS}:block/dyed_redstone_lamp'})
    write_json('assets/minecraft/models/item/redstone_lamp.json', {'parent': 'minecraft:block/redstone_lamp',
            'overrides': [{'predicate': {f'{NS}:dyed': 1.0}, 'model': f'{NS}:item/dyed_redstone_lamp'}]})
    # The cauldron's own models (vanilla's water cauldron ones, which resource packs may draw untinted), its water tinted.
    for level, template in ((1, 'level1'), (2, 'level2'), (3, 'full')):
        write_json(f'assets/{NS}/models/block/dyed_water_cauldron_{template}.json', {
            'parent': f'minecraft:block/template_cauldron_{template}',
            'textures': {'bottom': 'minecraft:block/cauldron_bottom', 'content': 'minecraft:block/water_still', 'inside': 'minecraft:block/cauldron_inner',
                         'particle': 'minecraft:block/cauldron_side', 'side': 'minecraft:block/cauldron_side', 'top': 'minecraft:block/cauldron_top'}})
    write_json(f'assets/{NS}/blockstates/dyed_water_cauldron.json', {'variants': {
        f'level={level}': {'model': f'{NS}:block/dyed_water_cauldron_{template}'} for level, template in ((1, 'level1'), (2, 'level2'), (3, 'full'))}})
    survives = [{'condition': 'minecraft:survives_explosion'}]
    write_json(f'data/{NS}/loot_table/blocks/dyed_redstone_lamp.json', {'type': 'minecraft:block', 'pools': [{
        'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': survives, 'entries': [{'type': 'minecraft:item', 'name': 'minecraft:redstone_lamp',
            'functions': [{'function': 'minecraft:copy_components', 'source': 'block_entity', 'include': ['minecraft:dyed_color']}]}]}],
        'random_sequence': f'{NS}:blocks/dyed_redstone_lamp'})
    write_json(f'data/{NS}/loot_table/blocks/dyed_water_cauldron.json', {'type': 'minecraft:block', 'pools': [{
        'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': survives, 'entries': [{'type': 'minecraft:item', 'name': 'minecraft:cauldron'}]}],
        'random_sequence': f'{NS}:blocks/dyed_water_cauldron'})


if __name__ == '__main__':
    main()
