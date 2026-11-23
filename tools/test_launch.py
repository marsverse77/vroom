"""Check that fresh and cached Android launches both reach the interactive title."""
import pathlib
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.vroombuddies.game"
out = pathlib.Path("launch-check")
out.mkdir(exist_ok=True)


def adb(*args):
    return subprocess.check_output(["adb", *args], timeout=40)


adb("install", "-r", "VroomBuddies-fixed.apk")
adb("logcat", "-c")
try:
    for launch in ("first", "cached"):
        adb("shell", "am", "force-stop", PACKAGE)
        adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.GameActivity")
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            if not adb("shell", "pidof", PACKAGE).strip():
                raise RuntimeError(f"Game exited during {launch} launch")
            result = adb("shell", "uiautomator", "dump", "/sdcard/window.xml")
            if b"dumped" in result.lower():
                root = ET.fromstring(adb("exec-out", "cat", "/sdcard/window.xml"))
                if any("Play" in n.get("text", "") and n.get("package") == PACKAGE
                       for n in root.iter("node")):
                    (out / f"{launch}.png").write_bytes(adb("exec-out", "screencap", "-p"))
                    break
            time.sleep(1)
        else:
            raise RuntimeError(f"Title did not appear during {launch} launch")
finally:
    logs = adb("logcat", "-d", "-t", "5000")
    (out / "startup.log").write_bytes(logs)
    for line in logs.decode(errors="replace").splitlines():
        if any(tag in line for tag in ("AndroidRuntime", " DEBUG ", "Filament", "filament", "Vroom", "Fatal signal")):
            print(line)
