# Balance report

Commit `local` · 2 bots × 3 seeds × 10 days · 1489.1 s on 3 threads

Runs: 6 · errors: 0

## Targets (median real time)

| Bot | Milestone | Target | p10 | p50 | p90 | Reached | Result |
|---|---|---|---|---|---|---|---|
| active | first_level | 0.0 m–1.0 m | 1.0 m | 1.0 m | 1.0 m | 3/3 | PASS |
| idle | first_level | 0.0 m–1.0 m | 1.0 m | 1.0 m | 1.0 m | 3/3 | PASS |
| active | first_manager | 5.0 m–8.0 m | 7.0 m | 7.0 m | 7.0 m | 3/3 | PASS |
| idle | first_manager | 5.0 m–15.0 m | 10.0 m | 10.0 m | 10.0 m | 3/3 | PASS |
| active | chapter1_done | 20.0 m–35.0 m | 22.0 m | 22.0 m | 22.0 m | 3/3 | PASS |
| idle | chapter1_done | 45.0 m–1.5 h | 1.0 h | 1.0 h | 1.0 h | 3/3 | PASS |
| active | chapter2_done | 3.0 h–6.0 h | 3.3 h | 3.7 h | 3.7 h | 3/3 | PASS |
| idle | chapter2_done | 8.0 h–16.0 h | 4.0 h | 12.0 h | 12.0 h | 3/3 | PASS |
| active | first_conglomerate | 18.0 h–2.0 d | 24.6 h | 24.7 h | 24.7 h | 3/3 | PASS |
| idle | first_conglomerate | 2.0 d–4.0 d | 2.5 d | 2.5 d | 2.5 d | 3/3 | PASS |
| active | chapter8_done | 5.0 d–6.0 d | 8.2 d | 8.2 d | 8.2 d | 1/3 | **FAIL** |
| idle | chapter8_done | 8.0 d–10.0 d | — | — | — | 0/3 | **FAIL** |

## All milestones (p50, with p10–p90)

| Milestone | active | idle |
|---|---|---|
| first_level | 1.0 m (1.0 m–1.0 m) 3/3 | 1.0 m (1.0 m–1.0 m) 3/3 |
| first_manager | 7.0 m (7.0 m–7.0 m) 3/3 | 10.0 m (10.0 m–10.0 m) 3/3 |
| restaurant_open | 20.0 m (20.0 m–20.0 m) 3/3 | 1.0 h (1.0 h–1.0 h) 3/3 |
| chapter1_done | 22.0 m (22.0 m–22.0 m) 3/3 | 1.0 h (1.0 h–1.0 h) 3/3 |
| logistics_open | 1.7 h (1.3 h–1.7 h) 3/3 | 4.0 h (1.0 h–4.0 h) 3/3 |
| networth_10k | 5.0 m (5.0 m–5.0 m) 3/3 | 6.0 m (6.0 m–6.0 m) 3/3 |
| networth_1m | 1.8 h (1.5 h–1.8 h) 3/3 | 4.0 h (4.0 h–4.0 h) 3/3 |
| hq_level_5 | 22.0 m (22.0 m–22.0 m) 3/3 | 1.0 h (1.0 h–1.0 h) 3/3 |
| chapter2_done | 3.7 h (3.3 h–3.7 h) 3/3 | 12.0 h (4.0 h–12.0 h) 3/3 |
| networth_100m | 5.7 h (5.7 h–5.7 h) 3/3 | 24.0 h (24.0 h–24.0 h) 3/3 |
| networth_10b | 24.7 h (24.6 h–24.7 h) 3/3 | 2.5 d (2.0 d–2.5 d) 3/3 |
| chapter3_done | 12.6 h (12.5 h–12.6 h) 3/3 | 36.0 h (36.0 h–36.0 h) 3/3 |
| chapter4_done | 35.4 h (29.6 h–35.4 h) 3/3 | 3.5 d (3.5 d–3.5 d) 3/3 |
| chapter5_done | 47.4 h (36.6 h–47.4 h) 3/3 | 4.0 d (4.0 d–4.0 d) 3/3 |
| chapter6_done | 47.4 h (36.6 h–47.4 h) 3/3 | 4.0 d (4.0 d–4.0 d) 3/3 |
| chapter7_done | 2.0 d (47.4 h–2.0 d) 3/3 | 5.5 d (5.0 d–5.5 d) 3/3 |
| chapter8_done | 8.2 d (8.2 d–8.2 d) 1/3 | — (—–—) 0/3 |
| first_conglomerate | 24.7 h (24.6 h–24.7 h) 3/3 | 2.5 d (2.5 d–2.5 d) 3/3 |
| holding_formed | 2.0 d (47.4 h–2.0 d) 3/3 | 5.5 d (5.0 d–5.5 d) 3/3 |

## Health

| Bot | Dead session minutes | Longest stall (p90) | Final net worth (p50) | Cash ÷ next cost (median sample) |
|---|---|---|---|---|
| active | 62.5% | 17.0 h | $2.60Sx | 224804554414.54 |
| idle | 25.6% | 24.0 h | $518Qi | 449757213.54 |

Dead session minute = an active-session minute in which the bot found nothing worth buying. Stall = time without 1% net-worth growth.

![Net worth over time](networth.svg)

Bots not yet simulated: `risky_trader` (needs markets, Phase 6) and `heavy_spender` (needs the store, Phase 14).
