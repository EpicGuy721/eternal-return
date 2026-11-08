"""Builds the Eternal Return content mod for Horizon (content-mod/) from the Fabric mod's resources.

Run: python tools/build_content.py [path to the Fabric repo]   (default: ../enchanting-overhaul)

Writes, under content-mod/ (do not hand-edit; change this script and rerun):
- the 64 wool and concrete stairs and slabs: Horizon block declarations (data/eternalreturn/horizon/block/),
  blockstates and models copied from the Fabric mod (tools/blocks/build_blocks.py made them), 26.3 item model
  definitions (assets/eternalreturn/items/), loot tables, recipes (turned into 26.3's ingredient format), tags,
  English names and a creative tab;
- the boulder: a configured feature (vanilla's forest_rock, of stone, granite, diorite or andesite), its placed
  feature, and a NeoForge biome modifier that adds it to every overworld biome.
"""
import json
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'content-mod')
# The Fabric repo: given, else the one this folder sits in (horizon-slice/ in it), else its sibling.
INSIDE = os.path.join(HERE, '..', '..')
FABRIC = sys.argv[1] if len(sys.argv) > 1 else (
    INSIDE if os.path.isdir(os.path.join(INSIDE, 'src', 'main', 'resources'))
    else os.path.join(HERE, '..', '..', 'enchanting-overhaul'))
RES = os.path.join(FABRIC, 'src', 'main', 'resources')
NS = 'eternalreturn'

COLORS = ['white', 'light_gray', 'gray', 'black', 'brown', 'red', 'orange', 'yellow', 'lime', 'green', 'cyan',
          'light_blue', 'blue', 'purple', 'magenta', 'pink']
# Vanilla MapColor ids of each dye colour (white is SNOW).
MAP_COLOR = {'white': 8, 'orange': 15, 'magenta': 16, 'light_blue': 17, 'yellow': 18, 'lime': 19, 'pink': 20,
             'gray': 21, 'light_gray': 22, 'cyan': 23, 'purple': 24, 'blue': 25, 'brown': 26, 'green': 27,
             'red': 28, 'black': 29}
MATERIALS = {
    # material: (stairs template, slab template, hardness, needs a tool)
    'wool': ('minecraft:oak_stairs', 'minecraft:oak_slab', 0.8, False),
    'concrete': ('minecraft:stone_stairs', 'minecraft:stone_slab', 1.8, True),
}


def write(path, data):
    full = os.path.join(OUT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def read(path):
    with open(os.path.join(RES, path), encoding='utf-8') as f:
        return json.load(f)


def ingredient(value):
    """1.21.1's {"item": id} / {"tag": id} ingredients become 26.3's "id" / "#id" strings."""
    if isinstance(value, list):
        return [ingredient(v) for v in value]
    if isinstance(value, dict):
        if 'item' in value:
            return value['item']
        if 'tag' in value:
            return '#' + value['tag']
    return value


def convert_recipe(recipe):
    if 'key' in recipe:
        recipe['key'] = {k: ingredient(v) for k, v in recipe['key'].items()}
    if 'ingredient' in recipe:
        recipe['ingredient'] = ingredient(recipe['ingredient'])
    if 'ingredients' in recipe:
        recipe['ingredients'] = ingredient(recipe['ingredients'])
    return recipe


def title(name):
    return ' '.join(w.capitalize() for w in name.split('_'))


def main():
    if os.path.isdir(OUT):
        for sub in ('assets', 'data'):
            shutil.rmtree(os.path.join(OUT, sub), ignore_errors=True)
    names = {}
    tags = {'minecraft:stairs': [], 'minecraft:slabs': [], 'minecraft:mineable/pickaxe': [],
            'minecraft:needs_stone_tool': []}
    tab = []
    for material, (stairs_template, slab_template, hardness, tool) in MATERIALS.items():
        for color in COLORS:
            base = f'{color}_{material}'
            for shape, template in (('stairs', stairs_template), ('slab', slab_template)):
                name = f'{base}_{shape}'
                block_id = f'{NS}:{name}'
                write(f'data/{NS}/horizon/block/{name}.json', {
                    'template': template,
                    'properties': {'hardness': hardness, 'explosion_resistance': hardness,
                                   'sounds': f'minecraft:{base}', 'map_color': MAP_COLOR[color],
                                   'requires_tool': tool},
                    'item': True,
                })
                # Blockstate and block models: the Fabric mod's, as they are (1.21.1 and 26.3 agree).
                write(f'assets/{NS}/blockstates/{name}.json', read(f'assets/{NS}/blockstates/{name}.json'))
                suffixes = ['', '_inner', '_outer'] if shape == 'stairs' else ['', '_top']
                for suffix in suffixes:
                    model = f'models/block/{name}{suffix}.json'
                    write(f'assets/{NS}/{model}', read(f'assets/{NS}/{model}'))
                # 26.3 draws items from assets/<ns>/items/: point it at the block model.
                write(f'assets/{NS}/items/{name}.json',
                      {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/{name}'}})
                write(f'data/{NS}/loot_table/blocks/{name}.json', read(f'data/{NS}/loot_table/blocks/{name}.json'))
                write(f'data/{NS}/recipe/{name}.json', convert_recipe(read(f'data/{NS}/recipe/{name}.json')))
                cut = f'data/{NS}/recipe/{name}_from_{base}_stonecutting.json'
                if os.path.exists(os.path.join(RES, cut)):
                    write(cut, convert_recipe(read(cut)))
                names[f'block.{NS}.{name}'] = title(name)
                tags['minecraft:stairs' if shape == 'stairs' else 'minecraft:slabs'].append(block_id)
                if tool:
                    tags['minecraft:mineable/pickaxe'].append(block_id)
                tab.append(block_id)
    del tags['minecraft:needs_stone_tool']  # concrete mines with any pickaxe
    for tag, values in tags.items():
        ns, path = tag.split(':')
        write(f'data/{ns}/tags/block/{path}.json', {'values': values})
        if path in ('stairs', 'slabs'):
            write(f'data/{ns}/tags/item/{path}.json', {'values': values})
    write(f'data/{NS}/horizon/creative_tab/cut_blocks.json',
          {'title': 'Eternal Return', 'icon': f'{NS}:white_wool_stairs', 'items': tab})

    # The boulder: vanilla's forest rock (the giant taiga's mossy cobblestone lumps) in the Fabric mod's four
    # boulder stones, one feature per stone, each rare on the land of every overworld biome.
    features = []
    for stone in ('stone', 'granite', 'diorite', 'andesite'):
        write(f'data/{NS}/worldgen/configured_feature/boulder_{stone}.json', {
            'type': 'minecraft:forest_rock',
            'config': {'state': {'Name': f'minecraft:{stone}'}},
        })
        write(f'data/{NS}/worldgen/placed_feature/boulder_{stone}.json', {
            'feature': f'{NS}:boulder_{stone}',
            'placement': [
                {'type': 'minecraft:rarity_filter', 'chance': 24},
                {'type': 'minecraft:in_square'},
                {'type': 'minecraft:heightmap', 'heightmap': 'MOTION_BLOCKING'},
                {'type': 'minecraft:biome'},
            ],
        })
        features.append(f'{NS}:boulder_{stone}')
    write(f'data/{NS}/neoforge/biome_modifier/boulders.json', {
        'type': 'neoforge:add_features',
        'biomes': '#minecraft:is_overworld',
        'features': features,
        'step': 'local_modifications',
    })
    names[f'itemGroup.{NS}.cut_blocks'] = 'Eternal Return'
    write(f'assets/{NS}/lang/en_us.json', dict(sorted(names.items())))
    print(f'{len(tab)} blocks, {len(features)} boulder features written to {os.path.normpath(OUT)}')


if __name__ == '__main__':
    main()
