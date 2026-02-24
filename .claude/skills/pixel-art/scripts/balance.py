#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Check PixelLab account credit balance.

Usage:
    python balance.py
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from pixel_art_utils import create_client


def main():
    client = create_client()
    balance = client.get_balance()
    print(f"PixelLab Balance: ${balance.usd:.4f} USD")


if __name__ == "__main__":
    main()
