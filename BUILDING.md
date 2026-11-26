# Building SukiRPC

Requirements: JDK 17, Gradle 8.11.1, Android SDK platform 35, NDK 27.3.13750724 and CMake 3.22.1. An authorized compatible Discord Social SDK Android AAR is required at `app/libs/discord_partner_sdk.aar`. It is not included. Fork maintainers must use their own authorized Discord application configuration and comply with Discord's terms.

For a local development build, with the SDK and tools already configured:

```sh
gradle :collector:assembleDebug --configure-on-demand
mkdir -p app/build/generated/collectorAssets
cp collector/build/outputs/apk/debug/collector-debug.apk app/build/generated/collectorAssets/collector.apk
gradle :app:assembleDebug -PsukiAbis=arm64-v8a
```

Choose a supported ABI appropriate to your device. Local debug builds use a developer-owned debug key; they cannot update official builds signed with a different certificate. Never distribute or request the official signing key. Official publishing CI and signing material are maintained separately. The public source snapshot is not a promise of bit-for-bit reproducible official APKs.

TV integration is primarily intended for Amazon Fire TV Stick; other Android TV devices are not guaranteed compatible.
