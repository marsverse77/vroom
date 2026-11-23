"""Exercise a clean install and a cached relaunch of the actual release APK."""
from pathlib import Path
import subprocess
import time

PACKAGE = "com.vroombuddies.game"
OUT = Path("launch-check")
OUT.mkdir(exist_ok=True)

def adb(*args):
    return subprocess.check_output(["adb", *args], timeout=45)

def running():
    return subprocess.run(["adb", "shell", "pidof", PACKAGE], stdout=subprocess.DEVNULL).returncode == 0

adb("install", "-r", "VroomBuddies-fixed.apk")
for launch in ("empty,ibl,warm,fx,sun,post", "ibl,warm,fx,sun,post", "ibl", "warm", "fx", "sun", "post", "empty", "unlit"):
    adb("shell", "am", "force-stop", PACKAGE)
    adb("logcat", "-c")
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.GameActivity", "--es", "probe", launch)
    try:
        deadline = time.monotonic() + 35
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
    except (RuntimeError, subprocess.SubprocessError) as e:
        print("PROBE FAILED", launch, e, flush=True)
    finally:
        logs = adb("logcat", "-d", "-t", "5000")
        (OUT / f"{launch}.log").write_bytes(logs)
        for line in logs.decode(errors="replace").splitlines():
            if any(tag in line for tag in ("I Vroom", "F DEBUG", "Fatal signal", "AndroidRuntime")):
                print(line)
