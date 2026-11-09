"""Writes the cactus arm's resources (com.eternalreturn.blocks.CactusArmBlock):

- textures (assets/eternalreturn/textures/block/cactus_arm_side.png, cactus_arm_top.png): the owner's, copied from
  tools/cactus/textures; the top texture is used for the bottom too;
- a block model per set of joins (16: which of north, east, south and west it is joined to a cactus on) and the
  blockstate choosing among them, the item model (the unjoined arm);
- the loot table (drops itself), its name in en_us.json (other lines kept).

The arm is a 10 x 10 pixel column drawn as vanilla draws cactus: each side face on its own plane 3 pixels in, the full
texture (spines past the edges) on it. A join is a stub from the arm across to the cactus face beside it (one pixel
into the next block, where the cactus face is), 10 x 10 across, its texture turned to run along it. No two faces share
a plane where both are drawn (no z-fighting): where a stub leaves the arm, the arm's side faces stop at the stub, and
the stub has no end faces (one would lie on the arm's face, the other on the cactus's).

Run: python tools/cactus/build_cactus_arm.py
"""
import json
import os
import shutil

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
NS = 'eternalreturn'
SIDES = ['north', 'east', 'south', 'west']


def write_json(rel, obj):
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def model(joins):
    north, east, south, west = (side in joins for side in SIDES)
    x0, x1 = (3 if west else 0), (13 if east else 16)
    z0, z1 = (3 if north else 0), (13 if south else 16)
    elements = [
        # Top and bottom: the top texture on both, on the block's top and bottom planes.
        {'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {
            'up': {'uv': [0, 0, 16, 16], 'texture': '#top', 'cullface': 'up'},
            'down': {'uv': [0, 0, 16, 16], 'texture': '#top', 'cullface': 'down'}}},
        # North and south faces on the planes 3 in, stopping where a stub leaves east or west.
        {'from': [x0, 0, 3], 'to': [x1, 16, 13], 'faces': {
            'north': {'uv': [16 - x1, 0, 16 - x0, 16], 'texture': '#side'},
            'south': {'uv': [x0, 0, x1, 16], 'texture': '#side'}}},
        # West and east faces, stopping where a stub leaves north or south.
        {'from': [3, 0, z0], 'to': [13, 16, z1], 'faces': {
            'west': {'uv': [z0, 0, z1, 16], 'texture': '#side'},
            'east': {'uv': [16 - z1, 0, 16 - z0, 16], 'texture': '#side'}}},
    ]
    turned = {'uv': [3, 6, 13, 10], 'texture': '#side', 'rotation': 90}
    along = {'uv': [3, 6, 13, 10], 'texture': '#side'}
    if west:
        elements.append({'from': [-1, 3, 3], 'to': [3, 13, 13], 'faces': {'north': turned, 'south': turned, 'up': turned, 'down': turned}})
    if east:
        elements.append({'from': [13, 3, 3], 'to': [17, 13, 13], 'faces': {'north': turned, 'south': turned, 'up': turned, 'down': turned}})
    if north:
        elements.append({'from': [3, 3, -1], 'to': [13, 13, 3], 'faces': {'west': turned, 'east': turned, 'up': along, 'down': along}})
    if south:
        elements.append({'from': [3, 3, 13], 'to': [13, 13, 17], 'faces': {'west': turned, 'east': turned, 'up': along, 'down': along}})
    return {'parent': 'minecraft:block/block', 'ambientocclusion': False,
            'textures': {'particle': f'{NS}:block/cactus_arm_side', 'side': f'{NS}:block/cactus_arm_side', 'top': f'{NS}:block/cactus_arm_top'},
            'elements': elements}


def name(joins):
    return 'cactus_arm' + ('_' + ''.join(side[0] for side in SIDES if side in joins) if joins else '')


def main():
    tex = os.path.join(RES, 'assets', NS, 'textures', 'block')
    os.makedirs(tex, exist_ok=True)
    for texture in ('cactus_arm_side', 'cactus_arm_top'):
        shutil.copyfile(os.path.join(ROOT, 'tools', 'cactus', 'textures', texture + '.png'), os.path.join(tex, texture + '.png'))
    variants = {}
    for mask in range(16):
        joins = [side for i, side in enumerate(SIDES) if mask & (1 << i)]
        write_json(f'assets/{NS}/models/block/{name(joins)}.json', model(joins))
        key = ','.join(f'{side}={"true" if side in joins else "false"}' for side in sorted(SIDES))
        variants[key] = {'model': f'{NS}:block/{name(joins)}'}
    write_json(f'assets/{NS}/blockstates/cactus_arm.json', {'variants': variants})
    write_json(f'assets/{NS}/models/item/cactus_arm.json', {'parent': f'{NS}:block/cactus_arm'})
    write_json(f'data/{NS}/loot_table/blocks/cactus_arm.json', {'type': 'minecraft:block', 'pools': [{
        'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:cactus_arm'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
        'random_sequence': f'{NS}:blocks/cactus_arm'})
    lang_file = os.path.join(RES, 'assets', NS, 'lang', 'en_us.json')
    lang = json.load(open(lang_file, encoding='utf-8'))
    lang[f'block.{NS}.cactus_arm'] = 'Cactus Arm'
    with open(lang_file, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(lang, f, indent='\t', ensure_ascii=False)
        f.write('\n')
    print('cactus arm: 16 models')


if __name__ == '__main__':
    main()
