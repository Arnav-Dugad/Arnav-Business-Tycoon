# Arnav Business Tycoon — The Gold Standard Plan (1.2 → 2.0)

_Drafted 2026-11-07 after the 1.1.0 release. **Status: 1.2 approved by the owner on 2026-11-07 (decisions D1–D6 and D8; D7 and D9 not taken).** Later releases are built only after the owner approves them (project rule: plan first, then phase by phase)._

This plan turns a complete, working game into a premium one. It starts from evidence: the simulator's health data, the open release gates and the gaps listed in PROGRESS.md. It then proposes four releases, each shippable on its own through the new auto-updater.

---

## 1. North star

> **Every minute in the game should feel like a decision a tycoon would enjoy making, and every screen should feel like it was crafted, not generated.**

### 1.1 What "better" means, in numbers
Each target is measurable by the simulator, a test or a device measurement. 1.1.0 values are measured, not estimated.

| Area | Metric | 1.1.0 | 2.0 target | How it's measured |
|---|---|---|---|---|
| Engagement | Active "dead session minutes" (nothing worth doing) | **~50 %** | **< 10 %** | Simulator health table |
| Engagement | Idle dead minutes | 7 % | < 5 % | Simulator |
| Economy | Income share from markets + property (day 1 / 3 / 7) | **~0 %** | 0–8 / 5–20 / 15–30 % | Simulator income table |
| Pacing | Balance targets passing | 16 / 17 | 17 / 17 + new 2.0 targets | Simulator report |
| Fairness | Spender advantage at any milestone | up to 2.4× | 25–40 %, never > 2× | Spender batch |
| Quality | UI flows passing on API 28 and 37, on the release build's content | partial (shared emulator) | 100 %, every release | Isolated test devices |
| Quality | Save compatibility from every past version | not re-tested 1.0 → 1.1 | 100 % (golden saves) | JVM upgrade tests |
| Quality | Screens with screenshot tests (dark, light, 200 % font) | 0 | every screen | Roborazzi |
| Performance | Cold start on a mid-range phone | unmeasured on hardware | < 1.0 s | Macrobenchmark on a real phone |
| Performance | Janky frames while scrolling | unmeasured on hardware | < 1 % | Macrobenchmark |
| Reliability | Crash-free sessions | unknown (no reporting) | ≥ 99.5 % | Crash reporting (owner decision D5) |
| Size | APK | 12.96 MB | ≤ 25 MB with all new art and audio | `check_release.py` |
| Craft | Music heard and approved by a human | no | yes, per track | Owner listening pass |

### 1.2 Non-negotiables (carried forward, never traded away)
- Spark plan only: no Cloud Functions, no Cloud Storage, no paid Google Cloud products. Anything that seems to need them is redesigned or skipped, never quietly upgraded.
- No ads, no paid random rewards, no countdown pressure, no "only N left", no interrupting offers. All purchases stay simulated and labelled DEMO.
- Real places, parody company and people names, no real logos, no photos of real people.
- Every release is signed with the same release key and published as an `.apk` asset (the 1.1.0 updater rejects anything else).
- Laptop caps: 4 Gradle workers, 2 GB heap, simulator ≤ 5 threads, and shut everything down after a session. Never disturb another session's emulator, Gradle or adb (see §8.1).

---

## 2. Honest audit of 1.1.0

### 2.1 What is already strong (protect it)
- A deterministic, event-driven engine: offline time equals live time, exact money, a double-entry ledger, seeded randomness. Balance can be proven, not guessed.
- 18 businesses with real mechanics, a merger tree, markets (120 stocks, bonds, crypto, commodities), real estate in 6 real cities, luxury with live auctions, 220 rivals, finance pressure, 8 story chapters, Dynasty prestige.
- An honest store, the Aurum Glass design system, custom icons, a mentor with 200 lines, 4 original music loops.
- Infrastructure: simulator with bots and reports, content validators, security-rules tests, signed releases, self-updating APK.

### 2.2 Gaps the data shows (what this plan fixes)
1. **Half of an active player's session time has nothing meaningful to do** (dead minutes ~50 %). This is the biggest threat to "Decisions over taps".
2. **Markets and property contribute ~0 % of income.** They are fun to look at but economically decorative. That breaks "Everything feeds everything".
3. **The late game is a straight line.** After the Holding company there is little new to aim for until the crown, and generation 2 replays generation 1 with a multiplier.
4. **The world tells too little story.** Offline returns are a number, not a narrative; chapters start with a card, not a moment; The Chairman has no face.
5. **Premium polish is uneven.** There's no motion vocabulary (each screen animates its own way), no growing HQ visual, no "Pass the Crown" cinematic (promised in GDD §16), and music nobody has listened to.
6. **Social is built but dark**: sign-in waits for the console switch, Google sign-in is untested, and friends can't see each other's showrooms yet.
7. **Testing is fragile on a shared machine.** Another project's adb restarts broke the final 1.1.0 UI run. 1.0 → 1.1 save compatibility was not re-tested.
8. **Content still below the CONTENT.md minimums**: themes, HQ skins and card backs aren't built; no world-event or property photos.

---

## 3. Workstreams

Ten workstreams. Each item has an ID (for commits and tracking), a design, acceptance criteria and an effort estimate. **S** ≈ part of a session, **M** ≈ one long session, **L** ≈ two to three sessions, **XL** ≈ four or more.

### A. "Always a good move" — the core loop (fixes gap 1)

**A1. Business Situations (L).** Each of the 18 business mechanics gets 6–10 short, mechanic-specific situations, about 150 in total. Up to one per owned business per game day, queued on the business card, never auto-resolved, ignorable at no cost.
- Examples:
  - Restaurants: "A food critic books for tomorrow: raise quality spend 2× for a day (+0.3 stars if she comes) or keep costs."
  - Airline: "A storm grounds the hub: reroute through Lisbon (−5 % load factor for 12 h) or wait (no income for 3 h)."
  - Pharma: "Phase II results leak early: announce now (+hype, −10 % odds if wrong) or wait for the readout."
- Rules: odds shown, worst case affordable (as Desk cards), costs in hours of income so they scale. Managers resolve their own business's situations at their skill level if the player doesn't. This rewards active play without punishing idle players.
- Acceptance: active dead minutes ≤ 25 % (A1 alone); every situation validated (reachable, affordable, no dominant option by EV > 20 %); a simulator bot plays them.

**A2. Specialisations at Scale Tiers (L).** GDD §5.1 promised that tiers unlock *mechanic options*, not just multipliers. Each business gets a 2-way choice at tiers 3 and 5, so 72 choices in total.
- Examples:
  - Retail tier 3: **Flagship** (one huge store, +footfall in its district) vs **Franchise** (cheaper outlets, less control).
  - Bank tier 5: **Investment bank** (deal fees, volatile) vs **Retail bank** (deposits, steady).
- Choices are re-pickable for a fee, so they're decisions, not traps. The UI shows a side-by-side comparison card with a simulated 24 h income preview from the real engine.
- Acceptance: each pair within 15 % of each other in simulated value at its tier (no trap option); a screenshot test per choice screen.

**A3. The Advisor (M).** One card on the Empire tab: the three best moves right now, each with **why** in one line ("Opening Hotels pays back in 3 h 10 m — the best option you have") and a one-tap action.
- It uses the simulator's own planner logic (payback ranking), moved into `:core` so the bots and the player see the same maths.
- Off by default after Chapter 3 (experts can switch it back on). Never auto-buys.
- Acceptance: suggestions match the planner in unit tests; never suggests an unaffordable or locked action; TalkBack reads the reason.

**A4. Session payoffs (S).** Each return gives one small, guaranteed "welcome back" goal tied to what happened offline ("Your Lisbon flat has a new tenant — set the rent"). This implements GDD §2 "no session ends without a payoff".
- Acceptance: simulator confirms a goal exists at ≥ 98 % of session starts.

**A5. Bulk and quality-of-life (S).** Long-press "Buy ×Max across all businesses up to X % of cash", "Collect all", and a "Plan routes for all" shortcut once each logistics hub is mastered.
- Acceptance: never removes a decision the player hasn't mastered (gated by owning a manager or a mastery flag).

### B. Make money work — markets and property (fixes gap 2)

**B1. Yield calibration (M).** Dividends, coupons, staking and rent are re-derived from the business ladder, so a sensible portfolio returns income comparable to a business of the same cost at the same chapter. Today they're fixed numbers that the ladder outgrew.
- Acceptance: the simulator's income-share bands pass (0–8 / 5–20 / 15–30 % on days 1 / 3 / 7) for active play; poor-choices bot never goes bankrupt from markets.

**B2. Family Office (M).** An idle-friendly managed portfolio: pick a risk dial (Preserve, Balanced, Growth, Speculate), set a share of income to sweep in, and a fictional fund manager allocates across the real in-game instruments and rebalances.
- Shown as one clean card with a 30-day chart and a "what it holds" sheet.
- Acceptance: idle bot's income share passes the bands; never exceeds the 49 % stake cap; fees shown up front.

**B3. Property income that feels like a landlord (M).**
- Rent rolls are paid as a visible "Rent day" every 6 h with a tenant-by-tenant summary.
- District cycles show a 7-day forecast band.
- Owning a business's premises gives a visible "rent-free" badge on the business card (the interlock already exists; make it seen).
- Acceptance: rent share passes; "rent-free" badge appears in a UI test.

**B4. Cause and effect in the markets (M).** Your own actions move parody stocks:
- launching a Tech product moves the tech sector;
- an airline disaster dents travel stocks;
- winning a sports championship lifts a media stock.

News headlines name the cause ("Analysts credit [your holding] for the chip rally"). Engine hook: player events → sector shocks (bounded, deterministic).
- Acceptance: unit-tested shock bounds (≤ ±8 % per event); news template coverage test.

### C. Late game and Dynasty 2.0 (fixes gap 3)

**C1. Megaprojects (L).** Multi-day builds after the Holding company that consume *outputs* of several businesses, not just cash:
- **Space Elevator**: Construction + Energy + Space + Telecom.
- **Smart District** in a real city: Construction + Tech + Telecom + Retail.
- **Global Rail Network**: Logistics + Mining + Energy.
- **Floating Resort**: Hotels + Construction + Airline.

Each has 4 stages, a stage gallery (illustrations), and a permanent world effect (e.g. Smart District raises that city's district indices).
- Acceptance: each completes in 1.5–3 days of active play in the simulator; none required for the crown (optional sinks).

**C2. Dynasty eras (L).** Each generation starts in a different **era**: a seeded world modifier that changes strategy, not just numbers.
- **Era of Steel**: industry +, tech −.
- **The Green Turn**: energy transition; fossil assets decline.
- **The Great Boom**: everything up, then a harder recession.
- **The Information Age**: media and telecom +.
- Eight eras in total. The heir screen shows the coming era before you pass the crown.
- Acceptance: each era simulated; generation-2 pacing within ±25 % of target across eras.

**C3. The family (M).** A **Family Tree** screen showing every generation as a portrait (built from the player's avatar choice plus generation-specific frames). Each portrait has the founder's era, peak net worth, trait, heirlooms and an epitaph line written by The Chairman. The Hall of Ancestors makes prestige feel like history, not a reset.

**C4. "Pass the Crown" cinematic (M).** GDD §16 promised it.
- Shot list (8–12 s, skippable, reduced-motion version is a still card):
  1. The empire's net-worth counter rolls up.
  2. Business icons fly into a family crest.
  3. The crest fades to the heir's portrait.
  4. The heir's name is set in Fraunces.
  5. The Chairman says one line.
- Audio: Dynasty theme crescendo. Haptics: heavy tick on the crest lock.
- Acceptance: renders at 60 fps on API 28 emulator (benchmark build), reduced-motion path tested.

**C5. Legacy shop and deeper Charter (M).** Spend a small share of Legacy on permanent cosmetic and convenience items (portrait frames for the family tree, an HQ facade for the next generation). 20 new Charter nodes for generation 3+. Every Legacy spend is reversible at the next prestige.

### D. A world that tells a story (fixes gap 4)

**D1. The Daily Ledger (M).** Returning after 30+ minutes away opens a one-page parody newspaper instead of a bare offline dialog.
- The front page summarises what happened: income earned, the biggest market move, a rival's action, a world event, a card waiting.
- Masthead in Fraunces, two columns, dated in game time, with a "Read later" option.
- Built from the real event log and news templates; every headline links to the screen it's about.
- Acceptance: content snapshot tests for 20 seeded offline periods; TalkBack reads headlines in order.

**D2. The Chairman, in person (M).**
- An AI caricature portrait of an invented person (same pipeline as the 112 existing portraits; owner approval for credits).
- A typewriter text reveal (respects reduced motion).
- Expressions per line type: pleased, wry, concerned.
- Chapter-opening scenes: a full-width chapter card with illustration, The Chairman's opening line and the chapter's three themes.
- Acceptance: all 200 lines tagged with an expression; validator checks tags.

**D3. Nemesis arcs with faces (S).** The 21 nemesis beats get portraits (the rivals already have them), a "Rivalry" panel that tracks the history between you, and a finale screen per chapter.

**D4. World calendar (S).** Fictional festivals and fairs on a yearly game calendar (an "Art Fair" week raises auction lot quality; "Harvest Moon" raises crop prices). Content-driven, rotating, and announced in the Daily Ledger one day ahead, never a countdown.

### E. Social that comes alive (fixes gap 6)

**E1. Switch it on (S, owner step + verification).** Owner enables Email/Password and Anonymous (and Google). I verify sign-up, verification, sync, leaderboards and gifts on the live project with a dedicated test account, then delete the test data. Google sign-in gets its first live test.

**E2. Showroom visits (M).** Visit a friend's profile and walk their Garage, Gallery and Marina as a horizontal glass carousel of their displayed items (public catalogue ids only, no new personal data). Leave one of 6 preset reactions per day ("Magnificent", "Bold choice"…), with no free text and so nothing to moderate.
- Rules: reactions doc per visitor per day, rate-limited by rules; rules tests.

**E3. Consortium week (L).** A co-op weekly goal for up to 5 friends ("Together, open 40 businesses this week"). Each member writes only their own progress document; every client sums them.
- Spark-safe because rules alone validate each member's own writes, as with Sealed Lots.
- Rewards are cosmetic plus Favor.
- Rules: per-member write-own, growth plausibility bound, membership by invite code.

**E4. Leagues with ceremony (S).** Weekly promotion and relegation shown as a short ceremony card with a tier badge (Bronze → Diamond, drawn in code), plus a league history on the profile.

**E5. Live events without an update (M).** Firebase Remote Config (no-cost on Spark; fetch quota ~100,000 a day per project, so fetch at most every 12 h per player) carries:
- the weekly event schedule;
- the featured Sealed Lot;
- update-channel rollout percentages (H2).

The game always ships safe defaults, so it works fully offline.

**E6. Notifications, local only (M).** Opt-in local notifications via WorkManager: "Expansion finished", "Your auction closes in 10 minutes (you're winning)", "3 decisions waiting".
- Per-category switches, quiet hours, and at most 3 a day.
- No server pushes, because personalised pushes need a backend Spark doesn't provide. Firebase Cloud Messaging is only used, if at all, for owner-sent "new update" broadcasts from the console.
- Requests the Android 13+ notification permission only when the player first opts in.

### F. Premium look, feel and sound (fixes gap 5)

**F1. Motion system (M).** One motion vocabulary in the design system, used everywhere:

| Token | Duration | Easing | Used for |
|---|---|---|---|
| `tap` | 90 ms | standard decelerate | press scale 0.97, button feedback |
| `quick` | 160 ms | standard | chips, toggles, small reveals |
| `standard` | 240 ms | emphasized decelerate | cards in, sheets up |
| `emphasized` | 400 ms | emphasized | screen transitions, shared elements |
| `celebrate` | 900 ms | spring (damping 0.7) | tier-up, chapter complete |
| `count` | 600–1,600 ms | ease-out, scaled by digits changed | rolling numbers |

- **Shared-element transitions**: the business card's icon, name and income move into the detail screen header. Instruments behave the same from Markets to chart. Property cards morph into the property screen.
- Reduced motion swaps every token for a 0 ms crossfade, already supported as a setting.
- Acceptance: no hard-coded durations left (lint check); screenshot tests in reduced motion.

**F2. Haptic vocabulary (S).**

| Moment | Haptic |
|---|---|
| Level bought | light tick |
| ×10/×Max bought | two light ticks |
| Scale Tier reached | double click |
| Chapter complete, conglomerate, crown | heavy click + slow rise (where supported) |
| Error / blocked | single low buzz |
| Auction: you're outbid | sharp tick |

Respects the existing Haptics setting. Tested on API 28 (fallback to simple vibration) and 37.

**F3. The growing HQ (L).** The Empire header shows your headquarters as a code-drawn illustration that grows through 8 stages:
1. folding table;
2. kiosk;
3. shopfront;
4. office floor;
5. corporate tower;
6. glass campus;
7. skyline landmark;
8. orbital ring.

Day and night ambience follows the device's local time, with lit windows at night, rain during a recession and fireworks for a chapter. Built with Compose vector drawing, so no new bitmaps and no extra size.
- Acceptance: each stage drawn, dark and light; 60 fps on the emulator benchmark build; reduced-motion still frame.

**F4. Estates skyline parallax (M).** Each city screen gets a layered, code-drawn skyline (Lisbon's hills and tram, London's river bends, Miami's palms, Mumbai's sea link, New York's grid, Dubai's tower). Parallax is tied to scroll; your own towers appear in the skyline once built.

**F5. Cosmetics that change the game's look (M).** These close the CONTENT.md gap (themes, HQ skins, card backs):
- 6 **themes**: Aurum (default), Emerald Club, Midnight Sapphire, Rose Quartz, Obsidian, Ivory, each a full token set validated for contrast AA.
- 8 **HQ facades** for F3.
- 8 **Desk card backs**.

Earned in play or bought with earned Bullion; one premium-track season item per theme. 52 cosmetics in total.

**F6. Sound and music, done properly (M).**
- Owner listening pass on the 4 synthesized loops; keep or replace each.
- Replacements: curated CC0 tracks (credited), or re-synthesis with better instruments and arrangement if CC0 quality is poor.
- **Adaptive layers**: a percussion layer fades in during Frenzy and live auctions, and a strings swell plays on milestones.
- Audio ducking when a dialog opens.
- A UI sound palette audit so every sound shares one tonal family (key of F, same reverb).
- Acceptance: owner approves every track; loudness −16 to −14 LUFS; seamless loops verified with a seam test.

**F7. Widget, shortcuts and system polish (M).**
- A home-screen widget (Jetpack Glance) showing net worth, income/s and a "3 decisions waiting" chip.
- Static app shortcuts: Collect, Desk, Auctions.
- A monochrome themed launcher icon.
- Predictive back animations on every screen.
- Per-app language plumbing for future translation.
- Acceptance: widget updates at most every 30 minutes (battery), reads from the save without running the engine; shortcuts deep-link correctly (UI test).

**F8. Words (S).** A UX-writing pass on every string: button verbs, empty states, errors with a next step, numbers with units. Plus a glossary so "Favor", "Bullion", "Status" and "Legacy" are always explained the first time they appear.

### G. Content depth (fixes gap 8 and adds replay value)

| Content | Now | Target | Notes |
|---|---|---|---|
| Business situations | 0 | ~150 | A1 |
| Specialisation choices | 0 | 72 | A2 |
| Decision cards (Desk) | 163 | 240 | +77, weighted to chapters 5–8 and eras |
| World events | 60 | 90 | eras and calendar |
| Mentor lines | 200 | 320 | megaprojects, eras, family tree epitaphs |
| Megaprojects | 0 | 4 | C1 |
| Eras | 0 | 8 | C2 |
| Cosmetics | 30 | 52 | F5 |
| Collection sets | 30 | 40 | megaproject and era sets |
| Achievements | 150 | 200 | new systems |
| Illustrations (chapters, megaproject stages, events) | 0 | ~48 | AI illustrations of invented scenes, owner-approved, credited; no real logos or people |
| Property/luxury photos | 99 museum works | +~60 | Wikimedia Commons CC0/CC-BY landscapes and architecture of the real cities (no people in frame), credited |

All content is generated by scripts in `tools/content/`, validated by `ContentLoader`, and covered by simulator reachability checks.

### H. Release and distribution

**H1. One-command release (M).** `tools/scripts/release.py <version>`:
1. bump the version;
2. run gates;
3. build;
4. verify the signer;
5. write SHA-256;
6. draft notes from commits and PROGRESS;
7. create the GitHub release as a draft for the owner to publish.

Removes manual steps that can go wrong (wrong key, wrong asset name).

**H2. Staged rollout (M).** (The beta channel was declined, D7.) Staged rollout: Remote Config holds a percentage, and each install hashes a random id into a bucket and only offers the update if inside it. This allows a 20 % → 100 % rollout with no server.

**H3. "What's new" sheet (S).** After an update, a single sheet shows the release's three highlights with small illustrations (from release notes in the APK). It replaces the snackbar and appears once.

**H4. Landing page.** Declined (D9).

### I. Engineering quality (fixes gap 7)

**I1. Isolated test devices (M).**
- Gradle Managed Devices for API 28 and 37: headless, created and destroyed by Gradle, used only by this project.
- A dedicated adb port for this project's runs (`ANDROID_ADB_SERVER_PORT`), so another project's adb restarts can't kill our tests.
- To be verified at implementation; fallback: a pre-run check that refuses to start if another adb server or emulator is active, and reports it.

**I2. Golden save compatibility (S).** Commit a real save from every released version (1.0.0, 1.1.0, then each release) as fixtures. A JVM test loads each into the current engine, advances an hour and checks invariants: the ledger balances, nothing is lost, the chapter is kept. This closes the 1.0 → 1.1 gap without an emulator.

**I3. Screenshot tests (L).** Roborazzi (already in the version catalogue) for every screen × dark/light × 100 %/200 % font × reduced motion, recorded on the JVM. Diffs are reviewed in PRs, so visual regressions can't slip through, and G8 becomes automatic.

**I4. Balance in CI (M).** A nightly GitHub Actions job runs a small simulator batch (2 seeds × 3 days, all bots) on the free runner and fails if any early-game target or invariant regresses. Full 10-day batches stay local, so the laptop budget isn't spent on routine checks.

**I5. Performance on real hardware (M, needs the owner's phone).** Macrobenchmarks for cold start, Empire scroll, Markets chart and the auction screen, run on a mid-range phone over USB.
- Budgets: cold start < 1.0 s; 90th-percentile frame time < 12 ms.
- Compose stability audit: stable params, `remember` keys, derived state.
- The baseline profile is regenerated with the plugin's correct output naming (fixing the 1.1.0 workaround).

**I6. Crash insight (M, owner decision D5).** Option A: Firebase Crashlytics (no-cost) with an explicit opt-in on first launch and a privacy note in Settings. Option B: no SDK; crashes are written to a local log the player can share from Settings with one tap. Either way, an ANR watchdog logs main-thread stalls > 2 s in debug builds.

**I7. Code health (S, ongoing).** Split the largest files (GameEngine, DebugScenarios, big screens) along feature lines, raise `:core` test coverage on new systems to ≥ 85 % lines, and keep zero StrictMode violations on the main thread.

### J. Accessibility and inclusion

**J1. Full TalkBack journeys (M).** The first-session, auction and Desk flows are completed with TalkBack only, recorded as accessibility tests, with custom actions for "Buy ×10" etc. so blind players don't need gestures.

**J2. Colour and contrast (S).** Every theme (F5) passes WCAG AA; gains and losses keep arrows and signs; a "high contrast" option.

**J3. Large text everywhere (S).** 200 % font screenshots (I3) for every screen; no clipped numbers (auto-shrinking number style for the header counter).

**J4. Motion and sound comfort (S).** Reduced motion covers the new cinematic, HQ and parallax; a "no sudden sounds" option lowers celebration sounds.

---

## 4. Release train

Each release ships through the auto-updater, keeps saves (golden tests), and passes the gates in §5. Effort is in working sessions at the project's normal pace; simulator batches run in the background.

### 1.2 "Momentum" — the loop and the money (≈ 6–8 sessions)
**Scope:** I1, I2, A1, A3, A4, A5, B1, B2, B3, E1, E6, F2, F6, F8, H3.
**Why first:** it fixes the two biggest measured problems (dead minutes, decorative markets), makes testing reliable on a shared laptop, switches social on, and gets the music approved.
**Exit criteria:** active dead minutes ≤ 25 %; income-share bands pass; 17/17 balance targets; golden saves 1.0.0 and 1.1.0 load; UI flows 100 % on isolated API 28 and 37 devices; owner has approved the music.

### 1.3 "Living World" — story and craft (≈ 7–9 sessions)
**Scope:** D1, D2, D3, D4, F1, F3, F4, F7, B4, A2, I3, J1–J4.
**Exit criteria:** every screen has screenshot tests; no hard-coded animation durations; HQ stages render at 60 fps (benchmark build); the Daily Ledger shown on every return over 30 minutes; specialisations balanced (no trap option); TalkBack journeys pass.

### 1.4 "Together" — social, live and distribution (≈ 5–6 sessions)
**Scope:** E2, E3, E4, E5, F5, H1, H2, H4, I4, I6.
**Exit criteria:** rules tests cover reactions and consortiums; live-project smoke test with test accounts (data cleaned up); staged rollout proven on a pre-release (two test installs, one in bucket, one out); 52 cosmetics; nightly CI balance job green for 7 nights.

### 2.0 "Dynasty" — the long game (≈ 8–10 sessions)
**Scope:** C1, C2, C3, C4, C5, the rest of G (content depth), I5, I7, and new 2.0 balance targets (generation 2–3 pacing per era, megaproject timing).
**Exit criteria:** all 2.0 balance targets pass across 8 eras; the cinematic runs at 60 fps on a real mid-range phone; cold start < 1.0 s on that phone; APK ≤ 25 MB; every content minimum in CONTENT.md met or exceeded.

---

## 5. Release gates, upgraded

The existing G1–G12 stay. New gates:

| Gate | Requirement |
|---|---|
| **G13 Save compatibility** | Golden saves from every released version load and play for an hour of game time with all invariants intact |
| **G14 Visual regression** | All Roborazzi screenshots match or have reviewed, intentional diffs |
| **G15 Performance budget** | Cold start, scroll and auction frame budgets met on the reference phone (from 2.0; emulator indicative before that) |
| **G16 Isolation** | UI tests ran on isolated devices; no foreign adb or emulator interference detected during the run |
| **G17 Updater** | The new APK's signer matches the release key; GitHub's digest matches the local SHA-256; a 1.(n−1) → 1.n auto-update test passes |

---

## 6. Decisions the owner makes (before or during 1.2)

**Owner answers, 2026-11-07:** D1 ✅ · D2 ✅ (listen first; alternatives prepared) · D3 ✅ · D4 ✅ (style approved on the first 4) · D5 ✅ (Crashlytics, explicit opt-in) · D6 ✅ · D7 ✗ (no beta channel) · D8 ✅ · D9 ✗ (no landing page).


| # | Decision | Recommendation |
|---|---|---|
| D1 | Approve the release train and 1.2 scope | Approve 1.2 as listed; revisit later releases after 1.2 ships |
| D2 | Music: keep the synthesized loops, replace with curated CC0 tracks, or re-synthesize | Listen first (Settings → Music); I prepare 2–3 CC0 alternatives per area for comparison |
| D3 | The Chairman's portrait (AI caricature of an invented person, same pipeline and credit rules as the existing 112) | Yes |
| D4 | AI illustrations for chapters, megaprojects and events (~48, invented scenes, no real logos or people) | Yes, with a style sheet approved on the first 4 |
| D5 | Crash reporting: Crashlytics with opt-in, or local-only crash log | Crashlytics with explicit opt-in (better data, still no-cost) |
| D6 | Local notifications (opt-in, max 3 a day, quiet hours) | Yes |
| D7 | Beta channel for friends who want early builds | Yes |
| D8 | A real mid-range phone for performance gates (from 2.0) | Yes, any 2022+ mid-range Android over USB |
| D9 | Landing page on GitHub Pages | Optional; low effort |

---

## 7. Risks and how they're handled

| Risk | Impact | Mitigation |
|---|---|---|
| New systems make balance drift | Pacing targets break | Every release ends with a full 10-day batch; nightly CI catches early regressions; tuning stays in `regen_economy.sh` + knobs |
| Situations and specialisations add decision fatigue | Feels like work | Ignorable at no cost; managers resolve; caps (one per business per day); Advisor explains |
| Spark quotas (50k reads / 20k writes a day; Remote Config fetches) | Cloud features throttle | Per-player budgets already exist; consortium and showroom reads cached; Remote Config fetched at most every 12 h |
| Shared laptop (another Claude session, low RAM) | Flaky tests, slow builds | I1 isolation; small background batches; never touch foreign processes; owner tells me when the emulator is free |
| Art consistency across AI illustrations | Cheap look | Style sheet + owner sign-off on the first batch; code-drawn art (HQ, skylines) for anything structural |
| APK growth from art and audio | Slow downloads | AVIF/WebP, size gate ≤ 25 MB, audio ≤ 6 MB budget |
| Save format changes | Lost progress | Golden saves (G13), migrator tests, never remove fields |
| Auto-update mistakes | Players stuck on old versions | G17 signer and digest gate; one-command release; draft-first publishing |

---

## 8. Working agreements

### 8.1 Shared-machine etiquette
- Before any emulator or adb work, check for other emulators and adb servers; if another project is using them, ask before starting.
- Prefer JVM tests (screenshot tests, golden saves, simulator) that don't need a device.
- Never stop processes I didn't start; never run `gradlew --stop` while another session might be using Gradle.
- Long batches run detached with logs in `build/`, so closing VS Code doesn't lose work.

### 8.2 Definition of done (unchanged, plus)
Tests pass → runs on an (isolated) emulator → new screens inspected from real screenshots in dark and light → PROGRESS.md updated → commit → push → CI green. For releases, all gates G1–G17 or a documented, owner-approved exception.

---

## 9. First three tasks after approval
1. **I1 + I2** (one session): isolated test devices and golden saves for 1.0.0 and 1.1.0. This closes the open 1.1.0 checks without depending on the shared emulator, and makes every later test trustworthy.
2. **E1** (owner click + 30 minutes): switch on sign-in, live-test accounts, sync, leaderboards and gifts, and Google sign-in.
3. **A1 design spike** (one session): write 20 of the ~150 Business Situations across 6 businesses, wire them through the engine and Desk validator, and run a 3-day batch to measure the dead-minute drop before writing the other 130.
