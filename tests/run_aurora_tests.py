from pathlib import Path
import hashlib
import os
import subprocess
import tempfile
import urllib.request

root = Path(__file__).resolve().parents[1]
base = root / "app/src/main/java/dev/nuvio/presence"
with tempfile.TemporaryDirectory() as tmp:
    gson = Path(os.environ.get("SUKI_TEST_GSON", Path(tmp) / "gson.jar"))
    if not gson.exists():
        urllib.request.urlretrieve("https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar", gson)
    assert hashlib.sha256(gson.read_bytes()).hexdigest() == "57928d6e5a6edeb2abd3770a8f95ba44dce45f3b23b7a9dc2b309c581552a78b"
    jar = Path(tmp) / "tests.jar"
    files = ["aurora/ThemeFile.kt", "aurora/ThemeStore.kt", "aurora/WidgetState.kt", "core/playback/Playback.kt", "core/playback/Prism.kt"]
    subprocess.run(["kotlinc", "-cp", str(gson), *(str(base / f) for f in files), str(root / "tests/AuroraTest.kt"), "-include-runtime", "-d", str(jar)], check=True)
    subprocess.run(["java", "-cp", os.pathsep.join(map(str, (jar, gson))), "dev.nuvio.presence.AuroraTestKt"], check=True)
