---
name: pixel-art
description: "Generate pixel art sprites, animations, and rotations via PixelLab API. 6 modes: sprite, style, animate, skeleton, rotate, edit. Supports transparent backgrounds, palette forcing, 8-direction rotation, and skeleton-based animation."
version: 1.0.0
license: MIT
---

# Pixel Art - PixelLab Image Generation

Generate pixel art using the PixelLab API. Sprites, animations, rotations, and inpainting.

**Validation interview is mandatory** (use `--skip` to bypass).

## Workflow

**IMPORTANT:** Follow `references/validation-workflow.md` when this skill is activated.

## Quick Start

```bash
# Generate a sprite
python3 scripts/generate.py "cute dragon" -o dragon.png --no-bg

# Generate walk animation
python3 scripts/animate.py "knight" --action "walk" --ref knight.png -o ./frames/

# Rotate to all directions
python3 scripts/rotate.py knight.png -o ./rotations/ --all-directions

# Edit existing sprite
python3 scripts/edit.py "add helmet" --image knight.png --mask mask.png -o knight_v2.png

# Check balance
python3 scripts/balance.py
```

## Generation Modes

| Mode | Script | Engine | Max Size | Description |
|------|--------|--------|----------|-------------|
| `sprite` | generate.py | pixflux | 400x400 | Text-to-pixel-art (default) |
| `style` | generate.py | bitforge | 200x200 | Style-transfer generation |
| `animate` | animate.py | text | 64x64 | Text-driven animation frames |
| `skeleton` | animate.py | skeleton | 256x256 | Skeleton-pose animation |
| `rotate` | rotate.py | rotate | 200x200 | Multi-direction sprite rotation |
| `edit` | edit.py | inpaint | 200x200 | Inpaint/edit existing sprites |

## Common Options

| Flag | Description |
|------|-------------|
| `-o, --output` | Output path (required) |
| `-s, --size` | Size WxH (default: 64x64) |
| `--no-bg` | Transparent background |
| `--outline` | black outline, single color, selective, lineless |
| `--shading` | flat, basic, medium, detailed, highly detailed |
| `--detail` | low, medium, highly detailed |
| `--view` | side, low top-down, high top-down |
| `--direction` | 8 compass directions (south, east, etc.) |
| `--isometric` | Isometric projection |
| `--seed` | Reproducible generation |
| `--palette-image` | Force color palette from reference |
| `-v, --verbose` | Show generation details |

## AIMON Integration

**Sprite pipeline for tamagotchi creatures:**
1. Generate base sprite: `generate.py "ai monster" -o base.png --no-bg --size 64x64`
2. Animate walk cycle: `animate.py "ai monster" --action "walk" --ref base.png -o ./walk/`
3. Rotate all directions: `rotate.py base.png -o ./rotations/ --all-directions`
4. Edit variations: `edit.py "happy expression" --image base.png --mask face.png -o happy.png`

**Style consistency:** Use `--palette-image` with a reference sprite to keep colors consistent across all generated assets.

---

## References

| Topic | File |
|-------|------|
| **Validation Workflow** | `references/validation-workflow.md` |
| **API Reference** | `references/pixellab-api-reference.md` |

## Scripts

| Script | Purpose |
|--------|---------|
| `generate.py` | Generate sprites (pixflux/bitforge engines) |
| `animate.py` | Generate animation frames (text/skeleton) |
| `rotate.py` | Rotate sprites to different directions |
| `edit.py` | Inpaint/edit existing pixel art |
| `balance.py` | Check PixelLab credit balance |
| `pixel_art_utils.py` | Shared utilities (client, env, I/O) |
