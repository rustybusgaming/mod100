# Localized Weather

A Fabric mod that replaces Minecraft's global weather system with **per-zone localized weather**. Rain, snow, and thunderstorms happen independently across the world, with smooth transitions at zone boundaries and Minecraft-style storm clouds.

## Features

- **Localized weather zones** — 256×256 block zones each have their own weather state
- **Biome-aware rules** — deserts stay dry, snowy biomes get snow, etc.
- **Smooth transitions** — rain/fog/sky color blend seamlessly across zone boundaries
- **Storm clouds** — blocky, Minecraft-style 3D cloud layers appear over storm zones, visible from a distance
- **Directional darkening** — sky, fog, and clouds darken toward approaching storms
- **Better Clouds compatible** — works alongside the Better Clouds mod

## Requirements

- Minecraft 1.21.9+
- Fabric Loader 0.19.2+
- Fabric API

## Optional Dependencies

- [Better Clouds](https://modrinth.com/mod/better-clouds) — enhanced cloud visuals that integrate with localized weather
- [YACL](https://modrinth.com/mod/yacl) — config library (required by Better Clouds)
- [Mod Menu](https://modrinth.com/mod/modmenu) — in-game mod configuration

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) and [Fabric API](https://modrinth.com/mod/fabric-api)
2. Drop the mod jar into your `mods` folder
3. Launch the game

## Commands

| Command | Description |
|---|---|
| `/localweather set <type>` | Set weather in your current zone (clear/rain/thunder/snow) |
| `/localweather query` | Check the weather in your current zone |

Requires operator permissions (level 2).

## Credits

Idea by **Mr. Random** on Discord.

## License

[MIT](LICENSE)
