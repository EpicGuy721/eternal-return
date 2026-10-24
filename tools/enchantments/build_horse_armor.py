"""Generates the vanilla enchantments that also go on horse armor (com.eternalreturn.items.HorseArmor).

Run from anywhere: python tools/enchantments/build_horse_armor.py (after a Gradle build, which fills the Loom cache).

For each enchantment in ENCHANTMENTS, reads vanilla's definition from the Minecraft jar in the Loom cache and writes
data/minecraft/enchantment/<enchantment>.json with two changes: its items also take horse armor (an
eternalreturn:enchantable/* tag of vanilla's items plus #eternalreturn:horse_armor), and it also works in the body
slot, where a horse wears its armor. Do not hand-edit the generated files.
"""
import glob
import json
import os
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', '..')
RESOURCES = os.path.join(ROOT, 'src', 'main', 'resources')
LOOM = os.path.join(ROOT, '.gradle', 'loom-cache', 'minecraftMaven', 'net', 'minecraft')
COMMON = [j for j in glob.glob(os.path.join(LOOM, 'minecraft-common-*', '*', '*.jar')) if not j.endswith('-sources.jar')]
if not COMMON:
    raise SystemExit('Minecraft jar not found in the Loom cache; run ./gradlew build first')

# Enchantment -> the eternalreturn:enchantable tag it moves to (vanilla's items plus horse armor).
ENCHANTMENTS = {
    'protection': 'armor_or_horse_armor',
    'fire_protection': 'armor_or_horse_armor',
    'blast_protection': 'armor_or_horse_armor',
    'projectile_protection': 'armor_or_horse_armor',
    'unbreaking': 'durability_or_horse_armor',
    'frost_walker': 'foot_or_horse_armor',
}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def main():
    jar = zipfile.ZipFile(COMMON[0])
    tags = {}
    for name, tag in ENCHANTMENTS.items():
        enchantment = json.loads(jar.read(f'data/minecraft/enchantment/{name}.json'))
        tags.setdefault(tag, enchantment['supported_items'])
        assert tags[tag] == enchantment['supported_items'], name
        enchantment['supported_items'] = f'#eternalreturn:enchantable/{tag}'
        if 'any' not in enchantment['slots'] and 'body' not in enchantment['slots']:
            enchantment['slots'].append('body')
        write(os.path.join(RESOURCES, 'data', 'minecraft', 'enchantment', f'{name}.json'), enchantment)
    for tag, vanilla in tags.items():
        write(os.path.join(RESOURCES, 'data', 'eternalreturn', 'tags', 'item', 'enchantable', f'{tag}.json'),
              {'values': [vanilla, '#eternalreturn:horse_armor']})
    print(f'wrote {len(ENCHANTMENTS)} enchantments and {len(tags)} tags')


if __name__ == '__main__':
    main()
