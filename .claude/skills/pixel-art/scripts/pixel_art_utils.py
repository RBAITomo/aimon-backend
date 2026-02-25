#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Shared utilities for pixel-art skill: client initialization, env resolution, image I/O.
"""

import os
import sys
from pathlib import Path

# Environment resolution — same pattern as ai-artist skill
CLAUDE_ROOT = Path.home() / '.claude'
sys.path.insert(0, str(CLAUDE_ROOT / 'scripts'))
PROJECT_CLAUDE = Path(__file__).parent.parent.parent.parent
sys.path.insert(0, str(PROJECT_CLAUDE / 'scripts'))

try:
    from resolve_env import resolve_env
    CENTRALIZED_RESOLVER = True
except ImportError:
    CENTRALIZED_RESOLVER = False
    try:
        from dotenv import load_dotenv
        load_dotenv(CLAUDE_ROOT / '.env')
        load_dotenv(CLAUDE_ROOT / 'skills' / '.env')
    except ImportError:
        pass

try:
    import pixellab
    PIXELLAB_AVAILABLE = True
except ImportError:
    PIXELLAB_AVAILABLE = False


def get_api_key() -> str:
    """Get PixelLab API key from environment."""
    if CENTRALIZED_RESOLVER:
        return resolve_env('PIXELLAB_SECRET', skill='pixel-art')
    return os.getenv('PIXELLAB_SECRET')


def create_client() -> 'pixellab.Client':
    """Create and return a PixelLab client."""
    if not PIXELLAB_AVAILABLE:
        print("ERROR: pixellab package not installed. Run: pip install pixellab", file=sys.stderr)
        sys.exit(1)

    api_key = get_api_key()
    if not api_key:
        print("ERROR: PIXELLAB_SECRET not set. Add it to your .env file.", file=sys.stderr)
        sys.exit(1)

    return pixellab.Client(secret=api_key)


def save_image(pil_image, output_path: str) -> str:
    """Save a PIL image to disk, ensuring parent directory exists."""
    path = Path(output_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    pil_image.save(str(path))
    return str(path.resolve())


def parse_size(size_str: str) -> dict:
    """Parse 'WxH' string into ImageSize dict. e.g. '64x64' -> {'width': 64, 'height': 64}."""
    parts = size_str.lower().split('x')
    if len(parts) != 2:
        raise ValueError(f"Invalid size format: {size_str}. Use WxH, e.g. 64x64")
    try:
        w, h = int(parts[0]), int(parts[1])
    except ValueError:
        raise ValueError(f"Invalid size format: {size_str}. Width and height must be integers")
    if w <= 0 or h <= 0:
        raise ValueError(f"Size dimensions must be positive: got {w}x{h}")
    return {"width": w, "height": h}


# Windows stdout encoding fix
if sys.platform == 'win32':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass
