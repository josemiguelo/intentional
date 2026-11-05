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

The day's time is in `~/.local/state/intentional/usage/<date>.json`, saved
every 10 seconds. `intentional status` prints each group's use.

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

## Not yet

- The pause for considered apps.
- Browsers other than Brave, and sites reached outside a browser (DNS
  blocking would cover those).

## Escapes

```sh
systemctl --user disable --now intentional.service
```

and remove the extension's path from `brave-flags.conf`. There's no strict
mode on the desktop: with sudo it would only be theatre.
