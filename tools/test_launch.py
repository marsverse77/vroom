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


from xml.sax.saxutils import escape

adb("install", "-r", "VroomBuddies-fixed.apk")

def check_title(label):
    adb("shell", "am", "force-stop", PACKAGE)
    adb("logcat", "-c")
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.GameActivity")
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        if subprocess.run(["adb","shell","pidof",PACKAGE], stdout=subprocess.DEVNULL).returncode:
            return False
        result = adb("shell", "uiautomator", "dump", "/sdcard/window.xml")
        if b"dumped" in result.lower():
            root = ET.fromstring(adb("exec-out", "cat", "/sdcard/window.xml"))
            if any("Play" in n.get("text", "") and n.get("package") == PACKAGE for n in root.iter("node")):
                time.sleep(2)
                if subprocess.run(["adb","shell","pidof",PACKAGE], stdout=subprocess.DEVNULL).returncode:
                    return False
                (out / f"{label}.png").write_bytes(adb("exec-out", "screencap", "-p"))
                return True
        time.sleep(1)
    return False

def trace(label):
    logs = adb("logcat", "-d", "-t", "4000")
    (out / f"{label}.log").write_bytes(logs)
    for line in logs.decode(errors="replace").splitlines():
        if any(tag in line for tag in ("AndroidRuntime", "Abort message", "Fatal signal", "Precondition")):
            print(line, flush=True)

for label in ("first", "cached"):
    ok = check_title(label)
    print("TITLE", label, ok, flush=True)
    trace(label)
    if not ok:
        break
else:
    raise SystemExit(0)

# Isolate graphics features on the emulator after the release configuration fails.
adb("root")
adb("wait-for-device")
low = {"aa":"FXAA", "shadows":"OFF", "ssao":"OFF", "bloom":False, "dof":False,
       "fx":"LOW", "dynres":False}
for label, changes in [("baseline", {}), ("shadows", {"shadows":"HIGH"}),
                       ("msaa", {"aa":"MSAA"}), ("ssao", {"ssao":"MEDIUM"}),
                       ("bloom", {"bloom":True,"fx":"HIGH"}), ("medium-shadow", {"shadows":"MEDIUM"})]:
    adb("shell","am","force-stop",PACKAGE)
    values = dict(low, **changes)
    elements = [
        f'<boolean name="{k}" value="{str(v).lower()}"/>' if isinstance(v,bool)
        else f'<string name="{k}">{escape(v)}</string>' for k,v in values.items()]
    prefs = out / "graphics.xml"
    prefs.write_text("<map>" + "".join(elements) + "</map>")
    dest = f"/data/data/{PACKAGE}/shared_prefs"
    adb("shell","mkdir","-p",dest)
    adb("push",str(prefs),dest+"/graphics.xml")
    adb("shell","chmod","644",dest+"/graphics.xml")
    adb("shell","restorecon",dest+"/graphics.xml")
    print("CONFIG", label, check_title(label), flush=True)
    trace(label)
raise SystemExit("Release launch failed")
