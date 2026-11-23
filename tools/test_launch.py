"""Exercise a clean install and a cached relaunch of the actual release APK."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import subprocess
import time

PACKAGE = "com.vroombuddies.game"
OUT = Path("launch-check")
OUT.mkdir(exist_ok=True)

def adb(*args):
    return subprocess.check_output(["adb", *args], timeout=45)

def running():
    return subprocess.run(["adb", "shell", "pidof", PACKAGE], stdout=subprocess.DEVNULL).returncode == 0

adb("shell", "settings", "put", "secure", "immersive_mode_confirmations", "confirmed")
adb("install", "-r", "VroomBuddies-fixed.apk")
def tap_label(text, exact=False):
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        result = adb("shell", "uiautomator", "dump", "/sdcard/window.xml")
        if b"dumped" in result.lower():
            root = ET.fromstring(adb("exec-out", "cat", "/sdcard/window.xml"))
            for n in root.iter("node"):
                if (n.get("text", "") == text if exact else text in n.get("text", "")) and n.get("package") == PACKAGE:
                    left, top, right, bottom = map(int, re.findall(r"\d+", n.get("bounds")))
                    adb("shell", "input", "tap", str((left+right)//2), str((top+bottom)//2))
                    time.sleep(1)
                    return
        time.sleep(1)
    raise RuntimeError("Could not find " + text)

for launch in ("first", "cached"):
    adb("shell", "am", "force-stop", PACKAGE)
    adb("logcat", "-c")
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.GameActivity")
    try:
        deadline = time.monotonic() + 100
        while time.monotonic() < deadline:
            if not running():
                raise RuntimeError(f"{launch} launch exited")
            if b"Showroom frame submitted" in adb("logcat", "-d", "-s", "Vroom:I"):
                break
            time.sleep(1)
        else:
            raise RuntimeError(f"{launch} launch timed out waiting for a showroom frame")
        time.sleep(5)
        if not running():
            raise RuntimeError(f"{launch} launch exited after first frame")
        (OUT / f"{launch}.png").write_bytes(adb("exec-out", "screencap", "-p"))
        print(f"Verified {launch} launch and showroom frame")
        if launch == "cached":
            tap_label("Play")
            for index, name in enumerate(("Zoomy", "Bubbles", "Stomper", "Rocket", "Scoot", "Hopper", "Bumble")):
                if index:
                    tap_label("▶", exact=True)
                time.sleep(1)
                (OUT / f"car-{name}.png").write_bytes(adb("exec-out", "screencap", "-p"))
            for label in ("Next", "Next", "01"):
                tap_label(label)
            deadline = time.monotonic() + 100
            while time.monotonic() < deadline:
                if not running():
                    raise RuntimeError("Game exited while starting a race")
                if b"Race frame submitted" in adb("logcat", "-d", "-s", "Vroom:I"):
                    break
                time.sleep(1)
            else:
                raise RuntimeError("Race did not render")
            time.sleep(5)
            if not running():
                raise RuntimeError("Game exited during the race")
            (OUT / "race.png").write_bytes(adb("exec-out", "screencap", "-p"))
            print("Verified race launch")
    finally:
        logs = adb("logcat", "-d", "-t", "5000")
        (OUT / f"{launch}.log").write_bytes(logs)
        for line in logs.decode(errors="replace").splitlines():
            if any(tag in line for tag in ("I Vroom", "F DEBUG", "Fatal signal", "AndroidRuntime")):
                print(line)
