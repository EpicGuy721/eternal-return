"""Builds every fish's resources from src/main/resources/eternalreturn/fish.json (which the game also reads to
register the fish and the fishermen's trades) and the textures in tools/fish/textures/.

Usage: python tools/fish/build_fish.py

Writes:
- each fish's item texture (the source image scaled down to 16 x 16, pixel for pixel) and item model, and chum's
  texture (tools/fish/textures/chum.png);
- the language lines: each fish's name, its tooltip (rarity, where and when it bites, which fishermen buy it) and the
  rarity names (other lines in en_us.json are kept);
- one biome tag per habitat, eternalreturn:fish_habitat/<habitat>;
- the catch tables: eternalreturn:gameplay/fishing/fish (water, vanilla's fish included) and
  eternalreturn:gameplay/lava_fishing/fish (the Nether), each fish weighted by its rarity, with its habitat, time,
  weather, moon, depth and height conditions; glistering melon bait doubles the weight of rare and rarer water fish;
- the item tags eternalreturn:raw_fish and minecraft:fishes;
- docs/fish.md, the table of every fish.
Do not hand-edit the generated files.
"""
import json
import os

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
NS = 'eternalreturn'
TEXTURES = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'textures')
TABLE = json.load(open(os.path.join(RES, NS, 'fish.json'), encoding='utf-8'))
FISH = TABLE['fish']
HABITATS = TABLE['habitats']
RARITIES = TABLE['rarities']
REGION_ORDER = ['plains', 'desert', 'savanna', 'snow', 'taiga', 'jungle', 'swamp']
# Rare and rarer water fish bite twice as often on glistering melon.
MELON_RARITIES = {'rare', 'epic', 'legendary'}
TIMES = {
    'day': [(0, 12000)],
    'night': [(13000, 23000)],
    'dawn_dusk': [(22500, 23999), (0, 1000), (11500, 13500)],
}
TIME_LABELS = {'day': 'by day', 'night': 'at night', 'dawn_dusk': 'at dawn and dusk'}
WEATHER = {'clear': {'raining': False}, 'rain': {'raining': True}, 'thunder': {'thundering': True}}
WEATHER_LABELS = {'clear': 'in clear weather', 'rain': 'in rain or snow', 'thunder': 'in thunderstorms'}
# Moon phase as the game counts it: 0 is the full moon, 4 the new moon.
MOONS = {'full': [0], 'new': [4]}
MOON_LABELS = {'full': 'under a full moon', 'new': 'under a new moon'}


def path(*parts):
    return os.path.join(RES, *parts)


def write_json(file, data, indent=2):
    os.makedirs(os.path.dirname(file), exist_ok=True)
    with open(file, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=indent, ensure_ascii=False)
        f.write('\n')


def fishermen(fish):
    """The villager regions whose fishermen buy this fish, or None for every fisherman."""
    regions = set()
    for habitat in fish['habitats']:
        buyers = HABITATS[habitat].get('fishermen')
        if not buyers:
            return None
        regions.update(buyers)
    if fish['nether'] or not fish['habitats']:
        return None
    return [r for r in REGION_ORDER if r in regions]


def texture(source, target):
    image = Image.open(os.path.join(TEXTURES, source)).convert('RGBA')
    if image.size != (16, 16):
        image = image.resize((16, 16), Image.NEAREST)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    image.save(target)


def biome_entry(biome):
    return {'id': biome[1:], 'required': False} if biome.startswith('?') else biome


def conditions(fish):
    out = []
    habitats = fish['habitats']
    if habitats:
        checks = [{'condition': 'minecraft:location_check', 'predicate': {'biomes': f'#{NS}:fish_habitat/{h}'}} for h in habitats]
        out.append(checks[0] if len(checks) == 1 else {'condition': 'minecraft:any_of', 'terms': checks})
    position = {}
    if 'min_y' in fish or 'max_y' in fish:
        y = {}
        if 'min_y' in fish:
            y['min'] = fish['min_y']
        if 'max_y' in fish:
            y['max'] = fish['max_y']
        position['position'] = {'y': y}
    if fish.get('underground'):
        position['can_see_sky'] = False
    if position:
        out.append({'condition': 'minecraft:location_check', 'predicate': position})
    if 'time' in fish:
        ranges = [{'condition': 'minecraft:time_check', 'period': 24000, 'value': {'min': lo, 'max': hi}} for lo, hi in TIMES[fish['time']]]
        out.append(ranges[0] if len(ranges) == 1 else {'condition': 'minecraft:any_of', 'terms': ranges})
    if 'weather' in fish:
        out.append(dict({'condition': 'minecraft:weather_check'}, **WEATHER[fish['weather']]))
    if 'moon' in fish:
        out.append({'condition': f'{NS}:moon_phase', 'phases': MOONS[fish['moon']]})
    if 'min_depth' in fish:
        out.append({'condition': f'{NS}:deep_water', 'min_depth': fish['min_depth']})
    return out


def entry(fish_id, fish, weight, extra=()):
    item = {'type': 'minecraft:item', 'name': f'{NS}:{fish_id}', 'weight': weight}
    conds = conditions(fish) + list(extra)
    if conds:
        item['conditions'] = conds
    return item


def catch_table(name, entries):
    write_json(path('data', NS, 'loot_table', 'gameplay', name, 'fish.json'), {
        'type': 'minecraft:fishing',
        'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': entries}],
        'random_sequence': f'{NS}:gameplay/{name}/fish',
    })


def join(words, last='and'):
    return words[0] if len(words) == 1 else ', '.join(words[:-1]) + f' {last} ' + words[-1]


def where_and_when(fish):
    """One line: where it bites and under what conditions, e.g. "Rivers and swamps, at night, in rain or snow"."""
    if fish['habitats']:
        # Any one of its habitats will do.
        place = join([HABITATS[h]['label'] for h in fish['habitats']], 'or')
        if fish['nether']:
            place = 'lava in ' + place
    elif fish['nether']:
        place = 'any lava'
    else:
        place = 'underground water'
    parts = [place]
    if 'max_y' in fish:
        parts.append(f"below y {fish['max_y']}")
    if 'min_y' in fish:
        parts.append(f"above y {fish['min_y']}")
    if 'min_depth' in fish:
        parts.append(f"over water {fish['min_depth']}+ blocks deep")
    if 'time' in fish:
        parts.append(TIME_LABELS[fish['time']])
    if 'weather' in fish:
        parts.append(WEATHER_LABELS[fish['weather']])
    if 'moon' in fish:
        parts.append(MOON_LABELS[fish['moon']])
    text = ', '.join(parts)
    return text[0].upper() + text[1:]


def buyers_line(fish):
    regions = fishermen(fish)
    return 'Bought by every fisherman' if regions is None else f'Bought by {join(regions)} fishermen'


def price(fish):
    sell = fish['sell']
    return f"{sell['count']} for {sell['emeralds']} emerald{'s' if sell['emeralds'] > 1 else ''}"


def main():
    # Textures and models.
    for fish_id, fish in FISH.items():
        texture(fish['texture'], path('assets', NS, 'textures', 'item', fish_id + '.png'))
        write_json(path('assets', NS, 'models', 'item', fish_id + '.json'),
                   {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{fish_id}'}})
    texture('chum.png', path('assets', NS, 'textures', 'item', 'chum.png'))

    # Language lines: drop the old fish lines, then write the current ones.
    lang_file = path('assets', NS, 'lang', 'en_us.json')
    lang = json.load(open(lang_file, encoding='utf-8'))
    lang = {k: v for k, v in lang.items() if not k.startswith(f'fish.{NS}.')
            and not (k.startswith(f'item.{NS}.') and k.split('.')[2] in FISH)}
    for rarity in RARITIES:
        lang[f'fish.{NS}.rarity.{rarity}'] = rarity.title()
    for fish_id, fish in FISH.items():
        lang[f'item.{NS}.{fish_id}'] = fish['name']
        lang[f'fish.{NS}.{fish_id}.habitat'] = where_and_when(fish)
        lang[f'fish.{NS}.{fish_id}.buyers'] = buyers_line(fish)
    write_json(lang_file, lang, indent='\t')

    # Habitat biome tags.
    for habitat, data in HABITATS.items():
        write_json(path('data', NS, 'tags', 'worldgen', 'biome', 'fish_habitat', habitat + '.json'),
                   {'values': [biome_entry(b) for b in data['biomes']]})

    # Catch tables.
    water = [{'type': 'minecraft:loot_table', 'value': 'minecraft:gameplay/fishing/fish', 'weight': 100}]
    lava = []
    for fish_id, fish in FISH.items():
        weight = RARITIES[fish['rarity']]['weight']
        (lava if fish['nether'] else water).append(entry(fish_id, fish, weight))
        if not fish['nether'] and fish['rarity'] in MELON_RARITIES:
            water.append(entry(fish_id, fish, weight, [{'condition': f'{NS}:bait', 'items': 'minecraft:glistering_melon_slice'}]))
    catch_table('fishing', water)
    catch_table('lava_fishing', lava)

    # Item tags.
    ids = [f'{NS}:{fish_id}' for fish_id in FISH]
    write_json(path('data', NS, 'tags', 'item', 'raw_fish.json'), {'values': [
        'minecraft:cod', 'minecraft:salmon', 'minecraft:tropical_fish', 'minecraft:pufferfish', *ids,
        {'id': '#c:foods/raw_fish', 'required': False}]})
    write_json(path('data', 'minecraft', 'tags', 'item', 'fishes.json'), {'replace': False, 'values': ids})

    # docs/fish.md.
    rows = ['| Fish | Rarity | Where and when | Bought by | Fisherman pays | From level |', '|---|---|---|---|---|---|']
    order = list(RARITIES)
    for nether in (False, True):
        for fish_id, fish in sorted(((k, v) for k, v in FISH.items() if v['nether'] == nether), key=lambda kv: (order.index(kv[1]['rarity']), kv[1]['name'])):
            regions = fishermen(fish)
            rows.append(f"| {fish['name']} | {fish['rarity'].title()} | {where_and_when(fish)} | {'every fisherman' if regions is None else join(regions)} "
                        f"| {price(fish)} | {RARITIES[fish['rarity']]['level']} |")
    habitat_rows = []
    for habitat, data in HABITATS.items():
        names = ', '.join(('(' + b[1:] + ')') if b.startswith('?') else b for b in data['biomes'])
        habitat_rows.append(f"| {data['label']} | {names} | {', '.join(data['fishermen']) if data.get('fishermen') else 'every fisherman'} |")
    baits = ', '.join(f"{b.split(':')[1].replace('_', ' ')} ({v['count']} for {v['emeralds']} emerald)" for b, v in TABLE['baits'].items())
    doc = f"""# Fish

Generated by `tools/fish/build_fish.py` from `src/main/resources/eternalreturn/fish.json`; do not edit by hand.

{len(FISH)} fish: {sum(not f['nether'] for f in FISH.values())} caught in water with chum or glistering melon, and {sum(f['nether'] for f in FISH.values())} caught in lava in the Nether with magma cream. All of them fill like raw salmon and can't be cooked.

A fish bites only where all its conditions hold. Its weight in the catch table comes from its rarity ({', '.join(f'{r} {v["weight"]}' for r, v in RARITIES.items())}; vanilla's cod, salmon, tropical fish and pufferfish together 100), so in any one spot the odds depend on which other fish bite there too. Glistering melon doubles the odds of rare, epic and legendary water fish.

Fishermen buy each fish from the level of its rarity ({', '.join(f'{r} {v["level"]}' for r, v in RARITIES.items())}), every fish of that rarity at once rather than a random two. A fisherman only buys the freshwater fish of his own region (his biome: plains, desert, savanna, snow, taiga, jungle or swamp); every fisherman buys fish from the sea, cherry groves, underground water and the Nether. From novice on, every fisherman also sells bait: {baits}.

{chr(10).join(rows)}

## Habitats

| Habitat | Biomes | Fishermen |
|---|---|---|
{chr(10).join(habitat_rows)}

Biomes in brackets are from Moderner Beta or other world types, used when present.
"""
    with open(os.path.join(ROOT, 'docs', 'fish.md'), 'w', encoding='utf-8', newline='\n') as f:
        f.write(doc)
    print(f'{len(FISH)} fish: textures, models, language lines, {len(HABITATS)} habitat tags, catch tables, tags, docs/fish.md')


if __name__ == '__main__':
    main()
