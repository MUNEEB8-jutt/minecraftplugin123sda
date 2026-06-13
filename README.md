# LegendaryWeaponsSMP

Paper plugin for Minecraft 1.21.7-1.21.11 with 15 legendary weapons, ritual forging, floating-island ritual dungeons, custom abilities, a paged weapon archive, and automatic resource-pack generation/hosting.

## Build

Java 21 or newer and Maven are required.

```powershell
mvn -DskipTests package
```

Output jar:

`target/LegendaryWeaponsSMP.jar`

## Texture Pipeline

High-detail weapon textures are generated with:

```powershell
powershell -ExecutionPolicy Bypass -File tools/generate_weapon_textures.ps1
```

Bundled assets are stored in:

`src/main/resources/rp/`

## Runtime Files

Plugin generates and uses:

- `plugins/LegendaryWeaponsSMP/config/general.yml`
- `plugins/LegendaryWeaponsSMP/config/weapons.yml`
- `plugins/LegendaryWeaponsSMP/config/rituals.yml`
- `plugins/LegendaryWeaponsSMP/config/cooldowns.yml`
- `plugins/LegendaryWeaponsSMP/config/particles.yml`
- `plugins/LegendaryWeaponsSMP/legendary-state.yml`
- `plugins/LegendaryWeaponsSMP/resourcepack/pack/`
- `plugins/LegendaryWeaponsSMP/resourcepack/LegendaryWeaponsSMP.zip`

## Commands

- `/legendary give <player> <weapon|core> <id>`
- `/legendary ritual spawn <weapon|all>`
- `/legendary ritual reveal <weapon|dungeon>`
- `/legendary ritual unreveal [weapon]`
- `/legendary ritual stop <weapon>`
- `/legendary ritual delete <weapon|dungeon>`
- `/legendary ritual setlimit <number>`
- `/legendary setweaponlimit <number>`
- `/legendary reload`

## Modules

- `core`
- `weapons`
- `rituals`
- `abilities`
- `structures`
- `particles`
- `animations`
- `commands`
- `config`
- `resourcepack`
- `ui`

## Checked-In Deliverables

- Plugin source under `src/main/java`
- Plugin resources under `src/main/resources`
- Floating-island and ritual schematics under `src/main/resources/schematics`
- Resource-pack models, textures, fonts, and HUD assets under `src/main/resources/rp`
