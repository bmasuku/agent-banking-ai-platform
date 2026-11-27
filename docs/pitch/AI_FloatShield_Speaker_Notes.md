# AI FloatShield — Speaker Notes (7-slide pitch)
**Deck:** `docs/pitch/AI_FloatShield_7_Slide_Pitch.pptx`  
**Tagline:** Keep agents liquid. Keep risk explainable.
**Timing guide:** ~5 minutes slides + ~7–10 minutes live demo.

---

## AI wow moments (say these; show these)

Use these lines when you need a punch — especially on slides 3 and 5 and during the live demo. Prefer the AI ones; non-AI differentiators are secondary.

### Must-hit AI wow moments

1. **Explainable fraud, not a red flag**  
   Open the seeded **$1,200 sudden cash-out**. Read the **reason codes**, **feature contributions**, and plain-language **explanation** out loud. Line to say: *“Judges can audit why the model fired — probability, typology, and drivers — in the same screen.”*

2. **Structuring that a human would miss**  
   David’s **below-threshold burst** (many small deposits) + open investigation. Then open **Anomalies**: robust z / Poisson / Isolation Forest signals, **peer comparison**, and **fraud alerts enriched into the anomaly view**. Line: *“We catch patterns, not just big tickets.”*

3. **Hours-to-stockout float forecast**  
   On an agent dashboard: predicted e-float / cash net flow, **hours to e-float stockout**, and **recommended top-up**. Line: *“Liquidity is predicted before the customer is turned away.”*

4. **AI-driven rebalancing, two paths**  
   Manager **pool replenish** plus **LP-style peer swap / pool top-up recommendations** (`float/optimise`) — then the **private peer exchange** that actually moves float. Line: *“The AI recommends redistribution; the ledger executes it.”*

5. **Credit scorecard you can defend**  
   Score **0–1000**, factors, and **APPROVED / REJECTED / MANUAL_REVIEW** — Mary strong vs David weak. Line: *“Advances are gated by an explainable scorecard, not a gut feel.”*

6. **Assistant that refuses to be dangerous**  
   Ask a balance / risk question → grounded answer from **read-only tools** using the caller’s JWT. Then ask it to transfer or approve → **guardrail refusal**. Line: *“It can explain the books; it cannot move money.”*

7. **Governance honesty**  
   Every AI call returns **prediction_id**, **model_version**, **confidence**, and a governance block (`PREDICTION`, `is_trained_model: false` for serving models). Humans can **record decisions against a prediction**. Line: *“We don’t fake ‘production-trained’ models — and every score is replayable.”*

### Secondary wow (product, supports the AI story)

8. **Private float exchange** — anonymous board → match → reveal → chat → dual verification tokens.  
9. **Double-entry ledger** — every demo balance was posted; trial balance balances.  
10. **In-person cash** — cash-in / cash-out force distance 0 so geo noise doesn’t false-flag legitimate customers.

### One-breath “AI stack” soundbite (slide 3)

> “Five AI surfaces, one rule: every score is explained and persisted — fraud typology, anomaly ensembles, float forecast and LP rebalancing, credit scorecard, and a read-only, role-scoped assistant.”

---

## Slide 1: Title

SPEAKER NOTES — Slide 1 (Title) · ~30–45 seconds
Open with the name and tagline once: “AI FloatShield — keep agents liquid, keep risk explainable.”
One sentence on what it is: an AI platform that predicts and rebalances agent float and flags fraud with explanations, on top of a real double-entry ledger—not a mock UI.
Plant one AI wow: “In the demo you’ll see a $1,200 spike with reason codes you can read — and an assistant that refuses to move money.”
Tell them the live demo is already running; this deck is the map, then we show the product.
Do not oversell guarantees—invite scrutiny.

---

## Slide 2: Problem → solution

SPEAKER NOTES — Slide 2 · ~60–75 seconds
Pain first (15s): liquidity turnaways + invisible fraud.
Solution (40s): one stack—agents/managers on mobile; risk/finance/admin on web; AI for prediction, rebalancing, fraud, assistant; all posting through real services.
Tease AI wow: “Float gets hours-to-stockout before the queue forms; fraud gets typology reasons, not a silent block.”
Close (10s): “Every balance you see was posted by the ledger—not hard-coded for the demo.”

---

## Slide 3: AI capabilities

SPEAKER NOTES — Slide 3 · ~60–75 seconds
Lead with the one-breath soundbite (five AI surfaces + explainability).
Then hit four wow beats fast:
  • Fraud — reason codes + feature contributions ($1,200 demo)
  • Anomaly — structuring / peer outliers / Isolation Forest ensemble
  • Float — forecast + hours-to-stockout + LP peer/pool optimise
  • Credit + Assistant — defendable scorecard; tools GET-only, guardrails refuse writes
Close: “We’ll prove each of these live — especially explainability and the assistant refusal.”

---

## Slide 4: Architecture

SPEAKER NOTES — Slide 4 · ~45 seconds
Keep it crisp—judges want to know it’s real software.
Point at the four layers; stress double-entry + Flyway + Docker + separate FastAPI AI service.
AI wow bridge: “Fraud, forecast, anomaly, credit, and assistant are real HTTP contracts — every call stores a prediction_id for governance.”
Offer: “We can open Swagger or the repo structure after the demo if you want depth.”
Don’t linger—demo is the proof.

---

## Slide 5: Standout + demo

SPEAKER NOTES — Slide 5 · ~75 seconds (then transition to live demo)
This is your differentiator slide. Walk the privacy model slowly once (anonymous → reveal → tokens).
Then announce the demo path; don’t do every persona—hit these wow moments in order:
  1) Private exchange anonymity → reveal → tokens (product wow)
  2) Agent dashboard: hours-to-stockout / recommended top-up (AI float wow)
  3) Risk: $1,200 sudden cash-out — read reason codes aloud (AI fraud wow)
  4) David structuring + Anomalies enriched with fraud alerts (AI pattern wow)
  5) Assistant: grounded question, then a prohibited transfer → refusal (AI guardrail wow)
Optional if time: credit Mary vs David; finance trial balance.
Say out loud: “Demo was reset; accounts use Demo@2026!”
Put the clicker down and switch to the product.

---

## Slide 6: Judging criteria

SPEAKER NOTES — Slide 6 · ~45 seconds
Use only if judges ask “why you?” or as a 30-second recap after demo.
Hit Innovation + Responsible AI hardest—map criteria to wow moments:
  • Innovation → LP rebalancing + private exchange + anomaly ensemble
  • Responsible AI → explanations, prediction_id governance, assistant refuses writes, honest is_trained_model
  • Technical → real ledger + separate AI microservice + persisted scores
If time is short, skip verbally and leave the slide on screen while Q&A starts.

---

## Slide 7: Close

SPEAKER NOTES — Slide 7 (Close) · ~20–30 seconds + Q&A
Repeat tagline once. Invite AI-specific probes: “Ask us to open any fraud reason code, an anomaly explanation, a prediction_id, or make the assistant try something unsafe.”
Have backup tabs ready: Swagger AI endpoints, flagged $1,200 detail, David anomalies, agent forecast card, trial balance, assistant chat.
If asked about training data: honest—rules + statistical / ensemble models with explanations; serving models are not claimed as production-trained; demo uses seeded history through real services.
Stay humble on “winning”—focus on verifiable capability.

---

## Live demo cheat sheet (after slide 5)

| Moment | Login | Show | Why it wows |
|--------|-------|------|-------------|
| Float forecast | `agent1` / `agent2` | Dashboard prediction, hours-to-stockout, recommended top-up | AI predicts liquidity before stockout |
| Cash-in / cash-out | `agent1` | Live balances + quote explanation | Real ledger + in-person distance 0 |
| Private exchange | `agent3` then `agent2` | Anonymous board → match → chat → tokens | Privacy + real float move |
| AI rebalance context | `manager` | Map / replenish / exchange board | Human + AI redistribution path |
| Fraud explainability | `risk` | Pending **$1,200** — reason codes, features, explanation | Defendable AI |
| Structuring + anomalies | `risk` | David alerts + Anomalies (ensemble + fraud enrichment) | Pattern AI, not only amount |
| Credit scorecard | `risk` / agent profiles | Mary ~760 vs David weak / factors | Explainable underwriting |
| Assistant guardrails | any role | Grounded Q → then “transfer $50” → refusal | Responsible AI |
| Books | `finance` | Trial balance / advances | Ledger proof |

Password for all: `Demo@2026!`  
Reset: `./reset-demo.sh`
