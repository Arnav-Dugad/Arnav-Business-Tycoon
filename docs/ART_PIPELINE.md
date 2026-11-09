# Arnav Business Tycoon — Art Pipeline (photo sourcing and licensing)

## 1. Rules
1. **Only properly licensed images.** Every image has a recorded source URL, author, license and license URL in `assets/credits.json`. No exceptions.
2. **No real logos or badges.** Prefer photos without visible badges. When a badge is visible, the manifest specifies **blur or crop regions** and the pipeline applies them. Vehicle, company, coin and watch names are parodies (GDD §1.1), never exact real names.
3. **No photos of real people.** Rivals and billionaires use parody names with illustrated portraits (GDD §1.1); exact real names are rejected by a real-person blocklist.
4. **Stock portraits for fictional people**, used respectfully: no demeaning captions, no criminal or scandal storylines attached to a pictured person (scandals attach to companies), no implied endorsement.
5. **Paintings and antiques** come from public-domain museum open-access programs (CC0 / public domain).
6. **No Firebase Storage or any remote image hosting.** Everything is bundled in the APK as WebP.
7. **Curated, not bulk-scraped.** Each image is chosen individually from contact sheets, and API use stays polite (Wikimedia's User-Agent and rate guidance).

## 2. Sources and allowed licenses
| Source | Use | Key needed | License handling |
|---|---|---|---|
| ~~Pexels API~~ | — | — | **Dropped (2026-11-06): Pexels has paused new API key issuance.** Unsplash was also rejected: its API terms require hotlinking, which conflicts with bundling images in the APK |
| **Wikimedia Commons API** (primary) | city skylines and neighbourhoods (real places), buildings, aircraft, ships, cars, raw materials | No (sends a descriptive User-Agent with the repo URL, no email) | Accept CC0, PD, CC BY 2.0/3.0/4.0, CC BY-SA 3.0/4.0. **Reject** NC, ND, GFDL-only and unknown. License read from `extmetadata`, never typed by hand |
| **Art Institute of Chicago API** | paintings, sculptures | No | Only records with `is_public_domain = true`; images via IIIF |
| **The Met Open Access API** | paintings, antiques, watches, jewelry | No | Only `isPublicDomain = true` |
| **Cleveland Museum of Art Open Access** | paintings, antiques | No | Only `share_license_status = CC0` |
| **National Gallery of Art (open data)** | paintings | No | Only open-access (CC0) images |

Licenses accepted by the validator: `CC0-1.0`, `PDM-1.0`, `PD`, `CC-BY-2.0`, `CC-BY-3.0`, `CC-BY-4.0`, `CC-BY-SA-3.0`, `CC-BY-SA-4.0`. For CC BY-SA, our cropped or blurred derivative is offered under the same license, and the Credits screen says so.

## 3. Pipeline (`tools/art/`, Python 3.13 + Pillow 11.3, both already installed)

```
1. search.py   <category> <query>   → candidates.json (id, preview URL, license, author) via the APIs
2. sheet.py    candidates.json      → contact-sheet JPGs with numbered thumbnails
3. (review)    Claude inspects each contact sheet visually and picks images: no badges, no
               recognizable people (except portraits), no watermarks, good composition
4. manifest    content/art/<category>.json  (asset id ↔ source id, crop box, blur regions, alt text)
5. fetch.py    downloads originals into tools/art/cache/ (git-ignored); re-verifies license from source metadata
6. process.py  crop → blur redactions → resize (hero 1080w / thumb 360w / portrait 320²) → WebP (q=78, method 6)
               → app/src/main/assets/img/<category>/<id>.webp
7. credits.py  → app/src/main/assets/credits.json  { id, title, author, authorUrl, source, sourceUrl, license, licenseUrl, modifications }
8. check.py    every content image reference exists; every file is in credits; no orphan files;
               total size ≤ budget; per-file max 250 KB; dimensions correct
```

- The **in-app Credits screen** is generated from `credits.json`: grouped by category, searchable, each entry linking to the source and license.
- Re-running the pipeline is deterministic (same manifest → byte-identical WebP output with pinned Pillow settings).
- Processed WebP files are committed (they are the game's assets). Original downloads stay in the git-ignored cache.

## 3a. AI-generated portraits (owner decision, 2026-11-06)
- Manager portraits are **caricature-style AI images of invented people**, generated with Figma AI (paid credits on the owner's plan, approved by the owner). Every prompt says the person is invented and must not resemble any real or famous person; thumbnails are checked and regenerated if they show text, logos or a famous likeness.
- Pipeline: prompts and Figma node ids in `tools/art/portraits/managers.json` → full-resolution PNGs cached in `tools/art/cache/portraits/` (git-ignored) → `tools/art/process_portraits.py` → 320×320 WebP in `app/src/main/assets/img/portraits/` + `credits.json` (license `AI-GENERATED`).
- Rivals (Phase 9) follow the same pipeline. Parody-named public figures get caricatures that hint at a *type* (tech founder, oil baron) without copying any real person's face.

## 3b. Illustrations (owner request, 2026-11-09)
- 54 hand-painted-style illustrations, one consistent look: gouache, flat shapes, a limited palette (deep navy, ivory, terracotta, muted gold), visible brush and paper texture, and no glossy 3D or render look. They cover the 18 businesses, 6 cities, 10 property types, 8 chapters, 11 collection categories and the welcome scene.
- Generated with Figma AI (gpt-image-2.5-flare) in the project's Figma file. Every prompt forbids text, letters, logos and brand names, and forbids faces in close-up; real places only appear as cityscapes. Each image was reviewed before use.
- Pipeline:
  - `tools/art/illustrations/generated.tsv` holds the set, id, Figma node, asset id and subject of every image.
  - The full-resolution PNGs live in `tools/art/cache/illustrations/` (git-ignored).
  - `tools/art/process_illustrations.py` turns them into WebP files: 960×640 for landscape art, 720×1080 for the portrait scene. They land in `app/src/main/assets/img/{business,city,property,chapter,luxurycat,scene}/`, each folder with a `credits.json` (license `AI-GENERATED`). The total is 3.7 MB.
- To add one: generate it with the same style prefix, append a row to `generated.tsv`, download the PNG into the cache, and re-run the script. `check_release.py` (G12) fails if any image is uncredited.
- **Second wave (owner request "a vast number of images", 2026-11-09): about 510 more images in the same style.**
  - Sets: every business tier (126), every expansion (54), every district (40), every tower, every HQ level, every world event, and a painting for each collection piece that has no museum photo.
  - `tools/art/illustrations/make_queue.py` writes the subjects from the content files into `queue.tsv`. Collection pieces never use their parody names in prompts, because names pull generators toward real brands; each gets an invented design instead.
  - The queue was split into `batches/batch1..8.tsv` and generated by parallel agents into `results/batch*.tsv`, using the same row format as `generated.tsv`.
  - `process_illustrations.py` reads `generated.tsv` and every `results/*.tsv`. Second-wave images are smaller: 768×512 landscape and 480×480 square, WebP q72. They land in `img/{tier,expansion,district,tower,hq,event,luxury}/`.
  - In the code, `Art.pick` falls back from a missing image to a broader one: tier → business, expansion → business, district → city, HQ level → office, piece → icon tile. A gap in the set never shows a blank.

## 3c. Museum works (built in Phase 8)
- `tools/art/fetch_aic.py` queries the Art Institute of Chicago API for highlighted works with `is_public_domain = true` (CC0), downloads 600-px IIIF images into the cache, writes 480-px WebP to `app/src/main/assets/img/art/` and `credits.json` (title, artist, date, credit line, source URL, CC0). Contact sheets are reviewed before use. 99 works (80 paintings, 19 sculptures), 2.1 MB.

## 3d. Sound effects (Phase 15)
- 7 effects synthesized from sine, square and filtered-noise tones by `tools/audio/gen_sfx.py` (deterministic WAV, 22.05 kHz mono, 65 KB). No third-party audio. Music is not included yet.

## 4. Non-photo art (made for this game)
- **Icons**: ~140 original glyphs (24-unit grid, 1.75 stroke, round caps and joins), hand-authored as path data in `designsystem/.../icons/AbtIcons.kt` and built into Compose `ImageVector`s at runtime. No emoji and no third-party icon packs anywhere in the game.
- **Company and coin logos**: generated procedurally in code (geometric monograms from a seeded shape grammar) and checked against the brand blocklist, so they cannot resemble real marks.
- **World and city maps**: original vector illustrations.
- **Player avatars**: illustrated, not photos.
- **Fonts**: Inter (SIL Open Font License) in Text and Display optical cuts, subsetted by `tools/fonts/build_fonts.py`.
- **Audio**: CC0 sound effects (for example Kenney audio packs) and CC0/CC-BY music, recorded in `credits.json` with the same fields.

## 5. Budget
| Group | Budget |
|---|---|
| Photos (~736) | ≤ 32 MB |
| Audio | ≤ 6 MB |
| Fonts | ≤ 0.6 MB |
| **APK total** | **≤ 60 MB hard cap, target ≤ 45 MB** |
