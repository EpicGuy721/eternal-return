"""Writes the cave mushrooms' resources (owner's request, 2026-11-05; com.eternalreturn.blocks.CaveMushrooms) from the
owner's textures in tools/mushrooms/textures/ and vanilla's huge mushroom block files in the Minecraft client jar in the
Gradle cache (run a Gradle build first):

- textures (copied as RGBA to assets/eternalreturn/textures/block); the puffed purple and blue mushrooms use the
  unpuffed ones' textures and models (they look the same);
- block and item models, blockstates (small mushrooms as vanilla's crosses; the cap blocks as vanilla's huge mushroom
  blocks, their inner faces vanilla's pores);
- loot tables: an unpuffed or green mushroom drops itself; a puffed one drops itself with Silk Touch, else the unpuffed
  one; a cap block (the purple and blue ones puffed or unpuffed) drops itself with Silk Touch, else 0 to 2 of its small
  (unpuffed) mushroom, as vanilla's; the names say "Puffed" or "Unpuffed" (the green mushroom comes one way only);
- the huge mushrooms grow with puffed caps;
- the huge mushrooms bone meal grows (worldgen/configured_feature/huge_<colour>_mushroom.json: flat, cone and dome on
  vanilla's stem; they need dirt or mycelium, as vanilla's do);
- the block tag eternalreturn:cave_mushroom_blocks (the axe's, through data/minecraft/tags/block/mineable/axe.json),
  the item tag eternalreturn:cave_mushrooms, and the names in en_us.json (other lines kept).

Run: python tools/mushrooms/build_mushrooms.py
"""
import glob
import json
import os
import sys
import zipfile

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
SRC = os.path.join(ROOT, 'tools', 'mushrooms', 'textures')
NS = 'eternalreturn'
JARS = glob.glob(os.path.expanduser('~/.gradle/caches/fabric-loom/1.21.1/minecraft-client.jar'))
if not JARS:
    sys.exit('Minecraft 1.21.1 client jar not found in the Gradle cache; run ./gradlew build first')
JAR = zipfile.ZipFile(JARS[0])
COLOURS = {'purple': 'flat', 'blue': 'cone', 'green': 'dome'}
PUFFED = ['purple', 'blue']
STEM = {'Name': 'minecraft:mushroom_stem', 'Properties': {'down': 'false', 'east': 'true', 'north': 'true', 'south': 'true', 'up': 'false', 'west': 'true'}}


def write_json(rel, obj):
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write('\n')


def silk_touch():
    return {'condition': 'minecraft:match_tool', 'predicate': {'predicates': {'minecraft:enchantments': [
        {'enchantments': 'minecraft:silk_touch', 'levels': {'min': 1}}]}}}


def loot(name, pool):
    write_json(f'data/{NS}/loot_table/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [pool], 'random_sequence': f'{NS}:blocks/{name}'})


def main():
    tex = os.path.join(RES, 'assets', NS, 'textures', 'block')
    os.makedirs(tex, exist_ok=True)
    models = f'assets/{NS}/models'
    lang_file = os.path.join(RES, 'assets', NS, 'lang', 'en_us.json')
    lang = json.load(open(lang_file, encoding='utf-8'))
    vanilla_states = json.loads(JAR.read('assets/minecraft/blockstates/brown_mushroom_block.json'))
    blocks, small = [], []
    for colour, shape in COLOURS.items():
        mushroom, cap = f'{colour}_mushroom', f'{colour}_mushroom_block'
        for name in (mushroom, cap):
            Image.open(os.path.join(SRC, name + '.png')).convert('RGBA').save(os.path.join(tex, name + '.png'))
        title = colour.title() + ' Mushroom'
        # Small mushroom (and its puffed twin, the same to look at).
        write_json(f'{models}/block/{mushroom}.json', {'parent': 'minecraft:block/cross', 'textures': {'cross': f'{NS}:block/{mushroom}'}})
        names = [mushroom] + ([f'puffed_{mushroom}'] if colour in PUFFED else [])
        for name in names:
            write_json(f'assets/{NS}/blockstates/{name}.json', {'variants': {'': {'model': f'{NS}:block/{mushroom}'}}})
            write_json(f'{models}/item/{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:block/{mushroom}'}})
            # The name says which it is (the owner's call, 2026-11-05); the green one comes one way only.
            lang[f'block.{NS}.{name}'] = (('Puffed ' if name.startswith('puffed_') else 'Unpuffed ') if colour in PUFFED else '') + title
            small.append(f'{NS}:{name}')
        loot(mushroom, {'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{mushroom}'}],
                        'conditions': [{'condition': 'minecraft:survives_explosion'}]})
        if colour in PUFFED:
            loot(f'puffed_{mushroom}', {'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:alternatives', 'children': [
                {'type': 'minecraft:item', 'name': f'{NS}:puffed_{mushroom}', 'conditions': [silk_touch()]},
                {'type': 'minecraft:item', 'name': f'{NS}:{mushroom}'}]}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]})
        # Cap block: vanilla's multipart, its own outer face; the purple and blue ones puffed or unpuffed, the same to look at.
        write_json(f'{models}/block/{cap}.json', {'parent': 'minecraft:block/template_single_face', 'textures': {'texture': f'{NS}:block/{cap}'}})
        write_json(f'{models}/block/{cap}_inventory.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/{cap}'}})
        states = json.loads(json.dumps(vanilla_states).replace('minecraft:block/brown_mushroom_block', f'{NS}:block/{cap}'))
        for name in [cap] + ([f'puffed_{cap}'] if colour in PUFFED else []):
            write_json(f'{models}/item/{name}.json', {'parent': f'{NS}:block/{cap}_inventory'})
            write_json(f'assets/{NS}/blockstates/{name}.json', states)
            loot(name, {'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:alternatives', 'children': [
                {'type': 'minecraft:item', 'name': f'{NS}:{name}', 'conditions': [silk_touch()]},
                {'type': 'minecraft:item', 'name': f'{NS}:{mushroom}', 'functions': [
                    {'function': 'minecraft:set_count', 'add': False, 'count': {'type': 'minecraft:uniform', 'min': -6.0, 'max': 2.0}},
                    {'function': 'minecraft:limit_count', 'limit': {'min': 0.0}},
                    {'function': 'minecraft:explosion_decay'}]}]}]})
            lang[f'block.{NS}.{name}'] = (('Puffed ' if name.startswith('puffed_') else 'Unpuffed ') if colour in PUFFED else '') + f'{title} Block'
            blocks.append(f'{NS}:{name}')
        grown_cap = f'puffed_{cap}' if colour in PUFFED else cap
        # The huge mushroom bone meal grows.
        write_json(f'data/{NS}/worldgen/configured_feature/huge_{mushroom}.json', {'type': f'{NS}:huge_cave_mushroom', 'config': {
            'shape': shape, 'cap': {'Name': f'{NS}:{grown_cap}', 'Properties': {'down': 'false', 'east': 'true', 'north': 'true', 'south': 'true', 'up': 'true', 'west': 'true'}},
            'stem': STEM}})
    write_json(f'data/{NS}/tags/block/cave_mushroom_blocks.json', {'values': blocks})
    write_json(f'data/{NS}/tags/item/cave_mushrooms.json', {'values': small})
    write_json(f'data/{NS}/tags/block/cave_mushrooms.json', {'values': small})
    with open(lang_file, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(lang, f, indent='\t', ensure_ascii=False)
        f.write('\n')
    print(f'{len(small)} small mushrooms, {len(blocks)} cap blocks')


if __name__ == '__main__':
    main()
