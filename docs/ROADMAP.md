# Arnav Business Tycoon — Roadmap

**Every phase ends when**: (1) its acceptance criteria are met, (2) all existing tests pass, (3) the app runs on the emulator and every new screen has been inspected from real screenshots, (4) PROGRESS.md is updated, (5) a milestone commit is made. Skipped or failing items are reported, never hidden.

Optional friend previews: alpha APKs (`v0.x.0-alpha`) can be published after Phases 4, 8 and 11 if the user wants early feedback.

| Phase | Name | Acceptance criteria |
|---|---|---|
| **0** | Planning | All 8 docs written; names checked; manual steps listed; **user approval** — ✅ DONE 2026-11-06 |
| **1** | Project skeleton and tooling ✅ DONE | Gradle multi-module build with a version catalog; JBR 25 pinned; `.gitignore` + secret scanner + pre-push hook; GitHub repo created; CI (core tests, content validation, rules tests) green; API 37 AVD created; the app launches to an Aurum-themed placeholder in dark and light; screenshots reviewed |
| **2** | Core foundations ✅ DONE | Money, Rate, Multiplier, MoneyFormat, Ledger, GameClock, RNG streams, ContentPack + validator, GameState, event-driven engine, save envelope + migrator + golden v1 fixture, offline + tamper logic. All TEST_PLAN §2 tests for these pass; `:core` has zero Android dependencies (enforced by a build check) |
| **3** | First playable (Chapter 1) ✅ DONE | Retail, Restaurants and Logistics with their real mechanics; managers, slots, expansions, Hustle and Frenzy; HQ; Room saves with autosave and recovery; offline-earnings dialog; onboarding; Empire tab UI; Next Move card; design-system components; icons v1. **A new player can play the first 30–60 minutes** on the emulator; UI flows 1–2 pass |
| **4** | Simulator v1 ✅ DONE | `:sim` with all 6 bots + probes on the Chapter 1–2 content; reports generated; early-game pacing tuned to targets; CI smoke batch |
| **5** | All businesses + merger tree ✅ DONE (Chapter 8 pacing deferred to Phase 11) | The remaining 15 business mechanics, conglomerates, Holding Co; per-mechanic tests; Empire UI for each |
| **6** | Markets ✅ DONE (income-share bands and Chapter 8 deferred to Phase 11) | 120 stocks, sectors, news, earnings, board seats, slippage, funds and bonds desk, commodities, crypto, short selling; Compose Canvas charts; Markets tab; flow 3 passes |
| **7** | Real estate ✅ DONE (real cities; income-share bands and Chapter 8 deferred to Phase 11) | Map, cities, districts, cycles, properties, rent, renovation, flips, mortgages, insurance, tenant events, skyscrapers; Estates tab; flow 4 passes |
| **8** | Luxury and collections + art pipeline ✅ DONE (museum photos and AI manager portraits; other luxury photos, estate photos and Heirlooms deferred) | Art pipeline built; all photos sourced, licensed and credited; luxury items, Status, live auctions, showrooms, collection sets, Heirlooms; Credits screen; flow 5 passes |
| **9** | World, rivals, finance pressure ✅ DONE (Negotiate mini-game, tenders and property bids deferred) | Cycle, events, decision cards, disasters; 220 rivals, Fortune List, takeovers, nemesis; credit rating, loans, taxes (auto and self-file), restructuring, Phoenix protocol; flow 6 passes |
| **10** | Progression and mini-games ✅ DONE except mini-games (moved to Phase 15) | Story chapters 1–8, missions, achievements, daily ladder, weekly events, seasons, Dynasty prestige with Family Charter, heir traits, challenges, IPO; Negotiation, Auction, Market Timing, Frenzy polish; flow 7 passes |
| **11** | Full balance pass | Full simulator batch (thousands of seeded runs) meets every ECONOMY §10.3 target; tuning log; reports in `docs/balance/` |
| **12** | Firebase: auth, cloud save, rules | Firebase project + Android app via CLI; Auth (email, Google, guest + upgrade, verification, reset, deletion); cloud save sync with conflict screen; Firestore rules + full emulator test suite; quota counters; flows 10–11 pass; rules tests green |
| **13** | Social | Friend codes, requests, profiles, global, friends and fair-play leaderboards (Boosted badges), weekly leagues, gifts, Sealed Lots; rules extended and tested |
| **14** | Store | PurchaseProvider + SimulatedPurchaseProvider with DEMO labeling, catalog, VIP, cosmetics, Boosted flags; spender-gap balance check; flow 8 passes |
| **15** | Polish and performance | Animations, rolling counters, celebration moments, audio + music + haptics with separate settings, accessibility pass (TalkBack, 200% font scale, reduced motion), empty, loading and error states everywhere; baseline profiles; R8; size budget met; macrobenchmarks |
| **16** | Release 1.0.0 | Release keystore (outside repo), signed optimized APK, in-app update checker, README with screenshots and friend install steps, GitHub Release v1.0.0 with changelog; **all release gates G1–G12 pass** |

Later: v1.x seasons and content drops, each a minor version, with upgrade-install gates.

## Manual steps for the user (complete list)

### Before Phase 1 — DONE (2026-11-06)
1. ✅ Plan approved. Name: **Arnav Business Tycoon**.
2. ✅ This repo commits with the GitHub no-reply address.
3. ✅ Public repo `Arnav-Dugad/Arnav-Business-Tycoon` approved.
4. ✅ Tool upgrades approved: firebase-tools 15.35.0, build-tools 37.0.0, API 37 system image, new AVD.

### Before Phase 8 (art)
5. ~~Pexels API key~~ — no longer needed: Pexels paused new keys (2026-11-06), so photos come from Wikimedia Commons and museum open-access APIs, which need no key.
6. ~~Portraits decision~~ — decided 2026-11-06: caricature-style AI portraits of invented people, generated with Figma AI on the owner's plan (owner approved the credits). Managers done in Phase 8; rivals follow in Phase 9.

### Phase 12 (Firebase)
7. Your Firebase CLI is already logged in with the owner account. **Note**: that account already has **24 Firebase projects**. Spark accounts have a project quota, so creating a new one may fail. If it does, delete an unused project in the console (Project settings → General → Delete project) or request a quota increase.
8. **Choose a Firestore region** (it cannot be changed later): recommended `asia-south1` (Mumbai) if most friends are in India, otherwise `nam5` (US) or `eur3` (Europe).
9. **Console steps** (the CLI cannot do these):
   - Authentication → Get started → Sign-in method → enable **Email/Password**, **Google** (set the public-facing name "Arnav Business Tycoon" and a support email), and **Anonymous**.
   - Authentication → Templates: optionally set the sender name for verification and reset emails to "Arnav Business Tycoon".
   - Authentication → Settings → **User account linking**: keep "Link accounts that use the same email".
10. **SHA-1 fingerprints for Google sign-in**: I will add the debug and release SHA-1 and SHA-256 with `firebase apps:android:sha:create` and download `google-services.json` with the CLI. If the CLI command is refused, the console path is: Project settings → Your apps → Android app → Add fingerprint (I will print the exact values to paste).

### Phase 16 (release)
11. **Keystore backup**: I will generate the release keystore at `C:\Users\rduga\Keystores\arnav-business-tycoon\abt-release.jks`, outside the repo, with passwords stored in `C:\Users\rduga\.gradle\gradle.properties`. **Back up both** (the .jks file and the passwords) to two places, for example a password manager and a USB drive or private cloud folder. **If the keystore is lost, friends cannot update the app without uninstalling it and losing local saves.**
12. Optional: test the release APK on a real mid-range phone, because emulator frame timings are not a reliable 60 fps proof.
13. Share the GitHub Releases link with friends. The README explains how to allow "Install unknown apps" for their browser or file manager.
