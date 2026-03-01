"""Moondream2 vision sidecar for food detection.

FastAPI service wrapping Moondream2 VLM for fast image classification.
Designed to run as a Docker sidecar with GPU access.
"""

import io
import json
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

CLASSIFY_PROMPT = (
    "Analyze this image. Is it food?\n"
    "If food, respond JSON only: "
    '{"is_food": true, "food_name": "<Vietnamese name>", '
    '"sprite_key": "<closest from list>", "description": "<brief>"}\n'
    "If not food, respond JSON only: "
    '{"is_food": false, "food_name": null, "sprite_key": null, '
    '"description": "<Vietnamese, child-friendly>"}\n'
    "Keep descriptions under 50 words. Be child-appropriate.\n"
    f"Sprite keys: {SPRITE_KEYS}"
)


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
    encoded = model.encode_image(img)
    answer = model.query(encoded, CLASSIFY_PROMPT)["answer"]
    elapsed = time.time() - t0
    log.info("Classification took %.3fs (%d bytes)", elapsed, len(raw))

    try:
        # Strip markdown fences if present
        text = answer.strip()
        if text.startswith("```"):
            first_nl = text.index("\n")
            last_fence = text.rfind("```")
            if first_nl > 0 and last_fence > first_nl:
                text = text[first_nl + 1:last_fence].strip()
        result = json.loads(text)
        result["inference_ms"] = int(elapsed * 1000)
        return result
    except (json.JSONDecodeError, ValueError) as e:
        log.warning("Failed to parse model output: %s — raw: %s", e, answer[:200])
        return {
            "is_food": False,
            "food_name": None,
            "sprite_key": None,
            "description": answer[:100],
            "inference_ms": int(elapsed * 1000),
        }


@app.post("/classify-base64")
async def classify_base64(request: Request):
    """Classify a base64-encoded JPEG image for food detection."""
    MAX_IMAGE_BYTES = 5 * 1024 * 1024
    t0 = time.time()
    if model is None:
        raise HTTPException(status_code=503, detail="Model not loaded")

    import base64
    body = await request.json()
    b64 = body.get("image_base64", "")
    raw = base64.b64decode(b64)
    if len(raw) > MAX_IMAGE_BYTES:
        raise HTTPException(status_code=413, detail="Image too large (max 5MB)")

    img = Image.open(io.BytesIO(raw))
    encoded = model.encode_image(img)
    answer = model.query(encoded, CLASSIFY_PROMPT)["answer"]
    elapsed = time.time() - t0
    log.info("Classification took %.3fs (%d bytes)", elapsed, len(raw))

    try:
        text = answer.strip()
        if text.startswith("```"):
            first_nl = text.index("\n")
            last_fence = text.rfind("```")
            if first_nl > 0 and last_fence > first_nl:
                text = text[first_nl + 1:last_fence].strip()
        result = json.loads(text)
        result["inference_ms"] = int(elapsed * 1000)
        return result
    except (json.JSONDecodeError, ValueError) as e:
        log.warning("Failed to parse model output: %s — raw: %s", e, answer[:200])
        return {
            "is_food": False,
            "food_name": None,
            "sprite_key": None,
            "description": answer[:100],
            "inference_ms": int(elapsed * 1000),
        }


@app.get("/health")
async def health():
    """Health check endpoint."""
    return {"status": "ok"}
