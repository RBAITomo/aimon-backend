#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Rotate pixel art sprites to different directions using PixelLab API.

Usage:
    python rotate.py <source.png> -o <output.png> --to-direction east [options]

Examples:
    python rotate.py knight.png -o knight_east.png --from-direction south --to-direction east
    python rotate.py knight.png -o ./rotations/ --all-directions
"""

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from pixel_art_utils import create_client, save_image, parse_size

VIEWS = ["side", "low top-down", "high top-down"]
DIRECTIONS = ["south", "south-east", "east", "north-east", "north", "north-west", "west", "south-west"]


def rotate_single(client, source_image, size, args, to_direction=None):
    """Rotate to a single direction."""
    kwargs = {
        "image_size": size,
        "from_image": source_image,
        "from_view": args.from_view,
        "to_view": args.to_view or args.from_view,
        "from_direction": args.from_direction,
        "to_direction": to_direction or args.to_direction,
        "image_guidance_scale": args.guidance,
        "seed": args.seed,
    }
    if args.isometric:
        kwargs["isometric"] = True
    if args.palette_image:
        import PIL.Image
        kwargs["color_image"] = PIL.Image.open(args.palette_image)

    return client.rotate(**kwargs)


def main():
    parser = argparse.ArgumentParser(description="Rotate pixel art with PixelLab API")
    parser.add_argument("source", help="Path to source sprite image")
    parser.add_argument("-o", "--output", required=True, help="Output file or directory (for --all-directions)")
    parser.add_argument("-s", "--size", default=None, help="Output size WxH (default: match source)")
    parser.add_argument("--from-direction", choices=DIRECTIONS, default="south",
                        help="Source facing direction (default: south)")
    parser.add_argument("--to-direction", choices=DIRECTIONS, default="east",
                        help="Target facing direction (default: east)")
    parser.add_argument("--from-view", choices=VIEWS, default="side", help="Source camera view")
    parser.add_argument("--to-view", choices=VIEWS, help="Target camera view (default: same as source)")
    parser.add_argument("--all-directions", action="store_true",
                        help="Generate all 8 directions (output must be directory)")
    parser.add_argument("--guidance", type=float, default=3.0, help="Image guidance scale (default: 3.0)")
    parser.add_argument("--isometric", action="store_true", help="Isometric projection")
    parser.add_argument("--palette-image", help="Force color palette from image")
    parser.add_argument("--seed", type=int, default=0, help="Seed for reproducibility")
    parser.add_argument("-v", "--verbose", action="store_true", help="Show details")

    args = parser.parse_args()

    import PIL.Image
    source_image = PIL.Image.open(args.source)

    if args.size:
        size = parse_size(args.size)
    else:
        size = {"width": source_image.width, "height": source_image.height}

    source_image = source_image.resize((size["width"], size["height"]))
    client = create_client()
    total_cost = 0.0

    if args.all_directions:
        out_dir = Path(args.output)
        out_dir.mkdir(parents=True, exist_ok=True)
        name = Path(args.source).stem

        for direction in DIRECTIONS:
            if direction == args.from_direction:
                # Copy source as-is for the original direction
                save_image(source_image, str(out_dir / f"{name}_{direction}.png"))
                if args.verbose:
                    print(f"  {direction}: copied source")
                continue

            try:
                response = rotate_single(client, source_image, size, args, to_direction=direction)
                save_image(response.image.pil_image(), str(out_dir / f"{name}_{direction}.png"))
                total_cost += response.usage.usd
                if args.verbose:
                    print(f"  {direction}: ${response.usage.usd:.4f}")
            except Exception as e:
                print(f"  WARNING: {direction} failed: {e}", file=sys.stderr)

        print(f"[OK] All directions saved to: {out_dir}")
        print(f"     Total cost: ${total_cost:.4f}")
    else:
        try:
            response = rotate_single(client, source_image, size, args)
        except Exception as e:
            print(f"ERROR: Rotation failed: {e}", file=sys.stderr)
            sys.exit(1)

        saved = save_image(response.image.pil_image(), args.output)
        print(f"[OK] Rotated image saved: {saved}")
        print(f"     Cost: ${response.usage.usd:.4f}")


if __name__ == "__main__":
    main()
