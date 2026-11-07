# Arnav Business Tycoon — Economy, Formulas and Balance Targets

All numbers here are **starting values**. Every tunable lives in `content/balance/*.json` and is tuned by the headless simulator (§10). Final values and the reports behind them go in `docs/balance/`. Formulas are original to this project.

Notation: `t` = real seconds, `h` = real hours, `L` = level, `NW` = net worth.

---

## 1. Money and precision rules

- Balances use `Money`: exact integer **cents**. The fast path is a `Long`; it promotes to `BigInteger` automatically past ±9.2e16 cents. No `Double` is ever used for a balance.
- Rates (income per second) are `Rate` values in **micro-cents per second** (BigInteger), integrated over milliseconds with a remainder carry. That makes `advance(a + b) == advance(a); advance(b)` exact (property-tested).
- Multipliers are `Multiplier` values: decimal with 34 significant digits (DECIMAL128 context). Applying one to Money rounds half-even to cents, and the sub-cent remainder goes into the source's carry.
- `Double` is allowed only for: random distributions *before* conversion, chart rendering, animation, and log-scale leaderboard scores.
- **Display**: below $1M, full format with grouping (`$12,345.67`). Then short scales: K, M, B, T, Qa, Qi, Sx, Sp, Oc, No, Dc, then `aa`…`zz`. A setting switches to scientific notation (`$1.23e45`). Always 3 significant digits, with a monospace tabular font in counters.

---

## 2. Time model

| Clock | Cadence |
|---|---|
| Engine | Event-driven; the live UI calls `advance()` once a second, and counters interpolate at frame rate |
| Market tick | Every 60 s, aligned to the epoch (prices are a deterministic function of world seed + tick index) |
| News | ~ every 20 min (seeded) |
| Dividends, rent, loan payments | Every 6 h |
| Farm and fashion seasons | 3 h and 6 h |
| Sports matchday | 4 h |
| Earnings season, tax quarter | Every 3 days |
| Economic cycle phase | 1.5–4 days (seeded) |
| Weekly event / Season | 7 days / 28 days |

**Offline progress** reuses the same `advance()` with no player input. That is identical to leaving the phone untouched while playing: managed businesses produce, timers complete, rent arrives, and decision cards queue (up to 3) but never auto-resolve.
- Offline cap: **2 h base**. Free upgrades raise it to 12 h (HQ "Night Office" levels, Charter nodes). Demo VIP adds +4 h; the Store adds up to +4 h. Absolute maximum 20 h.
- Clock-tamper rules: ARCHITECTURE §6.

---

## 3. Business formulas

For business `b` at level `L`:

```
cost(L → L+1)   = C0_b · g_b^L · (1 − d)                 d = stacked discounts, capped at 0.60
gross_rate(L)   = R0_b · L · T_b(L) · E_b · G            per second
T_b(L)          = Π tierMult_b,i for every Scale Tier i reached   (tiers at 10/25/50/100/150/250/400)
E_b             ∈ [0.25, 2.5]                           efficiency from the business's own mechanic (§4)
G               = (1 + Σ additive bonuses) · Π multiplicative bonuses
                  additive:      managers, Charter "flat" nodes, collection sets
                  multiplicative: Legacy, conglomerate, Status tier, sector cycle, temporary boosts
upkeep(L)       = u_b · gross_rate(L)                    u_b ∈ [0.10, 0.35]; rises ×1.25 in a recession for aggressive policies
net_rate        = gross_rate − upkeep − tax_withholding
```

Starting ranges (tuned by the simulator):

| Business | C0 | g | R0 ($/s) | u | tierMult range |
|---|---|---|---|---|---|
| Retail | $4 | 1.085 | 1.0 | 0.12 | 1.8–2.5 |
| Restaurants | $60 | 1.090 | 9 | 0.18 | 1.8–2.5 |
| Logistics | $720 | 1.095 | 70 | 0.20 | 1.8–2.6 |
| … each later business | ×10–14 previous C0 | 1.095–1.140 | ×7–10 previous R0 | 0.15–0.35 | 2.0–3.0 |
| Space | ~$4e15 | 1.140 | ~3e13 | 0.35 | 2.5–3.0 |

**Payback targets** (marginal level purchase, at the stage where the business is current):

| Stage | Marginal payback | Expansion payback |
|---|---|---|
| Chapter 1 (first hour) | 20 s – 5 min | 10–30 min |
| Chapters 2–3 (day 1–2) | 3–20 min | 30–90 min |
| Chapters 4–6 (day 2–6) | 10–60 min | 1–4 h |
| Late generation (pre-prestige) | 45–180 min | 3–8 h |

When most marginal paybacks pass ~2 h *and* the projected Legacy gain is ≥ 1.5× current Legacy, the **Dynasty Advisor** suggests prestiging. It is only a suggestion and never forced.

**Managers**: hire cost = 25 × the cost of the business's level-10 purchase × tier factor. Skill 1–5 stars; efficiency achieved by a manager = 0.70 + 0.07 × stars of the best possible E. Active manual play can reach E_max.

**Slots**: 3 at start; +1 at chapters 2–7 (6 more); +1 every 3 HQ levels (3, 6, … 24: 8 more); +1 from Charter nodes (4 more) = **21 free** for 18 businesses, so every business can run by the late game but choosing which to run matters before then. Store slots: max +3. (Changed in Phase 5 from "HQ 5/10/15/20/25" after the simulator showed slots blocking Chapter 5.)

---

## 4. Efficiency (E) by mechanic — examples

| Business | E formula (sketch) |
|---|---|
| Retail | `footfall(district)/footfall_ref × demand(price)·price / revenue_ref`, where `demand(p) = (p_ref/p)^ε` with ε from 1.3 to 2.2 by format, plus a holiday stock bonus |
| Restaurants | `0.4 + 0.35 × stars`; stars drift toward a quality target at 0.1 per hour; decay without upkeep |
| Logistics | `Σ_routes min(capacity, demand) × margin(fuel) / Σ capacity_ref` — rewards matching fleet to demand |
| Factory | `min(stage capacities)/max(stage capacities) × input_cost_factor` |
| Fashion | `0.6 + 0.9 × (hit trends chosen)/2` with variance; outlet recovers 40% on misses |
| Energy | `spot exposure × spot/ref + contracted share × contract/ref` (the hedge decision) |
| Bank | `(loan yield − deposit cost) × volume − default rate(standard, cycle) × loans` |
| Pharma | Expected value of the pipeline; realized outcomes are discrete (approval or failure) |

Each mechanic is a separate module in `:core` with its own unit and property tests.

---

## 5. Markets

### 5.1 Stocks (per company i in sector k)
```
log V_i(τ+1) = log V_i(τ) + μ_i·Δ + β_i·m(τ) + s_k(τ) + earnings shocks      fundamental value
X_i(τ+1)     = X_i(τ) − θ_i·X_i(τ)·Δ + σ_i·√Δ·Z + J_i(τ)                  mean-reverting noise + news jumps
P_i(τ)       = V_i(τ) · exp(X_i(τ))
```
- `m(τ)`: macro drift by cycle phase (Boom +, Recession −). `s_k(τ)`: sector event drift.
- `J`: news jumps from the news system; size from the template (±3% to ±25%).
- One tick = 60 s. Z comes from the seeded market stream `rng("market", tick)`.

| Risk band | Expected drift/day | Daily volatility | Dividend/day |
|---|---|---|---|
| Defensive | +0.8% | 1.0–1.8% | 0.15–0.30% |
| Core | +1.5% | 1.8–3.0% | 0.05–0.20% |
| Growth | +2.5% | 3.0–5.0% | 0–0.05% |
| Speculative | +3.0% | 5.0–9.0% | 0% |

(Drift is accelerated relative to real markets on purpose. It matches the game's compressed time.)

- **Slippage**: `price_impact = k · (order_value / depth)^0.6` with k = 0.5, capped at 50 %. Depth = the company's base market cap × the chapter's **depth scale** (1, 1, 1, 10, 10³, 10⁵, 10⁷, 10⁹ for chapters 1–8), so investing stays meaningful in the late game. Funds, bonds and commodities have much deeper markets than single stocks. The order ticket warns when slippage exceeds 2 %.
- **Orders never move the quoted price.** Quotes are a pure function of world seed and minute index, so the market is identical however time is split (live, offline, simulator) and identical whatever the player trades.
- **Shares outstanding** grow with the same depth scale (companies issue shares as the economy grows), so stakes are diluted when a chapter deepens the market; a news story announces it. A player may own at most **49 %** of a listed company.
- **Board seats**: a 5 % stake gives +5 % income to the businesses mapped to the company's sector. A 20 % stake (a "division stake") makes opening a business in that sector 25 % cheaper. (Blocking rival takeovers arrives with rivals in Phase 9.)
- **Fees**: 0.1% per trade. Capital-gains tax of 10 % on realized gains (the full tax system arrives in Phase 9).
- **Shorting** (stocks only, Chapter 5+): initial margin 150%, maintenance 125%, borrow fee 0.2% per day charged every 6 h. Forced cover at maintenance. A cover never takes more than the collateral (the broker absorbs any gap), and a short so large that slippage alone would breach maintenance is refused.
- **Noise and news**: 70 % of a stock's variance is in the fundamental random walk, 30 % in mean-reverting noise (reversion 2 per day). Company stories put half their jump into fundamentals and half into noise, so part of the move fades. Sector stories add drift for 6 hours.
- **Earnings** every 3 game days: each company gets a surprise ~ N(0, 3.5 %). Predictive stories ("Rumor" 55 %, "Report" 72 %, "Confirmed" 90 % reliable) add their magnitude when right and subtract half of it when wrong.
- **Payouts** every 6 h: dividends, fund dividends (weighted members minus fee), bond coupons (central-bank rate + spread), crypto staking.
- **Macro cycle**: Expansion → Boom → Slowdown → Recession phases of 1.5–4 days with drift −1.0 % … +0.5 % a day (× beta) and a central-bank rate of 0.4–1.1 % a day.
- **Unlocks**: stocks, funds and bonds in Chapter 2; crypto and commodities in Chapter 3; short selling in Chapter 5.
- **Commodity links**: each commodity scales the revenue or upkeep of linked businesses by (price / anchor)^elasticity (elasticity 0.05–0.30), kept within ×0.75–×1.35. Example: crude oil raises Energy revenue and Airline and Logistics upkeep.
- **Charts** keep 48 points at 30 minutes (1 day) and 28 points at 6 hours (1 week), stored as exact integer log-prices (0.01 % resolution), so saves stay under 64 KB.

### 5.2 Other instruments
| Instrument | Expected/day | Volatility/day | Notes |
|---|---|---|---|
| Index funds | +1.0–1.8% | 0.8–2.5% | Diversified across sectors |
| Government bonds | central-bank rate (0.4–1.2%) | ~0.2% | Price moves inversely to rate changes × duration |
| High-yield bonds | +1.3–2.0% | 0.5% | Default chance in recession |
| Crypto (4 tiers) | +2% … +4% | 6% … 18% | Crash events: −40% to −90% for tier 3–4 coins (rare, seeded) |
| Commodities | ±cycle | 1.5–6% | Mean-reverting to a cost-of-production band; shocks from events |

## 6. Real estate

```
value_p(τ)   = base_p · PI_d(τ) · condition_p · renovation_mult_p
PI_d(τ)      = trend_d(τ) · (1 + A_d · sin(2π τ / period_d + φ_d)) · city_corr · cycle_corr
rent_p/6h    = value_p · yield_d · occupancy(rent_price, tenant_quality) / 4
```
- Period_d: 4–9 days; amplitude A_d: 4–12% (city cycle 6–12 days, 2–5%); yield_d: 2.2–4.0% per day gross. (The original 10–35% over 2–5 days made the deterministic cycle a near-doubling buy-low/sell-high exploit; reduced in Phase 7.) Macro phase adds −8 % … +6 %; gentrification (3 % a day per district) and finished landmarks add permanent boosts.
- Buy/sell transaction cost 3% each way. Sale timer 2–6 h by type (illiquid by design). Listings: 3–5 per district, refreshed every 6 h from the world seed; 25 % are distressed (15–30 % below value, poor condition).
- Renovation: cost 8–20% of value, timer 2–6 h, +12–30% value, +12–25% rent, up to 3 times; the tenant leaves during works. Repair: 6 % of value, 2 h, restores condition.
- Rent: market rent × asking-rent factor; occupancy falls above 95 % of market rent, so revenue peaks near 100 %. Vacancy 1.5–3.9 h depending on asking rent. Unresolved tenant decisions cut rent by 20 %.
- Insurance 0.05 % of value a day; disasters (≈0.4 % per property per 6 h × district and type risk) damage uninsured properties only.
- Hosting: a business operating from your own property (retail unit, office floor, warehouse, hotel plot, mixed-use) pays no rent: its upkeep × 0.9.
- Property tax: 0.2% of value per day.
- Mortgage (Chapter 3+): loan-to-value 0 / 50 / 60 % until credit ratings arrive (Phase 9); interest = central-bank rate + 0.3 % a day, paid every 6 h; unpaid interest is added to the loan; at 95 % loan-to-value the bank sells (foreclosure) and absorbs any shortfall.
- City price scales follow the business ladder: Lisbon ×1, London ×12, then each later city is pegged to 1/1000 of the opening price of its chapter's first business.

## 7. Luxury and Status
- Value drift per day: cars −0.5% (classics +0.3%), jets −0.4%, yachts −0.3% with upkeep 0.3% per day, art cycle ±(0.2–1.5%), watches ±0.2%, wine +0.2%.
- Status points per item: `S = 10 · max(0, log10(price / $1,000))²`; only items on display (within showroom capacity) count. Status tiers (8: Newcomer, Bronze 50, Silver 250, Gold 800, Platinum 2,000, Diamond 4,500, Royal 9,000, Imperial 16,000) give dealer discounts of 0–15 % and will gate exclusive auctions, rival alliances and the IPO (tier 6).
- Dealers charge a 2 % fee and buy back at 85 % of value. Prices are realistic (cars $70K–$18M, jets up to $250M, yachts up to $600M, masterpieces up to $450M), so luxury is a mid-game Status and set-bonus sink rather than a late-game money sink.
- Collection sets (30) give permanent bonuses: business income (+3–5 % to a named business), Status (+3–10 %), dividend yield, rent, or extra dealer discount.
- Auction rival AI: each rival's maximum is the estimate × N(1, 0.15) (clamped 0.6–1.6) × a style factor (aggressive ×1.05, collector ×1.15 on art, steady ×0.95). Rivals bid on a minutes-scale rhythm while the auction is open and every few seconds in its final minute; snipers wait for the last 12 seconds. Bid step 5 %, opening bid 60 % of estimate.
- Auction schedule: one auction per 2-hour slot, starting at a seeded minute 0–75 into the slot and open 45 minutes. Lots are rare or legendary, worth at least $250K, and chosen from pieces costing ≤20 % of the player's peak net worth (the five cheapest eligible lots if none qualify). Proxy bidding (`SetMaxBid`) answers rivals one step at a time, live or offline.

## 8. Finance pressure
- **Credit score** 300–850 → rating bands: 800+ AAA, 760 AA, 720 A, 680 BBB, 640 BB, 600 B, 550 CCC, below 550 D.
- **Borrow limit** = `min(24h of net income × rating factor (0.5–2.0), 40% of NW)`.
- **Interest per day** = central-bank rate (0.4–1.2%) + spread (AAA 0.2% … CCC 4.0%).
- **Taxes**: business income progressive brackets 15/22/28/35 % by income per hour (marginal brackets up to $1B/h, $1T/h, $1Qa/h, then open-ended, so the bracket rises with the game stage); capital gains 12 %; property tax as above.
  - **Withholding** (Phase 9): from Chapter 4, tax is withheld from business income as it is earned, at the effective rate of the previous quarter's average hourly income (the first quarter uses the current income). A quarter is 72 game hours. This avoids lump-sum bills the player could not pay. The Hustle is never taxed.
  - **Filing**: at each quarter end a return opens for 24 hours. Auto-filing (the default when the window closes, or always if chosen) is always correct with no savings. Self-filing refunds the deductions earned through play, as a share of the tax withheld: R&D 3 % (own Technology, Pharmaceuticals or Space) plus R&D spending from cards (up to 3 %), depreciation 1 % per property (max 4 %), donations from cards (up to 5 %), green-energy 3 % (own Energy); total capped at 12 %.
  - Aggressive mode claims +5–10 % more with an audit probability of 15–35 % (both shown up front). An audit repays the extra twice over and costs 40 credit points.
- **Credit score** = payment history (starts at 700: +3 per on-time payment, −25 per missed payment, −60 per foreclosure, −40 per audit or restructuring, +1 every 6 h when below 680) + a debt-to-income adjustment (0 if debt ≤ 1 day of income, −20 ≤ 3 days, −50 ≤ 7 days, −80 beyond). Max mortgage LTV by rating: AAA/AA 75 %, A/BBB 60 %, BB/B 50 %, CCC 25 %, D 0 %.
- **Loans**: terms of 1, 3 or 7 days at the rate when taken, repaid in equal payments every 6 hours. A missed payment adds interest plus a 2 % penalty to the loan. Credit line limit = 12 h of income × rating factor. **Restructuring** (offered when payments exceed income) doubles every loan's term. **Phoenix protocol** (net worth below zero for 30 minutes while in debt) sells everything, clears debts (the bank writes off any shortfall), keeps managers, expansions and 10 % of levels, resets the score to 600 and grants a ×3 boost for 2 hours.

## 9. Progression and prestige
- **Legacy gain** at prestige: `ΔLegacy = floor( 4 · (peak_NW / 10^N)^0.42 ) × (1 + Legacy bonuses + challenge bonus)`, with `N = legacyNormalizerLog10` in progression.json. N tracks the Chapter 8 goal so the first prestige always earns ~130 Legacy: 9 in planning, 18 in Phase 10 (goal ≈ $4×10^21), **13.4 since Phase 11** (the late ladder was compressed and the goal is ≈ $9.3×10^16).
- **Legacy income multiplier**: `1 + 0.02 · Legacy^0.9`.
  - Example: first prestige at peak NW ≈ $9.3e16 (the Chapter 8 goal) → ~125 Legacy → ×2.5 income in generation 2.
- **Mission and reward cash** is hours of current income × a chapter scale (Ch1 0.02, Ch2 0.04, Ch3 0.1, Ch4 0.25, Ch5 0.5, Ch6+ 1.0).
- **Family Charter** node thresholds: 10, 25, 60, 150, 400, 1,000, 2,500 … Legacy, plus a generation count.
- **Generation scaling**: each generation's content scales so a typical generation lasts 60–80% of the previous one in real time until generation 6, then it stabilizes. Late-generation goals come from new systems (IPO, Space, collection sets) and challenges.

### Milestone pacing targets

| Milestone | Active player | Idle player |
|---|---|---|
| First business level bought | ≤ 60 s | ≤ 60 s |
| First manager | 5–8 min | 5–15 min (relaxed 2026-11-06: a first manager inside the first session helps idle players) |
| Chapter 1 complete (stocks unlock) | 20–35 min | 45–90 min |
| First property | 35–60 min | 1.5–3 h |
| First luxury item | 1–2 h | 3–6 h |
| Crypto unlock (Chapter 2 end) | 3–6 h | 8–16 h |
| First auction win | day 1–2 | day 2–3 |
| First conglomerate | 18 h–day 2 (Chapter 4 goal; revised in Phase 5, see TUNING_LOG) | day 2–4 |
| First prestige available | day 5–6 | day 8–10 |
| Generation 2 reaches Gen 1 peak | 40–55% of Gen 1 time | 45–60% |
| IPO | Gen 2–3 | Gen 3–4 |
| Generation 10 | ~3 months | ~4–5 months |

"Active" = 3 sessions per day of ~15 minutes (plus day-one check-ins at about 1.5 h and 3.5 h). "Idle" = 2 sessions per day of ~3 minutes (plus day-one check-ins at about 1 h and 4 h). Both use offline catch-up with free cap upgrades. Tuning history: `docs/balance/TUNING_LOG.md`.

### Income-share bands (share of total net income by source, active bot, median seed)

| Day | Businesses | Real estate | Markets (stocks, funds, bonds, crypto) | Luxury (Status-driven savings) |
|---|---|---|---|---|
| 1 | 85–100% | 0–10% | 0–8% | 0–2% |
| 3 | 55–75% | 10–25% | 5–20% | 2–8% |
| 7 | 40–60% | 15–30% | 15–30% | 5–15% |

---

## 10. Headless simulator and bots

Module `:sim` (JVM application). It uses the same `:core` engine and the same content JSON as the app.

### 10.1 Bots (policies over the public `Command` API)
| Bot | Behavior |
|---|---|
| **Idle** | 2 sessions per day, ~3 min each; buys the cheapest payback options; hires managers ASAP; never trades |
| **Active** | 3×15 min sessions; greedy best-payback across all classes; makes mechanic decisions well; plays mini-games at median skill |
| **Cautious investor** | Prefers bonds, index funds and property; never borrows; low-risk business policies |
| **Risky trader** | Concentrates on growth stocks and crypto, shorts in downturns, borrows to the limit |
| **Heavy spender** | The Active policy plus every demo purchase immediately (starter, VIP, boosts, slots) |
| **Poor choices** | Random purchases, overpays at auctions, max loans, ignores taxes (aggressive filing), never hedges |
| *Single-strategy probes* | Businesses-only, Stocks-only, Property-only, Crypto-only, Luxury-heavy (dominance checks) |

### 10.2 Runs
- Standard batch: 6 main bots × 1,000 seeds × the first generation (≤14 simulated days), plus 200 seeds × 3 generations, plus probes × 200 seeds.
- Parallel across CPU cores. The target is a full batch in under 10 minutes on the dev machine (i7-13650HX, 20 threads).
- CI runs a "smoke" batch (6 bots × 20 seeds) with the same assertions on looser tolerances.

### 10.3 Metrics and pass/fail targets
| Metric | Definition | Target |
|---|---|---|
| Milestone times | p10/p50/p90 per bot | p50 inside the pacing table bands |
| Dead zones | Active-bot session minutes with no affordable meaningful action (payback < 3× stage target) | < 5% of session time; no zone > 10 min |
| Runaway growth | log-NW growth rate exceeding the stage band × 1.5 for > 1 h | 0 occurrences at p99 |
| Inflation | Median cash-on-hand ÷ cost of next meaningful purchase | Between 0.2 and 3 most of the time |
| Soft-locks | Poor-choices bot below its previous peak for > 2× the recovery target, or unable to reach the next chapter in 3× target time | 0 at p99.9 |
| Dominance | Each single-strategy probe's NW at day 7 vs Active | Every probe ≥ 15% below Active; every probe still reaches Chapter 4 |
| Viability | Each asset class is the top marginal-ROI choice for the Active bot in ≥ 1 stage | True for businesses, property, markets, luxury |
| Spender gap | Heavy spender vs Active time to first prestige | 25–40% faster, never more than 2× |
| Leaderboard plausibility | Max observed Δlog10(NW)/h across all bots | Feeds the Firestore rule bound (with ×1.5 safety) |

Reports: `docs/balance/<date>-<gitsha>/summary.md` with charts (PNG) and CSV. The tuning log in `docs/balance/TUNING_LOG.md` records each change and its effect.
