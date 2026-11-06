"""Pause the existing sync opt-in while test fixtures are present; restore its original value.

Only the sync_enabled protobuf boolean is changed, with the app stopped and an atomic
file replacement. No account, token, event, theme or font value is printed or altered.
A local ignored backup is retained if restoration fails.
"""
import argparse
import base64
import pathlib
import re
import subprocess

PACKAGE = "com.nqutaoitfteikc.notehub"
SETTINGS = "files/datastore/settings.preferences_pb"


def varint(data, offset):
    result = shift = 0
    while True:
        byte = data[offset]
        offset += 1
        result |= (byte & 127) << shift
        if byte < 128:
            return result, offset
        shift += 7
        if shift > 70:
            raise ValueError("Invalid protobuf")


def encode(value):
    output = bytearray()
    while value > 127:
        output.append((value & 127) | 128)
        value >>= 7
    return bytes(output + bytes([value]))


def fields(data):
    offset = 0
    while offset < len(data):
        start = offset
        tag, offset = varint(data, offset)
        wire = tag & 7
        if wire == 0:
            value, offset = varint(data, offset)
        elif wire == 2:
            length, offset = varint(data, offset)
            value = data[offset:offset + length]
            offset += length
        elif wire in (1, 5):
            length = 8 if wire == 1 else 4
            value = data[offset:offset + length]
            offset += length
        else:
            raise ValueError("Unsupported protobuf wire")
        yield tag >> 3, wire, value, data[start:offset]


def patch_enabled(data, replacement=None):
    output = bytearray()
    enabled = False
    for number, wire, entry, raw in fields(data):
        if number == 1 and wire == 2:
            parts = list(fields(entry))
            key = next((p[2] for p in parts if p[0] == 1), b"")
            if key == b"sync_enabled":
                value = next(p[2] for p in parts if p[0] == 2)
                enabled = bool(next(p[2] for p in fields(value) if p[0] == 1))
                if replacement is not None:
                    value = b"\x08" + encode(int(replacement))
                    content = b"".join((b"\x12" + encode(len(value)) + value) if p[0] == 2 else p[3] for p in parts)
                    raw = b"\x0a" + encode(len(content)) + content
        output.extend(raw)
    return bytes(output), enabled


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--classes", required=True, help="Explicit audited classes; full suite must run on an isolated emulator")
    parser.add_argument("--log", default="feature11-device.log")
    args = parser.parse_args()
    # Tests that create dated notes/photo notes through the real AppContainer now earn durable awards.
    # These audited classes use isolated databases for achievements and do not add live reminders.
    safe_classes = {"SettingsTabsUiTest","CalendarImportUiTest", "CalendarSyncUiTest","DatabaseStartupTest", "StartupSettingsTest", "SettingsQualityTest","CalendarDropdownTest", "DayTimelineUiTest","PrivacyStorageTest", "CalendarCompactUiTest", "PhotoExportTest", "AlbumExportTest", "BatchExportUiTest", "CalendarSyncTest","AchievementRepositoryTest", "AchievementExportTest", "AchievementUiTest",
                    "MigrationCoverageTest", "MigrationCrudTest",
                    "ReminderMigrationTest", "CalendarImportTest", "PhotoRepositoryTest", "PhotoLocationRepositoryTest", "PhotoMapNativeTest", "PhotoGpsDeviceTest", "PhotoMapUiTest", "PhotoDetailsTest"}
    if any(item.split("#")[0].split(".")[-1] not in safe_classes for item in args.classes.split(",")):
        parser.error("This class may create live achievements. Run the full suite on a dedicated emulator without user data; this guard only permits audited isolated classes.")
    prefix = [args.adb, "-s", args.serial]
    backup = pathlib.Path("dist/.regression-settings-backup.pb")
    if backup.exists():
        raise RuntimeError("Previous settings backup exists; resolve restoration before testing")

    def command(*parts, **kwargs):
        return subprocess.run(prefix + list(parts), check=True, **kwargs)

    def read():
        return subprocess.check_output(prefix + ["exec-out", "run-as", PACKAGE, "cat", SETTINGS])

    def write(data):
        # Never send protobuf bytes through the adb shell stdin protocol on Windows.
        payload = base64.b64encode(data).decode("ascii")
        script = f"run-as {PACKAGE} sh -c 'echo {payload} | base64 -d > {SETTINGS}.guardtmp && chmod 600 {SETTINGS}.guardtmp && mv {SETTINGS}.guardtmp {SETTINGS}'"
        command("shell", script, stdout=subprocess.DEVNULL)
        if read() != data:
            raise RuntimeError("Settings write failed exact byte verification")

    command("shell", "am", "force-stop", PACKAGE)
    original = read()
    paused, was_enabled = patch_enabled(original, False)
    assert patch_enabled(paused)[1] is False
    backup.write_bytes(original)
    succeeded = False
    try:
        write(paused)
        assert patch_enabled(read())[1] is False
        print("Sync paused during fixture tests.", flush=True)
        invocation = ["shell", "am", "instrument", "-w", "-r"]
        if args.classes:
            invocation += ["-e", "class", args.classes]
        invocation += [PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"]
        with open(args.log, "wb") as output:
            process = subprocess.run(prefix + invocation, stdout=output, stderr=subprocess.STDOUT)
        result = pathlib.Path(args.log).read_text(encoding="utf-8", errors="replace")
        succeeded = process.returncode == 0 and bool(re.search(r"OK \(\d+ tests?\)", result)) and "FAILURES" not in result
        print("Tests passed." if succeeded else "Tests failed; inspect " + args.log, flush=True)
    finally:
        command("shell", "am", "force-stop", PACKAGE)
        try:
            restored, _ = patch_enabled(read(), was_enabled)
        except (ValueError, IndexError, subprocess.CalledProcessError):
            restored = original
        write(restored)
        assert patch_enabled(read())[1] == was_enabled
        backup.unlink()
        print("Original sync opt-in restored.", flush=True)
        if succeeded:
            command("shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity", stdout=subprocess.DEVNULL)
    if not succeeded:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
