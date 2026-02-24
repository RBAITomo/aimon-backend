# Pixel Art Validation Workflow

When the `pixel-art` skill is activated, follow this workflow before generating.

## Step 1: Parse Arguments

Extract from user request:
- `concept`: What to generate (required)
- `--skip`: If present, skip interview and use defaults

**Defaults (when --skip):**
- Size: 64x64
- Outline: single color black outline
- Shading: medium shading
- Background: transparent
- Direction: south
- View: side

## Step 2: Interview User (4 Questions)

Use `AskUserQuestion` with these questions:

```json
{
  "questions": [
    {
      "question": "What size should the pixel art be?",
      "header": "Size",
      "multiSelect": false,
      "options": [
        {"label": "64x64 (Recommended)", "description": "Standard game sprite size, works with all modes"},
        {"label": "32x32", "description": "Small sprite, retro style"},
        {"label": "128x128", "description": "Detailed sprite, good for portraits"},
        {"label": "256x256", "description": "Large, highly detailed pixel art"}
      ]
    },
    {
      "question": "What outline style?",
      "header": "Outline",
      "multiSelect": false,
      "options": [
        {"label": "Black outline (Recommended)", "description": "Classic pixel art with black border"},
        {"label": "Single color outline", "description": "Outline matches subject colors"},
        {"label": "Selective outline", "description": "Outline only where needed"},
        {"label": "Lineless", "description": "No outline, soft edges"}
      ]
    },
    {
      "question": "What shading level?",
      "header": "Shading",
      "multiSelect": false,
      "options": [
        {"label": "Medium shading (Recommended)", "description": "Balanced detail and readability"},
        {"label": "Flat shading", "description": "Minimal shading, clean look"},
        {"label": "Detailed shading", "description": "Rich shading with depth"},
        {"label": "Highly detailed", "description": "Maximum detail and texture"}
      ]
    },
    {
      "question": "Transparent background?",
      "header": "Background",
      "multiSelect": false,
      "options": [
        {"label": "Transparent (Recommended)", "description": "No background, ready for game use"},
        {"label": "Solid background", "description": "Keep generated background"}
      ]
    }
  ]
}
```

## Step 3: Map Answers to CLI Flags

| Answer | CLI Flag |
|--------|----------|
| 64x64 | `-s 64x64` |
| 32x32 | `-s 32x32` |
| 128x128 | `-s 128x128` |
| 256x256 | `-s 256x256` |
| Black outline | `--outline "single color black outline"` |
| Single color outline | `--outline "single color outline"` |
| Selective outline | `--outline "selective outline"` |
| Lineless | `--outline "lineless"` |
| Flat shading | `--shading "flat shading"` |
| Medium shading | `--shading "medium shading"` |
| Detailed shading | `--shading "detailed shading"` |
| Highly detailed | `--shading "highly detailed shading"` |
| Transparent | `--no-bg` |
| Solid background | (no flag) |

## Step 4: Build and Run Command

**Template:**
```bash
cd .claude/skills/pixel-art && VENV_PYTHON scripts/generate.py "<concept>" -o ./generated-$(date +%Y%m%d-%H%M%S).png -s <size> --outline "<outline>" --shading "<shading>" [--no-bg] -v
```

Replace `VENV_PYTHON` with:
- **Linux/macOS:** `.claude/skills/.venv/bin/python3`
- **Windows:** `.claude\skills\.venv\Scripts\python.exe`

## Step 5: Confirm & Generate

Show preview of settings before generating:
```
Concept: <concept>
Size: <size>
Outline: <outline>
Shading: <shading>
Background: transparent/solid

Generating...
```

## Error Handling

- **Missing API key**: Tell user to set `PIXELLAB_SECRET` in `.env`
- **402 Insufficient credits**: Tell user to add credits at pixellab.ai/account
- **429 Rate limited**: Wait and retry
- **Generation failed**: Show error, suggest adjusting parameters

## Output Format

```
[OK] Image saved: <path>
     Cost: $X.XXXX
     Size: WxH
```
