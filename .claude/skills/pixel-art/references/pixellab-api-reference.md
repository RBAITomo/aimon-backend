# PixelLab API Quick Reference

**Base URL:** `https://api.pixellab.ai/v1`
**Auth:** Bearer token via `PIXELLAB_SECRET` env var
**SDK:** `pip install pixellab` — `pixellab.Client(secret="...")`
**Docs:** https://api.pixellab.ai/v1/docs

## Endpoints

### Generate Image (PixFlux) — `POST /generate-image-pixflux`
Text-to-pixel-art. Max 400x400. Best for general sprite generation.

Key params: `description`, `image_size`, `no_background`, `outline`, `shading`, `detail`, `view`, `direction`, `isometric`, `init_image`, `color_image`, `seed`

### Generate Image (BitForge) — `POST /generate-image-bitforge`
Style-transfer generation. Max 200x200. Use with `style_image` for consistent style.

Additional params: `style_image`, `style_strength`, `inpainting_image`, `mask_image`, `skeleton_keypoints`

### Animate with Text — `POST /animate-with-text`
Text-driven animation. Fixed 64x64. 2-20 frames.

Key params: `description`, `action`, `reference_image`, `n_frames`, `view`, `direction`

### Animate with Skeleton — `POST /animate-with-skeleton`
Skeleton-pose animation. Sizes: 16, 32, 64, 128, 256.

Key params: `skeleton_keypoints` (list of frames), `reference_image`, `view`, `direction`

### Rotate — `POST /rotate`
Rotate sprites. Max 200x200.

Key params: `from_image`, `from_direction`, `to_direction`, `from_view`, `to_view`, `image_guidance_scale`

### Inpaint — `POST /inpaint`
Edit regions. Max 200x200. White mask = regenerate, black = preserve.

Key params: `description`, `inpainting_image`, `mask_image`, `outline`, `shading`

### Estimate Skeleton — `POST /estimate-skeleton`
Extract keypoints from character image. Sizes: 16, 32, 64, 128, 256.

Returns: list of `{x, y, label, z_index}` keypoints.

### Balance — `GET /balance`
Returns `{type: "usd", usd: <float>}`.

## Parameter Values

**Outline:** `single color black outline` | `single color outline` | `selective outline` | `lineless`
**Shading:** `flat shading` | `basic shading` | `medium shading` | `detailed shading` | `highly detailed shading`
**Detail:** `low detail` | `medium detail` | `highly detailed`
**View:** `side` | `low top-down` | `high top-down`
**Direction:** `south` | `south-east` | `east` | `north-east` | `north` | `north-west` | `west` | `south-west`

## Skeleton Labels
`NOSE`, `NECK`, `RIGHT SHOULDER`, `RIGHT ELBOW`, `RIGHT ARM`, `LEFT SHOULDER`, `LEFT ELBOW`, `LEFT ARM`, `RIGHT HIP`, `RIGHT KNEE`, `RIGHT LEG`, `LEFT HIP`, `LEFT KNEE`, `LEFT LEG`, `RIGHT EYE`, `LEFT EYE`, `RIGHT EAR`, `LEFT EAR`

## Image Format
All images: Base64 PNG. SDK handles encoding/decoding via `PIL.Image`.

## HTTP Status Codes
- 200: Success
- 401: Invalid token
- 402: Insufficient credits
- 422: Validation error
- 429/529: Rate limited
