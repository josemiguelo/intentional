# Intentional

A personal, open-source system for intentional phone, TV and computer use,
designed around the evidence on problematic use: attack cues and habitual
openings, not screen-time minutes. On the phone it is a launcher; on a Google
TV and on an Omarchy (Hyprland) desktop it is a guard beside what is already
there. All three share one budget per group, synced end-to-end encrypted.

## What it does

**Friction with an honest exit**
- **Considered apps** open through a pause: a breathing animation, a line of
  today's usage ("opened 3× today, 40 min so far, 1 in the last hour") and
  equal-weight **close** / **open** choices. No walls, no shame.
- **However the app opens.** The guard brings up the pause for a notification,
  a link, recents or another launcher too, not only from home. After **open**,
  coming back within 5 minutes of leaving doesn't pause again.
- **A friction ladder** per app: a pause, a long pause, typing the app's name,
  or locked at night (during wind-down). Pause lengths are a setting.
- On the phone, considered apps never appear in lists and only show up in
  search after three typed letters of their name.

**Budgets across apps, sites and devices**
- **A group** (say "youtube") holds apps and websites and shares one daily
  budget: the phone's YouTube app, the TV's YouTube app and youtube.com in
  Brave all add up. A budget can change once a week.
- **Over budget, an app is closed** and its media playback stopped.
- **Over budget, a site is covered and its tab moved** to the browser's
  new-tab page, so reopening the browser never lands on it. A DNS-only VPN
  refuses the site's lookups in every app until tomorrow.

**Wind-down and batching**
- **Wind-down** (default 22:00–06:00): home shrinks to the essential
  favorites and considered-app friction doubles.
- **Notification batching:** notifications from the apps you pick are held
  and delivered together at set drop times.

**The right metric**
- The home footer counts *intentional opens* and *paused-and-closed* moments
  for today, across every device. Never minutes. A reflection screen shows
  the last two weeks.

## On each device

| Device | What Intentional is | Details |
| --- | --- | --- |
| Phone | The launcher: a text-only home (clock, date, a few favorites; no icons, badges or grid), type-to-open search, and the guard (an accessibility service) | [docs/SETUP.md](docs/SETUP.md) |
| Android TV / Google TV | A guard beside the stock launcher: budgets, the pause, wind-down; the app itself opens on settings and works with the remote | [docs/SETUP.md](docs/SETUP.md) (Android TV section) |
| Omarchy / Hyprland | A Kotlin/Native daemon (`intentional.service`) and a Brave extension: counts focused time per app and per site, closes over-budget windows, refuses over-budget sites, pauses considered apps in a terminal, and stops counting while the screen is locked | [docs/DESKTOP.md](docs/DESKTOP.md) |

On the phone, long-press an app (on home or in search) for its options; on a
TV, hold select. On the desktop, groups and considered apps are edited from the
command line (`intentional group …`, `intentional consider …`; `intentional
status` shows the day).

## Sync

With [sync](docs/SYNC.md) on, every device keeps its own day and shares it
through a Firebase Realtime Database, about once a minute:

- **What's shared:** each device's group time and counts, the groups, the
  considered apps with their friction, and the settings (wind-down, pause
  lengths). A group's use is the sum across devices.
- **Encrypted on the device:** Firebase holds sealed blobs. The weekly limit
  lock is enforced by the database rules, so editing a file can't get around
  it.
- **Offline,** a device keeps counting and enforcing, and catches up when it
  reconnects. Usage is kept for 30 days.

## Install and update

Each `v*` tag publishes one [GitHub release](https://github.com/josemiguelo/intentional/releases)
with both builds:

- **Phone and TV:** the signed APK, `intentional-<version>.apk`. Install it
  with `adb install -r`, or follow the repo with
  [Obtainium](https://github.com/ImranR98/Obtainium) for updates on the
  device. Then press Home and choose Intentional as the default launcher on
  the phone; on a TV, enable the guard as `docs/SETUP.md` describes.
- **Desktop:** `intentional-<version>-linux-x64.tar.gz`; unpack it and run its
  `install.sh` (or let a loadout `intentional` program do it). Needs
  `libxcrypt-compat` on Arch.

Every APK is signed with the same key, so any build installs over any other
without losing settings or permissions. To build yourself:

```sh
./gradlew assembleDebug                         # Android, debuggable
./gradlew assembleRelease -PappVersion=0.5.4    # Android, as releases ship
desktop/install.sh                              # desktop: build and install
./gradlew :core:allTests :desktop:linuxX64Test  # tests
```

To release: bump `VERSION` in `desktop/src/linuxX64Main/.../Main.kt`, then tag
`v<VERSION>` and push the tag.

## Repository

| Path | What |
| --- | --- |
| `app/` | The Android app: launcher, guard, site-budget VPN, sync on the phone and TV |
| `core/` | Kotlin Multiplatform, shared by every device: groups and budget rules, friction, wind-down, usage, settings, and sync (Firebase over REST, encryption) |
| `desktop/` | The desktop daemon (Kotlin/Native, linuxX64), its pause and command line, and the installer |
| `browser/` | The Brave extension: reports the active tab, refuses over-budget sites |
| `firebase/` | The Realtime Database rules |
| `docs/` | [SETUP.md](docs/SETUP.md) (phone and TV), [DESKTOP.md](docs/DESKTOP.md), [SYNC.md](docs/SYNC.md) |

## Strict mode

Strict mode is mandatory on Android: the guard accessibility service blocks the
system screens for switching the default launcher, disabling the guard, (with
uninstall protection on) uninstalling the app, and switching off site budgets
(the VPN pages, Private DNS, a browser's own secure DNS). There is no in-app
off switch; the adb escapes in [docs/SETUP.md](docs/SETUP.md) are the last
resort. The desktop has none: with sudo it would only be theatre.

## Design rationale

The feature set follows the intervention literature: launch-time friction with
an escape option is the best-evidenced on-device mechanism (Grüning et al.
2023, *PNAS*: 36% of opens abandoned at the prompt, 37% fewer attempts after
six weeks); cue removal is the layer timers can't touch; and reduced minutes
alone don't improve well-being, so Intentional measures intentionality
instead. Utility apps stay one frictionless tap away — breaking legitimate
muscle memory is the main reason people abandon minimalist launchers.
