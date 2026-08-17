# Intentional

A personal, open-source Android launcher designed around the evidence on
problematic smartphone use: attack cues and habitual openings, not screen-time
minutes.

## What it does

- **Text-only home screen** — clock, date, and up to a handful of favorite
  apps as a plain monochrome list. No icons, no badges, no grid.
- **Type to open** — swipe up for search over all launchable apps. Opening an
  app means naming it.
- **Considered apps** — apps you mark as "considered" never appear in lists.
  They only show up in search after you've typed at least three characters of
  their name, and they open through a pause screen: an 8-second breathing
  animation with equal-weight **close** / **open** choices. Friction with an
  honest exit ramp — no walls, no "ignore" shame.
- **The right metric** — the footer counts *intentional opens* and
  *paused-and-closed* moments for today. Never minutes.

Long-press any app (on home or in search) to add/remove it from home or mark
it as considered.

## Build & install

```sh
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Then press Home and choose Intentional as the default launcher (or open it
from the app drawer to try it first).

## Strict mode (sideload setup)

Strict mode blocks the system screens used to switch the default launcher,
via an AccessibilityService. Sideloaded builds need two one-time unlocks:

```sh
# Android 13+ silently drops accessibility events for sideloaded apps:
adb shell appops set dev.josemiguel.launcher ACCESS_RESTRICTED_SETTINGS allow
```

Then enable "Intentional guard" in Settings > Accessibility (or via adb by
appending `dev.josemiguel.launcher/dev.josemiguel.launcher.GuardService` to
`settings get secure enabled_accessibility_services` and writing it back with
`settings put`). **After every reinstall** the service stays registered but
disconnected — cycle it off and on (remove it from that setting, wait a
second, add it back) to rebind.

Turning strict mode off happens in the launcher's settings and requires
typing "let me switch" — deliberate, but never a lockout. Emergency escape:
`adb shell settings put secure enabled_accessibility_services ""`.

## Design rationale

The feature set follows the intervention literature: launch-time friction with
an escape option is the best-evidenced on-device mechanism (Grüning et al.
2023, *PNAS*: 36% of opens abandoned at the prompt, 37% fewer attempts after
six weeks); cue removal is the layer timers can't touch; and reduced minutes
alone don't improve well-being, so the launcher measures intentionality
instead. Utility apps stay one frictionless tap away — breaking legitimate
muscle memory is the main reason people abandon minimalist launchers.

Planned next: usage-aware pause screens ("third time this hour") via
UsageStatsManager, a wind-down mode targeting the sleep pathway, and
notification batching.
