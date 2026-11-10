# Arnav Business Tycoon — Game Design Document

> Title: **Arnav Business Tycoon** (chosen by the owner on 2026-11-06).
> Studio: Arnav Games. Platform: Android (sideloaded APK via GitHub Releases). Genre: finance idle tycoon.
> Numbers live in `docs/ECONOMY.md`; counts live in `docs/CONTENT.md`.

---

## 1. Name

**Final name: Arnav Business Tycoon.** Package `com.arnavgames.tycoon`, repo `Arnav-Dugad/Arnav-Business-Tycoon` (GitHub repo names cannot contain spaces). Tagline: *"Build the empire. Pass on the crown."* The name includes the owner's own name, so it does not collide with existing products.

The proposals made during planning are kept below for the record.

Checks run on 2026-11-06: web search for the exact phrase plus "game"/"app", the GitHub repo name under the owner account, and obvious trademark collisions. This is a sanity check, not a legal trademark search.

| # | Name | Meaning | Check result | Verdict |
|---|------|---------|--------------|---------|
| 1 | **Crownfolio** | crown + portfolio; fits the Dynasty theme | No exact-match product, game or company found. Repo name free. | **Recommended** |
| 2 | Heirloom Capital | wealth passed between generations | No exact-match game or app. There is a game studio called "Heirloom Interactive" and an iPad tycoon called "Pocket Capital", so the field is a little crowded. | Good backup |
| 3 | Vault & Vanity | money plus luxury | No match. Leans toward luxury and undersells the business side. | Backup |
| 4 | Ascendant Capital | rising capitalist | No exact match, but the "Capital" tycoon space is crowded (Pocket Capital, Capital Kings, Capital Tycoon). | Not recommended |
| 5 | Gilt Empire | gilded empire | No exact match, but "Gilt" is an existing indie game and a luxury retail brand. | Not recommended |
| – | ~~Legacy Ledger~~ | — | "Ledger" is a major crypto-wallet trademark. | Rejected |

The planning recommendation was Crownfolio; the owner chose Arnav Business Tycoon instead.

---

### 1.1 Real places, parody names (owner decision, 2026-11-06)
The owner asked for the world to feel as real as possible, GTA-style:
- **Places are real**: cities and neighbourhoods use their real names; everything that happens there (prices, cycles, events) is invented.
- **Companies, coins, products and people are parodies**: clear puns on real names, never the exact name or ticker. The content validator still rejects exact real brand names, so every name stays a parody.
- **No real logos, badges or photos of real people.** Logos are procedural; portraits are illustrated.
- The Credits screen states that companies, products and people are parodies and not affiliated with anyone they resemble.
This replaces the original brief's "no real brands, no real public figures" rule. It suits a personal project shared with friends; revisit before any public store release.

## 2. Vision and pillars

A premium-feeling finance tycoon in which you grow a corner kiosk into a multi-generational dynasty. Early tapping quickly gives way to **meaningful decisions**: what to buy, expand, automate, hedge, or gamble on.

1. **Decisions over taps.** Tapping matters for about the first 10 minutes. After that every screen asks a question with a real trade-off.
2. **Everything feeds everything.** Businesses, markets, property, luxury, rivals and the world economy are one connected system. A recession hurts your restaurants, makes property cheaper, and gives you an opening to short a stock.
3. **A goal is always visible.** The "Next Move" card always shows 1–3 concrete goals with rewards. No session ends without a payoff.
4. **Respect the player.** No paid loot boxes, no countdown pressure, no ads, no dead ends. Spending speeds you up but is never required.
5. **A months-long arc.** Story chapters → conglomerates → IPO → Dynasty generations → seasons.

### Target player and session shape
- 3–10 minute sessions several times a day, plus occasional 30-minute "strategy" sessions.
- Session template: collect offline earnings (rolling counter moment) → resolve queued decision cards → complete 1–2 goals → set up something long (a timer, contract or mission) → leave with a reason to come back.

---

## 3. Core loop

```
            ┌──────────── earn cash (businesses, rent, dividends) ───────────┐
            ▼                                                                │
   spend: level up / expand / automate / invest / buy property / luxury     │
            │                                                                │
            ▼                                                                │
   decisions: pricing, contracts, routes, hedges, decision cards, auctions   │
            │                                                                │
            ▼                                                                │
   world reacts: cycles, news, rivals, events ──► new risks and opportunities ┘
            │
            ▼
   milestones: chapters, conglomerates, IPO ──► Dynasty prestige ──► stronger next generation
```

**Minute 0–10 (Hustle).** Tap the kiosk to serve customers; a combo meter rewards rhythm. Buy levels. First manager at roughly 5–8 minutes ends mandatory tapping for that business.
**Minute 10–60 (Founder).** Unlock Restaurants and Logistics, the first expansion timer, the stock market tutorial, the first decision card and the first rival.
**Day 1–3 (Operator).** More business types, property, crypto, the first auction and the first conglomerate on the horizon.
**Day 3–10 (Mogul).** Conglomerates, loans and credit, taxes, rival takeovers, skyscrapers, then the first Dynasty prestige.
**Week 2+ (Dynasty).** Generations, IPO, space, seasons, leagues, collection completion.

---

## 4. Systems overview — what stays, what changes, what is cut

| Requested system | Decision | Why |
|---|---|---|
| Tap frenzy mini-game | **Merged** into the "Hustle" (early tapping) plus an optional 15-second Frenzy, three per day | Avoids a standalone tapping mode nobody returns to; keeps tapping fun and finite |
| Taking your company public | **Merged** into the Merger tree finale (Holding Co → IPO) | IPO becomes the payoff of the business arc, not a separate menu |
| Index funds + bonds | **Merged** into one "Fixed Income & Funds" desk | Both are low-risk parking; one simple screen is better than two thin ones |
| Commodities | **Integrated** with businesses: commodity prices are the real inputs and outputs of Farmland, Mining, Energy, Factory, Logistics and Airline. You can also trade them. | Turns a silo into the glue of the economy |
| Options/derivatives | **Cut** | Too complex for mobile; short selling plus forward contracts give the same "bet against" fun |
| Weekly events + seasons | **Nested**: a 4-week Season contains 4 themed Weekly Events | One progression track instead of two competing ones |
| Asynchronous auctions | **Kept as friend "Sealed Lots"**: weekly sealed-bid auctions that clients resolve deterministically | No server functions are available on the Spark plan; sealed bids work with security rules alone (see ARCHITECTURE §9) |
| Insurance | **Added** | Gives disasters a meaningful pre-decision instead of pure punishment |
| Forward contracts | **Added** (Farmland, Energy) | The hedging decision gives commodities depth without options |
| Board seats | **Added** | Gives stock ownership strategic value beyond price speculation |

---

## 5. Businesses (18 types, each with its own mechanic)

### 5.1 Shared framework
Every business has:
- **Levels** (outlets, capacity, fleet size and so on) bought with cash. Costs grow exponentially; see ECONOMY §3.
- **Scale Tiers** at levels 10/25/50/100/150/250/400. Each unlocks a new *mechanic option*, not just a multiplier (for example, Retail tier 3 unlocks "flagship store"; Airline tier 2 unlocks long-haul).
- **Efficiency (E)** between 0.25× and 2.5×, set by how well the player (or manager) plays the business's mechanic.
- **Manager**: a named fictional person with a portrait, a skill (1–5 stars) and a *policy* (Safe / Balanced / Aggressive). Managers automate collection *and* run the mechanic decisions at their skill level. Players who make the decisions themselves can beat managers, so active play is rewarded and idle play is not punished.
- **Expansions**: timed builds (30 seconds early → 8 hours late) that add a branch, line or route. They can be rushed with Favor (free currency) or Bullion (premium).
- **Slots**: a limited number of active business slots (3 at the start, up to 18 free). Choosing *which* businesses to run is a strategic decision. Mothballing a business keeps its levels but stops income and upkeep.
- **Upkeep**: wages, rent and energy. Businesses can lose money in a recession if run aggressively.

### 5.2 The 18 businesses

| # | Business | Unique mechanic | Key decision | Feeds into | Risk |
|---|---|---|---|---|---|
| 1 | **Retail** | *Footfall and pricing*: outlets placed in map districts; footfall follows the district demand index; a price slider trades volume against margin along a demand curve | Where to open, what price, holiday stock-up | Real estate (own the unit → no rent) | Low |
| 2 | **Restaurants** | *Reputation stars (1.0–5.0)*: quality spend, chef tier and consistency move the rating; rating multiplies covers and the price ceiling; reputation decays without upkeep; review events | Quality versus cost; specials menu | Farmland (cheap produce) | Low–med |
| 3 | **Logistics** | *Routes and fleet*: city-pair routes with demand; assign vans, trucks, ships and cargo planes; utilization and fuel cost (oil price) | Fleet allocation puzzle; hub upgrades | Cuts input costs for Factory, Farmland and Retail | Med |
| 4 | **Farmland** | *Seasons and crops*: each field plants one crop per 3-hour season; yield depends on a weather forecast with a stated confidence; sell at harvest spot price or lock a forward contract at planting | Crop choice; hedge or speculate | Restaurants, commodity market | Med |
| 5 | **Factory** | *Production lines*: multi-stage lines; throughput = slowest stage; inputs (steel, chips, plastics) bought on the market or from your own Mining | Upgrade the bottleneck; buy or make inputs | Retail (own goods), Logistics | Med |
| 6 | **Fashion House** | *Trend calls*: each 6-hour season pick 2 of 5 trend themes; news and media hint at winners; hits sell at a premium, misses go to outlet | Read the news; diversify or go all-in | Retail outlets, Media | Med–high |
| 7 | **Mining** | *Survey and extract*: buy claims; paying for a survey reveals an ore-grade distribution; extraction depletes the deposit; depth versus safety (accident risk) | Survey or gamble; depth | Factory, commodity market | High |
| 8 | **Energy** | *Wells and contracts*: oil and gas wells with decline curves; solar and wind tied to weather; sell at spot or lock supply contracts (fixed price and term) | Hedge versus spot exposure | Discounted energy for Factory, Telecom and Hotels | Med |
| 9 | **Hotels** | *Occupancy and events*: occupancy follows the city tourism index, your airline routes into that city and an events calendar; dynamic room rates; star upgrades | Rate setting; which cities | Airline, Sports | Med |
| 10 | **Bank** | *Balance sheet*: deposits (pay interest), loans (earn interest), a lending-standard dial (prime/standard/subprime) that sets default rates, and a reserve ratio; bank-run risk in recessions if reserves are thin | Spread versus safety | Lowers your own borrowing rate; finances property | Med–high |
| 11 | **Construction** | *Tenders and crews*: sealed bids against rivals for projects (margin versus win chance); assign crews; milestone timers; overrun risk | Bid pricing; crew allocation | Builds your skyscrapers at cost | Med |
| 12 | **Media** | *Audience and slate*: greenlight shows from a slate (genre, quality, cost); audience share drives ad revenue; marketing campaigns boost other businesses' demand; PR softens scandals | Which shows; when to campaign | Demand boost to Retail, Fashion, Tech | Med |
| 13 | **Tech** | *R&D and launches*: allocate R&D to product lines; a hype meter; launch timing against market sentiment and rival launches; S-curve product life cycle | When to launch; sequel or new line | Telecom, Pharma, Space | High |
| 14 | **Telecom & Data** | *Capacity and utilization*: towers and data centers; client contracts; utilization above 95% risks outages; energy costs | Build ahead or run hot | Tech, Space | Med |
| 15 | **Airline** | *Fleet and network*: aircraft types, routes, load factor, fares; maintenance checks ground planes (skipping raises incident risk); fuel | Network design; maintenance discipline | Hotels occupancy, tourism | High |
| 16 | **Pharma** | *Trial pipeline*: candidates move Phase I → II → III → approval with stated odds and costs; patents expire into a revenue cliff | Portfolio balance; which trials to fund | Tech boosts discovery | Very high |
| 17 | **Sports Franchise** | *Season and roster*: a simulated league ladder (matchday every 4 hours); roster and stadium investment; wins build brand and merchandise revenue; championships become trophies in your Collection | Roster spend versus profit | Media rights, Hotels | Med |
| 18 | **Space** | *Missions*: 4–24 hour missions with launch windows; success odds from R&D and reliability upgrades; payloads are satellites (recurring income, boosts Telecom), orbital tourism and asteroid mining (rare commodities) | Launch now or improve odds | Telecom, commodity market | Very high |

Unlock order by story chapter: Ch1 Retail, Restaurants, Logistics · Ch2 Farmland, Factory, Fashion · Ch3 Mining, Energy, Hotels · Ch4 Bank, Construction, Media · Ch5 Tech, Telecom · Ch6 Airline, Pharma · Ch7 Sports, Space.

### 5.3 Merger tree → Conglomerates → Holding → IPO
Businesses can merge into **conglomerates** when every member is at Scale Tier ≥ 3 and their combined valuation passes a threshold. **A business can belong to only one conglomerate**, so the merger tree is a real build choice.

| Conglomerate | Members | Synergy |
|---|---|---|
| Main Street Group | Retail + Restaurants + Farmland | Food costs −30%, footfall +15% |
| Industrial Chain | Mining + Factory + Logistics | Inputs at cost, line throughput +20% |
| Power & Data | Energy + Telecom | Energy at cost, outage risk −50% |
| Leisure Empire | Airline + Hotels + Sports | Tourism +25%, matchday hotel surge |
| Capital Developments | Bank + Construction (+ ≥8 properties) | Mortgage rate −1 notch, build time −25% |
| Style & Story | Fashion + Media | Better trend hints, campaign cost −40% |
| Life Sciences | Pharma + Tech | Trial success +10 percentage points |
| Orbital Ventures | Space + Telecom + Tech | Satellite income ×2 |
| Heartland Commodities | Farmland + Energy + Mining | Better forward-contract terms |

Because members overlap (Telecom appears in two, Tech in two, Farmland in two), the player must choose a path. **Holding Co**: merge any 3 conglomerates into your named holding company. This unlocks the **IPO** (§6.6) and the endgame Fortune List top-10 race.

---

## 6. Investing

### 6.1 Stock market
- **120 parody companies** in 12 sectors (10 each): GTA-style puns on real companies (owner decision 2026-11-06, see §1.1), never the exact real name or ticker, each with a ticker, procedurally generated vector logo, beta, volatility band, dividend policy and a personality trait (for example "serial acquirer" or "meme darling").
- Prices are driven by fundamentals (sector cycle, macro cycle, quarterly earnings), mean-reverting noise and **news-driven jumps**. Model in ECONOMY §5.
- **Earnings season** every 3 real days, with guidance, beats and misses.
- **Board seats**: owning ≥5% of a company gives a sector perk (+5% income for your businesses in that sector); ≥20% (a division stake) makes opening a business in that sector 25% cheaper; holding stock in a rival's flagship blocks that rival's takeover attempts on you (arrives with rivals, Phase 9). You can own at most 49% of a listed company, and stakes are diluted when a new chapter deepens the market.
- **Market depth**: large orders cause slippage scaled by order size against market cap, so you can't dump trillions into one stock.
- **Short selling** (Chapter 5+): margin requirement, borrow fee and automatic forced cover at the margin limit. Losses are capped by liquidation, so a short can never create an unbounded debt.

### 6.2 Fixed Income & Funds desk
- 4 index funds (Total Market, Growth, Defensive, Global): diversified, low effort.
- 8 bonds (government short/long, investment-grade and high-yield corporate) with duration. Prices move inversely to the central-bank rate cycle; high-yield bonds can default in recessions.

### 6.3 Crypto
- 20 parody coins in 4 risk tiers. Hype cycles, staking yields and rare crash or "rug" events for low-tier coins. Each coin's risk rating is shown in plain words.
- Unlocks in Chapter 2. It is meant to be exciting and optional, never the dominant strategy.

### 6.4 Commodities
12 commodities (crude oil, natural gas, gold, silver, copper, iron ore, lithium, rare earths, wheat, coffee, cocoa, timber). Their prices set business input costs and output prices. You can trade spot, and forward contracts are available through Farmland and Energy.

### 6.5 News feed
Headlines are generated from templates naming the game's parody companies, coins, sectors and commodities. Some news is **predictive** (with stated reliability: "Rumor", "Report", "Confirmed"). Reading news is how skilled players beat the market and call fashion trends.

### 6.6 IPO — your Holding Co goes public
- Choose how much to float (10–49%; a ≥51% control guard is enforced) and receive a large cash injection.
- Each quarter you **set guidance**: beat it and the share price rallies; miss it and it drops.
- Activist rivals may buy your float; buybacks and dividends become tools.
- Your company's share price feeds the Fortune List and Dynasty legacy.

---

## 7. Real estate

- **World map** with 6 real cities, unlocking by chapter: Lisbon and London (2), Miami (3), Mumbai (4), New York (5), Dubai (6), and 40 real neighbourhoods (for example Alfama, Mayfair, Brickell, Bandra, SoHo, Palm Jumeirah). Prices, cycles and events are invented. The map is a stylised world map drawn in code.
- Each district has a **price index** on its own market cycle (correlated with its city and the global cycle), plus a rent yield, a trend and gentrification events.
- **Property types**: apartment, townhouse, villa, office floor, retail unit, warehouse, hotel plot, land, penthouse, mixed-use block.
- **Verbs**:
  - *Rent*: set the rent; tenant quality versus vacancy.
  - *Renovate*: a timer plus cost raises value and rent.
  - *Flip*: buy distressed listings at a discount, renovate, sell on a selling timer.
  - *Mortgage*: loan-to-value limit tied to your credit rating.
  - *Insure*: protection against disasters.
- **Tenant events**: late rent, damage, long-lease offers, noise complaints. Each has 2–3 choices.
- **Skyscrapers**: buy CBD land, then build in stages (foundation → structure → floors → fit-out) with a Construction-business discount. Floors are leased individually. A finished landmark raises the district index and your Status.
- Interlocks: your Retail, Restaurant and Hotel outlets placed on *your own* property pay no rent; district cycles move footfall.

---

## 8. Luxury and collections

- **Categories**: cars, jets, helicopters, yachts, islands, mansions, paintings (public-domain museum works), watches, wine, sculptures and antiques, jewelry. Brands and models use parody names (puns on real ones, never the exact name); real logos and badges are never shown.
- **Status**: luxury generates Status points. Status tiers unlock elite deals (lower acquisition prices), exclusive auctions, rival alliances and are a requirement for the IPO. That is luxury's real return.
- **Value behavior**: cars depreciate (except classics); art follows an art-market cycle; watches are stable; yachts carry heavy upkeep. Some items are investments, some are pure Status.
- **Live auctions** once in every 6-hour slot (starting at a varying minute), open for 4.5 hours like an online timed sale, so one always closes before the next opens (1.8.1; was 45 minutes every 2 hours, which a twice-a-day player rarely caught), against 3–5 parody-billionaire rivals with personalities (aggressive, sniper, steady, collector). Every bid extends the close so the hammer falls only after 30 quiet seconds. **Proxy bidding**: leave a maximum and your agent bids for you, one step at a time, even while you are away. Lots are rare or legendary pieces worth at least $250K.
- **Showrooms**: Garage, Vault, Hangar, Marina, Gallery and Residences (added for mansions and islands). Each has a capacity you can expand; only displayed items earn Status. Friends can view them once social features arrive (Phase 13).
- **Collection sets** (30): complete a themed set for a permanent cross-system bonus (for example, "Grand Tourers": +5% Status gain; "Harbor Lights" yachts: +5% Hotels occupancy; "Old Masters": +3% dividend yield).
- **Heirlooms**: at Dynasty prestige you keep 1–3 chosen items.

---

## 9. World

- **Economic cycle** state machine: Expansion → Boom → Slowdown → Recession → Recovery. Phase lengths are seeded and random (roughly 1.5–4 real days each) and announced with leading indicators in the news.
- **Sector events**: tech bubble, oil shock, housing boom, supply-chain crunch, crop blight, fictional "fever season" (a travel slump), and more.
- **Disasters** strike a city (storm, quake, flood). Insured assets recover; uninsured ones take damage. Disasters are never fatal to the save.
- **Decision cards** (163 in Phase 9): 2–5 options with explicit trade-offs and visible odds. For example, a union demands raises: *Accept* (+wages, +productivity), *Negotiate* (70 % deal), *Refuse* (strike risk 40 %). Costs are in hours of your income, so cards stay relevant at every stage, and you can only pick what you can afford in the worst case. Up to 3 cards queue on **the Desk** while you are away; they never resolve themselves. The Negotiate mini-game arrives in Phase 10; until then negotiation options show their odds.
- **Phase 9 notes**: the economic cycle has 4 phases (Expansion, Boom, Slowdown, Recession; built in Phase 6). World events (60) change the income of a sector or of every business for 6–48 hours; 8 disaster events strike a real city and damage uninsured properties there.

---

## 10. Rivals

- A **Fortune List** of 220 billionaires with parody names (no photos of real people), ranked weekly. You climb it.
- Each rival has a name, age, home city, archetype (Shark, Old Money, Disruptor, Collector, Philanthropist, Gambler, Recluse, Heir), favorite sectors, aggression, risk appetite and a **relationship score** with you (−100 to +100).
- **Interactions**: competing auction bids, construction tenders and property bids; acquisition offers for your businesses; joint-venture offers; hostile takeover attempts on undervalued businesses or on your IPO float.
- **Takeover defense**: white knight (an allied rival), buyback, poison pill (costly), negotiate, or accept the premium.
- **Nemesis**: each story chapter promotes one rival to nemesis, with story beats and a finale.
- Rivals are simulated cheaply with a net-worth model plus scheduled actions, not a full simulation.
- **Respect rule**: rival portraits are caricatures of invented people (40 AI portraits for the rivals seen most; monograms for the rest), and negative story beats attach to *companies*, never to a person.
- **Phase 9 notes**: rival fortunes are a pure function of game time (calibrated to the simulator's net-worth curve). Acquisition offers, joint ventures, takeovers, poaching and friendly gestures arrive as decision cards with a named rival. A pending takeover cuts that business's income 10 % until you answer; defences are a share buyback, a poison pill (rating hit), a white knight (needs an ally at relationship 40+), negotiation, or accepting the premium. Construction tenders and property bids against rivals are deferred.

---

## 11. Finance pressure

- **Credit rating**: AAA … CCC/D, computed from debt-to-income, payment history and recent defaults. The rating sets loan rates, mortgage loan-to-value and some deal access.
- **Loans**: term loans, a credit line and mortgages. Repayments come out automatically every 6 real hours. A missed payment cuts the rating.
- **Taxes**: business income tax (progressive), capital-gains tax on realized gains, property tax. Each quarter (3 real days) the player chooses:
  - **Auto-pay**: always correct, zero audit risk, no savings.
  - **Self-file**: a short screen where you claim deductions you *earned* through play (R&D credits, depreciation, donations, green-energy credits). These save 5–12%. An optional *Aggressive interpretation* toggle saves more but carries an audit risk shown up front. An audit means repaying the difference plus a penalty and a rating hit.
- **Never permanently stuck** (hard guarantees, tested by the simulator's poor-choices bot):
  1. The Hustle tap income never goes away, and it scales with your HQ level.
  2. *Restructuring*: if debt service exceeds income, offer longer loan terms (rating drops a notch).
  3. *Phoenix protocol*: if net worth stays below zero for 30 minutes, offer an orderly liquidation that clears debts, keeps Dynasty perks, Heirlooms and 10% of business levels, and grants a 2-hour ×3 "comeback" boost.
  4. Cash can never fall below −(credit line); purchases that would cross that line are blocked with a clear explanation.

---

## 12. Long-term goals

- **Story**: 8 chapters with 7 missions each. A fictional mentor ("The Chairman") guides you and a nemesis opposes you. Chapters gate new systems so complexity arrives gradually.
  1. *The Kiosk* · 2. *Main Street* · 3. *Raw Power* · 4. *Old Money* · 5. *Disruption* · 6. *Altitude* · 7. *Escape Velocity* · 8. *The Crown* (first Dynasty prestige).
- **Next Move card**: always 1–3 goals (chapter mission, a cheap win, a long-term aim).
- **Missions**: 3 daily (from 60 templates) and 5 weekly (from 30 templates). Rerolls are free once a day.
- **Achievements**: 150 in 10 categories, with Bullion and cosmetic rewards.
- **Daily rewards**: a 7-day ladder. A missed day **pauses** the ladder; it never resets it.
- **Weekly events**: themed weeks ("Oil Shock", "Art Fair", "Startup Week") with event goals and cosmetic rewards.
- **Seasons**: 4 weeks with a free track (every gameplay-relevant reward) and a demo-premium track (cosmetics plus small convenience). Season themes rotate through 12.
- **Dynasty (prestige)**: "Pass the Crown" retires your founder.
  - The heir starts over with **Legacy** (computed from peak net worth), which gives a permanent income multiplier.
  - The **Family Charter** is a 60-node tree in three branches (Industry, Finance, Influence). Nodes are unlocked by Legacy thresholds and generation count, and at each tier you **choose 1 of 2–3 options**. It can be re-chosen for free at every prestige, so builds vary.
  - **Heir traits**: pick 1 of 3 randomly offered traits (20 total).
  - **Heirlooms**: keep 1–3 luxury items.
  - **Dynasty challenges**: optional generation modifiers for bonus Legacy (for example "Self-made: no loans", "Purist: no crypto").

---

## 13. Mini-games (all optional, 15–60 seconds, all skippable with "auto-resolve at average outcome")

1. **Negotiation**: 3–5 rounds of offer and counteroffer against a rival personality, with a mood meter and tells. Used for acquisitions, contracts and union cards. Outcome is about ±15% on price.
2. **Auction bidding**: the live auction (§8). Watch rival budgets and tells; snipe or bully.
3. **Market timing**: a 30-second replay of a real in-game price tape. Tap buy and sell for a *practice* position; the score converts to a small daily bonus. It never risks your real money.
4. **Tap Frenzy**: a 15-second combo rush, 3 per day, giving a short income boost.

---

## 14. Social (Firebase, Spark plan)

- **Friends**: add by friend code; requests need acceptance; up to 100 friends.
- **Empire profiles**: net worth, generation, title, top businesses, showroom highlights, achievements.
- **Leaderboards**: global and friends. Every entry shows a **"Boosted" badge** if the player has used demo purchases. A separate **Fair-Play ranking** excludes boosted players.
- **Weekly leagues**: tiers Bronze → Diamond, ranked by *weekly growth* (the log of net-worth change), so new and veteran players compete fairly. The top 20% are promoted and the bottom 20% relegated.
- **Gifts**: one per friend per day. Favor tokens or a small cash gift capped at a share of the recipient's income, so gifts can't be exploited.
- **Sealed Lots**: weekly friend auctions with sealed bids. The highest bid wins and is revealed at close.

---

## 15. Store (simulated purchases only)

- Premium currency: **Bullion**. It is also earned free from achievements, missions, daily rewards, events and the season free track.
- Catalog: starter pack, income boosts (temporary and permanent), timer rushes, extra slots (capped at +3; every slot is also earnable free), longer offline earnings, a VIP pass (30 days), cosmetics (themes, profile frames, HQ skins, card backs).
- Every purchase goes through `PurchaseProvider`. The shipped `SimulatedPurchaseProvider` shows a clearly labeled **"DEMO PURCHASE — no real money is charged"** sheet. No payment details are ever requested.
- **Banned**: paid random rewards, countdown timers on offers, "only N left" pressure, and offers that interrupt gameplay.
- Purchases mark the player as Boosted on leaderboards (§14).

---

## 16. UX, look and feel

- **Design system "Aurum Glass"** (owner request, 2026-11-06): a premium look in the spirit of modern Apple interfaces.
  - **Continuous ("squircle") corners** everywhere, drawn by an original smoothed-corner shape.
  - **Frosted glass**: real backdrop blur on Android 12+ for headers, the floating tab bar, dialogs and banners. Cards use a translucent glass material with light-catching edges. Android 9–11 gets a frosted translucent fallback.
  - **Ambient glow backdrop** (gold, cool and warm light pools) so the glass has colour to refract.
  - **Large-title navigation** that hands over to a compact frosted bar as you scroll; a segmented control and capsule toggles; glossy gold capsule buttons.
  - **Type**: Inter in Text and Display optical cuts. Apple's SF Pro cannot be bundled on Android (licence), and Inter is the closest open alternative.
  - Gains and losses always pair colour with arrows and signs for colour-blind players.
- **Navigation**: 5 tabs — **Empire** (businesses), **Markets**, **Estates** (map), **Collection** (luxury and showrooms), **Dynasty** (story, rivals, social, store, settings). A persistent header shows a rolling net-worth counter, cash, Bullion and an inbox.
- **Moments**: rolling counters, purchase flourish, milestone celebration (confetti in gold leaf, subtle), haptic ticks, satisfying chart draws, a "Pass the Crown" cinematic.
- **Custom icon set** (~140 vector icons on a 24dp grid, 1.75 stroke). No emoji anywhere.
- **Audio**: separate volume and toggle for music, SFX and haptics. Licensed CC0/CC-BY sources are credited in-app.
- **Accessibility**: TalkBack labels on every control, font scale up to 200% without clipping, minimum 48dp touch targets, a reduced-motion setting, color-blind-safe signals.
- **States**: onboarding, helpful empty states ("No properties yet — Lisbon's listings refresh every six hours"), skeleton loading, clear error and retry states.

---

## 17. Out of scope for v1.0
- Real-money payments and ads (never).
- Real-time multiplayer.
- Tablets and foldables get a responsive layout but no dedicated two-pane design.
- Localization: English only in v1.0. Strings are externalized, so translation is possible later.
