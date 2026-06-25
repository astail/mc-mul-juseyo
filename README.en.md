# MulJuseyo

A Paper plugin that **reminds each player in their own chat to "drink water" once an hour** (server-side only).

When you get absorbed in the game, it's easy to forget to hydrate. MulJuseyo sends every player a reminder on an interval (**60 minutes by default**) to nudge real-world hydration.

> **No client mod required.** Just install the plugin on the server — vanilla clients get the reminders.

## The problem it solves

"I was so focused that I went hours without drinking anything."
MulJuseyo times each player individually and **gently reminds them in chat (and with a bell sound) on an interval (1 hour by default).**

## Features

- **Periodic reminders**: times each player and sends "drink water" to their own chat on the configured interval (default 60 min). A bell sound can play too.
- **Per-player timing**: measured from each player's login, so reminders are staggered rather than firing for everyone at once.
- **Log your drink**: `/muljuseyo drink` acknowledges that you drank, resets the timer, and pushes the next reminder back.
- **Mute / global ON-OFF**: `mute` silences reminders for yourself; `on|off` toggles the whole server.
- **Custom message**: change the reminder text freely in `config.yml`.

## Requirements

- Server: Paper 26.1.2 (build 69+)
- Java: 25
- Clients: vanilla (no mods, server-side only)

## Installation

1. Drop `MulJuseyo-1.0.0.jar` into `plugins/` and restart.
2. That's it — reminders start automatically. Each player is notified every 60 minutes from login.

Example:

```text
[MulJuseyo] 💧 Drink some water!
```

## Usage

Works out of the box with defaults. Tune behavior via `config.yml` (below) or in-game commands.

- Log that you drank and reset the timer → `/muljuseyo drink`
- Silence reminders for yourself → `/muljuseyo mute` / restore → `/muljuseyo unmute`
- Show current settings and time until your next reminder → `/muljuseyo status`

## Commands

| Command | Description | Permission |
|---|---|---|
| `/muljuseyo drink` | Log a drink and reset your reminder timer | `muljuseyo.use` |
| `/muljuseyo status` | Show current settings and time until your next reminder | `muljuseyo.use` |
| `/muljuseyo mute \| unmute` | Stop / resume reminders for yourself (resets on restart) | `muljuseyo.use` |
| `/muljuseyo on \| off` | Enable / disable reminders globally | `muljuseyo.manage` |
| `/muljuseyo reload` | Reload the configuration | `muljuseyo.manage` |

Aliases: `/mj`, `/water`

## Permissions

| Permission node | Description | Default |
|---|---|---|
| `muljuseyo.notify` | Receive hydration reminders | `true` (everyone) |
| `muljuseyo.use` | Use `drink` / `status` / `mute` | `true` (everyone) |
| `muljuseyo.manage` | `on` / `off` / `reload` (server-wide operations) | `op` |

## Configuration (`config.yml`)

```yaml
enabled: true              # whether reminders run (toggle with /muljuseyo on|off)
interval-minutes: 60       # how often to remind, in minutes (1-1440, default 60 = 1 hour)
remind-on-join: false      # also remind right after a player logs in
notify-sound: true         # play a bell sound on the reminder
message: "水を飲んでください！"  # the reminder body text
```

## How it works / technical notes

- Each player has a "next reminder time"; a repeating task (`runTaskTimer`, every 10s) checks whether it's due. On join, `now + interval` is scheduled; when it fires, the reminder is sent and the next is set to `now + interval`.
- Timing is **measured from each player's login**, not a global broadcast, so reminders are staggered per player.
- Deadlines that pass while muted or disabled are simply skipped and rescheduled, so **muting then unmuting (or `/muljuseyo on`) won't trigger a burst of reminders**.
- Reminders arrive in the player's own chat. `notify-sound` adds a bell sound.

### Limitations

- Delivery may lag by up to the internal check interval (10s), which is irrelevant for an hourly reminder.
- Next-reminder times and mute state are not persisted (reset on server restart).

## Build

```bash
./deploy.sh        # macOS native (JDK 25 + Maven). Output: target/MulJuseyo-1.0.0.jar
# or
mvn -B clean package
```

Pushing a `v*` tag triggers GitHub Actions (`.github/workflows/build.yml`) to build and attach the jar to a release.

## Deploying to a server

Place the jar in the server's `plugins/` and restart. See the Japanese [README.md](README.md) for Docker / `itzg/minecraft-server` auto-download instructions.

## License

MIT License — see [LICENSE](LICENSE).
