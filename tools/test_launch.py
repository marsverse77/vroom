"""Exercise a clean install and a cached relaunch of the actual release APK."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from io import BytesIO
from PIL import Image
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
def wait_label(text, exact=False):
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        result = adb("shell", "uiautomator", "dump", "/sdcard/window.xml")
        if b"dumped" in result.lower():
            root = ET.fromstring(adb("exec-out", "cat", "/sdcard/window.xml"))
            for n in root.iter("node"):
                if (n.get("text", "") == text if exact else text in n.get("text", "")) and n.get("package") == PACKAGE:
                    left, top, right, bottom = map(int, re.findall(r"\d+", n.get("bounds")))
                    return (left+right)//2, (top+bottom)//2
        time.sleep(1)
    raise RuntimeError("Could not find " + text)

def tap_label(text, exact=False):
    x, y = wait_label(text, exact)
    adb("shell", "input", "tap", str(x), str(y))

def capture(name, label=None):
    if label:
        wait_label(label)
    deadline = time.monotonic() + 50
    time.sleep(2)
    while time.monotonic() < deadline:
        data = adb("exec-out", "screencap", "-p")
        with Image.open(BytesIO(data)) as frame:
            w, h = frame.size
            central = frame.crop((w//4, h//3, w*3//4, h*2//3)).convert("L").resize((40, 30))
            if sum(central.histogram()[:15]) < 0.65 * 40 * 30:
                (OUT / (name + ".png")).write_bytes(data)
                return
        time.sleep(1)
    raise RuntimeError("No visible game scene in " + name)

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
        capture(launch, "Play")
        print(f"Verified {launch} launch and showroom frame")
        if launch == "cached":
            tap_label("Play")
            for index, name in enumerate(("Zoomy", "Bubbles", "Stomper", "Rocket", "Scoot", "Hopper", "Bumble")):
                if index:
                    tap_label("▶", exact=True)
                capture("car-" + name, name.upper())
            for screen, label in (("paint", "Next"), ("tracks", "Next"), ("grid", "01")):
                tap_label(label)
                if screen != "grid":
                    capture(screen, "PICK A COLOUR" if screen == "paint" else "WHERE TO RACE")
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
            capture("race")
            print("Verified race launch")
    finally:
        logs = adb("logcat", "-d", "-t", "5000")
        (OUT / f"{launch}.log").write_bytes(logs)
        for line in logs.decode(errors="replace").splitlines():
            if any(tag in line for tag in ("I Vroom", "F DEBUG", "Fatal signal", "AndroidRuntime")):
                print(line)
