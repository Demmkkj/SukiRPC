# SukiRPC — source map

The Android package `dev.nuvio.presence` remains unchanged intentionally, including the JNI bridge and the notification listener. Source folders express ownership; they are not yet separate Gradle modules or Kotlin packages.

| Area | Responsibility |
| --- | --- |
| `core/discord` | Native Discord SDK bridge |
| `core/playback` | Playback model, timestamps, presence reconciliation |
| `core/network` | Pinned TLS client |
| `core/storage` | Encrypted pairing and preferences |
| `core/state` | Observable dashboard and bounded logs |
| `integration/mobile` | Notification listener, media sessions and callbacks |
| `integration/television` | Collector installation |
| `artwork` | Public URL validation and opt-in poster lookup |
| `service` | Foreground service and RPC orchestration |
| `ui/components` | QR pairing, Collector setup, settings toggle, presence preview |
| `ui/eclipse` | Eclipse theme, home, navigation, splash and notifications |
| `MainActivity.kt` | Activity lifecycle, screen state and navigation |

The Collector remains a separate Android module. No change to package IDs, JNI names, pairing storage, SDK callbacks, timing, signing or build variants is part of this refactor.

## Local regression test

From the repository root, with a JVM and Kotlin compiler installed:

```sh
python3 tests/run_core_tests.py
```

This tests pure Kotlin code only. The Android CI build and device tests remain necessary.

Keep `pairing.log` local. Remove any previously tracked copy with `git rm --cached -- pairing.log` before publishing.
