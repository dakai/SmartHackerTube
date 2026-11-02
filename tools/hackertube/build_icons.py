#!/usr/bin/env python3
"""Render the SmartHackerTube icon set (Hacker News orange, play-arrow "H").

Usage: python3 tools/hackertube/build_icons.py
Writes the PNGs directly, no source files are kept: edit the SVG helpers below.
Requires rsvg-convert.
"""
import subprocess
import sys
import tempfile
from pathlib import Path

ORANGE = "#ff6600"
RED = "#d32f2f"
GREY = "#262626"
WHITE = "#ffffff"
DARK = "#1f1f1f"

GLYPH = """  <rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{colour}"/>
  <rect x="{x2}" y="{y}" width="{w}" height="{h}" fill="{colour}"/>
  <path d="M {x3} {y1} L {x3} {y2} L {x4} {y3} Z" fill="{colour}"/>"""


def glyph(size, colour=WHITE, scale=1.0):
    """The play-arrow H centred in a `size` box."""
    def p(value):
        return size / 2 + (value - size / 2) * scale

    stem = size * 0.13
    top = p(size * 0.27)
    bottom = p(size * 0.73)
    left = p(size * 0.24)
    right = p(size * 0.63)
    return (
        f'  <rect x="{left:.1f}" y="{top:.1f}" width="{stem:.1f}" height="{bottom - top:.1f}" fill="{colour}"/>\n'
        f'  <rect x="{right:.1f}" y="{top:.1f}" width="{stem:.1f}" height="{bottom - top:.1f}" fill="{colour}"/>\n'
        f'  <path d="M {left + stem:.1f} {p(size * 0.43):.1f} L {left + stem:.1f} {p(size * 0.57):.1f} L {right:.1f} {p(size * 0.5):.1f} Z" fill="{colour}"/>'
    )


def svg(width, height, body):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
            f'viewBox="0 0 {width} {height}">{body}</svg>')


def icon(size=320, box=ORANGE, rounded=True, label=None):
    radius = size * 0.22 if rounded else 0
    body = f'  <rect width="{size}" height="{size}" rx="{radius:.1f}" fill="{box}"/>\n  {glyph(size)}'

    if label:
        body += (
            f'\n  <text x="{size / 2}" y="{size * 0.84}" font-family="Nimbus Sans" font-size="{size * 0.17:.0f}"'
            f' font-style="italic" font-weight="bold" fill="{WHITE}" fill-opacity="0.95"'
            f' text-anchor="middle">{label}</text>'
        )

    return svg(size, size, body)


def logo(width=320, height=180, box=ORANGE, background=None):
    box_size = height * 0.56
    box_y = (height - box_size) / 2
    body = ""

    if background:
        body += f'  <rect width="{width}" height="{height}" fill="{background}"/>\n'

    body += (
        f'  <g transform="translate({height * 0.06:.1f}, {box_y:.1f})">\n'
        f'    <rect width="{box_size:.1f}" height="{box_size:.1f}" rx="{box_size * 0.22:.1f}" fill="{box}"/>\n'
        f'    <g transform="scale({box_size / 320:.4f})">{glyph(320)}</g>\n'
        f'  </g>\n'
        f'  <text x="{height * 0.06 + box_size + height * 0.06:.1f}" y="{height * 0.56:.1f}"'
        f' font-family="Nimbus Sans" font-size="{height * 0.16:.0f}" font-weight="bold" fill="{WHITE}">HackerTube</text>'
    )

    return svg(width, height, body)


def render(content, path):
    with tempfile.NamedTemporaryFile("w", suffix=".svg", delete=False) as source:
        source.write(content)
        svg_path = source.name

    path.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["rsvg-convert", "-o", str(path), svg_path], check=True)
    print(path)


def main():
    root = Path(__file__).resolve().parents[2]
    flavors = {
        "ststable": None,
        "stbeta": "beta",
        "stfdroid": None,
    }

    for flavor, label in flavors.items():
        res = root / "smarttubetv/src" / flavor / "res"
        render(icon(320, ORANGE, True, label), res / "mipmap-nodpi/app_icon.png")
        render(icon(320, ORANGE, False), res / "mipmap-nodpi-v30/app_icon.png")
        render(logo(320, 180, ORANGE), res / "mipmap-nodpi/app_logo.png")
        render(logo(320, 180, RED), res / "mipmap-nodpi/app_logo_semi_red.png")
        render(logo(320, 180, GREY), res / "mipmap-nodpi/app_logo_semi_grey.png")
        render(logo(320, 180, ORANGE, DARK), res / "mipmap-nodpi/app_banner.png")

    main_res = root / "smarttubetv/src/main/res/mipmap-nodpi"
    render(icon(320, ORANGE, True), main_res / "app_icon.png")
    render(logo(320, 180, ORANGE), main_res / "app_logo.png")
    render(logo(320, 180, RED), main_res / "app_logo_semi_red.png")
    render(logo(320, 180, GREY), main_res / "app_logo_semi_grey.png")
    render(logo(320, 180, ORANGE, DARK), main_res / "app_banner.png")


if __name__ == "__main__":
    sys.exit(main())
