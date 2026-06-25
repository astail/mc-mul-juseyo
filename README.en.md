# MulJuseyo

A Paper plugin that **reminds each player in their own chat to "drink water" once an hour** (server-side only).

When you get absorbed in the game, it's easy to forget to hydrate. MulJuseyo sends a reminder on an interval (**60 minutes by default**) to **each player who has opted in**, nudging real-world hydration.

> **Reminders are OFF by default (opt-in).** Each player runs `/muljuseyo on` to start receiving them. Once enabled, the choice persists across restarts.
>
> **No client mod required.** Just install the plugin on the server — vanilla clients work as-is.

## The problem it solves

"I was so focused that I went hours without drinking anything."
MulJuseyo times each player individually and **gently reminds them in chat (and with a bell sound) on an interval (1 hour by default).**

## Features

- **Opt-in reminders (OFF by default)**: only players who run `/muljuseyo on` receive reminders. `/muljuseyo off` stops them.
- **Persistent ON/OFF**: each player's choice is saved to `players.yml` and survives re-login and server restarts.
- **Periodic reminders**: times each player and sends "drink water" to their own chat on the configured interval (default 60 min). A bell sound can play too.
- **Per-player timing**: measured from each player's login, so reminders are staggered rather than firing for everyone at once.
- **Log your drink**: `/muljuseyo drink` acknowledges that you drank, resets the timer, and pushes the next reminder back.
- **Custom message**: change the reminder text freely in `config.yml`.

## Requirements

- Server: Paper 26.1.2 (build 69+)
- Java: 25
- Clients: vanilla (no mods, server-side only)

## Installation

1. Drop `MulJuseyo-1.0.0.jar` into `plugins/` and restart.
2. Reminders are OFF by default. Each player who wants them runs `/muljuseyo on`, then gets notified every interval (60 minutes by default).

Example:

```text
[MulJuseyo] 💧 Drink some water!
```

## Usage

Reminders are OFF by default; each player opts in. Tune behavior via `config.yml` (below) or in-game commands.

- Start / stop reminders for yourself → `/muljuseyo on` / `/muljuseyo off` (the choice persists across restarts)
- Log that you drank and reset the timer → `/muljuseyo drink`
- Show current settings, your ON/OFF state, and time until your next reminder → `/muljuseyo status`

## Commands

| Command | Description | Permission |
|---|---|---|
| `/muljuseyo on \| off` | Turn your reminders ON / OFF (OFF by default; persists across restarts) | `muljuseyo.use` |
| `/muljuseyo drink` | Log a drink and reset your reminder timer | `muljuseyo.use` |
| `/muljuseyo status` | Show current settings, your ON/OFF state, and time until your next reminder | `muljuseyo.use` |
| `/muljuseyo reload` | Reload the configuration | `muljuseyo.manage` |

Aliases: `/mj`, `/water`

## Permissions

| Permission node | Description | Default |
|---|---|---|
| `muljuseyo.notify` | Whether reminders can be received (even when ON, nothing arrives without this) | `true` (everyone) |
| `muljuseyo.use` | Self-service operations: `on` / `off` / `drink` / `status` | `true` (everyone) |
| `muljuseyo.manage` | `reload` and other server-wide operations | `op` |

## Configuration (`config.yml`)

```yaml
enabled: true              # server-wide switch for reminders (apply edits with /muljuseyo reload)
interval-minutes: 60       # how often to remind, in minutes (1-1440, default 60 = 1 hour)
remind-on-join: false      # also remind right after login (only for players who opted in)
notify-sound: true         # play a bell sound on the reminder
message: "水を飲んでください！"  # the reminder body text
```

> `enabled` is the server-wide switch (for admins). Each player turns their own notifications ON/OFF with `/muljuseyo on | off`.

## How it works / technical notes

- Each player has a "next reminder time"; a repeating task (`runTaskTimer`, every 10s) checks whether it's due. On join, `now + interval` is scheduled; when it fires, the reminder is sent and the next is set to `now + interval`.
- Timing is **measured from each player's login**, not a global broadcast, so reminders are staggered per player.
- Deadlines that pass while OFF or disabled are simply skipped and rescheduled, so **turning `/muljuseyo on` won't trigger a burst of reminders** (enabling re-stacks the next time to `now + interval`).
- Reminders arrive in the player's own chat. `notify-sound` adds a bell sound.

### Limitations

- Delivery may lag by up to the internal check interval (10s), which is irrelevant for an hourly reminder.
- The ON/OFF (opt-in) state is saved to `players.yml` and persists across server restarts.
- Next-reminder times (`nextAt`) are not persisted (kept only while online; reset on server restart).

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
