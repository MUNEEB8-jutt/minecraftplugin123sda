# Architecture

## Core

`LegendaryWeaponsPlugin` boots all services, listeners, and commands.

## Config

`ConfigManager` loads YAML from `plugins/LegendaryWeaponsSMP/config/` and provides typed accessors for combat, ritual, and resource-pack settings.

## Weapons

- `WeaponType` defines canonical weapon IDs/material/model data.
- `WeaponItemFactory` creates and resolves legendary items using persistent data keys.
- `LegendaryStateStore` persists global uniqueness and ritual state.
- `LegendaryItemListener` enforces storage restrictions and owner transfer on pickup.

## Abilities

- `AbilityListener` implements active abilities and passives for all 8 legendary weapons.
- `CooldownManager` manages per-player cooldown state and bossbar visualization.

## Rituals + Structures + UI

- `RitualBoxService` provides craftable ritual trigger item.
- `RitualSelectionMenu` opens on ritual box placement and weapon selection.
- `RitualStructureBuilder` creates 13x13 ritual build plans.
- `RitualManager` coordinates build animation, cinematic effects, 15-minute timers, broadcasts, completion rewards, and anti-duplication.
- `RitualProtectionListener` blocks griefing in active ritual zones.

## Particles + Animations

- `ParticleService` centralizes themed effects and storms.
- `AnimationService` rotates floating ritual weapon displays.

## Resource Pack

- `ResourcePackManager` auto-generates pack metadata and models, copies high-detail scripted textures from bundled resources, generates sounds/fonts/particles, zips, hashes, and hosts via embedded HTTP.
- `ResourcePackListener` pushes pack on join and handles status feedback.

## Commands

`LegendaryCommand` handles admin and gameplay operations:

- give
- startRitual
- stopRitual
- remove
- reload
- list
- ritualinfo
