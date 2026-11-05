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

The day's time is in `~/.local/state/intentional/usage/<date>.json`, saved
every 10 seconds. `intentional status` prints each group's use.

## Install

Needs a JDK for Gradle and `libxcrypt-compat` (Kotlin/Native binaries link
`libcrypt.so.1`, which Arch's libxcrypt no longer ships).

```sh
desktop/install.sh          # build, install, (re)start; idempotent
desktop/install.sh check    # is everything in place?
```

It installs `~/.local/bin/intentional`, the `intentional.service` systemd user
unit (for the graphical session), the extension in
`~/.local/share/intentional/extension`, and Brave's native-messaging manifest
for it; and it adds the extension to Omarchy's `--load-extension` line in
`~/.config/brave-flags.conf` (Chromium reads only the last such line). Restart
Brave once to load it. If Omarchy rewrites that file, `check` fails and
`install` adds it back.

## Not yet

- The pause for considered apps.
- Idle: time counts while a window has focus, even with nobody there (the
  phone stops at screen off).
- Browsers other than Brave, and sites reached outside a browser (DNS
  blocking would cover those).

## Escapes

```sh
systemctl --user disable --now intentional.service
```

and remove the extension's path from `brave-flags.conf`. There's no strict
mode on the desktop: with sudo it would only be theatre.
