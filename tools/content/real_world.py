"""Real-world money (owner decision 2026-11-09): rebases every absolute price that is not produced by the business
ladder, in place and idempotently. Run after tools/content/regen_economy.sh.

- Headquarters: the first five levels keep their tuned prices; after that each level costs x1.7 (a $7B campus at
  level 25, not $6 quintillion).
- Holding company fee: $20B.
- Stock market: companies have their real size at every chapter (no "the market deepens a millionfold").
- Rivals: real billionaires grow a few percent over a generation, not a quadrillion-fold.
- Legacy: normalised on the new final goal (~$2.7e11).

Usage: python -I tools/content/real_world.py <repo-root>
"""
import json
import sys
from decimal import Decimal
from pathlib import Path


def load(p: Path):
    return json.loads(p.read_text(encoding="utf-8"))


def save(p: Path, d) -> None:
    # Keep each file's own indentation and key order so diffs stay readable.
    orig = p.read_text(encoding="utf-8")
    second = orig.split("\n")[1]
    indent = (len(second) - len(second.lstrip(" "))) or 2
    sort = orig == json.dumps(json.loads(orig), indent=indent, ensure_ascii=False, sort_keys=True) + "\n"
    p.write_text(json.dumps(d, indent=indent, ensure_ascii=False, sort_keys=sort) + "\n", encoding="utf-8", newline="\n")


WORDS = {"nw_3": "$100", "nw_5": "$1,000", "nw_7": "$10,000", "nw_9": "$100,000", "nw_11": "$1 million", "nw_13": "$10 million",
         "nw_15": "$100 million", "nw_17": "$1 billion", "nw_19": "$10 billion", "nw_21": "$100 billion", "nw_23": "$1 trillion"}


def money_words(log10: float) -> str:
    v = 10 ** log10
    for unit, name in ((1e12, "trillion"), (1e9, "billion"), (1e6, "million"), (1e3, "thousand")):
        if v >= unit:
            n = v / unit
            return f"${n:g} {name}" if n >= 10 or n == int(n) else f"${n:.1f} {name}"
    return f"${v:.0f}"


def main(repo: Path) -> None:
    bal_p = repo / "content/balance/balance.json"
    bal = load(bal_p)
    for h in bal["hqLevels"]:
        lvl = h["level"]
        cost = Decimal(0) if lvl == 1 else Decimal(500) * Decimal(5) ** min(lvl - 2, 4) * Decimal("1.7") ** max(0, lvl - 6)
        h["cost"] = f"{cost.quantize(Decimal('0.01'))}"
    bal["holdingFee"] = "20000000000.00"
    save(bal_p, bal)

    # Mergers that involve the big later businesses need Scale Tier 2 (level 25): tier 3 on a bank or an airline would
    # mean trillions of dollars of expansion, which no real fortune covers.
    bz = {b["id"]: b for b in load(repo / "content/data/businesses.json")}
    cg_p = repo / "content/data/conglomerates.json"
    cg = load(cg_p)
    for c in cg:
        c["requiredTier"] = 2 if any(bz[m]["chapter"] >= 4 for m in c["members"]) else 3
    save(cg_p, cg)

    mk_p = repo / "content/data/markets.json"
    mk = load(mk_p)
    mk["config"]["depthScaleByChapter"] = [1] * len(mk["config"]["depthScaleByChapter"])
    save(mk_p, mk)

    w_p = repo / "content/data/world.json"
    w = load(w_p)
    top = max(w["rivals"], key=lambda r: r["base"])
    curve = w["config"]["rivalCurve"]
    # The richest grows about 12% (log10 +0.05) by day 4.3, then slowly: real fortunes move like that.
    curve["earlyPerDay"] = round(0.05 / (top["growth"] * curve["kneeDays"]), 5)
    curve["latePerDay"] = 0.004
    # Progressive income tax on real-world incomes (per hour of income): most of the climb pays 15-22%, a world-scale
    # empire pays the top rate.
    w["config"]["finance"]["taxBrackets"] = [
        {"upToPerHour": "100000000.00", "rate": 0.15}, {"upToPerHour": "1000000000.00", "rate": 0.22},
        {"upToPerHour": "10000000000.00", "rate": 0.28}, {"upToPerHour": None, "rate": 0.35},
    ]
    save(w_p, w)

    pr_p = repo / "content/data/progression.json"
    pr = load(pr_p)
    final = next(Decimal(m["goal"]["amount"]) for c in load(repo / "content/data/chapters.json") for m in c["missions"] if m["id"] == "c8_networth")
    # Same offset as before (13.4 vs the old 9.3e16 goal): the first prestige at the final goal pays ~130 Legacy.
    pr["config"]["legacyNormalizerLog10"] = round(float(final.log10()) - 3.57, 2)
    # Wealth achievements on the real ladder (ids kept so earned ones stay earned).
    wealth = {"nw_3": (2, "First hundred"), "nw_5": (3, "First thousand"), "nw_7": (4, "Five figures"), "nw_9": (5, "Six figures"),
              "nw_11": (6, "Millionaire"), "nw_13": (7, "Ten million"), "nw_15": (8, "Hundred million"), "nw_17": (9, "Billionaire"),
              "nw_19": (10, "Ten billion"), "nw_21": (11, "Hundred billion"), "nw_23": (12, "Trillionaire")}
    for ach in pr["achievements"]:
        if ach["id"] in wealth:
            ach["goal"]["log10"], ach["title"] = wealth[ach["id"]]
            ach["description"] = f"Reach a net worth of {WORDS[ach['id']]}."
    save(pr_p, pr)

    me_p = repo / "content/data/mentor.json"
    me = load(me_p)
    for line in me["lines"] if isinstance(me, dict) and "lines" in me else me:
        if line.get("id") == "m_c4_networth":
            line["text"] = "Ten billion. There's no rulebook up here. Write a good one."
        if line.get("id") == "tip_4_10":
            line["text"] = "Ten billion. Somewhere, a rival is drawing up plans about you. Draw yours first."
    save(me_p, me)
    print(f"real-world: HQ L25 ${bal['hqLevels'][-1]['cost']}, final goal ${final}, legacy N {pr['config']['legacyNormalizerLog10']}")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
