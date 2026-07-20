# Changelog

All notable changes to this project will be documented in this file.

## [1.0.0] - 2026-07-20

Initial release.

### Added

- Block logging: place, break, explosions, fire, signs, buckets, block interactions (doors, levers, etc.)
- Container logging: items added/removed from chests, barrels, furnaces and other containers
- Entity kill, chat, command and login/logout session logging
- Inspector mode (`/cl inspect`): click any block to see its history
- Lookup with filters: player, time, radius, action type, include/exclude blocks, world, pagination
- Rollback and restore (`/cl rollback`, `/cl restore`), including safe container handling
- `/cl near [radius]` for quick lookups around the player
- `/cl structures` overview of loaded Create contraptions and physics structures
- `/cl purge` for trimming old data
- SQLite storage out of the box, optional MySQL support (both drivers bundled)
- Permission model: inspector and help available to everyone, all other commands require operator permission level 2
