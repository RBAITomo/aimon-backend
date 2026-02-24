#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generate pixel art sprites using PixelLab API (PixFlux or BitForge engines).

Usage:
    python generate.py "<description>" -o <output.png> [options]

Engines:
    --engine pixflux  : High-quality generation, up to 400x400 (default)
    --engine bitforge : Style-transfer generation, up to 200x200

Examples:
    python generate.py "cute dragon" -o dragon.png
    python generate.py "warrior with sword" -o warrior.png --size 64x64 --no-bg
    python generate.py "forest tile" -o tile.png --engine bitforge --style-image ref.png
"""

import argparse
import sys
from pathlib import Path

import PIL.Image

sys.path.insert(0, str(Path(__file__).parent))
from pixel_art_utils import create_client, save_image, parse_size

# Valid parameter choices
OUTLINES = ["single color black outline", "single color outline", "selective outline", "lineless"]
SHADINGS = ["flat shading", "basic shading", "medium shading", "detailed shading", "highly detailed shading"]
DETAILS = ["low detail", "medium detail", "highly detailed"]
VIEWS = ["side", "low top-down", "high top-down"]
DIRECTIONS = ["south", "south-east", "east", "north-east", "north", "north-west", "west", "south-west"]


def generate_pixflux(client, args):
    """Generate using PixFlux engine (text-to-pixel-art, up to 400x400)."""
    kwargs = {
        "description": args.description,
        "image_size": parse_size(args.size),
        "no_background": args.no_bg,
        "seed": args.seed,
    }
    if args.negative:
        kwargs["negative_description"] = args.negative
    if args.guidance:
        kwargs["text_guidance_scale"] = args.guidance
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
    if args.isometric:
        kwargs["isometric"] = True
    if args.init_image:
        kwargs["init_image"] = PIL.Image.open(args.init_image)
        kwargs["init_image_strength"] = args.init_strength
    if args.palette_image:
        kwargs["color_image"] = PIL.Image.open(args.palette_image)

    response = client.generate_image_pixflux(**kwargs)
    return response


def generate_bitforge(client, args):
    """Generate using BitForge engine (style-transfer, up to 200x200)."""
    kwargs = {
        "description": args.description,
        "image_size": parse_size(args.size),
        "no_background": args.no_bg,
        "seed": args.seed,
    }
    if args.negative:
        kwargs["negative_description"] = args.negative
    if args.guidance:
        kwargs["text_guidance_scale"] = args.guidance
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
    if args.isometric:
        kwargs["isometric"] = True
    if args.style_image:
        kwargs["style_image"] = PIL.Image.open(args.style_image)
        kwargs["style_strength"] = args.style_strength
    if args.init_image:
        kwargs["init_image"] = PIL.Image.open(args.init_image)
        kwargs["init_image_strength"] = args.init_strength
    if args.palette_image:
        kwargs["color_image"] = PIL.Image.open(args.palette_image)

    response = client.generate_image_bitforge(**kwargs)
    return response


def main():
    parser = argparse.ArgumentParser(description="Generate pixel art with PixelLab API")
    parser.add_argument("description", help="Text description of the pixel art to generate")
    parser.add_argument("-o", "--output", required=True, help="Output file path (.png)")
    parser.add_argument("-s", "--size", default="64x64", help="Image size WxH (default: 64x64)")
    parser.add_argument("-e", "--engine", choices=["pixflux", "bitforge"], default="pixflux",
                        help="Generation engine (default: pixflux)")
    parser.add_argument("--no-bg", action="store_true", help="Transparent background")
    parser.add_argument("--negative", help="Negative description (what to avoid)")
    parser.add_argument("--guidance", type=float, help="Text guidance scale (1.0-20.0)")
    parser.add_argument("--outline", choices=OUTLINES, help="Outline style")
    parser.add_argument("--shading", choices=SHADINGS, help="Shading style")
    parser.add_argument("--detail", choices=DETAILS, help="Detail level")
    parser.add_argument("--view", choices=VIEWS, help="Camera view angle")
    parser.add_argument("--direction", choices=DIRECTIONS, help="Subject facing direction")
    parser.add_argument("--isometric", action="store_true", help="Isometric projection")
    parser.add_argument("--seed", type=int, default=0, help="Seed for reproducibility (0=random)")
    parser.add_argument("--init-image", help="Path to init image (img2img)")
    parser.add_argument("--init-strength", type=int, default=300, help="Init image strength 1-999")
    parser.add_argument("--style-image", help="Style reference image (bitforge only)")
    parser.add_argument("--style-strength", type=float, default=50.0, help="Style strength 0-100")
    parser.add_argument("--palette-image", help="Force color palette from image")
    parser.add_argument("-v", "--verbose", action="store_true", help="Show generation details")

    args = parser.parse_args()

    # Warn if style-image used with pixflux
    if args.engine == "pixflux" and args.style_image:
        print("WARNING: --style-image only supported with --engine bitforge; ignoring.", file=sys.stderr)

    if args.verbose:
        print(f"Engine: {args.engine}")
        print(f"Size: {args.size}")
        print(f"Description: {args.description}")
        if args.no_bg:
            print("Background: transparent")

    client = create_client()

    try:
        if args.engine == "pixflux":
            response = generate_pixflux(client, args)
        else:
            response = generate_bitforge(client, args)
    except Exception as e:
        print(f"ERROR: Generation failed: {e}", file=sys.stderr)
        sys.exit(1)

    image = response.image.pil_image()
    saved_path = save_image(image, args.output)

    print(f"[OK] Image saved: {saved_path}")
    print(f"     Cost: ${response.usage.usd:.4f}")
    if args.verbose:
        print(f"     Size: {image.width}x{image.height}")


if __name__ == "__main__":
    main()
