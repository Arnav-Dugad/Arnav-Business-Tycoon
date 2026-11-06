# Arnav Business Tycoon — Test Plan and Release Gates

## 1. Test layers

| Layer | Where | Tools | Runs |
|---|---|---|---|
| Core unit + property tests | `:core` | JUnit 6, Kotest property | Every build, CI |
| Content validation | `:core` test + `tools/art/check.py` | JUnit, Python | Every build, CI |
| Save migration golden tests | `:core` | JUnit + fixture saves | Every build, CI |
| Simulator smoke batch | `:sim` | Custom runner | CI (6 bots × 20 seeds) |
| Simulator full batch | `:sim` | Custom runner | Each balance milestone + release |
| Android unit tests (ViewModels, repos) | `:app`, `:data` | JUnit, Turbine, Robolectric | Every build |
| Screenshot tests (dark + light, font scale 1.0 and 2.0) | `:app` | Roborazzi (JVM) | Every build; diffs reviewed |
| Compose UI tests (main flows) | `:app` androidTest | Compose UI Test on emulator | Phase end + release |
| Firestore rules tests | `firebase/test` | @firebase/rules-unit-testing + Firestore emulator | CI + release |
| Random-input stress test | emulator | `adb shell monkey` + UI Automator random walker | Release |
| Upgrade-install test | emulator | Script: install previous release → seed save → install new over it | Release |
| Macrobenchmark (startup, frame timing) | `:baselineprofile` | Macrobenchmark | Release (emulator figures are indicative only) |
| Manual visual review | emulator screenshots | `adb exec-out screencap` | Every new screen; release |

## 2. Core test catalog (must exist and pass)

**Money**
- Properties: addition is associative and commutative; `a + b − b == a`; multiplication by a `Multiplier` then rounding stays within ½ cent; Long↔BigInteger promotion is seamless at the ±9.2e16 boundary; no overflow up to 1e300.
- Format and parse round-trips; formatting golden table (0, negatives, $0.01, $999,999.99, every short-scale suffix, scientific mode).

**Ledger**
- Every transaction balances to zero; `balance == replay(journal)` after 100,000 random postings; the credit floor is enforced; minute batching equals unbatched totals.

**Engine**
- `advance(a + b) == advance(a) then advance(b)` for random states and durations (the offline ≡ live guarantee).
- Offline catch-up equals the live 1-second loop over 8 hours with no input (exact cents).
- Determinism: same seed and commands → byte-identical state hash.
- Every Command: success path plus each typed error.

**Per mechanic**: E stays within [0.25, 2.5]; edge cases (zero capacity, depleted wells, expired patents, recession upkeep).

**Clock tamper**: forward jump, backward jump, back-then-forward, reboot, network-time mismatch. Credit never exceeds the cap or the high-water-mark rule.

**Offline cap**: each source of cap upgrades stacks correctly; absolute max 20 h.

**Saves**: round-trip; gzip/sha corruption → falls back to the previous snapshot; truncated blob; newer-schema refusal; migration of every golden fixture vN → current; atomic write (simulated crash mid-write keeps the previous snapshot readable).

**Never stuck**: Phoenix protocol triggers; restructuring math; Hustle floor is always > 0.

**RNG**: stream independence and reproducibility; no use of `Random.Default` in `:core` (lint rule test).

## 3. Content validation
- Schema conformance, unique IDs, references resolve, numeric ranges, counts ≥ CONTENT.md minimums.
- Name rules: parody names only (GDD §1.1): no exact real brand, famous real ticker or exact real-person name; no duplicate names.
- Images: every referenced image exists; every image appears in `credits.json` with an allowed license; no orphans; sizes within budget.

## 4. Compose UI flows (instrumented, emulator)
1. Fresh install → onboarding → first Hustle taps → buy first level → hire first manager.
2. Expansion: start a timer → advance a fake clock → complete → collect.
3. Markets: buy stock → sell → portfolio and ledger update; short-selling lock before Chapter 5. (`MarketsFlowTest`, from the debug `markets` scenario.)
4. Estates: open map → district → buy property → set rent → renovate. (`EstatesFlowTest`, from the debug `markets` scenario.)
5. Collection: auction flow (bid, win or lose) → item appears in showroom.
6. Decision card resolve; tax filing (auto and self).
7. Dynasty prestige flow with confirmation.
8. Store: demo purchase sheet shows the DEMO label → entitlement granted → Boosted flag set.
9. Settings: theme switch, audio toggles, reduced motion, export save.
10. Auth (against the Firebase Auth emulator): sign up → verification banner → sign out → sign in; guest → upgrade.
11. Sync conflict screen appears when both copies changed; both choices work; nothing is lost.
12. Accessibility: TalkBack content descriptions present on all interactive nodes (automated check); 200% font scale has no clipped text on key screens (screenshot review).

## 5. Firestore rules tests
For each collection: owner allowed, other user denied, unauthenticated denied, unverified-email denied where required. Plus:
- Leaderboard: write another user's key (deny); write within 10 minutes (deny); implausible lnw jump (deny); decrease (allow); boosted true → false (deny).
- Saves: wrong revision (deny); oversize blob (deny).
- Gifts: non-friend (deny); second gift in a day (deny).
- Sealed Lots: bid after close (deny); read others' bids before close (deny), after close (allow).

## 6. Balance gates
All targets in ECONOMY §10.3 pass on the full batch (6 bots × 1,000 seeds + probes). The report is committed in `docs/balance/`.

## 7. Release gates (every release must pass all of these)

| # | Gate | Evidence |
|---|---|---|
| G1 | Unit and property tests: economy, Money, Ledger, offline earnings, save migrations | Gradle test report, 0 failures |
| G2 | Content and image-reference validation | Validator output |
| G3 | Balance simulator targets | `docs/balance/<release>/summary.md` all green |
| G4 | Compose UI tests of main flows on the emulator (API 36 + API 28) | androidTest report |
| G5 | Firestore rules tests | `firebase emulators:exec` output |
| G6 | Random-input stress test ≥ 30 min (monkey 100k events + random walker) with no crash or ANR | logcat scan report |
| G7 | Clean upgrade install over the previous release preserves the save | Script output + before/after screenshots |
| G8 | Visual check of every screen, dark and light | Screenshot set reviewed; issues listed |
| G9 | Signed release build (R8, shrink, baseline profile) installed and smoke-tested on the emulator | Install log + smoke-test screenshots |
| G10 | APK size ≤ 60 MB | `check_size.py` output |
| G11 | Secret scan clean, no APK or keystore in the repo | `scan_secrets.py` output |
| G12 | Credits complete (every asset licensed) | `check.py` output |

Anything skipped or failing is listed in `docs/PROGRESS.md` and the release notes. A release is not published with a failing gate unless the user explicitly accepts it.
