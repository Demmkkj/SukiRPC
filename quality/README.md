# Genesis quality

| Level | Command | Scope |
| --- | --- | --- |
| Quick | bash scripts/audit.sh quick | Lint, Detekt, Ktlint, existing quick JVM regressions, Actionlint |
| Full | bash scripts/audit.sh full | Quick scope, Clang-Tidy, Android JVM tests, Kover and security tools |
| Deep | bash scripts/audit.sh deep | Visual and instrumented tests, diagnostic builds, sanitizers and benchmarks |
| Complete | bash scripts/audit.sh complete | Full and Deep, each selected tool once |

Run subsets through the same script. Java 17, Gradle 8.11.1, SDK 35, NDK 27.3.13750724 and CMake 3.22.1 match the project. Use authorized access to the private Discord AAR. Install the command-line tools with bash scripts/quality/install-tools.sh security or bash scripts/quality/install-tools.sh maestro. The script puts binaries in build/quality-tools by default. Audit reports are in build/reports/code-audit/run.*. Start with summary.md and findings.tsv. Use status.tsv and the tool's run.log to distinguish findings, execution errors and skipped tools.

| Tool | Audit selector | Evidence and limitation |
| --- | --- | --- |
| Android Lint | lint | App and Collector debug XML/HTML |
| Detekt | detekt | SARIF. CLI analysis without a resolved compiler classpath |
| Ktlint | ktlint | SARIF. Reports only |
| Clang-Tidy | clang-tidy | Real CMake/NDK database, ARM64 Debug by default |
| JUnit | coverage | Android JVM suites and JUnit XML |
| Robolectric | coverage | API 34 simulated Android, notification visibility and Collector metadata |
| MockK | coverage | Mocked Horizon catalog boundary |
| MockWebServer | coverage | Local HTTP manifest fixture and production manifest validator. Does not test the real updater TLS endpoint |
| Kotlin Coroutines Test | coverage | Virtual-time artwork engine scheduling |
| Kover | coverage | Kotlin app debug XML. The Java-only Collector uses JaCoCo 0.8.13 for its host JVM tests. Robolectric sandbox tests have separate pass/fail XML |
| LeakCanary | diagnostics | Private opt-in Debug package. Leak observability requires a lifecycle on Android |
| StrictMode | diagnostics | Debug-only provider logs violations. Requires exercising a flow on Android |
| Macrobenchmark | macrobenchmark | Cold start of the profileable Genesis package. ARM64 device recommended for timings |
| Microbenchmark | microbenchmark | Actual shared Java artwork policy hot path |
| Perfetto | perfetto | Explicit trace of the profileable startup on an isolated test device |
| Android Studio Profiler | profiler | Manual. Use Android Studio's Profiler with the benchmark variant for startup/CPU or the diagnostic Debug variant for allocations and memory |
| HWASan | sanitizers | ARM64 instrumented private APK and verified wrap.sh. Runtime requires ARM64 Android 14+ |
| UBSan | sanitizers | Separate ARM64 instrumented private APK. Runtime smoke uses the same test device |
| Maestro | maestro | Genesis settings flow using the isolated .quality app |
| Compose UI Test | android-ui | Genesis navigation and startup JNI loading on Android |
| Paparazzi | paparazzi | Snapshot of the app's real notification vector. Compose navigation is covered separately by the instrumented flow |
| Gitleaks | gitleaks | Redacted SARIF from local Git history |
| OSV-Scanner | osv-scanner | CycloneDX from resolved app/Collector Maven runtime packages. File-based private AAR excluded |
| Semgrep | semgrep | Curated Android source rules, local scan with telemetry disabled |
| CodeQL | codeql | Requires confirmed GitHub Code Security entitlement and permission in the private repo |
| Actionlint | actionlint | Workflow validation and structured JSON. Go 1.25 and a pinned upstream fix prevent ShellCheck from deadlocking on long inline steps |
| Zizmor | zizmor | Offline GitHub Actions analysis and SARIF |

For emulator execution set SUKI_QUALITY_DEVICE=1 and SUKI_QUALITY_ABI=x86_64. For an isolated ARM64 Android 14+ performance device, set SUKI_QUALITY_DEVICE=1 and SUKI_QUALITY_ABI=arm64-v8a. SUKI_QUALITY_SANITIZER_DEVICE=1 enables native startup smoke, and SUKI_QUALITY_TRACE=1 opts into Perfetto capture. On Android Studio, choose the benchmark build variant and select Profile. For LeakCanary and StrictMode, build via the diagnostics selector, install only that local Debug package and exercise lifecycle transitions. Use the LeakCanary report and the StrictMode app log as evidence. Device runs must use a QA device or profile. Do not upload personal traces.

Set SUKI_RECORD_GOLDENS=1 to generate a candidate Paparazzi baseline. View the PNG and commit it only after review. The default selector verifies the checked-in snapshot.

Private Actions have separate code audit, security, instrumented/visual and performance jobs. Workflow dispatch requires the workflow on the default branch. The performance job defaults to build verification on a hosted runner. Device measurements use manually enrolled self-hosted runners labeled suki-quality-device. SDK downloads stay local to the job. The publication steps upload only quality reports, not APKs or SDK files.

The existing JVM regression suites remain the principal coverage for Horizon selection, RPC Studio, Chronicle, Prism, Echo state, Aurora/persistence, widget state and Discord callback handling. Composer/device suites add app navigation, notification privacy and actual JNI initialization. No synthetic test proves a live Discord presence, TV pairing or memory behavior on a 4 GB phone. For those checks identify the device, build and observed result.
