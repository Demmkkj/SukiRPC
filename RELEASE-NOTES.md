# SukiRPC 1.0.0 · Eclipse

**One presence. Anywhere.**

The first public major release of SukiRPC brings Discord Rich Presence to Nuvio Mobile and Nuvio TV, with TV integration primarily designed for Amazon Fire TV Stick.

## What's new

- **Eclipse:** minimalist UI, OLED theme, spring motion, native splash screen and dynamic notifications.
- **Nuvio Mobile:** local media-session integration and optional automatic artwork lookup.
- **Nuvio TV:** bundled Collector, encrypted local connection and QR/manual pairing, with Fire TV Stick as the primary target.
- **Performance:** adaptive polling, event-driven media-session cache invalidation, optimized artwork handling and reduced background work.
- **Updates:** integrated update center, per-architecture downloads, SHA-256 and signer checks.
- **Distribution:** non-debuggable release APKs for ARM64, ARM32, x86 and x86-64, plus a universal build.

## Installation

Download the APK that matches your device. Most modern phones use `arm64-v8a`; choose the universal APK if unsure. Android 8.0 or newer is required. Existing installations can update in place when the signing certificate and version code are compatible.

For Fire TV Stick, use the Collector included in the mobile application. See the README for setup instructions.

## Known limitations

- Artwork and playback details depend on available metadata; external artwork lookup is optional.
- Other Android TV and Fire OS devices may work but are not guaranteed compatible.
- The integration depends on compatible Nuvio and Discord versions.

The release includes `SHA256SUMS.txt` and `update.json`. Please remove private information from diagnostic logs before submitting bug reports.
