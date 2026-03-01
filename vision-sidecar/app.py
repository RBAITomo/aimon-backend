"""Moondream2 vision sidecar for food detection.

FastAPI service wrapping Moondream2 VLM for fast image classification.
Uses simple yes/no + food name prompts (small model can't do JSON).
"""

import base64
import io
import logging
import time

import moondream as md
from fastapi import FastAPI, HTTPException, Request, UploadFile, File
from PIL import Image

logging.basicConfig(level=logging.INFO)
log = logging.getLogger(__name__)

app = FastAPI(title="Vision Sidecar", version="1.0.0")

model = None


@app.on_event("startup")
async def load_model():
    """Load model at startup instead of module level to avoid import crash."""
    global model
    log.info("Loading Moondream2 model...")
    t0 = time.time()
    try:
        model = md.vl(model="/app/moondream-2b-int8.mf.gz")
        log.info("Model loaded in %.1fs", time.time() - t0)
    except Exception as e:
        log.error("Failed to load model: %s", e, exc_info=True)
        raise


SPRITE_KEYS = [
    "apple pie", "bacon", "bagel", "baguette", "banana", "bread",
    "bubble_gum", "bun", "burger", "burrito dish", "butter2", "cabbage",
    "cheesecake", "cheesepuff_bowl", "chocolate", "chocolate cake",
    "cocacola", "coffee_espresso", "coffee_foam", "coffee_greentea",
    "cookies", "curry dish", "default", "default2", "donut", "dumplings",
    "egg_brown", "egg_white", "eggsalad", "eggtart", "energy_bar", "fanta",
    "frenchfries", "friedegg", "fruit_blueberry", "fruit_cherry",
    "fruit_grape_red", "fruit_kiwi", "fruit_lemon", "fruit_lime",
    "fruit_orange", "fruit_orange_slice", "fruit_peach", "fruit_strawberry",
    "fruit_watermelon", "fruit_watermelon_slice", "fruitcake", "garlicbread",
    "gingerbreadman", "green_apple", "green_grape", "gummybear", "hotdog",
    "icecream", "jam", "jelly", "lemonpie", "loafbread", "macncheese",
    "meat", "meatball", "milk_bottle", "milk_chocolate", "nacho", "omlet",
    "onigiri", "pancakes", "pastry_baguette", "pastry_bread",
    "pastry_brioche", "pastry_croissant", "pastry_pretzel", "pepsi",
    "pizza", "popcorn", "popsicle_blue", "potato", "potatochip_blue",
    "potatochip_green", "potatochip_yellow", "potatochips", "pudding",
    "ramen", "red_apple", "red_grape", "roastedchicken", "salmon",
    "sandwich", "sliced_bread_p", "spaghetti", "sprite", "steak",
    "strawberry", "strawberrycake", "sushi", "taco", "vegetable_carrot",
    "vegetable_corn", "waffle", "watermelon", "white_cheese", "wine_red",
]

# Build a lookup set for fast matching
_SPRITE_SET = {k.lower(): k for k in SPRITE_KEYS}


def _match_sprite(food_name: str) -> str:
    """Best-effort match food name to a sprite key."""
    name = food_name.lower().strip()
    # Exact match
    if name in _SPRITE_SET:
        return _SPRITE_SET[name]
    # Substring match (e.g. "banana" matches "banana")
    for key_lower, key_orig in _SPRITE_SET.items():
        if key_lower in name or name in key_lower:
            return key_orig
    return "default"


def _classify_image(img: Image.Image) -> dict:
    """Run two-step classification: is_food check, then food name."""
    encoded = model.encode_image(img)

    # Step 1: Is it food?
    check = model.query(encoded, "Is this food? Answer yes or no.")
    answer = check.get("answer", "") if isinstance(check, dict) else str(check)
    answer = answer.strip().lower()
    log.info("Food check answer: '%s'", answer)

    is_food = answer.startswith("yes")
    if not is_food:
        return {
            "is_food": False,
            "food_name": None,
            "sprite_key": None,
            "description": "Not food",
        }

    # Step 2: What food is it?
    name_result = model.query(encoded, "What food is this? Answer with just the food name.")
    food_name = name_result.get("answer", "") if isinstance(name_result, dict) else str(name_result)
    food_name = food_name.strip()
    log.info("Food name answer: '%s'", food_name)

    sprite_key = _match_sprite(food_name)
    return {
        "is_food": True,
        "food_name": food_name,
        "sprite_key": sprite_key,
        "description": food_name,
    }


@app.post("/classify")
async def classify(image: UploadFile = File(...)):
    """Classify an uploaded JPEG image for food detection."""
    MAX_IMAGE_BYTES = 5 * 1024 * 1024
    t0 = time.time()
    if model is None:
        raise HTTPException(status_code=503, detail="Model not loaded")
    raw = await image.read()
    if len(raw) > MAX_IMAGE_BYTES:
        raise HTTPException(status_code=413, detail="Image too large (max 5MB)")

    img = Image.open(io.BytesIO(raw))
    result = _classify_image(img)
    result["inference_ms"] = int((time.time() - t0) * 1000)
    return result


@app.post("/classify-base64")
async def classify_base64(request: Request):
    """Classify a base64-encoded JPEG image for food detection."""
    MAX_IMAGE_BYTES = 5 * 1024 * 1024
    t0 = time.time()
    if model is None:
        raise HTTPException(status_code=503, detail="Model not loaded")

    raw_body = await request.body()
    log.info("Received %d bytes body", len(raw_body))
    try:
        body = __import__("json").loads(raw_body)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Invalid JSON body: {e}")

    b64 = body.get("image_base64", "")
    if not b64:
        raise HTTPException(status_code=400, detail="Missing image_base64 field")

    try:
        raw = base64.b64decode(b64)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Invalid base64: {e}")

    if len(raw) > MAX_IMAGE_BYTES:
        raise HTTPException(status_code=413, detail="Image too large (max 5MB)")

    try:
        img = Image.open(io.BytesIO(raw))
        result = _classify_image(img)
    except Exception as e:
        log.error("Classification failed: %s", e, exc_info=True)
        raise HTTPException(status_code=500, detail=f"Classification failed: {e}")

    result["inference_ms"] = int((time.time() - t0) * 1000)
    log.info("Classification result: %s (%.1fs)", result, time.time() - t0)
    return result


@app.get("/health")
async def health():
    """Health check endpoint."""
    return {"status": "ok"}
