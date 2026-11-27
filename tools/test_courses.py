"""Exercise five release courses per tablet, including the 3D countdown and sustained racing."""
from pathlib import Path
import json
import re
import sys
import time
import xml.etree.ElementTree as ET
from test_launch import adb, install, running, capture, tap_label, wait_label, PACKAGE, OUT

def select_course(number):
    target = f"{number:02}"
    for _ in range(22):
        adb("shell", "uiautomator", "dump", "/sdcard/course.xml")
        root = ET.fromstring(adb("exec-out", "cat", "/sdcard/course.xml"))
        for node in root.iter("node"):
            if node.get("text") == target:
                left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
                if right-left > 16 and bottom-top > 12:
                    adb("shell", "input", "tap", str((left+right)//2), str((top+bottom)//2))
                    return
        # Deliberately slow swipes prevent flinging past the target card.
        adb("shell", "input", "swipe", "850", "440", "540", "440", "600")
    raise RuntimeError("Could not reach course " + target)

def measure(number):
    adb("shell", "am", "force-stop", PACKAGE)
    adb("logcat", "-c")
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.GameActivity")
    tap_label("Play")
    tap_label("Next")
    tap_label("Next")
    wait_label("WHERE TO RACE")
    start = time.monotonic()
    select_course(number)
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        logs = adb("logcat", "-d", "-s", "Vroom:I").decode(errors="replace")
        if not running():
            raise RuntimeError("Process exited on course " + str(number))
        intervals = re.findall(r"Frame intervals us median=(\d+) p95=(\d+) worst=(\d+)", logs)
        if len(intervals) >= 2:
            setup = re.search(r"Course ready in (\d+) ms", logs)
            row = dict(course=number, setup_ms=int(setup[1]) if setup else None,
                       frame_windows_us=[[int(v) for v in sample] for sample in intervals[:2]])
            print("TABLET_COURSE", json.dumps(row), flush=True)
            capture(f"course-{number:02}")
            return row
        time.sleep(1)
    raise RuntimeError(f"Course {number} did not submit 120 intervals within 180 seconds")

if __name__ == "__main__":
    install()
    adb("shell", "wm", "size", "800x1280")
    adb("shell", "wm", "density", "160")
    group = int(sys.argv[1])
    results = []
    for course in range(group*5+1, group*5+6):
        try:
            results.append(measure(course))
        finally:
            (OUT / "timings.json").write_text(json.dumps(results, indent=2))
            (OUT / f"course-{course:02}.log").write_bytes(adb("logcat", "-d", "-t", "5000"))
