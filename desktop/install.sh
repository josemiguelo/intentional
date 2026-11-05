#!/bin/sh
# Builds and installs the desktop guard for this user: the daemon in
# ~/.local/bin (a systemd user service), the Brave extension, the native
# messaging manifest that lets the extension reach the daemon, and the
# extension's path on Omarchy's --load-extension line in brave-flags.conf.
# Idempotent; rerun after pulling. Modes: `check` / `install` (default).
set -eu

repo=$(cd "$(dirname "$0")/.." && pwd)
config="${XDG_CONFIG_HOME:-$HOME/.config}"
bin="$HOME/.local/bin/intentional"
ext="${XDG_DATA_HOME:-$HOME/.local/share}/intentional/extension"
# Pinned by the "key" in browser/manifest.json.
ext_id=kpdjdhjnomciahnpmcipdmifplkfklad
host_manifest="$config/BraveSoftware/Brave-Browser/NativeMessagingHosts/dev.josemiguel.intentional.json"
unit="$config/systemd/user/intentional.service"
flags="$config/brave-flags.conf"

# The number of the last --load-extension line (the only one Chromium
# reads), empty without one.
last_line() {
  grep -n '^--load-extension=' "$flags" | tail -n 1 | cut -d: -f1
}

flags_ready() {
  [ -f "$flags" ] || return 1
  line=$(last_line)
  [ -n "$line" ] || return 1
  sed -n "${line}p" "$flags" | cut -d= -f2- | tr , '\n' | grep -qxF "$ext"
}

case "${1:-install}" in
check)
  [ -x "$bin" ] && [ -f "$host_manifest" ] && [ -f "$ext/manifest.json" ] &&
    systemctl --user is-enabled --quiet intentional.service && flags_ready
  ;;
install)
  (cd "$repo" && ./gradlew -q :desktop:linkReleaseExecutableLinuxX64)
  install -Dm755 "$repo/desktop/build/bin/linuxX64/releaseExecutable/intentional.kexe" "$bin"

  rm -rf "$ext"
  mkdir -p "$ext"
  cp -r "$repo/browser/." "$ext/"

  mkdir -p "$(dirname "$host_manifest")"
  cat >"$host_manifest" <<JSON
{
  "name": "dev.josemiguel.intentional",
  "description": "Intentional desktop guard",
  "path": "$bin",
  "type": "stdio",
  "allowed_origins": ["chrome-extension://$ext_id/"]
}
JSON

  mkdir -p "$config/intentional"
  [ -f "$config/intentional/groups.json" ] || echo '[]' >"$config/intentional/groups.json"

  install -Dm644 "$repo/desktop/intentional.service" "$unit"
  systemctl --user daemon-reload
  systemctl --user enable intentional.service
  systemctl --user restart intentional.service

  if ! flags_ready; then
    if [ -f "$flags" ] && line=$(last_line) && [ -n "$line" ]; then
      awk -v line="$line" -v ext="$ext" 'NR == line { $0 = $0 "," ext } 1' "$flags" >"$flags.tmp"
      mv "$flags.tmp" "$flags"
    else
      printf -- '--load-extension=%s\n' "$ext" >>"$flags"
    fi
    echo "Restart Brave to load the Intentional extension" >&2
  fi
  ;;
*) echo "usage: $0 [check|install]" >&2; exit 2 ;;
esac
