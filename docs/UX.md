# UX principles (2026-11-09)

These are the rules every page follows. Each comes from a source that was read for this work (listed at the end). When a page breaks a rule, fix the page, not the rule.

## The rules
1. **Respond within 0.1 s.** A tap shows its result at once: the coin, the number, the tick. Anything slower than 1 s gets a "working" state.
2. **Make feedback as loud as the event matters.** There are three strengths: a subtle haptic and number for routine events, a chip or banner for notable ones, and a full celebration only for rare milestones (chapter, auction won, crown). Badges appear only when the player can act on them.
3. **Show what an action will do before the player commits.**
   - Buy buttons show the cost and the gained income ("Buy ×10 · $1.2K", "+$4.10/s income").
   - Loans show the repayment schedule.
   - Desk choices show their effects.
4. **Prefer undo to confirmation dialogs.**
   - Confirm only serious or irreversible actions.
   - The title names the item and the consequence ("Retire the founder?").
   - Buttons say the outcome, and there is no default "Yes".
5. **Teach in play, one thing at a time.**
   - A first-visit tip appears when a feature is first met, and never two teaching cards at once. Markets shows the starter card instead of its tip until the first investment.
   - No swipe-through tutorials.
   - Show locks before keys: locked things appear with the condition that opens them.
6. **Progressive disclosure.** Lead with the 1–2 numbers that drive the next decision and fold the rest away (for example, the credit score's "How it works"). Use at most two levels.
7. **One primary action per pushed screen, in a fixed place.** GlassScaffold's `bottomBar` pins it to the bottom edge. The business page's Buy bar is the model.
8. **Juice amplifies; it does not replace.** Use easing, short durations and cause-and-effect motion. Reduce motion turns it all off, and the numbers still update.
9. **Haptics are crisp, never buzzy.** Map each event to a specific constant by importance (AppRoot). Counter ticks are capped at one every 45 ms, so multi-finger tapping never blurs into a buzz.
10. **Touch targets are at least 48 dp. Never use colour alone:** gains and losses carry an arrow and a sign (ChangeLabel).
11. **Write in plain words.** No "Something went wrong" and no "Are you sure?". Say what happened and what to do next.
12. **No dark patterns.**
    - A missed day pauses the daily ladder; it never resets it.
    - No paid random boxes and no countdown pressure.
    - Demo purchases are labelled as demos.

## How the pages apply them
- **Counter:** only the cash, the price per customer and the combo are shown. Your best business is painted faintly into the stage. The shader is GPU-only and has a gradient fallback.
- **Empire:** the next-goal card is a painting with one button. Business cards show the tier painting, the till, and the buy cost with the income it adds. Locked businesses and mergers show their conditions.
- **Business page:** a sticky Buy bar sits at the bottom (tap ×1 to cycle the amount). Expansions show as painted thumbnails with a check when done.
- **Markets:** arrows and signs on every change. News links to the asset it moved. With nothing held, a single starter card replaces the tip.
- **Estates:** a city list sits under the map, so panning is never required. District rows are painted, with a signed change arrow and one-line labels that never wrap.
- **More:** grouped into "Play" and "You".
- **Dynasty:** the painted family seat, with your avatar set into it.
- **Desk:** each decision is headed by the painting of the business it concerns.
- **Finance:** the headline score and the points to the next rating come first; the explanation is folded away.
- **Daily Ledger:** the earned number leads, and a world event's painting heads the stories.

## Sources
- NN/g: response times, the ten heuristics, confirmation dialogs, mobile onboarding, progressive disclosure, animation purpose, empty states, the aesthetic-usability effect.
- Apple HIG: feedback, motion, designing for games.
- Android: haptics principles, accessibility API defaults, notification permission.
- W3C WCAG 2.2: animation from interactions, dragging movements.
- Celia Hodent, onboarding (The Gamer's Brain).
- Game Developer: "Squeezing more juice", "The math of idle games III".
- deceptive.design: monetisation dark patterns.
- arXiv 2412.05039: streak dark patterns.
- Steven Hoober, UXmatters: designing for fingers and touch.
