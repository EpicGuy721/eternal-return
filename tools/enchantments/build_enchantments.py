"""Generates the vanilla enchantments that go on more items: horse armor (com.eternalreturn.items.HorseArmor) and spears
(com.eternalreturn.items.Spears).

Run from anywhere: python tools/enchantments/build_enchantments.py (after a Gradle build, which fills the Loom cache).

For each enchantment in ENCHANTMENTS, reads vanilla's definition from the Minecraft jar in the Loom cache and writes
data/minecraft/enchantment/<enchantment>.json with its item lists (supported_items, what it can go on; primary_items,
what the enchanting table offers it for) moved to eternalreturn:enchantable/* tags holding vanilla's items and more;
those in BODY_SLOT also work in the body slot, where a horse wears its armor. It also adds spears to the vanilla
enchantable tags in EXTENDED, so every enchantment using them takes spears. Do not hand-edit the generated files.
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

# Enchantment -> {field of its definition: the eternalreturn:enchantable tag it moves to}.
ENCHANTMENTS = {
    'protection': {'supported_items': 'armor_or_horse_armor'},
    'fire_protection': {'supported_items': 'armor_or_horse_armor'},
    'blast_protection': {'supported_items': 'armor_or_horse_armor'},
    'projectile_protection': {'supported_items': 'armor_or_horse_armor'},
    'unbreaking': {'supported_items': 'durability_or_horse_armor'},
    'frost_walker': {'supported_items': 'foot_or_horse_armor'},
    'knockback': {'supported_items': 'sword_or_spear'},
    'looting': {'supported_items': 'sword_or_spear'},
    'sharpness': {'primary_items': 'sword_or_spear'},
    'smite': {'primary_items': 'sword_or_spear'},
    'bane_of_arthropods': {'primary_items': 'sword_or_spear'},
    'fire_aspect': {'primary_items': 'sword_or_spear'},
}
# What each eternalreturn:enchantable tag adds to vanilla's items.
MORE = {
    'armor_or_horse_armor': '#eternalreturn:horse_armor',
    'durability_or_horse_armor': '#eternalreturn:horse_armor',
    'foot_or_horse_armor': '#eternalreturn:horse_armor',
    'sword_or_spear': '#eternalreturn:spears',
}
BODY_SLOT = {'protection', 'fire_protection', 'blast_protection', 'projectile_protection', 'frost_walker'}
# Vanilla enchantable tags spears join: Sharpness, Smite and Bane of Arthropods; Fire Aspect; Unbreaking, Mending and
# Curse of Vanishing.
EXTENDED = {'sharp_weapon': '#eternalreturn:spears', 'fire_aspect': '#eternalreturn:spears', 'durability': '#eternalreturn:spears'}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def main():
    jar = zipfile.ZipFile(COMMON[0])
    tags = {}
    for name, fields in ENCHANTMENTS.items():
        enchantment = json.loads(jar.read(f'data/minecraft/enchantment/{name}.json'))
        for field, tag in fields.items():
            tags.setdefault(tag, enchantment[field])
            assert tags[tag] == enchantment[field], (name, field)
            enchantment[field] = f'#eternalreturn:enchantable/{tag}'
        if name in BODY_SLOT and 'any' not in enchantment['slots'] and 'body' not in enchantment['slots']:
            enchantment['slots'].append('body')
        write(os.path.join(RESOURCES, 'data', 'minecraft', 'enchantment', f'{name}.json'), enchantment)
    for tag, vanilla in tags.items():
        write(os.path.join(RESOURCES, 'data', 'eternalreturn', 'tags', 'item', 'enchantable', f'{tag}.json'), {'values': [vanilla, MORE[tag]]})
    for tag, more in EXTENDED.items():
        write(os.path.join(RESOURCES, 'data', 'minecraft', 'tags', 'item', 'enchantable', f'{tag}.json'), {'values': [more]})
    print(f'wrote {len(ENCHANTMENTS)} enchantments, {len(tags)} tags and {len(EXTENDED)} vanilla tag additions')


if __name__ == '__main__':
    main()
