# Sync: one budget across devices

With sync on, a group's budget is spent across the phone, the TV and the
desktop: 30 minutes of YouTube is 30 minutes total, wherever they're spent.
Groups are shared too (edit them on any device; the newest edit wins), and
their limits are enforced by the server: one change a week.

## How it works

Every device runs the same code (`core/.../sync/`), talking to a Firebase
**Realtime Database** over REST, about once a minute (the phone and TV only
while the screen is on):

```
/users/<uid>/
  usage/<day>/<device>   that device's day, sealed
  groups                 {blob: sealed groups, updatedAt}
  considered             {blob: sealed considered apps and their friction, updatedAt}
  settings               {blob: sealed wind-down and pause lengths, updatedAt}
  limits/<group id>      {minutes, changedAt}   (in the clear, for the rules)
```

- **Encrypted on the device.** Usage and groups go up as AES-256-GCM under a
  key only the devices have; Firebase stores blobs. Only time that counts
  toward a group leaves a device. Group ids are a keyed hash of the name.
- **No conflicts.** Each device writes only its own usage; a group's use is
  the sum. Groups: a device's first sync merges its groups with the synced
  ones (same name: apps and sites joined; each device knows its own apps),
  after that a local edit goes up and a newer edit elsewhere comes down.
- **The weekly lock is the server's.** `firebase/database.rules.json` lets
  only the account in, and refuses a limit change within 7 days of the last
  (stamped with the server's clock). A limit set before sync carries over
  once. A refused change snaps back to the server's limit.
- **Offline**, a device keeps counting and enforcing with the last numbers it
  had; two devices offline at once can each spend into the same budget until
  they reconnect.
- **Settings** (wind-down, pause lengths) are shared too; a device that never
  had any of its own (a new install, the desktop) takes the synced ones and
  never pushes its defaults over them.
- Desktop apps are `hypr:<window class>` in groups; Android packages and site
  domains need no prefix.

Usage is history after its day, kept for a year: once a day a syncing
device deletes every device's older days in Firebase (and its own: the
desktop's day files, the phone's per-site and per-app time). The phone and
TV fetch the history once a day (only the days since the last fetch) for
settings → history; `intentional history` reads it from Firebase each time.
A year of three devices is about 1 MB.

Cost: the free (Spark) plan, with a wide margin: three devices every minute
all day is ~4,300 small writes and ~270 MB downloaded a month, against
Realtime Database's 10 GB.

## Setup

1. **Firebase project** (once): create a project, add a Realtime Database,
   enable Authentication → Email/Password and create one user for
   Intentional, and deploy the rules (`firebase deploy --only database`
   from the repo root). The database URL and the web API key are in the
   project settings.
2. **sync.json** (the same on every device but `device`; it holds the
   account's password and the encryption key, so keep it in a password
   manager, never in a repo):

   ```json
   {
     "databaseUrl": "https://<project>-default-rtdb.firebaseio.com",
     "apiKey": "<web API key>",
     "email": "<the Intentional user>",
     "password": "<its password>",
     "key": "<32 random bytes, base64>",
     "device": "phone"
   }
   ```

   `openssl rand -base64 32` makes a key.
3. **Install it** (both steps below are automated: loadout's
   `intentional-sync` script on a desktop, `scripts/provision-android.sh` for
   a phone or TV, each reading the 1Password item):
   - Desktop: `~/.config/intentional/sync.json` (`chmod 600`), then
     `systemctl --user restart intentional`.
   - Phone / TV: drop it in (SETUP.md, "Handing the app files"), then press
     Home; the launcher imports it, keeps it private and starts syncing.
     ```sh
     adb shell mkdir -p /sdcard/Android/data/dev.josemiguel.launcher/files
     adb push sync.json /sdcard/Android/data/dev.josemiguel.launcher/files/
     ```

Logs: `journalctl --user -u intentional` on the desktop (`sync: …`),
`adb logcat -s SyncRunner` on Android. `intentional status` shows all
devices' time once sync has run.
