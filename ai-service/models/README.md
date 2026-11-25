# Model artefacts

Serving models in this service are **deterministic rules, scorecards, statistical forecasters and a request-time Isolation Forest**. They are not pre-trained classifiers loaded from disk.

- `evaluation/` — JSON metrics produced by `training/evaluate_*.py` against synthetic labelled samples. These measure how the hand-crafted rules behave on known scenarios; they are **not** evidence that a model "learned" patterns from production data.
- `fraud-challenger.model-card.json` — optional experimental challenger trained by `training/train_fraud_challenger.py`. It is **not served**. The card records that fact explicitly.

Never put a serving flag of `true` on an artefact that has not been reviewed and wired into a router.
