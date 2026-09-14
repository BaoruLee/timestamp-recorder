#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Subset MiSans TTFs to the character set this app can actually display.

The app's own UI text is simplified Chinese + Latin + digits + punctuation, and
user-facing text is event names typed on a phone (CJK). Subsetting to GB2312
plus Latin/punctuation keeps coverage high while cutting each weight from ~7.5MB
to a fraction of that, so the APK stays small.

Usage:
    python tools/subset_misans.py <src_dir> <dst_dir>
"""
import os
import sys

from fontTools import subset


def unicode_set():
    codes = set()
    # ASCII + Latin-1 supplement
    codes.update(range(0x0020, 0x007F))
    codes.update(range(0x00A0, 0x0100))
    # General punctuation (… — “ ” • etc.) and letterlike/arrows/symbols
    codes.update(range(0x2000, 0x2070))
    codes.update(range(0x2190, 0x2200))
    codes.update(range(0x2260, 0x2266))
    codes.update(range(0x25A0, 0x2600))
    codes.update(range(0x2600, 0x2700))
    # CJK symbols and punctuation (、。「」etc.) + fullwidth forms （＋＃：）
    codes.update(range(0x3000, 0x3040))
    codes.update(range(0xFF00, 0xFFF0))
    # GB2312 (level 1+2 hanzi and symbols): what simplified Chinese text needs
    for b1 in range(0xA1, 0xFF):
        for b2 in range(0xA1, 0xFF):
            try:
                codes.add(ord(bytes([b1, b2]).decode("gb2312")))
            except UnicodeDecodeError:
                pass
    return codes


def main():
    src_dir, dst_dir = sys.argv[1], sys.argv[2]
    os.makedirs(dst_dir, exist_ok=True)
    codes = unicode_set()
    unicodes = ",".join("U+%04X" % c for c in sorted(codes))
    print("subsetting to %d code points" % len(codes))

    for name in ("MiSans-Regular.ttf", "MiSans-Medium.ttf", "MiSans-Bold.ttf"):
        src = os.path.join(src_dir, name)
        dst = os.path.join(dst_dir, name)
        if not os.path.exists(src):
            print("!! missing source: %s" % src)
            continue
        args = [
            src,
            "--output-file=" + dst,
            "--unicodes=" + unicodes,
            "--layout-features=*",
            "--glyph-names",
            "--drop-tables+=DSIG",
            "--no-subset-tables+=meta",
        ]
        subset.main(args)
        print("%-20s %6.2f MB -> %5.2f MB" % (
            name,
            os.path.getsize(src) / 1048576,
            os.path.getsize(dst) / 1048576,
        ))


if __name__ == "__main__":
    main()
