"""Exercise a clean install and a cached relaunch of the actual release APK."""
from pathlib import Path
import re
import json
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

def install():
    adb("shell","svc","power","stayon","true")
    adb("shell","settings","put","system","screen_off_timeout","1800000")
    adb("shell","input","keyevent","KEYCODE_WAKEUP")
    adb("shell","wm","dismiss-keyguard")
    adb("shell", "settings", "put", "secure", "immersive_mode_confirmations", "confirmed")
    adb("install", "-r", "VroomBuddies-fixed.apk")
    adb("install", "-r", "ui-probe-debug.apk")

def visible_nodes():
    report = adb("shell", "am", "instrument", "-w",
                 "com.vroombuddies.probe/.WindowProbe").decode(errors="replace")
    line = next((line for line in report.splitlines() if line.startswith("INSTRUMENTATION_RESULT: nodes=")), None)
    if line is None:
        raise RuntimeError("Window inspection failed: " + report[-500:])
    return json.loads(line.split("=", 1)[1])

def wait_label(text, exact=False):
    deadline = time.monotonic() + 55
    nodes = []
    while time.monotonic() < deadline:
        nodes = visible_nodes()
        if not any("Opening the garage" in n["text"] for n in nodes):
            for node in nodes:
                if node["text"] == text if exact else text in node["text"]:
                    left, top, right, bottom = node["bounds"]
                    return (left+right)//2, (top+bottom)//2
        time.sleep(.25)
    raise RuntimeError(f"Could not find {text}. Visible labels: {[n['text'] for n in nodes]}")

def tap_label(text, exact=False):
    x, y = wait_label(text, exact)
    adb("shell", "input", "tap", str(x), str(y))

def check_play_pixels(data):
    nodes = visible_nodes()
    play = next(node for node in nodes if node["text"] == "Play")
    with Image.open(BytesIO(data)) as frame:
        w,h = frame.size
        l,t,r,b = play["bounds"]
        if not (0 < l < r < w and 0 < t < b < h):
            raise RuntimeError(f"Play bounds {play['bounds']} touch the edge of {w}x{h}")
        frame = frame.convert("RGB")
        for x0,y0,x1,y1 in ((.78,.28,.92,.68),(.38,.68,.78,.81)):
            area = frame.crop((int(l+(r-l)*x0),int(t+(b-t)*y0),int(l+(r-l)*x1),int(t+(b-t)*y1)))
            green = sum(g > b*1.25 and g > r*.98 for r,g,b in area.getdata())
            if green < area.width*area.height*.32:
                raise RuntimeError(f"Play face is visibly cut off at {w}x{h}")

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
                if label == "Play":
                    check_play_pixels(data)
                (OUT / (name + ".png")).write_bytes(data)
                return
        time.sleep(1)
    raise RuntimeError("No visible game scene in " + name)

if __name__ == "__main__":
    install()
    for launch in ("compact", "first", "cached"):
        adb("shell", "wm", "size", {"compact":"360x720","first":"768x1024","cached":"800x1280"}[launch])
        adb("shell", "wm", "density", "160")
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
            tap_label("Settings", exact=True)
            wait_label("Smooth", exact=True)
            if launch == "compact":
                capture("compact-settings")
                adb("shell", "wm", "size", "420x840")
                wait_label("Smooth", exact=True)
                tap_label("◀", exact=True)
                tap_label("Play")
                capture("compact-garage", "ZOOMY")
                tap_label("Next")
                capture("compact-paint", "PICK A COLOUR")
                tap_label("Next")
                capture("compact-courses", "WHERE TO RACE")
                continue
            if launch == "first":
                tap_label("Max", exact=True)
                wait_label("TAA", exact=True)
                tap_label("Smooth", exact=True)
                tap_label("FXAA", exact=True)
            wait_label("MSAA 4x", exact=True)
            if launch == "cached":
                tap_label("Smooth", exact=True)
                wait_label("FXAA", exact=True)
            capture("settings-" + launch)
            tap_label("◀", exact=True)
            if launch == "first":
                tap_label("Play")
                capture("tablet-4x3-garage", "ZOOMY")
                tap_label("Next")
                capture("tablet-4x3-paint", "PICK A COLOUR")
                tap_label("Next")
                capture("tablet-4x3-courses", "WHERE TO RACE")
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
                deadline = time.monotonic() + 120
                while time.monotonic() < deadline:
                    rows = re.findall(rb"Frame intervals us median=(\d+) p95=(\d+) worst=(\d+)", adb("logcat", "-d", "-s", "Vroom:I"))
                    if len(rows) >= 2:
                        print("Software-rendered tablet, 60 submitted frames per sample:", rows, flush=True)
                        break
                    if not running():
                        raise RuntimeError("Race exited during timing sample")
                    time.sleep(1)
                else:
                    raise RuntimeError("Fewer than 120 measured frames")
        finally:
            if __import__("sys").exc_info()[0] is not None:
                print("Focused window", [x for x in adb("shell","dumpsys","window").decode(errors="replace").splitlines() if "mCurrentFocus" in x][:2])
                print("Display state", [x for x in adb("shell","dumpsys","power").decode(errors="replace").splitlines() if "mWakefulness=" in x][:1])
                print("Recent frame samples", adb("logcat","-d","-s","Vroom:I").decode(errors="replace")[-6000:])
                print("Render layer stats", adb("shell","dumpsys","SurfaceFlinger","--list").decode(errors="replace")[-3000:])
            logs = adb("logcat", "-d", "-t", "5000")
            (OUT / f"{launch}.log").write_bytes(logs)
            for line in logs.decode(errors="replace").splitlines():
                if any(tag in line for tag in ("I Vroom", "F DEBUG", "Fatal signal", "AndroidRuntime")):
                    print(line)
