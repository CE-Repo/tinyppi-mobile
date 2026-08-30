#!/usr/bin/env python3
"""Cut the delta fixtures the merge test runs against, out of the add-on itself.

`SnapshotMerge` on the Kotlin side has to undo exactly what `_snapshot_delta`
on the add-on side does. Fixtures written by hand only ever test one of those
against an idea of the other, so these are produced by running the add-on's own
function: it is lifted verbatim out of ``resources/lib/web/server.py`` - the
delta block needs nothing from Kodi - and run over pairs of realistic snapshots.
Each fixture carries the ``current`` the delta was measured to, which is what
the test asserts the fold reconstructs.

Usage::

    python tools/generate-delta-fixtures.py /path/to/script.tinyppi

Writes ``app/src/test/resources/delta_fixtures.json``. Re-run it whenever the
add-on's delta format changes; a fixture set that no longer covers every branch
fails the guard in ``DeltaFixtureTest`` rather than passing quietly.
"""

from __future__ import annotations

import io
import json
import os
import sys

# Where the delta block starts and ends in the add-on's server module. Both
# markers are lines that exist for their own reasons rather than for this
# script, so a rename shows up here as a clean failure.
_BLOCK_START = '_DELTA_LISTS = ("groups", "metadata")'
_BLOCK_END = "# --- Artwork ---"

_OUTPUT = os.path.join("app", "src", "test", "resources", "delta_fixtures.json")


def load_snapshot_delta(addon_root: str):
    """The add-on's own ``_snapshot_delta``, imported without importing Kodi."""
    path = os.path.join(addon_root, "resources", "lib", "web", "server.py")
    source = io.open(path, encoding="utf-8").read()
    try:
        start = source.index(_BLOCK_START)
        end = source.index(_BLOCK_END)
    except ValueError:
        raise SystemExit(
            f"{path} no longer has the delta block this script lifts.\n"
            "Find where _snapshot_delta lives now and update the markers."
        )
    namespace: dict = {}
    exec(source[start:end], namespace)  # noqa: S102 - the add-on's own code
    return namespace["_snapshot_delta"]


def playing(seq, time, mode, depth, l1_max, *, wide_groups=False, metadata=None):
    """One snapshot of a title that is running, in the shape the builder emits."""
    groups = [
        {"id": "video", "title": "Video", "rows": [
            {"id": "video.32000", "label": "Display mode", "value": mode, "detail": ""},
            {"id": "video.32099", "label": "Bit depth", "value": depth, "detail": "(dv)"},
        ]},
        {"id": "audio", "title": "Audio", "rows": [
            {"id": "audio.32045", "label": "Codec", "value": "TrueHD 7.1",
             "detail": "(Atmos)"},
        ]},
    ]
    # A card that gains a row is a changed *shape*, which is what makes the
    # add-on send the whole list instead of a row patch.
    if wide_groups:
        groups[0]["rows"].append(
            {"id": "video.32287", "label": "Decoder", "value": "am-h265", "detail": ""})

    return {
        "seq": seq, "playing": True, "paused": False, "title": "Dune",
        "filename": "/media/dune.mkv", "hdr_type": "dolbyvision",
        "effective": "dolbyvision", "output_type": "hdr10",
        "time": time, "duration": "2:35:00",
        "metrics": {"l1": {"min": 0, "max": l1_max, "avg": 98},
                    "bars": [0, 0, 138, 138], "frame": {"w": 3840, "h": 2160},
                    "aspect": 2.39, "fps_in": 23.976, "fps_drop": 0.0,
                    "fps_out": 23.976, "progress": 7.8, "cpu": 31, "cpu_temp": 58,
                    "memory": 42, "cache": 100},
        "groups": groups,
        "metadata": metadata if metadata is not None else default_metadata(),
        "vs10": {"options": [{"mode": "sdr8", "label": "Dolby Vision -> SDR"}],
                 "output": "DV-LL"},
        "logos": {"video": "codecs/Dolby_Vision.png",
                  "audio": "codecs/Dolby_TrueHD_Atmos.png"},
        "art": {"poster": "1a2b3c4d", "fanart": ""},
        "media": {"year": "2021", "genre": "Sci-Fi", "show": "", "season": "",
                  "episode": ""},
        "controls": {"audio": [{"index": 0, "label": "ENG"}], "subtitle": [],
                     "audio_current": 0, "subtitle_current": -1,
                     "subtitle_on": False, "volume": 72, "muted": False},
        "session": {"seq": 4, "switches": 2, "warnings": 0},
        "control": True, "streams_full": False,
    }


def default_metadata(min_pq="0", slope=("1", "2", "3")):
    return [
        {"kind": "section", "name": "L1", "value": ""},
        {"kind": "row", "name": "Min PQ", "value": min_pq},
        {"kind": "headings", "name": "Trim", "cells": ["100", "600", "1000"]},
        {"kind": "columns", "name": "Slope", "cells": list(slope)},
    ]


def stopped(seq):
    """What the builder sends once nothing is playing: five keys and no more."""
    return {"seq": seq, "playing": False, "groups": [], "metrics": {}, "metadata": [],
            "vs10": {"options": [], "output": ""},
            "session": {"seq": 0, "switches": 0, "warnings": 0}, "last": {},
            "control": True, "streams_full": False}


def build_pairs():
    """Consecutive snapshots that between them reach every branch of the delta."""
    a = playing(1, "0:12:03", "3840x2160p23", "10 bit", 1200.5)
    # Only readings moved: a `set` and nothing else.
    b = playing(2, "0:12:04", "3840x2160p23", "10 bit", 980.0)
    # Rows moved too, with the card list still the same shape: a row patch.
    c = playing(3, "0:12:05", "3840x2160p59", "12 bit", 4000.0)
    # A card gained a row: the whole list travels.
    d = playing(4, "0:12:06", "3840x2160p59", "12 bit", 4000.0, wide_groups=True)
    # Metadata moved, in both of its shapes: a value and a row of cells.
    e = playing(5, "0:12:07", "3840x2160p59", "12 bit", 4000.0, wide_groups=True,
                metadata=default_metadata(min_pq="7", slope=("4", "5", "6")))
    # Playback ended: a dozen keys are deleted at once.
    f = stopped(6)
    # And a new title started, which brings them all back.
    g = playing(7, "0:00:01", "1920x1080p24", "8 bit", 100.0)
    return [(a, b), (b, c), (c, d), (d, e), (e, f), (f, g)]


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit(f"usage: {sys.argv[0]} /path/to/script.tinyppi")

    snapshot_delta = load_snapshot_delta(sys.argv[1])
    fixtures = [
        {"base": previous,
         "delta": snapshot_delta(previous, current),
         "expected": current}
        for previous, current in build_pairs()
    ]

    os.makedirs(os.path.dirname(_OUTPUT), exist_ok=True)
    with io.open(_OUTPUT, "w", encoding="utf-8") as handle:
        json.dump(fixtures, handle, ensure_ascii=False, indent=2)

    print(f"wrote {len(fixtures)} fixtures to {_OUTPUT}")
    for index, fixture in enumerate(fixtures):
        delta = fixture["delta"]
        print(f"  {index}: keys={sorted(delta)} "
              f"groups={type(delta.get('groups')).__name__} "
              f"metadata={type(delta.get('metadata')).__name__}")


if __name__ == "__main__":
    main()
