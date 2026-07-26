#!/usr/bin/env python3
"""Build the subdued, semantic texture set used by the physical map.

The renderer fills arbitrary OpenStreetMap polygons, so complete building
sprites are the wrong primitive.  This script turns seamless CC0 material
textures into a coherent top-down palette and adds restrained semantic marks
for the building categories that need to read differently at map scale.

Requires Pillow:
    python -m pip install Pillow
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance, ImageOps


SIZE = 512
RESAMPLE = Image.Resampling.LANCZOS


def square(source: Path) -> Image.Image:
    """Load and centre-crop an image to the output tile size."""
    with Image.open(source) as image:
        return ImageOps.fit(image.convert("RGB"), (SIZE, SIZE), method=RESAMPLE)


def recolour(
    image: Image.Image,
    shadow: tuple[int, int, int],
    highlight: tuple[int, int, int],
    contrast: float = 0.62,
) -> Image.Image:
    """Keep material detail while constraining it to one quiet colour range."""
    grey = ImageOps.autocontrast(ImageOps.grayscale(image), cutoff=1)
    grey = ImageEnhance.Contrast(grey).enhance(contrast)
    return ImageOps.colorize(grey, black=shadow, white=highlight)


def line_grid(
    image: Image.Image,
    spacing: int,
    colour: tuple[int, int, int],
    width: int = 2,
    horizontal: bool = True,
    vertical: bool = True,
) -> None:
    """Draw a tile-safe panel grid, including matching outer edges."""
    draw = ImageDraw.Draw(image, "RGB")
    positions = list(range(0, SIZE, spacing)) + [SIZE - 1]
    if vertical:
        for position in positions:
            draw.line((position, 0, position, SIZE - 1), fill=colour, width=width)
    if horizontal:
        for position in positions:
            draw.line((0, position, SIZE - 1, position), fill=colour, width=width)


def hospital_marks(image: Image.Image) -> None:
    """Add a sparse red-cross repeat that remains legible on large footprints."""
    draw = ImageDraw.Draw(image, "RGBA")
    for centre_y in range(64, SIZE, 128):
        for centre_x in range(64, SIZE, 128):
            draw.rounded_rectangle(
                (centre_x - 7, centre_y - 24, centre_x + 7, centre_y + 24),
                radius=3,
                fill=(177, 66, 66, 205),
            )
            draw.rounded_rectangle(
                (centre_x - 24, centre_y - 7, centre_x + 24, centre_y + 7),
                radius=3,
                fill=(177, 66, 66, 205),
            )


def parking_marks(image: Image.Image) -> None:
    """Draw understated parking bays instead of an unlabelled asphalt slab."""
    draw = ImageDraw.Draw(image, "RGBA")
    for aisle_y in range(0, SIZE, 128):
        for edge_y in (aisle_y, min(aisle_y + 63, SIZE - 1)):
            draw.line((0, edge_y, SIZE - 1, edge_y), fill=(225, 228, 225, 190), width=3)
        for x in range(0, SIZE, 48):
            draw.line(
                (x, aisle_y, x, min(aisle_y + 63, SIZE - 1)),
                fill=(225, 228, 225, 180),
                width=2,
            )


def rooftop_units(image: Image.Image) -> None:
    """Give generic roofs sparse plant boxes without turning them into noise."""
    draw = ImageDraw.Draw(image, "RGBA")
    for y in range(80, SIZE, 192):
        for x in range(80, SIZE, 192):
            draw.rounded_rectangle(
                (x - 13, y - 9, x + 13, y + 9),
                radius=3,
                fill=(79, 86, 88, 105),
                outline=(235, 235, 230, 90),
                width=2,
            )


def save_jpeg(image: Image.Image, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    image.save(destination, "JPEG", quality=84, optimize=True, progressive=True)


def build_surfaces(road_source: Path, pavement_source: Path, output: Path) -> None:
    road = recolour(square(road_source), (39, 43, 48), (91, 96, 101), 0.52)
    save_jpeg(road, output / "surfaces" / "asphalt.jpg")

    # Junctions are still carriageway, merely a touch lighter for readability.
    junction = ImageEnhance.Brightness(road).enhance(1.08)
    save_jpeg(junction, output / "surfaces" / "junction.jpg")

    normal = recolour(square(pavement_source), (154, 156, 156), (205, 205, 201), 0.48)
    save_jpeg(normal, output / "land" / "normal.jpg")


def build_buildings(existing: Path, output: Path) -> None:
    palettes = {
        "residential": ((104, 70, 59), (200, 157, 130)),
        "commercial": ((63, 87, 96), (144, 174, 180)),
        "office": ((75, 86, 99), (161, 172, 183)),
        "school": ((82, 101, 79), (167, 184, 154)),
        "hospital": ((144, 151, 151), (222, 222, 215)),
        "industrial": ((82, 91, 94), (173, 184, 185)),
        "parking": ((47, 52, 57), (97, 102, 106)),
        "other": ((111, 108, 101), (184, 181, 172)),
    }

    tiles = {
        name: recolour(square(existing / f"{name}.jpg"), shadow, highlight)
        for name, (shadow, highlight) in palettes.items()
    }

    line_grid(tiles["commercial"], 64, (71, 101, 112), width=3)
    line_grid(tiles["office"], 96, (89, 100, 112), width=2)
    line_grid(tiles["school"], 128, (103, 122, 96), width=3)
    line_grid(tiles["industrial"], 12, (104, 115, 118), width=1, horizontal=False)
    line_grid(tiles["industrial"], 128, (76, 85, 88), width=3, vertical=False)
    hospital_marks(tiles["hospital"])
    parking_marks(tiles["parking"])
    rooftop_units(tiles["other"])

    for name, image in tiles.items():
        save_jpeg(image, output / "buildings" / f"{name}.jpg")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--road-source", type=Path, required=True)
    parser.add_argument("--pavement-source", type=Path, required=True)
    parser.add_argument("--existing-buildings", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    build_surfaces(args.road_source, args.pavement_source, args.output)
    build_buildings(args.existing_buildings, args.output)


if __name__ == "__main__":
    main()
