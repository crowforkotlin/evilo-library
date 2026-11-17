#!/usr/bin/env python3
"""Raise the published SDK version by one position.

Positions carry like an odometer with base 10: raising a higher position resets
the ones below it.

    1.0.0 + z -> 1.0.1
    1.0.9 + z -> 1.1.0      (carry resets z)
    1.9.5 + y -> 2.0.0      (carry resets y and z)

The version is the protocol contract that plugin repositories compile against.
Raise it for every release: this repository publishes only when the version is
new, so tagging an unchanged version would upload nothing.

Usage:
    ./.github/scripts/bump_version.py z
    ./.github/scripts/bump_version.py z --dry-run
"""

import argparse
import pathlib
import re
import sys

POSITIONS = {"x": 0, "y": 1, "z": 2}
PROPERTIES = pathlib.Path("gradle.properties")
NAME = "EVILO_LIBRARY_VERSION_NAME"


def bump(version, position):
    parts = [int(part) for part in version.split(".")]
    if len(parts) != 3 or any(part < 0 for part in parts):
        raise SystemExit(f"Expected x.y.z, found '{version}'.")
    index = POSITIONS[position]
    parts[index] += 1
    # Odometers reset the lower positions when a higher one moves.
    for lower in range(index + 1, 3):
        parts[lower] = 0
    # A single decimal position cannot hold ten; carry it upward.
    for lower in range(2, 0, -1):
        if parts[lower] >= 10:
            parts[lower] = 0
            parts[lower - 1] += 1
    return ".".join(str(part) for part in parts)


def main():
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("position", choices=sorted(POSITIONS), help="position to raise")
    parser.add_argument("--dry-run", action="store_true", help="print the result without writing")
    args = parser.parse_args()

    if not PROPERTIES.is_file():
        raise SystemExit("Run this from the repository root; no gradle.properties here.")
    text = PROPERTIES.read_text()
    match = re.search(rf"^{NAME}=(.*)$", text, re.MULTILINE)
    if not match:
        raise SystemExit(f"gradle.properties does not contain {NAME}.")
    current = match.group(1).strip()
    updated = bump(current, args.position)

    print(f"{current} -> {updated} (raise {args.position})")
    if args.dry_run:
        return 0
    PROPERTIES.write_text(
        re.sub(rf"^{NAME}=.*$", f"{NAME}={updated}", text, count=1, flags=re.MULTILINE)
    )
    print(f"Commit, then tag library-v{updated}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
