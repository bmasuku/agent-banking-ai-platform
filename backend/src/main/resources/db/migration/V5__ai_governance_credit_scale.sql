-- Align credit scores with the AI scorecard (0-1000), store AI prediction ids, and capture
-- transaction geo for fraud location rules.

-- Rescale existing FICO-like scores (300-850) onto the new 0-1000 scorecard approximately:
-- new = round((old - 300) * 1000 / 550)
UPDATE agents
SET credit_score = LEAST(1000, GREATEST(0, ROUND((credit_score - 300) * 1000.0 / 550.0)))
WHERE credit_score BETWEEN 300 AND 850;

UPDATE credit_scores
SET score = LEAST(1000, GREATEST(0, ROUND((score - 300) * 1000.0 / 550.0)))
WHERE score BETWEEN 300 AND 850;

ALTER TABLE agents DROP CONSTRAINT IF EXISTS agents_credit_score_check;
ALTER TABLE agents ADD CONSTRAINT agents_credit_score_check CHECK (credit_score BETWEEN 0 AND 1000);
ALTER TABLE agents ALTER COLUMN credit_score SET DEFAULT 500;

ALTER TABLE credit_scores DROP CONSTRAINT IF EXISTS credit_scores_score_check;
ALTER TABLE credit_scores ADD CONSTRAINT credit_scores_score_check CHECK (score BETWEEN 0 AND 1000);

ALTER TABLE credit_scores
    ADD COLUMN IF NOT EXISTS recommendation VARCHAR(24),
    ADD COLUMN IF NOT EXISTS eligible BOOLEAN,
    ADD COLUMN IF NOT EXISTS ai_prediction_id VARCHAR(64);

ALTER TABLE fraud_checks
    ADD COLUMN IF NOT EXISTS ai_prediction_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS recommended_action VARCHAR(40);

ALTER TABLE float_predictions
    ADD COLUMN IF NOT EXISTS ai_prediction_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS metrics JSONB;

ALTER TABLE financial_transactions
    ADD COLUMN IF NOT EXISTS latitude NUMERIC(10, 6),
    ADD COLUMN IF NOT EXISTS longitude NUMERIC(10, 6);

-- Auto-approval threshold moves with the new scale (was 700 on 300-850).
UPDATE system_settings
SET setting_value = '720', default_value = '720', updated_at = NOW()
WHERE setting_key = 'advance.auto_approve.min_score';
