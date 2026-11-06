#!/bin/sh
# Sets up Intentional on an Android phone or TV over adb, in one go: installs
# the latest release's APK (checked against its SHA-256), grants what adb can
# grant (usage access, restricted settings so the guard can run, the battery
# exemption), drops in sync.json from the "Intentional sync" item in 1Password
# with this device's name, and turns the guard on, which imports it. Run again
# to update or repair: every step is idempotent. What only the device itself
# can do (default launcher, VPN consent, Obtainium, notification access) is
# listed at the end.
#
#   scripts/provision-android.sh <adb serial> <device name> [--apk <file>] [--no-sync]
#
# The device name is how this device appears in sync ("phone", "tv"): keep it
# the same across runs, or its usage starts a second record. Needs adb, curl,
# jq and, unless --no-sync, the 1Password CLI unlocked (see docs/SYNC.md).
set -eu

PKG=dev.josemiguel.launcher
GUARD=$PKG/$PKG.GuardService
REPO=https://github.com/josemiguelo/intentional
DROP=/sdcard/Android/data/$PKG/files
ITEM="op://Personal/Intentional sync"

usage() { echo "usage: $0 <adb serial> <device name> [--apk <file>] [--no-sync]" >&2; exit 2; }
[ $# -ge 2 ] || usage
serial=$1 device=$2
shift 2
apk="" sync=yes
while [ $# -gt 0 ]; do
  case $1 in
  --apk) [ $# -ge 2 ] || usage; apk=$2; shift 2 ;;
  --no-sync) sync=no; shift ;;
  *) usage ;;
  esac
done

say() { printf '\033[1m==>\033[0m %s\n' "$*"; }
a() { adb -s "$serial" "$@"; }

# A USB phone can drop off for a moment; give it a little time to come back.
timeout 30 adb -s "$serial" wait-for-device 2>/dev/null &&
  [ "$(a get-state 2>/dev/null)" = device ] || { echo "no device $serial (adb devices)" >&2; exit 1; }
tv=no
a shell pm list features 2>/dev/null | grep -q android.software.leanback && tv=yes

# 1Password first: better to stop before touching the device than halfway.
if [ $sync = yes ]; then
  op vault list >/dev/null 2>&1 || {
    echo "1Password isn't unlocked for the CLI: unlock the app (and approve its prompt), or pass --no-sync" >&2
    exit 1
  }
fi

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

if [ -z "$apk" ]; then
  # /releases/latest redirects to /releases/tag/v<version>.
  version=$(curl -fsSLo /dev/null -w '%{url_effective}' "$REPO/releases/latest")
  version=${version##*/v}
  say "Downloading Intentional $version"
  apk=$tmp/intentional-$version.apk
  curl -fsSL -o "$apk" "$REPO/releases/download/v$version/intentional-$version.apk"
  want=$(curl -fsSL "$REPO/releases/download/v$version/intentional-$version.apk.sha256" | cut -d' ' -f1)
  [ "$(sha256sum "$apk" | cut -d' ' -f1)" = "$want" ] || { echo "the APK doesn't match its SHA-256" >&2; exit 1; }
fi

say "Installing on $serial ($([ $tv = yes ] && echo TV || echo phone))"
a install -r "$apk" | tail -n 1

say "Granting usage access, restricted settings, the battery exemption"
a shell appops set $PKG GET_USAGE_STATS allow
a shell appops set $PKG ACCESS_RESTRICTED_SETTINGS allow
a shell dumpsys deviceidle whitelist +$PKG >/dev/null

if [ $sync = yes ]; then
  say "Handing over sync.json as \"$device\""
  database_url=$(op read "$ITEM/database url")
  api_key=$(op read "$ITEM/api key")
  email=$(op read "$ITEM/username")
  password=$(op read "$ITEM/password")
  key=$(op read "$ITEM/encryption key")
  for value in "$database_url" "$api_key" "$email" "$password" "$key"; do
    [ -n "$value" ] || { echo "1Password returned an empty field in \"Intentional sync\"" >&2; exit 1; }
  done
  (umask 077 && jq -n \
    --arg databaseUrl "$database_url" --arg apiKey "$api_key" --arg email "$email" \
    --arg password "$password" --arg key "$key" --arg device "$device" \
    '{databaseUrl: $databaseUrl, apiKey: $apiKey, email: $email, password: $password, key: $key, device: $device}' \
    >"$tmp/sync.json")
  a shell mkdir -p "$DROP"
  a push "$tmp/sync.json" "$DROP/sync.json" >/dev/null
fi

# The guard last: connecting, it imports the dropped-in sync.json (a TV has no
# Home of Intentional's to do it). Toggled off and on, since a reinstall leaves
# it registered but disconnected; other enabled services are kept.
say "Turning the guard on (strict mode comes with it)"
others=$(a shell settings get secure enabled_accessibility_services | tr -d '\r' | tr ':' '\n' |
  grep -v -e "^$GUARD\$" -e '^null$' -e '^$' | paste -sd: - || true)
a shell settings put secure enabled_accessibility_services "${others:-\"\"}"
sleep 1
a shell settings put secure enabled_accessibility_services "${others:+$others:}$GUARD"
a shell settings put secure accessibility_enabled 1

if [ $sync = yes ]; then
  sleep 3
  if a shell ls "$DROP/sync.json" >/dev/null 2>&1; then
    echo "  sync.json not picked up yet: it is when the guard connects or the launcher comes up" >&2
  fi
fi

say "Done. Left to do on the device:"
if [ $tv = yes ]; then
  cat <<'MSG'
  - Obtainium: add https://github.com/josemiguelo/intentional (updates).
  - Approve the VPN prompt when it appears (site budgets).
MSG
else
  cat <<'MSG'
  - Press Home and choose Intentional as the default launcher.
  - Obtainium: add https://github.com/josemiguelo/intentional (updates).
  - Approve the VPN prompt when it appears (site budgets).
  - Settings → notification access, if you batch notifications.
MSG
fi
