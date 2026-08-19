#!/usr/bin/env python3
"""下载 IANA 特殊用途地址注册表，并与测试快照比较。"""

from pathlib import Path
import urllib.request

MODULE_ROOT = Path(__file__).resolve().parent.parent
SNAPSHOT_DIRECTORY = MODULE_ROOT / "src" / "test" / "resources" / "iana"
CANDIDATE_DIRECTORY = MODULE_ROOT / "build" / "iana-snapshot-candidate"
REGISTRY_URLS = {
    "iana-ipv4-special-registry-1.csv": (
        "https://www.iana.org/assignments/iana-ipv4-special-registry/"
        "iana-ipv4-special-registry-1.csv"
    ),
    "iana-ipv6-special-registry-1.csv": (
        "https://www.iana.org/assignments/iana-ipv6-special-registry/"
        "iana-ipv6-special-registry-1.csv"
    ),
}


def main() -> int:
    changed = False
    for filename, url in REGISTRY_URLS.items():
        with urllib.request.urlopen(url, timeout=30) as response:
            current = response.read()
        snapshot_path = SNAPSHOT_DIRECTORY / filename
        snapshot = snapshot_path.read_bytes()
        if current == snapshot:
            print(f"UNCHANGED {filename}")
            continue
        changed = True
        CANDIDATE_DIRECTORY.mkdir(parents=True, exist_ok=True)
        candidate_path = CANDIDATE_DIRECTORY / filename
        candidate_path.write_bytes(current)
        print(f"CHANGED   {filename} -> {candidate_path}")
    return 1 if changed else 0


if __name__ == "__main__":
    raise SystemExit(main())
