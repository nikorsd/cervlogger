# CervLogger

Block logging, lookup, rollback and restore for NeoForge 1.21.1 servers.

CervLogger records player activity in a local database and lets staff inspect block history, search the logs and roll back grief — with no client-side installation required.

## Features

- **Logging**: block place/break, explosions, fire, container transactions, signs, buckets, block interactions (doors, levers, buttons, etc.), entity kills, chat, commands, login/logout sessions
- **Inspector** (`/cl i`): left/right click any block to instantly see its history
- **Lookup / rollback / restore** filtered by player, time, radius, action and block types
- **Storage**: SQLite by default (zero configuration) or MySQL — both drivers are bundled in the jar
- **Server-side only**: vanilla and modded clients can join without installing anything

## Commands

Root commands: `/cl` and `/cervlogger`.

| Command | Description | Permission |
| --- | --- | --- |
| `/cl help` | Show the command list | everyone |
| `/cl inspect` (`/cl i`) | Toggle the inspector | everyone |
| `/cl lookup <params>` (`/cl l`) | Search the logs | op (level 2) |
| `/cl rollback <params>` (`/cl rb`) | Roll back changes | op (level 2) |
| `/cl restore <params>` (`/cl rs`) | Re-apply rolled back changes | op (level 2) |
| `/cl near [radius]` | Lookup around you (default radius 5) | op (level 2) |
| `/cl structures [r:<radius>]` | List loaded contraptions / physics structures (Create, Sable) | op (level 2) |
| `/cl purge t:<time>` | Delete old log data | op (level 2) |
| `/cl status` | Mod and database status | op (level 2) |

### Parameters

```
u:<player> t:<time> r:<radius|#global> a:<action> b:<blocks> e:<exclude> w:<world> p:<page>
```

Actions: `block`, `+block`, `-block`, `click`, `explosion`, `burn`, `container`, `+container`, `-container`, `kill`, `sign`, `chat`, `command`, `session`

Time examples: `t:2h`, `t:1d`, `t:3d12h`

Example:

```
/cl rollback u:Notch t:2h r:20 a:-block
```

## Installation

1. Install [NeoForge](https://neoforged.net/) 21.1.x for Minecraft 1.21.1 on your server.
2. Drop the CervLogger jar into the `mods` folder.
3. Start the server — the config and database are created automatically.

## Configuration

Created on first run at `config/cervlogger/config.properties`.

Key options: per-category logging toggles, `max-radius`, `default-radius`, `lookup-page-size`, `log-interact-block-ids` (comma-separated list of extra block IDs whose clicks should be logged).

### SQLite (default)

The database is stored at `config/cervlogger/database.db`. No setup required.

### MySQL

```properties
storage-type=mysql
mysql-host=127.0.0.1
mysql-port=3306
mysql-database=cervlogger
mysql-username=root
mysql-password=secret
```

The database must exist beforehand (`CREATE DATABASE cervlogger CHARACTER SET utf8mb4;`); tables are created automatically.

## Building from source

Requires JDK 21.

```bash
./gradlew build
```

The jar is produced in `build/libs/`. For a development instance use `./gradlew runClient` or `./gradlew runServer`.

## Compatibility notes

- Rollback and restore run on the main server thread, so the mod is safe to use alongside optimization mods such as c2me and Lithium.
- Blocks moved by Create contraptions or Sable sub-levels bypass standard block events and are not logged while in motion; use `/cl structures` to list loaded structures.

## Credits

CervLogger is an independent, from-scratch implementation for NeoForge. Its command set and behaviour are modelled after the [CoreProtect](https://github.com/PlayPro/CoreProtect) Bukkit plugin by Intelli/PlayPro.

## License

[AGPL-3.0-only](LICENSE). If you run a modified version on a publicly accessible server, you must offer its users access to the modified source code (see section 13 of the license).
