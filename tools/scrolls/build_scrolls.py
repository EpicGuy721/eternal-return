"""Generates the enchanted book recipes that scrolls teach (com.eternalreturn.scrolls.Scrolls).

Run from anywhere: python tools/scrolls/build_scrolls.py

For each enchantment in tools/scrolls/scrolls.json, writes
data/eternalreturn/recipe/enchanted_book/<enchantment>.json: a book in the middle, the enchantment's
1 to 3 themed items, and diamonds and amethyst shards around it, making a level I book. The recipes are
locked until the player has read that enchantment's scroll. Do not hand-edit the generated files.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', '..', 'src', 'main', 'resources')
OUT = os.path.join(ROOT, 'data', 'eternalreturn', 'recipe', 'enchanted_book')

# Rows of the 3 x 3 grid for 1, 2 and 3 themed items (X, Y, Z); B book, D diamond, A amethyst shard.
PATTERNS = {1: ['DXD', 'ABA', 'DAD'], 2: ['DXD', 'ABA', 'DYD'], 3: ['DXD', 'YBZ', 'DAD']}


def recipe_path(enchantment):
    namespace, path = enchantment.split(':')
    return path if namespace == 'minecraft' else f'{namespace}_{path}'


def main():
    table = json.load(open(os.path.join(HERE, 'scrolls.json'), encoding='utf-8'))
    os.makedirs(OUT, exist_ok=True)
    for old in os.listdir(OUT):
        os.remove(os.path.join(OUT, old))
    count = 0
    for enchantment, items in table.items():
        if enchantment.startswith('_'):
            continue
        assert 1 <= len(items) <= 3, enchantment
        pattern = list(PATTERNS[len(items)])
        key = {'B': {'item': 'minecraft:book'}, 'D': {'item': 'minecraft:diamond'}, 'A': {'item': 'minecraft:amethyst_shard'}}
        symbols = {}
        for slot, item in zip('XYZ', items):
            # The same item twice uses the same symbol.
            symbol = symbols.setdefault(item, slot)
            if symbol != slot:
                pattern = [row.replace(slot, symbol) for row in pattern]
            key[symbol] = {'item': item}
        recipe = {
            'type': 'minecraft:crafting_shaped',
            'category': 'misc',
            'group': 'eternalreturn_enchanted_book',
            'key': key,
            'pattern': pattern,
            'result': {'count': 1, 'id': 'minecraft:enchanted_book',
                       'components': {'minecraft:stored_enchantments': {'levels': {enchantment: 1}}}},
        }
        with open(os.path.join(OUT, recipe_path(enchantment) + '.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump(recipe, f, indent=2)
            f.write('\n')
        count += 1
    print(f'wrote {count} enchanted book recipes')


if __name__ == '__main__':
    main()
