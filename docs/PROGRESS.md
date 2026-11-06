# Arnav Business Tycoon — Progress

_Last updated: 2026-11-06._

## Current status
- Phase 0 (Planning): **DONE**. The user approved the plan on 2026-11-06.
- Phase 1 (Project skeleton and tooling): **DONE**. CI green on GitHub.
- Phase 2 (Core foundations): **DONE**. Details below.
- Next: **Phase 3: First playable (Chapter 1)**: Room saves, GameSession, onboarding, Empire UI.

## Decisions
| Topic | Decision |
|---|---|
| Name | **Arnav Business Tycoon** (chosen by the user) |
| Package / applicationId | `com.arnavgames.tycoon` (debug builds use the `.debug` suffix) |
| GitHub repo | `Arnav-Dugad/Arnav-Business-Tycoon` (public). GitHub names cannot contain spaces |
| Commit identity | Repo-local `user.email` = GitHub no-reply address (`171219870+Arnav-Dugad@users.noreply.github.com`) |
| SDK levels | compileSdk 37, targetSdk 36, minSdk 28 |
| Icons | Custom only. Hand-authored path data in `AbtIcons.kt`, no emoji, no icon packs |
| Fonts | Inter (UI and numbers, tabular figures) + Fraunces (display), OFL, subset to 265 KB |
| Portraits | Pending the user's choice (needed before Phase 8) |

## Phase 1: what was done and verified
- **Tools**: firebase-tools upgraded 15.1.0 → 15.35.0. Installed build-tools 37.0.0 and the API 37 Google APIs x86_64 image. Created AVD `abt_api37` (Pixel 7, 4 GB RAM, host GPU).
- **Gradle**: wrapper 9.8.2, version catalog `gradle/libs.versions.toml`, modules `:core` (pure JVM), `:sim`, `:data`, `:designsystem`, `:app`. Builds run on the Android Studio JBR 25.
  - Set `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"` before running `./gradlew`; the system Java is 26.
  - `verifyCorePurity` fails the build if `:core` gets an Android dependency.
  - `google-services` is applied only when `app/google-services.json` exists; without it the app is the "offline edition".
- **Design system "Aurum"**:
  - Dark and light tokens, checked by `tools/scripts/check_contrast.py`: all text tokens pass WCAG AA in both themes. 4 tokens failed at first and were tuned.
  - Typography, spacing and shapes; the `AurumTheme` wrapper with a reduced-motion local.
  - Components: AbtIcon, CrownMark (crest drawn in code), GoldButton, GhostButton, AurumCard, SectionHeader, StatPill, ChangeLabel (always arrow + sign + color), HairlineDivider.
  - 54 custom icons so far.
- **App**: Hilt application (StrictMode in debug), edge-to-edge activity with a splash screen, an adaptive launcher icon with a monochrome layer, HTTPS-only network config, OS backup disabled (saves are protected by our own sync and export). Phase 1 FoundationScreen shows the tokens, fonts and icons.
- **Repo hygiene**:
  - `.gitignore` covers secrets and binaries.
  - `tools/scripts/scan_secrets.py` runs as a pre-push hook (install with `python -I tools/scripts/install_hooks.py`).
  - Private email patterns live in the git-ignored `.secret-patterns.local`. On a new machine, recreate it with the owner's addresses, one per line.
- **CI**: `.github/workflows/ci.yml` runs the secret scan, contrast check, core purity check, core and sim tests, and the debug APK build.

### Evidence
- `./gradlew :core:test :sim:run :app:assembleDebug verifyCorePurity` → BUILD SUCCESSFUL.
- Debug APK 32.2 MB (debug, unminified). The release size budget is checked in Phase 15/16.
- Installed and launched on `abt_api37` (Android 17 / API 37). Cold start `TotalTime` 2146 ms on a debug build in the emulator.
- Screenshots reviewed for dark and light themes. One bug was found and fixed: status bar icons did not follow the in-game theme.
- Secret scan: CLEAN (61 files, 2 private patterns loaded).

## Phase 2: what was done and verified
All in `:core` (pure Kotlin/JVM, enforced by `verifyCorePurity`):
- `money/`:
  - `Money`: exact cents; Long fast path that promotes to BigInteger on overflow and demotes back when it fits.
  - `Multiplier`: DECIMAL128.
  - `Rate` and `Accrual`: micro-cents per second, integrated in nano-cents with a carry, so it is exact for every split.
  - `MoneyFormat`: full, short, compact and scientific formats. Suffixes K…Dc, then aa…zz. Truncates, never rounds up.
- `ledger/`: double-entry `Ledger` (every Txn sums to zero), floors for the credit line, income merged per source per minute and stamped at the minute start.
- `time/`: `GameClock`/`FakeClock`, `OfflineCalculator` (same-boot monotonic check, high-water mark, never negative, server-time override, cap).
- `random/`: PCG32 with named SplitMix64-derived streams.
- `content/`: typed schema (dollar strings in JSON), loader with content hash, validator (ids, ranges, references, whole-word real-brand blocklist).
- `state/` + `engine/`:
  - Immutable `GameState`.
  - Event-driven `GameEngine.advance()` (boundaries: minutes, timers, frenzy and promo ends, storage filling).
  - 19 commands with typed errors.
  - Chapter 1 mechanics: Retail pricing and promos, Restaurant reputation drift with quality tiers, Logistics fleet routing (optimal marginal-gain planner).
  - Managers, slots, HQ, Hustle combo + Frenzy, expansions with timers + Favor rush, missions and chapters.
- `save/`: `SaveCodec` (magic + header + gzip payload + SHA-256), typed `SaveError`s, `SaveMigrator` chain, golden fixture `core/src/test/resources/saves/v1.abtsave`.
- Content: `content/data/businesses.json` (3 businesses), `content/data/chapters.json` (chapters 1–2), `content/balance/balance.json` (generated by `tools/content/gen_balance.py`).

### Evidence
- `./gradlew :core:test`: **62 tests, 0 failures**. Includes:
  - property tests (Money laws vs BigInteger, half-even rounding, serialization round-trip, accrual split invariance);
  - `advance` split invariance over 25 random split patterns;
  - offline catch-up equal to 8 hours of 1-second live ticks, to the cent and the whole state;
  - a 100,000-posting ledger fuzz with replay;
  - a clock-tamper fuzz;
  - save corruption, truncation and newer-schema refusal;
  - content validation.
- Bugs found by these tests and fixed:
  1. Income was stamped into the wrong minute.
  2. The logistics greedy plan was suboptimal.
  3. **Property tests were silently skipped by JUnit** because they returned non-Unit values. Fixed, and guarded by `TestHygieneTest`.

## Environment inventory (verified 2026-11-06)
| Tool | State |
|---|---|
| OS | Windows 11 Home, i7-13650HX (20 threads), 15.6 GB RAM |
| Android Studio | 2026.1.4, JBR 25.0.3 at `C:\Program Files\Android\Android Studio\jbr` |
| Android SDK | `C:\Users\rduga\AppData\Local\Android\Sdk`; platforms 34–37; build-tools 36.0.0 and 37.0.0 |
| AVDs | `abt_api37` (main), `un36` (API 36), `un28` (API 28, oldest-device checks) |
| Emulator start | `emulator -avd abt_api37 -no-snapshot-save -no-boot-anim -gpu host` |
| git / gh | 2.54 / 2.95 (logged in as Arnav-Dugad) |
| Node / Firebase CLI | 24.16 / 15.35.0 (logged in with the owner account; 24 existing projects, quota risk) |
| Python | 3.13.13 + Pillow 11.3.0 + fonttools 4.60.1 |

## Resume notes
- Docs are the source of truth: GDD, ECONOMY, ARCHITECTURE, CONTENT, ART_PIPELINE, TEST_PLAN, ROADMAP.
- Never commit the owner's email addresses, `google-services.json`, keystores, API keys or APKs. Run the secret scan before every push.
- Local scratch screenshots go to `screenshots-local/` (git-ignored).
