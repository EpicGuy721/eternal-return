"""Writes the ice blocks' resources (worldgen expansion phase 6, ice caves; com.eternalreturn.blocks.IceBlocks):

- textures (assets/eternalreturn/textures/block): the owner's hand-made ones from tools/ice/textures, copied; the
  icicle's upward segments (stalagmites) are its downward ones flipped, as no up textures were drawn;
- block and item models, blockstates (the icicle as pointed dripstone's, the ice crystal as an amethyst cluster's);
- loot tables (each drops itself), recipes (polished ice from packed ice, ice bricks from polished ice, chiseled ice
  from ice bricks; stonecutting from packed ice or polished ice), the block tag eternalreturn:ice_blocks (the pickaxe's,
  through data/minecraft/tags/block/mineable/pickaxe.json) and the names in en_us.json (other lines kept).

Run: python tools/ice/build_ice.py
"""
import json
import os
import shutil

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
SRC = os.path.join(ROOT, 'tools', 'ice', 'textures')
NS = 'eternalreturn'
TEX = os.path.join(RES, 'assets', NS, 'textures', 'block')
CUBES = {'polished_ice': 'Polished Ice', 'ice_bricks': 'Ice Bricks', 'chiseled_ice': 'Chiseled Ice'}
THICKNESSES = ['tip', 'tip_merge', 'frustum', 'middle', 'base']


def write_json(rel, obj, indent=2):
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent=indent, ensure_ascii=False)
        f.write('\n')


def item(name):
    return {'item': f'{NS}:{name}'}


def main():
    os.makedirs(TEX, exist_ok=True)
    for name in list(CUBES) + ['ice_crystal'] + [f'icicle_down_{t}' for t in THICKNESSES]:
        shutil.copyfile(os.path.join(SRC, name + '.png'), os.path.join(TEX, name + '.png'))
    for t in THICKNESSES:
        Image.open(os.path.join(SRC, f'icicle_down_{t}.png')).transpose(Image.Transpose.FLIP_TOP_BOTTOM).save(os.path.join(TEX, f'icicle_up_{t}.png'))

    models = f'assets/{NS}/models'
    for name in CUBES:
        write_json(f'{models}/block/{name}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/{name}'}})
        write_json(f'{models}/item/{name}.json', {'parent': f'{NS}:block/{name}'})
        write_json(f'assets/{NS}/blockstates/{name}.json', {'variants': {'': {'model': f'{NS}:block/{name}'}}})
    # Ice crystal: amethyst cluster's model and turns, one per face it grows from.
    write_json(f'{models}/block/ice_crystal.json', {'parent': 'minecraft:block/cross', 'textures': {'cross': f'{NS}:block/ice_crystal'}})
    write_json(f'{models}/item/ice_crystal.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:block/ice_crystal'},
                                                   'display': {'head': {'translation': [0, 14, -5]}}})
    turns = {'up': {}, 'down': {'x': 180}, 'north': {'x': 90}, 'east': {'x': 90, 'y': 90}, 'south': {'x': 90, 'y': 180}, 'west': {'x': 90, 'y': 270}}
    write_json(f'assets/{NS}/blockstates/ice_crystal.json', {'variants': {
        f'facing={face}': {'model': f'{NS}:block/ice_crystal', **turn} for face, turn in turns.items()}})
    # Icicle: pointed dripstone's crossed planes, one model per segment and direction.
    variants = {}
    for direction in ('down', 'up'):
        for t in THICKNESSES:
            write_json(f'{models}/block/icicle_{direction}_{t}.json',
                       {'parent': 'minecraft:block/pointed_dripstone', 'textures': {'cross': f'{NS}:block/icicle_{direction}_{t}'}})
            variants[f'thickness={t},vertical_direction={direction}'] = {'model': f'{NS}:block/icicle_{direction}_{t}'}
    write_json(f'assets/{NS}/blockstates/icicle.json', {'variants': variants})
    write_json(f'{models}/item/icicle.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:block/icicle_down_tip'},
                                              'display': {'thirdperson_righthand': {'rotation': [0, 100, 0], 'translation': [-1, -1, 0], 'scale': [0.9, 0.9, 0.9]},
                                                          'firstperson_righthand': {'rotation': [0, 100, 0], 'translation': [0, -2, 0], 'scale': [0.9, 0.9, 0.9]}}})

    all_blocks = list(CUBES) + ['icicle', 'ice_crystal']
    for name in all_blocks:
        write_json(f'data/{NS}/loot_table/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{
            'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{name}'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
            'random_sequence': f'{NS}:blocks/{name}'})
    write_json(f'data/{NS}/tags/block/ice_blocks.json', {'values': [f'{NS}:{name}' for name in all_blocks]})

    recipes = f'data/{NS}/recipe'
    write_json(f'{recipes}/polished_ice.json', {'type': 'minecraft:crafting_shaped', 'category': 'building', 'pattern': ['##', '##'],
                                               'key': {'#': {'item': 'minecraft:packed_ice'}}, 'result': {'id': f'{NS}:polished_ice', 'count': 4}})
    write_json(f'{recipes}/ice_bricks.json', {'type': 'minecraft:crafting_shaped', 'category': 'building', 'pattern': ['##', '##'],
                                             'key': {'#': item('polished_ice')}, 'result': {'id': f'{NS}:ice_bricks', 'count': 4}})
    write_json(f'{recipes}/chiseled_ice.json', {'type': 'minecraft:crafting_shaped', 'category': 'building', 'pattern': ['#', '#'],
                                               'key': {'#': item('ice_bricks')}, 'result': {'id': f'{NS}:chiseled_ice', 'count': 1}})
    for source, results in {'minecraft:packed_ice': list(CUBES), f'{NS}:polished_ice': ['ice_bricks', 'chiseled_ice'], f'{NS}:ice_bricks': ['chiseled_ice']}.items():
        for result in results:
            write_json(f'{recipes}/{result}_from_{source.split(":")[1]}_stonecutting.json', {
                'type': 'minecraft:stonecutting', 'ingredient': {'item': source}, 'result': {'id': f'{NS}:{result}', 'count': 1}})

    lang_file = os.path.join(RES, 'assets', NS, 'lang', 'en_us.json')
    lang = json.load(open(lang_file, encoding='utf-8'))
    for name, title in {**CUBES, 'icicle': 'Icicle', 'ice_crystal': 'Ice Crystal'}.items():
        lang[f'block.{NS}.{name}'] = title
    with open(lang_file, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(lang, f, indent='\t', ensure_ascii=False)
        f.write('\n')
    print(f'{len(all_blocks)} ice blocks')


if __name__ == '__main__':
    main()
