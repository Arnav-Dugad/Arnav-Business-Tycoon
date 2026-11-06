# Arnav Business Tycoon — Content Inventory

Every category lives in `content/data/<category>.json` and is checked by the validator (ARCHITECTURE §3.3). The **Min** column is enforced by tests and the release gate; **Target** is what v1.0 ships. Photo counts feed the art budget in ART_PIPELINE.md.

## 1. Businesses and operations
| Category | Min | Target | Photos | Notes |
|---|---|---|---|---|
| Business types | 18 | 18 | 18 heroes | Each has a unique mechanic module (GDD §5) |
| Scale-tier formats (per business × 7 tiers) | 126 | 126 | 54 (3 per business: early/mid/late look) | Tier name, unlock text, mechanic option |
| Managers | 72 | 90 | portraits shared with the people pool | 4–5 per business, traits + policy |
| Mechanic sub-content | — | see right | — | 12 crops, 10 product lines, 24 routes, 30 TV shows, 20 drug candidates, 16 missions, 12 aircraft types, 8 fleet types, 10 fashion trends, 12 ore types, 10 energy assets, 20 tender templates |
| Conglomerates | 9 | 9 | 9 | Merger tree (GDD §5.3) |
| HQ levels | 25 | 25 | 5 office photos | Slots, offline cap, Hustle power |

## 2. Markets
| Category | Min | Target | Photos | Notes |
|---|---|---|---|---|
| Sectors | 12 | 12 | — | Each has a cycle profile |
| Stocks | 100 | 120 | 0 (procedural vector logos) | Original names and tickers; validated against a real-ticker/brand blocklist |
| Index funds | 4 | 4 | — | |
| Bonds | 6 | 8 | — | |
| Crypto coins | 15 | 20 | 0 (procedural logos) | 4 risk tiers |
| Commodities | 10 | 12 | 12 (stock photos of raw materials) | |
| News templates | 250 | 320 | — | Sector, company, macro, rumor; with reliability levels |

## 3. Real estate
| Category | Min | Target | Photos | Notes |
|---|---|---|---|---|
| Cities | 6 | 6 | 6 skylines | Fictional cities on an original illustrated map |
| Districts | 36 | 40 | 40 | Cycle parameters, yield, trend |
| Property types | 10 | 10 | 40 (4 variants each) | |
| Listing templates | 120 | 150 | reuse type photos | Procedurally combined with districts |
| Skyscraper designs | 6 | 8 | 8 | Staged builds |
| Tenant events | 30 | 40 | — | 2–3 choices each |

## 4. Luxury and collections
| Category | Min | Target | Photos | Notes |
|---|---|---|---|---|
| Cars | 50 | 60 | 60 | Fictional makes and models; badge-free photos |
| Jets | 14 | 16 | 16 | |
| Helicopters | 6 | 8 | 8 | |
| Yachts | 16 | 20 | 20 | |
| Islands | 8 | 10 | 10 | |
| Mansions | 16 | 20 | 20 | |
| Paintings | 70 | 80 | 80 | Public-domain museum works (real title/artist shown, as required by attribution) |
| Watches | 24 | 30 | 30 | Fictional makers |
| Wine | 15 | 20 | 20 | Fictional estates |
| Sculptures and antiques | 15 | 20 | 20 | Public-domain museum objects where possible |
| Jewelry and gems | 12 | 15 | 15 | |
| **Luxury total** | **246** | **299** | **299** | |
| Collection sets | 25 | 30 | — | Cross-system bonuses |
| Showroom types | 5 | 5 | 5 backgrounds | Garage, Hangar, Marina, Gallery, Vault |
| Trophies (sports championships) | 8 | 12 | vector | |

## 5. World and rivals
| Category | Min | Target | Photos | Notes |
|---|---|---|---|---|
| Economic cycle phases | 5 | 5 | — | |
| World and sector events | 50 | 60 | 20 mood photos | |
| Decision cards | 150 | 160 | — | 2–3 options with explicit trade-offs |
| Rivals | 200 | 220 | 220 portraits | Fictional; 8 archetypes; portraits used respectfully (GDD §10) |
| Rival archetypes | 8 | 8 | — | |
| Negotiation personalities | 8 | 8 | — | |

## 6. Progression
| Category | Min | Target | Notes |
|---|---|---|---|
| Story chapters | 8 | 8 | |
| Story missions | 50 | 56 | |
| Daily mission templates | 50 | 60 | |
| Weekly mission templates | 25 | 30 | |
| Achievements | 140 | 150 | 10 categories |
| Daily reward ladder steps | 7 | 7 | Pause, never reset |
| Weekly event templates | 12 | 16 | |
| Season themes | 8 | 12 | 30 tiers each (free + demo-premium) |
| Family Charter nodes | 54 | 60 | 3 branches, choice tiers |
| Heir traits | 18 | 20 | |
| Dynasty challenges | 10 | 12 | |
| Mentor and nemesis dialogue lines | 200 | 250 | |

## 7. Store and cosmetics
| Category | Min | Target | Notes |
|---|---|---|---|
| Store products | 20 | 25 | All simulated |
| Cosmetics (themes, frames, HQ skins, card backs, titles) | 30 | 40 | |
| Avatar options for the player | 24 | 30 | Illustrated (not photos of real people) |

## 8. UI assets
| Category | Target | Notes |
|---|---|---|
| Custom icons | ~140 | Original SVG → Android vector drawables |
| Fonts | 1 family, 5 cuts | Inter Text (400/500/600) + Inter Display (600/700), SIL OFL; Latin subset, 244 KB |
| Music tracks | 4 | Menu, play ×2, Dynasty theme; CC0/CC-BY; OGG ≈ 1.2 MB each |
| Sound effects | ~40 | CC0 sources |
| Illustrated world map | 1 + 6 city maps | Original vector art drawn for this game |

## 9. Photo totals (input to the size budget)
| Group | Count |
|---|---|
| Luxury | 299 |
| Real estate (cities, districts, types, skyscrapers) | 94 |
| Businesses (heroes + tiers + conglomerates + HQ) | 86 |
| Commodities + events + showrooms | 37 |
| Rival + manager portraits (shared pool) | 220 |
| **Total** | **~736** |

Budget: heroes at 1080 px wide, quality 78 (~70 KB avg); thumbnails at 360 px (~14 KB); portraits at 320 px (~12 KB). Estimated total ≈ 30 MB, inside the 32 MB image budget. If it runs over, heroes drop to 900 px.
