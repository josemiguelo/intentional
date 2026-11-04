# Setting up Intentional on a phone

Intentional is a sideloaded personal launcher. Because it's not installed from a
store, several capabilities need permissions that Android deliberately gates
behind manual grants. This guide lists every one, what it's for, and both ways
to grant it: **on the phone** (no computer needed) or via **adb** (faster if you
have it plugged in).

Component names used below:

- package: `dev.josemiguel.launcher`
- launcher activity: `dev.josemiguel.launcher/.MainActivity`
- guard service: `dev.josemiguel.launcher/dev.josemiguel.launcher.GuardService`
- notification listener: `dev.josemiguel.launcher/dev.josemiguel.launcher.NotifListener`
- device admin: `dev.josemiguel.launcher/.UninstallGuardAdmin`
- site-budget VPN: `dev.josemiguel.launcher/.SiteBlockVpnService`

## What each feature needs

| Feature | Requires |
| --- | --- |
| Home screen, search, pause screens (core) | default-launcher role |
| Usage shown on pause screens; **time-limit enforcement** | usage access **and** the guard service |
| Strict mode (block switching launcher / disabling guard / uninstall) | guard accessibility service (+ "restricted settings" on Android 13+) |
| Notification batching (holding notifications) | notification access |
| Batched digest delivered at drop times | exact alarms + post-notifications |
| Alarms & background services stay reliable | battery-optimization exemption |
| Uninstall protection (optional) | device admin (granted in-app, no adb) |
| Website time in group budgets (measuring + bouncing off over-budget sites) | the guard service + usage access |
| Over-budget sites blocked in every app and browser | VPN consent + Private DNS on **Automatic** or off (+ always-on VPN) |

Minimum Android version: 10 (API 29).

## 1. Install

```sh
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 2. Default launcher (required)

Open the app, or press Home. On the app's home screen tap **"→ set as default
launcher"** and choose Intentional. That's the only required step for the basic
launcher to work.

## 3. Usage access (for stats + time limits)

Powers the usage line on pause screens and the per-group time-limit enforcement.

- **On the phone:** the pause screen shows "→ grant usage access to see your
  day"; tap it, find Intentional, enable. Or Settings → Apps → Special access →
  Usage access → Intentional → Allow.
- **adb:** `adb shell appops set dev.josemiguel.launcher GET_USAGE_STATS allow`

## 4. Guard accessibility service (for strict mode + time-limit enforcement)

The guard watches foreground apps: it blocks the system screens for switching
launcher / disabling itself / uninstalling, and it closes apps that are over
their group's time budget.

**Android 13+ first requires "restricted settings"** — a sideload protection
that otherwise silently drops accessibility events even though the service
appears enabled:

- **On the phone:** Settings → Apps → Intentional → tap the **⋮** menu (top
  right) → **Allow restricted settings** → confirm.
- **adb:** `adb shell appops set dev.josemiguel.launcher ACCESS_RESTRICTED_SETTINGS allow`

Then enable the service: Settings → Accessibility → Installed apps →
**Intentional guard** → On.

## 5. Notification access (only if you use batching)

Lets the launcher hold notifications from apps you mark "batched."

- Settings → Apps → Special access → Notification access → **Intentional** → On.
- Or the home screen shows "→ enable notification filtering" once you have a
  batched app; tap it.

## 6. Exact alarms + notifications (only if you use batching)

The batched digest ("N notifications waiting", with sound) is posted at each
drop time via an exact alarm.

- **Exact alarms** — Settings → Apps → Intentional → Alarms & reminders → Allow.
  adb: `adb shell appops set dev.josemiguel.launcher SCHEDULE_EXACT_ALARM allow`
  (takes effect on the app's next fresh start).
- **Post notifications** — the app asks on first launch (Android 13+). adb:
  `adb shell pm grant dev.josemiguel.launcher android.permission.POST_NOTIFICATIONS`

## 7. Battery-optimization exemption (strongly recommended)

Without this, aggressive power management delays or drops alarms and kills the
guard / notification services. Two layers:

- **Stock Android Doze exemption**
  - On the phone: Settings → Apps → Intentional → Battery → **Unrestricted**.
  - adb: `adb shell dumpsys deviceidle whitelist +dev.josemiguel.launcher`
    (verify: `adb shell dumpsys deviceidle whitelist | grep josemiguel`)
- **Samsung One UI only** (no adb equivalent — must be done on the phone):
  Settings → Battery → Background usage limits → make sure Intentional is **not**
  in "Sleeping/Deep sleeping apps" and add it to **Never sleeping apps**. Also
  turn off "Put unused apps to sleep," or Samsung will re-sleep it after a few
  idle days.

## 8. Uninstall protection (optional)

Entirely in-app, no adb: Settings (long-press the clock) → strict mode →
**"uninstall protection · off"** → activate the device admin. Once on, it's
locked; removing it needs the adb escape below.

## 9. Site budgets (only if a group has websites)

Groups can hold websites next to apps (settings → groups & time limits →
**+ add site**). Their time is measured by the guard from Brave/Chrome's
address bar; once a group is over budget, the guard bounces off its sites and a
local VPN refuses their DNS lookups, so they don't load in any app or browser
for the rest of the day.

The VPN only carries DNS: it routes nothing but its own resolver address
(`10.111.0.53`), relays allowed lookups to the current network's DNS server,
and never sees any other traffic.

1. **Private DNS → Automatic** (or off). A Private DNS *hostname* sends lookups
   around the VPN. Settings → Connections → More connection settings → Private
   DNS, or adb: `adb shell settings put global private_dns_mode opportunistic`
2. **VPN consent** — adding the first site asks once ("Intentional wants to set
   up a VPN connection"). adb alternative: `adb shell appops set
   dev.josemiguel.launcher ACTIVATE_VPN allow`. From then on the VPN starts by
   itself whenever the home screen comes up and a group has sites, and stops
   once none do.
3. **Always-on VPN** — Settings → Connections → More connection settings → VPN
   → ⚙ next to Intentional → **Always-on VPN** on. Android then restarts the VPN
   after a crash or reboot and won't let another VPN app replace it. Do this
   *before* the guard is on: strict mode blocks the VPN pages afterwards.

**Leave "Block connections without VPN" off.** It only lets traffic through
the VPN, and this one carries DNS alone — with it on, the phone has no
internet at all.

Strict mode additionally blocks, for site budgets: the VPN list, Intentional's
VPN page (always-on, delete profile), the VPN disconnect dialogs, the Private
DNS page, and Brave/Chrome's own **Use secure DNS** page (a custom provider
there would resolve around the VPN). A site's removal, or its group's deletion,
is locked while the group is over budget.

Known gaps: names resolved before a block stay cached for their TTL (usually a
few minutes); open connections survive until they close; browsers inside Secure
Folder and browsers doing their own encrypted DNS (e.g. Firefox) aren't covered.

## 10. Wallpapers (optional)

The launcher follows the system's dark/light mode with Tokyo Night's night and
day palettes. Home and the app drawer can show a wallpaper per mode, read from
the app's private files; without them the screens stay plain. The images aren't
in this repo — they're third-party art (e.g. Omarchy's theme backgrounds) — so
crop them to the screen and push them per phone:

```sh
# e.g. 1080x2340: Omarchy's tokyo-night/0-liftoff for dark, catppuccin-latte/1-color-fade for light.
# Dark: zoomed out to ~53% of the screen height, centred, with night background
# above and below and the image's top and bottom edges faded into it:
magick dark-source.webp -resize x1250 -gravity center -crop 1080x1250+0+0 +repage \
  \( -size 1080x1250 gradient:black-white -fx "u<0.08?u/0.08:(u>0.92?(1-u)/0.08:1)" \) -alpha off -compose CopyOpacity -composite \
  -background '#1a1b26' -compose Over -gravity center -extent 1080x2340 -flatten -quality 85 wallpaper-dark.webp
# Light: fills the screen, cropped to the image's middle:
magick light-source.webp -resize x2340 -gravity center -crop 1080x2340+0+0 +repage -quality 85 wallpaper-light.webp
for f in wallpaper-dark.webp wallpaper-light.webp; do
  adb exec-in run-as dev.josemiguel.launcher sh -c "mkdir -p files && cat > files/$f" < $f
done
```

`run-as` needs the debug build. The files are app data: a reinstall with
`install -r` keeps them; uninstalling removes them.

## After every reinstall

`adb install -r` keeps app data but **resets these**, so redo them:

1. Battery whitelist: `adb shell dumpsys deviceidle whitelist +dev.josemiguel.launcher`
2. Appops, if you use adb: `GET_USAGE_STATS`, `ACCESS_RESTRICTED_SETTINGS`,
   `SCHEDULE_EXACT_ALARM`, and the `POST_NOTIFICATIONS` grant.
3. **Rebind the guard** — after a reinstall the accessibility service stays
   *registered but disconnected*. Toggle it off and back on in
   Settings → Accessibility, or via adb:
   ```sh
   # replace <OTHERS> with whatever is already enabled (see: settings get secure enabled_accessibility_services)
   adb shell settings put secure enabled_accessibility_services "<OTHERS>"
   sleep 1
   adb shell settings put secure enabled_accessibility_services "<OTHERS>:dev.josemiguel.launcher/dev.josemiguel.launcher.GuardService"
   ```

The default-launcher role and notification access usually survive `install -r`.
So do the VPN consent and always-on setting: the reinstall stops the VPN, and
it comes back on the next press of Home.

## Emergency escapes (adb)

Strict mode is mandatory and has no in-app off switch by design; these are the
last-resort exits:

```sh
# disable the guard (unblocks launcher-switching, uninstall, etc.)
adb shell settings put secure enabled_accessibility_services ""

# remove uninstall protection so the app can be uninstalled
adb shell dpm remove-active-admin dev.josemiguel.launcher/.UninstallGuardAdmin

# stop the site-budget VPN (e.g. lookups failing): withdraw its VPN permission,
# then stop the app so neither the app nor always-on can bring it back
adb shell appops set dev.josemiguel.launcher ACTIVATE_VPN ignore
adb shell am force-stop dev.josemiguel.launcher
# ...and to undo it later
adb shell appops set dev.josemiguel.launcher ACTIVATE_VPN allow
```

Stopping the app also unbinds the guard; rebind it as in "After every
reinstall".

## Non-Samsung phones

The guard identifies the "switch home app" screen per-OEM in
`guard/GuardPolicy.kt` (`OneUiGuardPolicy` for Samsung, `DefaultGuardPolicy`
otherwise). If, on another device, strict mode fails to block the home-app
picker — or over-blocks another Settings screen — capture that screen's activity
class from `adb logcat -s GuardService` and add a policy for it.

The screen-matching text is English-locale; a non-English system language needs
the localized strings added to the policy.
