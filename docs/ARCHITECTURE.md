# Arnav Business Tycoon — Architecture

## 1. Toolchain (verified 2026-11-06 from Google Maven, Maven Central, npm and gradle.org)

| Item | Version | Note |
|---|---|---|
| Android Gradle Plugin | 9.5.1 | Supports API 37 at most; built-in Kotlin (no `kotlin-android` plugin) |
| Gradle | 9.8.2 (wrapper) | AGP 9.5 minimum is 9.6.0 |
| Kotlin | 2.4.21 | |
| KSP | 2.3.13 | AGP 9 needs ≥ 2.3.6; kapt is not used |
| JDK for builds | Android Studio JBR 25.0.3 | Pinned via `org.gradle.java.home`; the system Java 26 is not used for builds |
| Compose BOM | 2026.09.00 | |
| Activity Compose | 1.13.0 | |
| Lifecycle | 2.11.0 | |
| Navigation | navigation-compose 2.10.2 (type-safe routes) | Navigation 3 (1.2.1) was evaluated; Navigation 2's type-safe API is more mature for deep links and tests |
| Hilt | 2.60.1 (+ hilt-navigation-compose 1.4.0) | AGP 9 needs ≥ 2.59.2 |
| Room | 2.8.5 | |
| DataStore | 1.3.0 | Settings |
| WorkManager | 2.12.0 | Background cloud sync |
| Coroutines | 1.11.0 | |
| kotlinx.serialization | 1.12.0 | Saves and content |
| Firebase BoM | 35.1.0 | Auth + Firestore only |
| google-services plugin | 4.5.0 | Applied only when `google-services.json` exists |
| Credential Manager | credentials 1.6.0 + googleid 1.2.1 | Google sign-in |
| Coil | 3.6.3 | Image loading from assets |
| Haze | 2.0.1 (Apache-2.0) | Backdrop blur for the glass UI. 2.1.0 was only 4 days old when chosen, so 2.0.1 (2026-09-29) is used |
| Baseline profile / Macrobenchmark | 1.5.0; profileinstaller 1.4.1 | |
| Tests | JUnit 6.1.3 (Jupiter), Kotest property 6.2.6, Turbine 1.2.1, Robolectric 4.17, Roborazzi 1.77.0, UI Automator 2.4.1 | |
| firebase-tools | 15.35.0 (installed: 15.1.0 → upgrade) | Emulator Suite; needs Node ≥ 20 (installed: 24.16) |
| @firebase/rules-unit-testing | 6.0.0 | |

**SDK levels**: `compileSdk 37` (Android 17, stable since June 2026), `targetSdk 36` (the current Google Play requirement since 2026-08-31), `minSdk 28` (Android 9; we have an API 28 AVD for oldest-device testing).

**Versions are pinned in `gradle/libs.versions.toml`.** Phase 1 re-checks them, and any change goes through the catalog only.

## 2. Modules

```
:core            Pure Kotlin/JVM. NO Android dependencies. Game rules, Money, Ledger, Clock, RNG,
                 content schema + loader + validator, GameState, engine, save model + migrations.
:sim             JVM application. Bots, batch runner, report generator (Markdown + CSV + PNG charts).
:data            Android library. Room DB, SaveRepository, SettingsStore (DataStore), CloudSync,
                 AuthRepository, PurchaseProvider impls, UpdateChecker, AssetRepository.
:designsystem    Android library. Aurum Glass theme: smooth-corner shapes, glass surfaces (Haze),
                 ambient backdrop, typography, color tokens, icons, components, charts, money counter.
:app             Android application. Navigation, feature screens (package per feature), DI wiring,
                 GameSession (state holder), onboarding.
:baselineprofile com.android.test module generating baseline profiles; macrobenchmarks.
content/         Validated JSON data (packaged into app assets AND read by :core tests and :sim).
firebase/        firestore.rules, firestore.indexes.json, firebase.json, rules tests (Node + Vitest).
tools/art/       Python art pipeline (fetch → verify license → process → WebP → credits.json).
tools/scripts/   Secret scanner, size-budget check, release helper, emulator scripts.
```

Dependency direction: `app → data, designsystem, core`; `data → core`; `sim → core`. `:core` depends on nothing but kotlinx (serialization, coroutines-core) and the JDK.

## 3. Core design

### 3.1 State and engine
- `GameState`: one immutable data-class tree (player, businesses, portfolio, properties, collection, world, rivals, progression, ledger summary, timers, RNG states).
- `GameEngine` is pure. `advance(state, toTime) → (state', events)` and `apply(state, command, now) → Result<(state', events)>`.
- **Event-driven integration**: `advance` jumps from boundary to boundary (timer completions, market ticks, payouts, cycle changes). Between boundaries all rates are constant and are integrated exactly. The same code path serves live play (called every second), offline catch-up and the simulator, which guarantees offline ≡ live.
- `Command` is a sealed hierarchy (`BuyLevel`, `HireManager`, `PlaceOrder`, `BuyProperty`, `Bid`, `ResolveCard`, …). Each one is validated and returns typed errors (`InsufficientFunds`, `SlotLimit`, …) that the UI turns into friendly messages.
- `GameEvent` (sealed) drives UI moments, haptics, sounds and analytics-free history.

### 3.2 Money, Ledger, Clock, RNG
- `Money` (value class over a sealed `Long`/`BigInteger` representation), `Rate`, `Multiplier`. Formatting lives in `MoneyFormat`. See ECONOMY §1.
- **Ledger** (single choke point): `Ledger.post(Txn)`. Each `Txn` has an id, game time, category, memo and ≥2 `Posting(account, amount)` entries that **sum to zero** (double entry).
  - Accounts: `Cash`, `Bullion`, `Favor`, `Loans:<id>`, `TaxPayable`, `Revenue:<source>`, `Expense:<kind>`, `Investments:<asset>`, `World` (external source/sink).
  - Invariants (property-tested): every Txn balances; `balance(account) == replay(journal)`; no posting can push Cash below the credit floor unless it is an allowed overdraft.
  - Storage: the recent journal (last 5,000 Txns) plus hourly per-category aggregates for history charts. Continuous income is batched into one Txn per source per minute.
- **Clock**: `GameClock { wallNow(); monotonicNow(); bootId() }`. `FakeClock` for tests and the simulator.
- **RNG**: a PCG32-style generator with named, splittable streams: `rng(stream, key)` derived from `worldSeed` (for example `("market", tick)` or `("events", day)`). Reproducible per save and per seed. Never `kotlin.random.Random.Default` in rules code (a lint check enforces this).

### 3.3 Content
- JSON in `content/data/**`, one file per category (businesses, stocks, districts, luxury, cards, rivals, chapters, achievements, store, …) plus `content/balance/*.json` for tunables.
- Schema: Kotlin `@Serializable` classes in `:core` are the single source of truth. A JSON Schema is generated from them for editor hints.
- **Validator** (runs in unit tests, in the simulator and at app start in debug builds): unique IDs, cross-references resolve, numeric ranges, every image reference exists in `assets/credits.json`, counts meet CONTENT.md minimums, banned-word and real-brand blocklist on names, and no rival name equals an entry in the real-person blocklist.
- The app loads content from assets once at startup into an immutable `ContentPack`, with a content hash stored in saves.

## 4. Android app architecture
- Single activity, Compose, type-safe Navigation. Features are packages: `onboarding`, `empire`, `markets`, `estates`, `collection`, `dynasty`, `rivals`, `social`, `store`, `settings`, `auth`, `credits`.
- **GameSession** (Hilt singleton): owns the current `GameState` in a `MutableStateFlow`, serializes commands through a single-consumer coroutine actor on `Dispatchers.Default`, ticks `advance()` at 1 Hz while in the foreground, and autosaves.
- ViewModels expose `StateFlow<UiState>` slices using `map` + `distinctUntilChanged` so recomposition stays local.
- Process death: the save is the source of truth; ViewModels restore from `GameSession`.
- Images: Coil 3 loading from `file:///android_asset/img/...` with size-specific requests (thumbnails decoded at thumbnail size).

## 5. Save system
- **Format**: `SaveEnvelope { magic, schemaVersion, contentHash, revision, deviceId, createdAt, savedAt, sha256, payload }`. The payload is gzip-compressed kotlinx JSON of `GameState`. Field names use explicit `@SerialName`.
- **Storage (Room)**: table `save_snapshots(id, slot, revision, schemaVersion, savedAt, sha256, blob)`.
  - Each save inserts a new row and prunes to the last 5 snapshots in **one Room transaction** (WAL mode), so writes are atomic.
  - Also `ledger_journal` and `ledger_hourly` tables, and `sync_state(slot, lastSyncedRevision, lastRemoteRevision, dirty)`.
- **Autosave**: every 30 s while playing, on every significant command (purchase ≥ 1% of NW, prestige, real-estate deal), and on `ON_STOP`.
- **Migrations**: `SaveMigrator` chains pure `JsonObject → JsonObject` steps (v1 → v2 → …). Every released schema version has a **golden fixture save** in `core/src/test/resources/saves/` that must load and validate after migration. A save from a newer app version is refused with a "please update" message, never truncated.
- **Corruption recovery**: on load, verify gzip, sha256, parse, migrate and validate. On failure, try the previous snapshot (up to 5), then the cloud copy, then a **Recovery screen** with "export the corrupted file" and "start a new game". The corrupted blob is kept and never silently overwritten.
- **Manual backup**: export and import a `.abtsave` save file through the Storage Access Framework.

## 6. Offline progress and clock-tamper detection
- On resume: `elapsed = clamp(wallNow − lastSavedWall, 0, offlineCap)`, then `advance(state, lastSavedWall + elapsed)`.
- Tamper heuristics:
  1. **Same boot** (same `bootId`): compare the wall-clock delta with the monotonic `elapsedRealtime` delta. If wall time runs ahead by more than 2 minutes, credit only the monotonic delta.
  2. **High-water mark**: keep `maxCreditedWall`. Offline time is only ever counted after that mark, so moving the clock back and then forward again gives nothing.
  3. **Backwards clock**: never a negative credit. Timers based on wall time freeze until the wall clock passes the mark again.
  4. **Network time** when online: after sign-in, Firestore `serverTimestamp` from the sync write. If the device and server differ by more than 10 minutes, rule (1) applies and a gentle "check your clock" notice appears.
- Suspected tampering never deletes progress. It only limits the credit and sets a `clockFlags` counter (shown on the profile as nothing; used only to tighten leaderboard plausibility).

## 7. Firebase (Spark plan only)
**Used**: Authentication (Email/Password, Google, Anonymous), Cloud Firestore.
**Not used**: Cloud Functions, Cloud Storage, Firebase Storage, Hosting, App Check enforcement (Play Integrity verdicts are unreliable for sideloaded APKs), Analytics, Crashlytics (kept out to avoid data collection; can be reconsidered).

### 7.1 Auth flows
- **Guest**: anonymous auth when the player opts into cloud features. The game itself needs no account and works fully offline.
- **Upgrade guest**: `linkWithCredential` (email or Google) keeps the same UID, so progress carries over.
- **Email/password**: sign-up → send verification email → features that touch other players (leaderboards, friends, gifts, Sealed Lots) require `email_verified` (enforced in rules). Password reset via `sendPasswordResetEmail`.
- **Google**: Credential Manager `GetGoogleIdOption` (server client ID = the web client) → `GoogleAuthProvider.getCredential(idToken)`. Sign-out also calls `clearCredentialState`.
- **Account deletion** (Settings → Account → Delete): re-authenticate → delete this user's Firestore docs (save, profile, social, friend code, leaderboard entries via field delete, pending gifts and requests) → `user.delete()` → local data is kept as an offline save unless the player also chooses "wipe this device".
- Spark email limits (verification ~1,000/day, password reset ~150/day) are far above friend-group needs. The app rate-limits resends (60 s cooldown) in the UI.

### 7.2 Firestore data model
```
/saves/{uid}                  { rev, schema, contentHash, device, savedAt(server), summary{nwLog10, gen, playSec}, sha, blob(Bytes, gzip ≤ 900 KB) }
/profiles/{uid}               public: { name, friendCode, avatarId, title, gen, nwLog10, fairNwLog10, boosted, showcase[], updatedAt(server) }
/friendCodes/{code}           { uid }                        create-only, unique
/social/{uid}                 { friends: [uid…≤100] }         owner-only
/friendRequests/{to}_{from}   { from, to, createdAt }         create by `from`; read/delete by either party
/boards/{boardId}/shards/{0..3}  { entries: { <uid>: { n, lnw, flnw, b, g, t } } }   global + weekly league boards
/gifts/{to}/inbox/{from}_{yyyymmdd}  { from, kind, amount, createdAt }          create-only → 1 per friend per day
/lots/{weekId}/bids/{uid}     { lotId, amountLog10, amount (string), createdAt } sealed until close
```
- **Sharded map leaderboards**: each board is 4 documents with a map of entries. A player writes only their own key (`entries.<uid>`) in shard `hash(uid) % 4`. Reading a whole leaderboard costs **4 reads**. The friends leaderboard filters the same data (0 extra reads). Capacity is about 4 × ~4,000 entries — plenty for this game.
- **Leagues**: board `w<isoYear>-<week>` holding weekly growth `lnw − lnw_at_week_start` plus a tier. Tier assignment is computed by each client from the previous week's board.

### 7.3 Security rules (strict; written and tested in Phase 12)
- Default deny. Each path allows only the owner (`request.auth.uid == uid`) except public reads of `/profiles`, `/boards`, and `/lots` bids after close.
- **Saves**: write only if `request.resource.data.rev == resource.data.rev + 1` (optimistic concurrency), the blob size is under 900 KB, the schema is a known version, and `savedAt == request.time`.
- **Leaderboard entry** (`entries.<uid>`):
  - `affectedKeys().hasOnly([uid])` on the map diff.
  - `t == request.time` and `request.time > old.t + duration.value(10, 'm')` (rate limit: one write per 10 minutes).
  - **Plausibility**: `new.lnw ≤ old.lnw + 0.35 + 0.25 × hoursSince(old.t)` (capped at +2.0 per write). Decreases are always allowed (prestige). The constants come from the simulator's max-growth report × 1.5.
  - The `b` (boosted) flag can never go from true to false within a board.
  - Requires `email_verified` or a Google provider.
- **Gifts**: sender must be in the recipient's `/social` friend list (`get()`), the doc ID must be `<sender>_<today>` (create-only, no update), and the amount is capped.
- **Sealed Lots**: create/update own bid only while `request.time < close(weekId)` (close is computed from the weekId via `timestamp.date`); read others' bids only when `request.time ≥ close`. The winner is resolved identically by every client (highest bid, ties broken by earliest `createdAt`, then UID).
- **Rules tests**: `firebase/test/*.test.ts` using `@firebase/rules-unit-testing` against the Firestore emulator via `firebase emulators:exec`. Every allow and deny path has a test. Run in CI.

### 7.4 Sync and conflicts
- `CloudSync` runs only when signed in and online.
  - **Pull** once at sign-in and app start (1 read).
  - **Push** when dirty: on `ON_STOP`, after major events, and at most every 15 minutes of play (WorkManager with a network constraint).
- **Revision lineage**: the local copy tracks `lastSyncedRevision`.
  - Remote `rev == lastSynced` and local dirty → push (rev + 1).
  - Local clean and remote newer → pull silently (it is a strict descendant).
  - **Both changed → conflict screen**: two cards (net worth, generation, play time, device, last saved) and the choices "Keep this device", "Use cloud", "Decide later". The losing copy is kept locally as a backup snapshot. **Nothing is overwritten silently.**
- Leaderboard and profile writes are batched with the save push when due (≤ 1 per 10 min, respecting the rule).

### 7.5 Daily quota budget (Spark: 50K reads, 20K writes, 20K deletes, 1 GiB stored, 10 GiB/month egress)

| Per active user per day | Reads | Writes |
|---|---|---|
| Save pull/push (≤ 40 pushes) | 2 | 40 |
| Leaderboards (≤ 15 refreshes × 4 shards; 10-minute cache) | 60 | 12 |
| Profiles viewed | 10 | 2 |
| Friends, requests, gifts, lots | 15 | 6 |
| **Total** | **~87** | **~60** |

Capacity: about **330 daily active users** before hitting the write quota, about 570 for reads. Saves are about 50–300 KB each, so 1 GiB holds thousands of players. The app tracks its own usage counters and backs off (longer cache, fewer pushes) if Firestore returns `RESOURCE_EXHAUSTED`. The game never breaks; it only defers cloud features.

## 8. Purchases
- `interface PurchaseProvider { catalog(): Flow<List<Product>>; purchase(productId): PurchaseResult; restore(): List<Entitlement> }`.
- `SimulatedPurchaseProvider` (the only implementation shipped): shows a "DEMO PURCHASE — no real money is charged" bottom sheet, grants the entitlement through the Ledger (Bullion) or the entitlement store, and sets `boosted = true` on the profile. No payment UI, no payment data, and no Play Billing dependency.

## 9. In-app update check
- At most once per 24 h (and on demand in Settings): `GET https://api.github.com/repos/<owner>/<repo>/releases/latest` (unauthenticated; 60 requests/hour/IP is plenty).
- Compare the semver tag with `BuildConfig.VERSION_NAME`. If newer, show a dialog with the changelog and a **"Open download page"** button that launches the browser on the release page. No silent download and no `REQUEST_INSTALL_PACKAGES` permission.

## 10. Security and repo hygiene
- `.gitignore`: `google-services.json`, `*.jks`, `*.keystore`, `keystore.properties`, `local.properties`, `.env*`, `*.apk`, `*.aab`, `firebase-debug.log`, `/tools/art/cache/`.
- The google-services plugin is applied **only if** `app/google-services.json` exists. Without it the app builds in "offline edition" mode with cloud features hidden, so friends can build from source.
- **Signing**: the keystore lives outside the repo (see ROADMAP manual steps). Credentials are read from `~/.gradle/gradle.properties` (`ABT_KEYSTORE`, `…_STORE_PASSWORD`, `…_KEY_ALIAS`, `…_KEY_PASSWORD`).
- **Secret scan** before every push (`tools/scripts/scan_secrets.py`, also a git `pre-push` hook): regexes for Google API keys, private keys, OAuth tokens, `google-services.json`, keystores, APKs, plus a **local-only** pattern file (`.secret-patterns.local`, git-ignored) holding the owner's emails so they are never committed. Optionally gitleaks if the user approves installing it.
- Network: HTTPS only (network security config), no cleartext. Firestore emulator hosts are allowed in debug builds only.

## 11. Performance
- Targets: cold start < 1.2 s on a mid-range phone; 60 fps scrolling and counters; no main-thread I/O (StrictMode in debug).
- Release: R8 full mode, resource shrinking, baseline profile + startup profile (generated by `:baselineprofile`), `profileinstaller`.
- Engine work runs off the main thread. The UI receives state slices. Counters animate with `animateValueAsState`-style interpolation instead of 60 Hz state updates.
- **Size budget: APK ≤ 60 MB hard cap (target ≤ 45 MB)**. Images ≤ 32 MB, audio ≤ 6 MB, fonts ≤ 0.6 MB, code and resources ≤ 12 MB. Enforced by `tools/scripts/check_size.py` in the release gate.
