# Arnav Business Tycoon — Progress

_Last updated: 2026-11-06._

## Current status
- Phase 0 (Planning): **DONE**. The user approved the plan on 2026-11-06.
- Phase 1 (Project skeleton and tooling): **DONE**. CI green on GitHub.
- Phase 2 (Core foundations): **DONE**.
- Phase 3 (First playable, Chapter 1): **DONE**. Details below.
- **Design refresh "Aurum Glass"**: done (owner request). Details below.
- Phase 4 (Simulator v1): **DONE**. Details below.
- Phase 5 (All businesses + merger tree): **DONE**, with one known balance miss (Chapter 8, deferred to Phase 11). Details below.
- **Laptop resource caps** (owner request): Gradle uses at most 4 workers and a 2 GB heap, and its daemon stops after 10 idle minutes. The simulator uses a quarter of the cores (max 6) unless `--threads` is given. Gradle daemons and the emulator are stopped after every work session.
- Phase 6 (Markets): **DONE**, with known gaps (market income share, Chapter 8 pacing; both deferred to Phase 11). Details below.
- Phase 7 (Real estate): **DONE**, with the same known balance gaps (income shares, Chapter 8; Phase 11). Details below.
- **Owner decisions (2026-11-06)**: real cities and neighbourhoods; GTA-style parody names for companies, coins and (later) people (GDD §1.1). Pexels paused new API keys, so photos will come from Wikimedia Commons and museum open-access APIs (no key needed).
- Phase 8 (Luxury + art pipeline): **DONE**. Museum works have real CC0 photos; all 72 manager portraits are AI caricatures of invented people (owner approved the Figma AI credits). Deferred: photos for other luxury categories and estates, Heirlooms (Phase 10), Chapter 8 and income shares (Phase 11). Details below.
- Next: **Phase 9: World, rivals, finance pressure**.

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
| UI tests | Android Test Orchestrator with clearPackageData; failure screenshots saved to the app's external files dir |

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

## Phase 3: what was done and verified
- `:data`:
  - Room `TycoonDatabase` v1 (exported schema `data/schemas/`): `save_snapshots`, with `quarantine` for corrupted saves.
  - `SaveRepository`: atomic insert-and-prune keeping 5 snapshots; fallback to older snapshots; quarantine; import validation; export.
  - `SettingsStore` (DataStore) and `SystemGameClock` (boot id from `Settings.Global.BOOT_COUNT`).
  - Hilt `DataModule`.
- `:app`:
  - `GameSession`: one mutex; 1 Hz foreground ticks; offline credit on every resume; autosave every 30 s, on background and after important commands; export/import; debug-only `DebugScenarios`.
  - `GameViewModel` maps state to UI models off the main thread.
  - Screens: Welcome, Empire (rolling counters, guide, Next Move, Hustle with combo/Frenzy and floating pops, business cards with collect/buy, open-business cards, HQ, milestone banner), Business detail (retail price slider + promo, restaurant stars + kitchen tiers, logistics route steppers + auto-plan, managers, expansions with countdown + Favor rush, tiers, mothball), Dynasty (story/missions/menu), Coming-soon tabs, Settings, Credits, offline-earnings dialog, recovery and loading screens.
  - Haptics are mapped to game events.
- `:designsystem`: RollingMoney (frame-rate extrapolation that stops when idle), GoldProgress, SegmentedSelector, AurumChip, IconTile, AurumBottomBar (label scale capped at 130%, full semantics), MenuRow, AurumDialog; 66 custom icons.
- Content: `starterName` field; dedicated business icons.

### Evidence
- `:core:test` 62 passed. `:data:testDebugUnitTest` 5 passed (Robolectric + in-memory Room: round trip, prune, corrupted newest → previous + quarantine, unrecoverable reported, import validation).
- **Compose UI test `FirstSessionFlowTest` passed on `abt_api37`**: welcome → open kiosk → claim → 60 taps → claim Favor → buy to Lv 5 → detail → build expansion → rush with Favor → back. It runs with the Test Orchestrator and `clearPackageData`, so every run starts from a fresh install state.
- Manually verified on the emulator:
  - The first-session loop.
  - The mid-game debug scenario.
  - **Offline earnings on a cold start**: killed for 76 s → +$75,286 at about $983/s, which matches.
  - Both themes (dark via `cmd uimode night yes`).
  - 200% font scale.
- Screenshot review found and fixed:
  1. The business header wrapped.
  2. Floating pops were unreadable over the coin.
  3. Manager chip truncation.
  4. "Till full" wrapping.
  5. Stars had no filled state.
  6. Manager cards wrapped awkwardly.
  7. Scrolled content collided with the status bar (scrim added).
  8. Bottom-tab labels truncated at 200%.
  9. Business names broke mid-word at 200% (adaptive stacking).
  10. A redundant "per second".
- Framework fixes:
  - Robolectric on Android 36+ needs `--add-exports java.base/jdk.internal.access`.
  - Compose UI test pulled in Espresso 3.5.0, which crashes on Android 17. Pinned to 3.7.0.
  - Debug theme extras are unreliable when Android restores a task. Theme screenshots now use `adb shell cmd uimode night yes|no`.

### Not done yet / known gaps (honest list)
- Pacing is **not tuned**. The 30–60 minute first-session targets are verified by the simulator in Phase 4. Manual play only covered about the first 10 minutes.
- Light-theme screenshots were reviewed for welcome, Empire, details, Dynasty and Markets. Settings and Credits were only reviewed in dark theme.
- No audio or music yet (Phase 15). No photos yet (Phase 8). Markets, Estates and Collection are informative "coming soon" tabs.
- Chapter 2 only has missions for existing businesses. New Chapter 2 businesses arrive in Phase 5.
- Not tested on the API 28 AVD yet (planned for the release gates).

## Design refresh: Aurum Glass (owner request, 2026-11-06)
- **Shapes**: `SmoothCornerShape` is an original continuous-corner implementation (each corner blends Béziers into a shortened arc; smoothing 0.6). Shape scale: tiny 8, small 14, medium 22, large 30, sheet 36; capsules for buttons.
- **Glass**: `Glass.kt` with Haze 2.0.1.
  - Ambient backdrop is glass layer z = 0; scrolling content is z = 1.
  - Chrome (Empire header, `GlassScaffold` navigation bar, floating tab bar, status-bar strip, in-window `AurumDialog`, milestone banner, snackbar) blurs everything beneath it.
  - Cards use the light-weight `glassCard` material (no per-card blur).
  - Real blur needs Android 12+; below that a frosted translucent fill is used.
- **Type**: Inter Text (opsz 14) and Inter Display (opsz 32), 244 KB. Fraunces removed. SF Pro cannot legally ship on Android.
- **Colours**: Apple-like neutrals plus glass tokens (fills, edge light, sheen, separator, selection) and gold fill tokens. All text tokens pass WCAG AA, including dark text on every gold fill.
- **Components**: glossy gold capsule `GoldButton`/`PillAction`, glass `GhostButton`, sliding-thumb `SegmentedSelector`, `AurumToggle`, app-icon style `IconTile`, floating glass `AurumBottomBar` with a sliding lens, `GlassScaffold` (large title that hands over to a compact frosted bar), in-window glass `AurumDialog`.
- **New `benchmark` build type**: R8-optimized, debug-signed, `.bench` suffix. Used for performance measurement and, later, baseline profiles. R8 APK size: 3.1 MB.

### Evidence
- Screenshots reviewed in dark and light: welcome, Empire (top and scrolled), business detail (large and compact title), Dynasty, Settings, import dialog.
- Fixed during review: muddy light-mode gold (new fill tokens), invisible light-mode tab lens (selection token), dialog state lost on theme change (`rememberSaveable`).
- Tests: core 62, data 5, instrumented first-session flow all pass on the redesign.
- **Performance (emulator, benchmark build, detail-screen scroll)**: blur on gives 90th percentile 22 ms / 99th 32 ms; blur off gives 18 ms / 26 ms. The emulator's baseline frame pacing is about 17 ms even without blur, so this is indicative only. **A physical mid-range phone test is still required** (manual step 12).

## Phase 4: what was done and verified
- **`:sim`**: bots play the real engine through the public Command API.
  - `ActiveBot` and `cautiousBot` (30% reserve, no promotions) buy by payback and play the mechanics optimally.
  - `IdleBot` hires managers first and uses auto-plan.
  - `PoorChoicesBot` makes random purchases and sets bad prices.
  - Schedules include day-one check-ins. Live steps are 5 s; gaps between sessions use the same offline cap as the app.
- **Reports**: `docs/balance/<run>/summary.md` (targets, all milestones p10/p50/p90, health), `results.csv`, `networth.svg`. CLI: `./gradlew :sim:run --args="--seeds 100 --days 2"`.
- **Tuning tooling**: `tools/content/tune.py` + knob files `tools/content/knobs/t01…t07.json`. History in `docs/balance/TUNING_LOG.md`.
- **CI smoke test** `SimulationSmokeTest`: every bot runs without errors, runs are reproducible, early pacing stays within ×2 of target.

### Evidence
- **Final batch `docs/balance/2026-11-06-phase4/`**: 4 bots × 100 seeds × 2 days, 400 runs, 0 errors, 505 s. **All 9 pacing targets pass**:
  - active: first manager 8 m, Chapter 1 20 m, Chapter 2 3.5 h;
  - idle: first manager 10 m, Chapter 1 1.1 h, Chapter 2 12.1 h;
  - poor choices: Chapter 1 1.7 h.
- Tests: core 62, sim 3, data 5, instrumented UI flow 1, all pass.
- Fixed along the way:
  - The idle bot only valued managed income, so it never opened a second business.
  - Engine tests had hard-coded prices.
  - The UI test assumed an expansion price (it now earns until the action is affordable).
  - Sim tests did not declare the content folder as a Gradle input.

### Known gaps (honest)
- **Dead session minutes are 72% for the active bot over 2 days**, and stalls reach up to 12 h. Only three businesses exist, so growth saturates after Chapter 2. The dead-zone target (<5%) is expected to fail until Phase 5+ content exists.
- Targets were run on 100 seeds rather than the planned 1,000. Bots currently have little randomness (only schedule jitter), so p10 = p90 for most milestones. More seeds become meaningful once world events and markets add variance.
- Batch speed is about 1.3 s per 2-day run per thread; the planned full batch would take about 17 minutes. Engine profiling is planned before Phase 11.
- Two active-bot values sit on band edges (first manager 8.0 m, Chapter 1 20.0 m). Re-check after Phase 5.
- `risky_trader` and `heavy_spender` wait for markets (Phase 6) and the store (Phase 14).

## Phase 5: what was done and verified
- **Module mechanics framework** (`core/engine/mechanics/`): each of the 15 new businesses is a `MechanicModule` with generic state (`MechanicState.Module`: numbers and strings) and content-defined parameters.
  - The engine updates modules only at minute boundaries and on player actions, with seeded per-business randomness, so `advance` split invariance and offline == live still hold.
  - Each module describes its own UI (`MechanicView`: stats, choices, actions, progress), so one generic `ModulePanel` renders all 15. No per-business screen code.
  - Managers play the module for the player. They spend from a capped budget, min(5% of cash, 30 minutes of income), through the ledger.
- **15 business mechanics**: Farmland (crop seasons and weather), Factories (lines and defects), Fashion House (trends), Mining (ore depletion and redrills), Energy (spot price and hedging), Hotels & Resorts (tourism seasons and pricing), Banking (lending standard, reserve ratio and bank-run risk), Construction (projects), Media (content slate), Technology (R&D sprints), Telecom & Data, Airline (fares, load factor and maintenance incidents), Pharmaceuticals (three-phase trials, patents and generics), Sports Franchise, Space.
  - Cross-business synergies: tourism feeds Hotels, Airline and Sports; some businesses cut others' upkeep.
- **Merger tree**: 9 conglomerates (`content/data/conglomerates.json`). Each needs its members at Scale Tier 3 and is unlocked in Chapter 4. Members earn ×1.4–1.5, some also pay less upkeep, and a business can join only one conglomerate.
  - **Holding company**: three conglomerates under a player-chosen name makes every business earn ×1.25. The IPO comes with Dynasty in Phase 10.
- **Content**: 18 businesses and 8 chapters, all prices derived from one ladder by `tools/content/gen_businesses.py` and `gen_chapters.py`. Each chapter adds a business slot, giving 17 free slots before Dynasty perks. 15 new custom icons.
- **UI**: business detail renders module panels. Long option scales (fares, hedging) show as a compact 3-column grid. Merger tree and holding company cards on Empire. Manager chips shorten "Dr. Imogen Fairweather" to "Dr. Fairweather".
- **Simulator**: the planner values tier jumps, module investments, mergers, and opening or mothballing businesses. New milestones run through Chapter 8, first conglomerate and holding company. Report targets added.
- **Debug scenario `lategame`** (debug builds only): plays the story through Chapter 7 with real engine commands, starting from a fresh game, and leaves Fashion, Energy, Banking and Airline unmanaged so their controls are visible. Launch with `adb shell am start -S -n com.arnavgames.tycoon.debug/com.arnavgames.tycoon.MainActivity --es abt.scenario lategame`.

### Evidence
- Tests: **core 72** (new `ModulesTest`: per-module fuzz, determinism, split invariance and offline == live with 14 businesses, conglomerate rules, slots, synergies), **sim 3**, **data 5**, **instrumented first-session flow 1**: all pass. `verifyCorePurity` passes.
- **Balance batch `docs/balance/2026-11-06-phase5/`** (2 bots × 3 seeds × 10 days, 0 errors): **10 of 12 targets pass**. All early targets still pass. Active: Ch4 35 h, Ch7 2.0 d, first conglomerate 24.7 h, holding 2.0 d. Idle: Ch7 5.5 d, first conglomerate 2.5 d. History p5a–p5f in `TUNING_LOG.md`.
- Emulator screenshots reviewed (dark and light): Empire at Chapter 7, merger tree, holding card, and the Pharmaceuticals, Airline, Banking, Energy and Fashion panels, both managed and hands-on.
- Fixed during review:
  1. Merger members past the required tier showed "tier 5/3".
  2. The "Auto · Dr." manager chip.
  3. A ten-row fare list (now a grid).
  4. The debug scenario was layered onto the existing save.

### Known gaps (honest)
- **Chapter 8 misses its bands** (active 8.2 d in 1 of 3 seeds, target 5–6 d; idle not reached in 10 d, target 8–10 d). Once all businesses are bought, growth slows to about 0.3 decades per day, and the sinks and sources that fix this are markets, real estate, luxury and Dynasty (Phases 6–10). Re-tune in Phase 11. Dead session minutes stay high (active 62%) for the same reason.
- `first_conglomerate` target moved from day 3–4 / 5–7 to 18 h–2 d / 2–4 d, because the merger tree is now Chapter 4's story goal (reason in TUNING_LOG).
- The holding fee ($1 quintillion) is small next to late-game income; review it in Phase 11.
- The balance batch was 3 seeds, not 100, to respect the laptop caps (a 10-day run takes about 4 minutes per thread). Bots have little randomness yet, so p10 ≈ p90.
- Save schema is still v1. The new state fields have defaults, so old saves load. Bump the schema at the first public release.

## Phase 6: what was done and verified
- **Content** (`content/data/markets.json`, generated by `tools/content/gen_markets.py`):
  - 12 sectors (each mapped to the businesses its board seats help), 120 stocks (coined names checked against blocklists of real brands, famous tickers and offensive substrings), 4 index funds, 8 bonds, 20 coins in 4 risk tiers, 12 commodities linked to business revenue and costs.
  - 64 news templates, a 4-phase macro cycle and market tunables. The validator checks ids, tickers, ranges, references and blocklists.
- **Engine** (`core/market/`):
  - `MarketEngine` runs one tick per game minute: fundamental random walk plus mean-reverting noise per stock, fund NAVs from their members, bond prices from the central-bank rate and duration, crypto hype and crashes, commodities reverting to an anchor.
  - News about every 20 minutes, earnings every 3 days with Rumor/Report/Confirmed hints, macro phases with rate changes, payouts every 6 hours (dividends, coupons, staking, borrow fees), high-yield defaults in recessions, margin calls, and dilution news when a chapter deepens the market.
  - **Quotes depend only on the world seed and the minute**, never on trades, so offline equals live and splits are invariant (tested).
  - `Trading`: buy, sell (by fraction), short and cover. Slippage grows with order size against depth, plus a 0.1% fee and 10% tax on gains. A 49% stake cap. A short can never lose more than its collateral, and shorts that slippage alone would liquidate are refused.
  - Ledger accounts: `asset:investments` (cost basis), `asset:collateral`, `liability:shorts`, `revenue:capital_gains` / `dividends` / `coupons` / `staking`.
  - Exact share quantities (`Qty`, BigInteger micro-units). Chart history is stored as integer log-prices packed as base64 varint deltas, so saves stay under 64 KB.
- **Economy links**: net worth includes the portfolio and short equity. Commodities scale linked businesses' revenue or upkeep (×0.75–1.35). A 5% board seat gives +5% income to the sector's businesses; a 20% stake makes opening one 25% cheaper.
- **Story**: Chapter 2 adds "Market debut" (first trade); Chapter 3 adds "A seat at the table" (board seat).
- **UI** (`ui/markets/`):
  - Markets tab: glass portfolio header with economy phase and rate, news card, positions, desks as pills with lock state, sector filter and headings.
  - Instrument rows: procedural logos, sparklines, signed changes.
  - Instrument screen: Canvas price chart (1 day / 1 week), facts, position with board-seat progress, trade ticket with live order preview (quantity, fill, slippage warning above 2%, fee, tax), cover with margin health.
  - All-news screen. Haptics and banners for trades, forced covers and earnings seasons.
- **Simulator**: bots make the market missions and park spare cash between sessions. New `risky_trader` bot. Report table of income share by source. The trace shows portfolio value and top income sources.
- **Balance fix found by the simulator**: chapter 3–8 non-business mission rewards now follow the price ladder. Fixed rewards were up to 400× the economy and caused the Phase 5 "cascade".
- **Performance**: board-seat lookup in `businessRate` memoised (it was in the hottest path).
- **Debug scenarios**: `markets` (Chapter 1 completed with real commands; used by the UI test); `lategame` now also makes the story trades and holds a spread of positions including a short.

### Evidence
- Tests: **core 86** (new `MarketTest`, 14 tests: market opens with history; 10 days of sane prices, about one news story per 20 minutes, earnings every 3 days; quotes ignore the player and time splits; buy → sell ledger round trip; unlock gates; slippage; stake cap and board-seat perk; dilution; dividends and coupons; short loss capped by liquidation; profitable short; division discount; mission counting; packed-history property test; net worth), **sim 3**, **data 5**, **instrumented 2** (`FirstSessionFlowTest`, new `MarketsFlowTest`): all pass.
- **Balance batch `docs/balance/2026-11-06-phase6/`** (3 bots × 3 seeds × 10 days, 0 errors): 10 of 12 targets pass; all early targets unchanged. Active: Ch4 2.0 d, Ch5 4.0 d, Ch6 5.0 d, Ch7 7.0 d. History p6a–p6c in `TUNING_LOG.md`.
- Emulator screenshots reviewed (dark and light): Markets tab (Chapter 2 and late game), stock detail, ticket before and after buying, board-seat stock, coin detail, all news.
- Fixed during review:
  1. The desk selector was clipped at the screen edge (now scrollable pills).
  2. Missing sector headings.
  3. Cramped position rows.
  4. "0.000%" stake text.
  5. "Brands's" possessive.
  6. Big shorts were instantly force-covered by their own slippage (now refused, with a test-backed message).
  7. The late-game scenario stalled at the new Chapter 2 mission.

### Known gaps (honest)
- **Markets earn about 0% of income** for all bots (target 15–30% by day 7). Funds return 1–2% a day while businesses pay back in hours. Revisit in Phase 11 with real estate and luxury, which share the bands.
- **Chapter 8 still out of reach in 10 days** (deferred to Phase 11, as in Phase 5).
- News templates: 64 of the planned 250+ (more with world events in Phase 9). Commodity photos wait for the art pipeline (Phase 8).
- Board seats do not yet block rival takeovers (rivals arrive in Phase 9). The tax system is a flat 10% on realised gains until Phase 9.
- The instrumented tests ran before the final reward rescale. That change only touches chapters 3–8, which the UI tests never reach.
- Balance batches used 3 seeds (a 10-day batch takes about 45 minutes on 3 threads).

## Phase 7: what was done and verified
- **Content** (`content/data/estates.json`, generated by `tools/content/gen_estates.py`):
  - 6 real cities on a stylised world map: Lisbon and London (Chapter 2), Miami (3), Mumbai (4), New York (5), Dubai (6).
  - 40 real neighbourhoods (Alfama, Mayfair, Brickell, Bandra, SoHo, Palm Jumeirah…) with invented cycles, yields and risk.
  - 10 property types, 8 skyscraper designs, 40 tenant decisions with 2–3 choices each.
  - City price scales are pegged to the business ladder.
- **Engine** (`core/estate/EstateEngine.kt`):
  - District price index (trend × district cycle × city cycle × macro phase × permanent boosts).
  - Seeded listings per district every 6 h (25 % distressed).
  - Buying with 3 % costs and optional mortgage (0/50/60 % LTV, Chapter 3+).
  - Asking rent vs occupancy and vacancy; renovation (×3) and repair on timers; sale timers with capital-gains tax.
  - Insurance against disasters; tenant decisions (max 3, never auto-resolved, −20 % rent while pending).
  - Skyscrapers in 4 stages on central-business-district land (Chapter 5+, 20 % cheaper with your Construction business; landmark lifts the district).
  - Gentrification news; mortgage interest (unpaid interest capitalised) and foreclosure with the bank absorbing any shortfall.
  - Businesses run from your own property pay no rent (upkeep ×0.9).
  - Settles every 6 h on the same boundaries as market payouts, so offline equals live (tested).
- **Economy links**: net worth includes property value minus mortgages; the hosting perk; Chapter 2 "Landlord" and Chapter 4 "Property portfolio" missions.
- **Markets renamed**: 120 parody companies (Microhard, Pear Inc, Gooble, Amazoom, Wallmark, Starbux…) and 20 parody coins (Bitcorn, Dogecorn…); famous real tickers blocked. The Credits screen carries the parody disclaimer.
- **UI** (`ui/estates/`):
  - Estates tab: property header, decision cards, world map with city pins (locked cities dimmed), city list, your properties.
  - City screen: districts with cycle word, yield and 24 h sparkline.
  - District screen: price-index chart (1 day / 1 week), listings with condition and a buy ticket (mortgage choice, insurance toggle, cash needed).
  - Property screen: value and gain, rent levels with expected income, tenant status, works, decisions, hosting, skyscraper stages, insurance, mortgage repayment, sale.
  - New custom icons: House, Villa, Warehouse, Plot, Tower, Key, Wrench, Umbrella. Estates tab badge for pending decisions.
- **Simulator**: estate behaviour for all bots, `first_property` milestone and targets.

### Evidence
- Tests: **core 101** (new `EstateTest`, 15 tests: index determinism, listings per epoch, purchase ledger and net worth, unlocks, rent at 6 h, rent curve, renovation, sale, mortgage and foreclosure (no debt left), hosting, decisions queue, skyscraper stages and landmark, insurance vs disasters, offline equals live with properties, missions), **sim 3**, **data 5**, **instrumented 3** (`FirstSessionFlowTest`, `MarketsFlowTest`, new `EstatesFlowTest` = flow 4): all pass.
- **Balance batch `docs/balance/2026-11-06-phase7/`** (3 bots × 3 seeds × 10 days, 0 errors): 12 of 14 targets pass. History p7a–p7d in `TUNING_LOG.md`.
- Emulator screenshots reviewed: Estates tab (world map) in Chapter 2 and late game, Lisbon city screen, Alfama district with buy ticket, bought apartment, City of London land with the skyscraper picker, hosted retail unit.
- Fixed during review:
  1. Price cycles were an exploit (±35 % predictable swings), now ±4–12 %.
  2. Listing rent text wrapped awkwardly.
  3. A river line left the coast on the first map.
  4. Map labels were clipped at the edges.
  5. The Black and Caspian seas drew as dots.
  6. The late-game scenario stalled on the new property mission.

### Known gaps (honest)
- **Income share from markets and property ≈ 0 %** (bands 15–30 % by day 7) and **Chapter 8** out of reach in 10 days: Phase 11.
- Property photos (6 skylines, 40 neighbourhoods, 40 type photos) wait for the art pipeline in Phase 8; the UI uses icons for now.
- Credit ratings (which should set mortgage LTV) and property tax brackets arrive with finance in Phase 9; LTV is fixed at 0/50/60 % until then.
- The first-property target bands were widened to session-reachable windows (reason in TUNING_LOG).
- Rival bids on listings arrive with rivals (Phase 9).

## Phase 8: what was done and verified
- **Content** (`content/data/luxury.json`, generated by `tools/content/gen_luxury.py`):
  - 296 items in 11 categories: 60 cars, 28 watches, 20 wines, 15 jewels, 16 jets, 8 helicopters, 20 yachts, 10 islands, 20 mansions, 80 paintings, 19 sculptures.
  - Parody makers (Ferraro, Lamborgotti, Rolecks, Pateck Filippe, Gulfstreem, Feedship…), islands and mansions in real places, and real public-domain museum works.
  - Realistic prices ($70K cars to $450M masterpieces); value models (cars depreciate, classics and wine appreciate, art cycles).
  - 30 collection sets, 8 Status tiers, 24 parody-billionaire auction rivals (Jeff Bozos, Melon Tusk…) with four bidding styles.
- **Engine** (`core/luxury/LuxuryEngine.kt`):
  - Dealer buy (2 % fee, tier discount) and sell (85 % buy-back); six showrooms with expandable capacity; Status only from displayed items.
  - Set bonuses (business income, Status, dividends, rent, dealer discount), applied in the economy and memoised.
  - Live auctions: one per 2-hour slot at a seeded start minute, open 45 min. Bids extend the close so the hammer falls after 30 quiet seconds.
  - Proxy bidding answers rivals step by step, live or offline. Lots are rare or legendary, ≥ $250K, and matched to the player's net worth.
  - Showroom upkeep every 6 h; offline equals live (tested).
- **Economy links**: net worth includes the collection; Chapter 3 "Collector" (three pieces) and Chapter 4 "First auction win" missions.
- **Art pipeline**:
  - `tools/art/fetch_aic.py`: 99 CC0 works from the Art Institute of Chicago (80 paintings, 19 sculptures), 480-px WebP, 2.1 MB, every work credited.
  - Manager portraits: 72 caricature-style AI portraits of invented people via Figma AI. Prompts in `tools/art/portraits/managers.json`; `process_portraits.py` writes 320-px WebP (904 KB); 3 retakes after review.
  - Total app assets 3.4 MB.
- **UI** (`ui/collection/`):
  - Collection tab: Status and tier, live-auction card, six showrooms, sets.
  - Category screen with images (Coil from assets); item screen with value history and buy or sell; auction screen with bid, proxy-maximum pills and rival tells.
  - Manager portraits on business screens.
  - Credits screen: parody disclaimer, every museum work, AI-portrait note.
  - 18 new custom icons (Car, Watch, Wine, Gem, Heli, Yacht, Frame, Bust, Island, Mansion…).
- **Simulator**: luxury behaviour for all bots, `first_luxury` and `first_auction_win` milestones and targets.

### Evidence
- Tests: **core 113** (new `LuxuryTest`, 12 tests: dealer ledger, unlocks, value models, Status from displayed items only, tiers, sets, showroom upkeep, auction win by bidding, proxy win while away, offline equals live with auctions, missions), **sim 3**, **data 5**, **instrumented 4** (new `AuctionFlowTest` = flow 5).
- **Balance batch `docs/balance/2026-11-06-phase8/`**: 3 bots × 3 seeds × 10 days, 0 errors: **14 of 16 targets pass** (only Chapter 8 misses, deferred). First auction win: active 36.6 h, idle 3.0 d. Final net worth active $262Qi (+16 % vs Phase 7, from set bonuses), idle unchanged. History p8a–p8g in `TUNING_LOG.md`.
- Emulator screenshots reviewed: Collection tab, Paintings category with museum photos, item screen, live auction, manager cards with portraits, Credits screen.
- Fixed during review:
  1. 3-minute auctions nobody could catch: now 45 minutes, with proxy bidding.
  2. The "Expand" showroom pill was truncated.
  3. The auction image placeholder was too tall.
  4. A watch id with a dot failed validation.
  5. Idle players won their first auction only after 5.5 days: auctions now start at a seeded minute.

### Known gaps (honest)
- Photos exist only for museum works. Cars, watches, wine, jewels, jets, helicopters, yachts, islands and mansions use illustrated cards; estate photos are also pending. Next sources: Wikimedia Commons (no key) or more Figma AI images with the owner's approval.
- Heirlooms move to Phase 10 (Dynasty); friends viewing showrooms to Phase 13.
- Luxury, market and property income ≈ 0 % of income, and Chapter 8 is out of reach in 10 days: Phase 11.
- Six showrooms instead of the planned five (Residences added for mansions and islands).
- Rival portraits (caricatures) come with rivals in Phase 9.

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
