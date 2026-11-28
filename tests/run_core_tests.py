import os
import shutil
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
base = root / "app/src/main/java/dev/nuvio/presence"
with tempfile.TemporaryDirectory() as tmp:
    for source, test in [(base / "ui/renaissance/RenaissanceRoutes.kt", root / "tests/RenaissanceRoutesTest.kt"), (base / "echo/EchoRules.kt", root / "tests/EchoTest.kt"), (base / "core/playback/Playback.kt", root / "tests/v03/PlaybackTest.kt"), (base / "core/discord/RpcShutdown.kt", root / "tests/RpcShutdownTest.kt")]:
        jar = Path(tmp) / "test.jar"
        subprocess.run(["kotlinc", str(source), str(test), "-include-runtime", "-d", str(jar)], check=True)
        subprocess.run(["java", "-jar", str(jar)], check=True)

    policy = Path(tmp) / "policy"
    policy.mkdir()
    policy_source = root / "shared/artwork/src/main/java/dev/nuvio/artwork/ArtworkPolicy.java"
    subprocess.run(["javac", "-d", str(policy), str(policy_source)], check=True)

    artwork = Path(tmp) / "artwork.jar"
    subprocess.run(["kotlinc", "-cp", str(policy), str(base / "artwork/PublicArtwork.kt"),
                    str(root / "tests/ArtworkTest.kt"), "-include-runtime", "-d", str(artwork)], check=True)
    subprocess.run(["java", "-cp", os.pathsep.join(map(str, (artwork, policy))), "dev.nuvio.presence.ArtworkTestKt"], check=True)

    studio = Path(tmp) / "studio.jar"
    subprocess.run(["kotlinc", "-cp", str(policy), str(base / "artwork/PublicArtwork.kt"),
                    str(base / "studio/RpcPreset.kt"), str(root / "tests/RpcPresetTest.kt"),
                    "-include-runtime", "-d", str(studio)], check=True)
    subprocess.run(["java", "-cp", os.pathsep.join(map(str, (studio, policy))), "dev.nuvio.presence.RpcPresetTestKt"], check=True)

    kotlin = Path(shutil.which("kotlinc")).resolve()
    coroutines = kotlin.parent.parent / "lib/kotlinx-coroutines-core-jvm.jar"
    engine = Path(tmp) / "engine.jar"
    sources = [base / path for path in ("artwork/HorizonArtworkEngine.kt", "artwork/ArtworkCache.kt", "core/playback/Playback.kt",
                                                "artwork/SessionArtworkOverride.kt", "artwork/PublicArtwork.kt")]
    subprocess.run(["kotlinc", "-cp", os.pathsep.join(map(str, (coroutines, policy))), *map(str, sources), str(root / "tests/HorizonArtworkTest.kt"),
                    "-include-runtime", "-d", str(engine)], check=True)
    subprocess.run(["java", "-cp", os.pathsep.join(map(str, (engine, coroutines, policy))), "dev.nuvio.presence.HorizonArtworkTestKt"], check=True)

    sources = [base / path for path in ("core/playback/Playback.kt", "core/playback/Prism.kt",
               "artwork/PublicArtwork.kt", "core/storage/MediaFormatSettings.kt")]
    jar = Path(tmp) / "prism.jar"
    subprocess.run(["kotlinc", "-cp", str(policy), *map(str, sources), str(root / "tests/PrismTest.kt"), "-include-runtime", "-d", str(jar)], check=True)
    subprocess.run(["java", "-cp", os.pathsep.join(map(str, (jar, policy))), "dev.nuvio.presence.PrismTestKt"], check=True)

