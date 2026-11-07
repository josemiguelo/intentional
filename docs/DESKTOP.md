# Desktop (Omarchy / Hyprland)

On the desktop Intentional is a guard, not a launcher: a small Kotlin/Native
daemon (`desktop/`, built on the shared `:core`) and a Brave extension
(`browser/`).

- **App time** — the daemon follows Hyprland's focus events and counts focused
  time per window class.
- **Site time** — while a Brave window has focus, the time goes to the active
  tab's site instead, which the extension reports through native messaging.
  Omarchy's web apps are Brave windows too (`brave-claude.ai__-Default`), so
  they count as their site.
- **Budgets** — the same groups as on the phone, in
  `~/.config/intentional/groups.json` (the phone's JSON format; on a desktop
  `packages` are Hyprland window classes). Re-read when the file changes.
- **Enforcement** — an over-budget app's window is closed when it gets focus
  (like SUPER+Q), with a notification. Over-budget sites are refused by the
  extension until tomorrow: open tabs move to its "budget used" page (never
  left on the site, which would be a dead end on reopening) and new visits
  are redirected there.

Nothing counts while the screen is locked (an ext-session-lock, which
Hyprland reports as `LOCK` in `j/monitors`), nor Omarchy's screensaver window,
which takes focus after a couple of idle minutes and so ends a tab's or app's
time by itself. With Omarchy's "stay awake" on there's no screensaver, so time
counts until something else takes focus.

Each pause's decision counts, as on the phone: opened, or paused and closed.
The counts sync with the day's time, so `intentional status` and the phone's
home screen show every device's.

The day's time is in `~/.local/state/intentional/usage/<date>.json`, saved
every 10 seconds. `intentional status` prints each group's use;
`intentional history [days]` each day's before it (14 by default) and the
weeks before those, across every device with sync.

## Install

**From a release** (no checkout, JDK or Kotlin/Native needed): each `v*` tag
publishes `intentional-<version>-linux-x64.tar.gz` and its `.sha256`
(`.github/workflows/release-desktop.yml`). Unpack it and run its `install.sh`;
loadout's `intentional` program does exactly that. Needs `libxcrypt-compat`:
Kotlin/Native binaries link `libcrypt.so.1`, which Arch's libxcrypt no
longer ships.

**From a checkout** (needs a JDK for Gradle):

```sh
desktop/install.sh          # build, then install as a release would
desktop/install.sh check    # is everything in place? prints the version
desktop/package.sh --archive  # just build and pack a release locally
```

Either way it installs `~/.local/bin/intentional`, the `intentional.service`
systemd user unit (for the graphical session), the extension in
`~/.local/share/intentional/extension` (the plain files in `browser/`, loaded
unpacked; the `key` in its manifest pins the ID the native-messaging manifest
allows), Brave's native-messaging manifest, and a copy of `install.sh` for
`check`; and it adds the extension to Omarchy's `--load-extension` line in
`~/.config/brave-flags.conf` (Chromium reads only the last such line). Quit
Brave fully once (web-app windows keep it running) and start it again to load
the extension. If Omarchy rewrites that file, `check` fails and `install` adds
it back.

**Releasing:** bump `VERSION` in `desktop/.../Main.kt`, then tag `v<VERSION>`
and push the tag.

## Groups and considered apps from the command line

The phone can't see desktop apps, so the desktop puts its own in groups, as
`hypr:<window class>` (with sync on, every device gets the edit within a
minute):

```sh
intentional apps                          # today's window classes, time, group
intentional group list
intentional group add youtube freetube    # or --focused, from a keybinding
intentional group remove youtube freetube
intentional group limit youtube 20        # once a week; with sync, the server decides
```

**Considered apps** open through a pause, as on the phone: a new window of
one is hidden (on `special:intentional`) while the pause breathes in a
terminal of its own (kitty, alacritty, ghostty or foot); **c** closes the app,
**o** shows it once the pause is done. After **open**, coming back within 5
minutes of leaving doesn't pause again; apps opening in the first 30 seconds
of the session (autostart) aren't paused. The friction ladder is the
phone's: `pause`, `long`, `type` (type its name), `night` (locked during
wind-down). The list, the pause lengths and the wind-down window are the
phone's, through sync (`intentional settings` shows them).

```sh
intentional consider steam long           # or --focused
intentional unconsider steam
intentional considered
```

## Not yet

- Browsers other than Brave, and sites reached outside a browser (DNS
  blocking would cover those).

## Escapes

```sh
systemctl --user disable --now intentional.service
```

and remove the extension's path from `brave-flags.conf`. There's no strict
mode on the desktop: with sudo it would only be theatre.
