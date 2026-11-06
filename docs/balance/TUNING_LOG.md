# Tuning log

Every balance change is a knob file in `tools/content/knobs/` applied with `python -I tools/content/tune.py . <file>`, followed by a simulator run. Times are **median real time** after install. "Active" plays 3×15 min per day (plus day-one check-ins at 1.5 h and 3.5 h); "idle" plays 2×3 min per day (plus day-one check-ins at 1 h and 4 h).

## 2026-11-06 — Phase 4, Chapter 1–2 content (retail, restaurants, logistics)

| Step | Change | Active first manager / Ch1 / Ch2 | Idle first manager / Ch1 / Ch2 | Verdict |
|---|---|---|---|---|
| baseline | Phase 2 starting values | 1 m / 2 m / 2 m | 2 m / never / never | Far too fast for active play. Idle bot bug: it valued only managed income, so it never opened Restaurants |
| t01 | Cost growth 1.085 → 1.13–1.14; higher unlock, manager and expansion prices; taps pay 0.12 s of income instead of 0.5 s; idle bot fixed | 1 m / 2 m / 45 m | 1 m / 2 m / 11.3 h | Still far too fast. Scale Tiers ×2 at levels 10 and 25 make Retail pay back in seconds |
| t02 | Retail revenue ÷2.4, early tiers ×1.5, growth 1.15; later businesses' costs scaled faster than revenue | 3 m / 12 m / 5.6 h | 4 m / 11.9 h / never | Active close. Idle slowness was the 12-hour gap after install, not the economy |
| t03 | First manager $6K; Restaurants unlock $12K; Chapter 1 income goal $120 → $300/s; idle and poor-choices bots get day-one check-ins | 12 m / 20 m / 12.5 h | 1.1 h / 4.0 h / — | Overshot |
| t04 | First manager $4K; Logistics unlock $150K; Logistics first manager $2M | 9 m / 20 m / 12.5 h | 10 m / 1.0 h / 4.0 h | Idle now faster than active at Chapter 2 because of session timing |
| t05 | Active bot gets day-one check-ins; Chapter 2 rewards cut (they fed the next goals); net-worth goal $1M → $5M | 9 m / 20 m / 3.7 h | 10 m / 1.0 h / 12.0 h | 8 of 9 targets pass |
| t06 | First manager $3.3K | 3 m / 19 m / 3.5 h | 9 m / 1.1 h / 12.0 h | Full batch (800 runs, 0 errors): 7 of 9 pass. $3.3K crosses the active bot's "hire within 20 minutes of income" threshold, so the time jumps from 9 to 3 minutes |
| t07 | First manager $3.7K | 8 m / 20 m / 3.5 h | 10 m / 1.0 h / 12.0 h | **All targets pass** (10-seed check). Final report: `2026-11-06-phase4/summary.md`. The active values sit on the band edges, so watch them when Phase 5 content lands |

### Target changes
- I briefly widened the idle bands during t04 (Chapter 1 to 1.5–5 h). At t05 the observed idle times sat inside the **original** ECONOMY.md bands, so the original bands were restored. **Apart from the one change below, no target has been loosened.**
- `idle first_manager` was relaxed from 8–15 min to **5–15 min**. A first manager inside the first session is good for idle players (it is what lets the game earn while they are away).

### Known limits (expected at this content level)
- After Chapter 2, only three businesses exist, so growth saturates. Dead session minutes and stalls are high late in day 2. More businesses arrive in Phase 5 (and markets, property and luxury after that), which give the money somewhere to go.
- `risky_trader` and `heavy_spender` cannot be simulated until markets (Phase 6) and the store (Phase 14) exist.
