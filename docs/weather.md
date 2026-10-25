# Local weather

In the overworld, weather is local: rain and thunder come as storms that form, drift with the wind and break up, instead of the same weather everywhere at once. Other dimensions keep vanilla's. Code: `com.eternalreturn.weather` (server and shared), `com.eternalreturn.client.weather` (what players see). Settings: the `weather` section of `config/eternalreturn.json`.

## Storms

- A storm is a patch of rain (snow where the biome is cold) about 220 to 740 blocks across, round but with a ragged edge. It rains hardest in its middle six tenths and thins out gradually from there to nothing 40 blocks past its edge, so walking out of one the rain eases off over a hundred blocks or more. It builds up over its first minute, holds, and dies away over its last; it lasts 5 to 15 minutes.
- Storms form upwind of players, 500 to 1,400 blocks off, so they come over. Each player has about `stormsNearPlayers` (3) storms within 1,600 blocks over the wettest country (jungles, swamps, the sea, rivers, taigas), fewer over ordinary country, and very few over deserts, savannas and badlands, where storms also weaken and wear out twice as fast. Storms far from every player (4,000 blocks) go.
- About a quarter are thunderstorms (`thunderstormChance` 0.25), more over wet country. Lightning strikes only inside thunderstorms, two and a half times as often there as vanilla's thunder does everywhere.
- A thunderstorm over snow is a blizzard (`blizzards`): no lightning, snow piling up four times as fast, and thick snow driven sideways with fog closing in to about 24 blocks.

## Wind

The wind blows from one direction across the world, turning slowly and changing strength every couple of minutes, between 1 and 3 blocks a second. Storms move with it, and so do the clouds; it slants the rain a little and the snow a lot, a blizzard's most (at most 45 degrees), each sheet leaning upwind from where it lands, and stopping where the ground upwind is in the way, so none slants through blocks into a hole (`windyRain`).

## What rain does

Everything that asks whether it rains at a spot asks the storms there: undead and vampires burning by day, fire going out and spreading, crops growing faster in the rain, farmland, cauldrons filling, snow piling up, fishing (fish that bite in the rain or in thunder), bees staying in the hive, endermen, tridents with Riptide, slimes turning up in the rain at night, Channeling. Nothing rains above the clouds.

## What players see

Each player's game is told of the storms near them every second. The sky and clouds darken as a storm comes near (from 160 blocks beyond its edge), rain falls on them only inside one, and the rain of a storm a short way off can be seen falling, out to 32 blocks (16 on fast graphics).

## Commands

- `/weather rain` and `/weather thunder` make a storm where the command is run (at the world spawn from the console or another dimension), raining within seconds, for the time given.
- `/weather clear` clears every storm and keeps new ones from forming for the time given.
- `/eternalreturn weather` lists the storms within 3,200 blocks: how big, how strong, and where, and the wind.
- With the `doWeatherCycle` game rule off, storms and the wind stand still.

## Settings (`weather` in the config)

| Setting | Default | |
|---|---|---|
| `localWeather` | true | Off: vanilla's weather. |
| `stormsNearPlayers` | 3.0 | Storms within 1,600 blocks of each player over the wettest country. |
| `thunderstormChance` | 0.25 | The share of storms that thunder. |
| `blizzards` | true | Thunderstorms over snow become blizzards. |
| `windyRain` | true | The wind slants the rain and snow and drifts the clouds. |

## Tests

`WeatherTests` (batches `weather` and `weather_command`): rain and thunder only under a storm, the sky darkening beyond it; drifting and ageing; how rainy each kind of country is; blizzards over snow; the weather check fishing and Channeling use; saving storms and sending them to players; `/weather` and other code setting the weather. Tests that need rain put a storm over themselves (`Weathers`).
