# SqueezeGroups

Android app for grouping/ungrouping Logitech Media Server (Squeezebox) players
via the "LMS Group Manager" MQTT API.

## Status

MVP: connects to an MQTT broker and exercises the two currently-wired
endpoints:

- `lms/groups/get` — fetch current groups / standalone / unreachable players
- `lms/groups/set` — apply a new grouping (`targeted` or `world` mode)

Presets, single-player ungroup, and snapshot save/restore from the API spec
are not wired up yet.

## How it works

1. On first launch (or via the settings screen), enter your MQTT broker's
   host, port, TLS toggle, optional username/password, and a client ID.
   Saving connects immediately and persists the settings (DataStore) for
   next launch, which auto-connects.
2. Once connected, the player list is fetched automatically
   (`lms/groups/get`) and shown one player per row, with the server's
   current groups reflected in the group columns.
3. Tap **+ Add group** to add a group column. Each player row can have at
   most one checked column (checking one unchecks any other in that row) —
   an unchecked player stays standalone.
4. Choose **Targeted** (only touches mentioned players, fails on a
   collision with an unmentioned player in the same existing group) or
   **World** (unsyncs everyone first, then applies exactly what's checked).
5. **Apply groups** sends `lms/groups/set` and refreshes state on success.

## Project layout

- `data/` — `MqttSettings`, DataStore-backed `SettingsRepository`, and the
  JSON request/response models (`kotlinx.serialization`) for the two wired
  topics.
- `mqtt/MqttRepository.kt` — Eclipse Paho `MqttAsyncClient` wrapper. Since
  the API has no request ID, each request is correlated to its
  `<topic>/response` topic under a mutex (one in-flight request at a time),
  with a timeout.
- `ui/` — Compose screens (`SettingsScreen`, `HomeScreen`) and
  `SqueezeGroupsViewModel`.

## Building

Requires an Android SDK (compileSdk 34) — this repository was scaffolded in
an environment without one installed, so the Gradle build has not been
run here. From a machine with Android Studio / the SDK installed:

```
./gradlew assembleDebug
```

## Notes / follow-ups

- Broker credentials are stored in plain DataStore preferences (no
  encryption) — fine for a home LAN broker, not for anything more exposed.
- No TLS certificate pinning; `ssl://` uses the platform default trust
  store, so a broker with a self-signed cert needs that cert trusted on
  the device (or TLS left off for a LAN-only broker).
- Presets, per-player ungroup, and snapshot save/restore can be added the
  same way as `lms/groups/set`: a request/response data class pair plus a
  `MqttRepository.request(...)` call.
