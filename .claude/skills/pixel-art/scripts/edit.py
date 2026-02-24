#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Edit existing pixel art via inpainting using PixelLab API.

Usage:
    python edit.py "<description>" --image <source.png> --mask <mask.png> -o <output.png> [options]

The mask image should be white where you want to regenerate and black where you want to preserve.

Examples:
    python edit.py "add a helmet" --image knight.png --mask helmet_mask.png -o knight_helmet.png
    python edit.py "change to red cape" --image knight.png --mask cape_mask.png -o knight_red.png
"""

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from pixel_art_utils import create_client, save_image, parse_size

OUTLINES = ["single color black outline", "single color outline", "selective outline", "lineless"]
SHADINGS = ["flat shading", "basic shading", "medium shading", "detailed shading", "highly detailed shading"]
DETAILS = ["low detail", "medium detail", "highly detailed"]
VIEWS = ["side", "low top-down", "high top-down"]
DIRECTIONS = ["south", "south-east", "east", "north-east", "north", "north-west", "west", "south-west"]


def main():
    parser = argparse.ArgumentParser(description="Edit pixel art via inpainting with PixelLab API")
    parser.add_argument("description", help="What to inpaint in the masked area")
    parser.add_argument("--image", required=True, help="Path to source image to edit")
    parser.add_argument("--mask", required=True, help="Path to mask image (white=inpaint, black=preserve)")
    parser.add_argument("-o", "--output", required=True, help="Output file path (.png)")
    parser.add_argument("-s", "--size", default=None, help="Output size WxH (default: match source)")
    parser.add_argument("--no-bg", action="store_true", help="Transparent background")
    parser.add_argument("--negative", help="Negative description")
    parser.add_argument("--guidance", type=float, default=3.0, help="Text guidance scale (default: 3.0)")
    parser.add_argument("--outline", choices=OUTLINES, help="Outline style")
    parser.add_argument("--shading", choices=SHADINGS, help="Shading style")
    parser.add_argument("--detail", choices=DETAILS, help="Detail level")
    parser.add_argument("--view", choices=VIEWS, help="Camera view")
    parser.add_argument("--direction", choices=DIRECTIONS, help="Subject facing direction")
    parser.add_argument("--palette-image", help="Force color palette from image")
    parser.add_argument("--seed", type=int, default=0, help="Seed for reproducibility")
    parser.add_argument("-v", "--verbose", action="store_true", help="Show details")

    args = parser.parse_args()

    import PIL.Image
    source = PIL.Image.open(args.image)
    mask = PIL.Image.open(args.mask)

    if args.size:
        size = parse_size(args.size)
    else:
        size = {"width": source.width, "height": source.height}

    source = source.resize((size["width"], size["height"]))
    mask = mask.resize((size["width"], size["height"]), resample=PIL.Image.Resampling.NEAREST)

    if args.verbose:
        print(f"Size: {size['width']}x{size['height']}")
        print(f"Description: {args.description}")

    client = create_client()

    kwargs = {
        "description": args.description,
        "image_size": size,
        "inpainting_image": source,
        "mask_image": mask,
        "text_guidance_scale": args.guidance,
        "no_background": args.no_bg,
        "seed": args.seed,
    }
    if args.negative:
        kwargs["negative_description"] = args.negative
    if args.outline:
        kwargs["outline"] = args.outline
    if args.shading:
        kwargs["shading"] = args.shading
    if args.detail:
        kwargs["detail"] = args.detail
    if args.view:
        kwargs["view"] = args.view
    if args.direction:
        kwargs["direction"] = args.direction
    if args.palette_image:
        kwargs["color_image"] = PIL.Image.open(args.palette_image)

    try:
        response = client.inpaint(**kwargs)
    except Exception as e:
        print(f"ERROR: Inpainting failed: {e}", file=sys.stderr)
        sys.exit(1)

    saved = save_image(response.image.pil_image(), args.output)
    print(f"[OK] Edited image saved: {saved}")
    print(f"     Cost: ${response.usage.usd:.4f}")


if __name__ == "__main__":
    main()
