#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generate pixel art animations using PixelLab API.

Usage:
    python animate.py "<description>" --action "<action>" --ref <reference.png> -o <output_dir> [options]

Modes:
    --mode text     : Text-driven animation (default, fixed 64x64)
    --mode skeleton : Skeleton-driven animation (16-256px, requires keypoints)

Examples:
    python animate.py "knight" --action "walk" --ref knight.png -o ./frames/
    python animate.py "cat" --action "idle" --ref cat.png -o ./frames/ --frames 6
"""

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from pixel_art_utils import create_client, save_image, parse_size

VIEWS = ["side", "low top-down", "high top-down"]
DIRECTIONS = ["south", "south-east", "east", "north-east", "north", "north-west", "west", "south-west"]


def animate_text(client, args):
    """Generate animation using text-driven mode (fixed 64x64)."""
    import PIL.Image
    ref = PIL.Image.open(args.ref).resize((64, 64), resample=PIL.Image.Resampling.NEAREST)

    kwargs = {
        "image_size": {"width": 64, "height": 64},
        "description": args.description,
        "action": args.action,
        "reference_image": ref,
        "n_frames": args.frames,
        "view": args.view,
        "direction": args.direction,
        "seed": args.seed,
    }
    if args.negative:
        kwargs["negative_description"] = args.negative
    if args.palette_image:
        kwargs["color_image"] = PIL.Image.open(args.palette_image)

    return client.animate_with_text(**kwargs)


def animate_skeleton(client, args):
    """Generate animation using skeleton keypoints."""
    import PIL.Image

    size = parse_size(args.size)
    ref = PIL.Image.open(args.ref).resize((size["width"], size["height"]), resample=PIL.Image.Resampling.NEAREST)

    if not args.keypoints:
        print("ERROR: --keypoints JSON file required for skeleton mode", file=sys.stderr)
        sys.exit(1)

    with open(args.keypoints) as f:
        keypoints_data = json.load(f)

    # Expect list of frames, each frame is a list of keypoints
    skeleton_keypoints = []
    for frame_kps in keypoints_data:
        skeleton_keypoints.append({"keypoints": frame_kps})

    kwargs = {
        "image_size": size,
        "skeleton_keypoints": skeleton_keypoints,
        "reference_image": ref,
        "view": args.view,
        "direction": args.direction,
        "seed": args.seed,
    }
    if args.palette_image:
        kwargs["color_image"] = PIL.Image.open(args.palette_image)

    return client.animate_with_skeleton(**kwargs)


def main():
    parser = argparse.ArgumentParser(description="Generate pixel art animations with PixelLab API")
    parser.add_argument("description", help="Character/subject description")
    parser.add_argument("--action", required=True, help="Animation action (e.g. walk, run, idle, attack)")
    parser.add_argument("--ref", required=True, help="Path to reference character image")
    parser.add_argument("-o", "--output", required=True, help="Output directory for frames")
    parser.add_argument("-m", "--mode", choices=["text", "skeleton"], default="text",
                        help="Animation mode (default: text)")
    parser.add_argument("-s", "--size", default="64x64", help="Image size WxH (skeleton mode only)")
    parser.add_argument("--frames", type=int, default=4, help="Number of frames 2-20 (default: 4)")
    parser.add_argument("--view", choices=VIEWS, default="side", help="Camera view (default: side)")
    parser.add_argument("--direction", choices=DIRECTIONS, default="east", help="Facing direction (default: east)")
    parser.add_argument("--negative", help="Negative description")
    parser.add_argument("--keypoints", help="Path to keypoints JSON file (skeleton mode)")
    parser.add_argument("--palette-image", help="Force color palette from image")
    parser.add_argument("--seed", type=int, default=0, help="Seed for reproducibility (0=random)")
    parser.add_argument("-v", "--verbose", action="store_true", help="Show details")

    args = parser.parse_args()

    if not (2 <= args.frames <= 20):
        print("ERROR: --frames must be 2-20", file=sys.stderr)
        sys.exit(1)

    if args.verbose:
        print(f"Mode: {args.mode}")
        print(f"Description: {args.description}")
        print(f"Action: {args.action}")
        print(f"Frames: {args.frames}")

    client = create_client()

    try:
        if args.mode == "text":
            response = animate_text(client, args)
        else:
            response = animate_skeleton(client, args)
    except Exception as e:
        print(f"ERROR: Animation failed: {e}", file=sys.stderr)
        sys.exit(1)

    # Save frames
    out_dir = Path(args.output)
    out_dir.mkdir(parents=True, exist_ok=True)

    name = Path(args.ref).stem
    saved = []
    for i, frame in enumerate(response.images):
        frame_path = out_dir / f"{name}_{args.action}_{i:02d}.png"
        save_image(frame.pil_image(), str(frame_path))
        saved.append(str(frame_path))

    print(f"[OK] {len(saved)} frames saved to: {out_dir}")
    print(f"     Cost: ${response.usage.usd:.4f}")
    for path in saved:
        print(f"     - {path}")


if __name__ == "__main__":
    main()
