-- 1. New wallet columns
ALTER TABLE wallets ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE wallets ADD COLUMN version   BIGINT  NOT NULL DEFAULT 0;

-- 2. Only the system wallet may go negative; only one system wallet may exist
ALTER TABLE wallets DROP CONSTRAINT ck_wallets_balance_non_negative;
ALTER TABLE wallets ADD  CONSTRAINT ck_wallets_balance_non_negative
    CHECK (is_system OR balance_paise >= 0);
CREATE UNIQUE INDEX uq_wallets_single_system ON wallets (is_system) WHERE is_system;

-- The ledger cannot know which wallet is the system wallet; the wallets CHECK is the guard.
ALTER TABLE ledger_entries DROP CONSTRAINT ck_ledger_balance_non_negative;

-- 3. System user and wallet with fixed IDs
INSERT INTO users (id, name, email, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'System', 'system@wallet.internal', now());

INSERT INTO wallets (id, user_id, balance_paise, created_at, updated_at, is_system)
VALUES ('00000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-000000000001', 0, now(), now(), TRUE);

-- 4. Backfill existing top-ups so history stays balanced
ALTER TABLE wallet_transactions DROP CONSTRAINT ck_wallet_transactions_shape;

UPDATE wallet_transactions
   SET from_wallet_id = '00000000-0000-0000-0000-000000000002'
 WHERE type = 'TOPUP' AND from_wallet_id IS NULL;

INSERT INTO ledger_entries
    (id, transaction_id, wallet_id, direction, amount_paise, balance_after_paise,
     counterparty_wallet_id, created_at)
SELECT gen_random_uuid(), t.id, '00000000-0000-0000-0000-000000000002', 'DEBIT',
       t.amount_paise,
       -SUM(t.amount_paise) OVER (ORDER BY t.created_at, t.id),
       t.to_wallet_id, t.created_at
  FROM wallet_transactions t
 WHERE t.type = 'TOPUP';

UPDATE wallets
   SET balance_paise = -COALESCE((SELECT SUM(amount_paise)
                                    FROM wallet_transactions WHERE type = 'TOPUP'), 0),
       updated_at = now()
 WHERE is_system;

-- 5. Every transaction now has a sender
ALTER TABLE wallet_transactions ALTER COLUMN from_wallet_id SET NOT NULL;
ALTER TABLE wallet_transactions ADD CONSTRAINT ck_wallet_transactions_shape
    CHECK (from_wallet_id <> to_wallet_id);

-- 6. Reconciliation results
CREATE TABLE reconciliation_runs (
    id                    UUID        PRIMARY KEY,
    started_at            TIMESTAMPTZ NOT NULL,
    finished_at           TIMESTAMPTZ NOT NULL,
    status                VARCHAR(16) NOT NULL,
    wallets_checked       INTEGER     NOT NULL,
    transactions_checked  INTEGER     NOT NULL,
    discrepancy_count     INTEGER     NOT NULL,
    total_balance_paise   BIGINT      NOT NULL,
    details               JSONB       NOT NULL DEFAULT '[]'::jsonb,
    CONSTRAINT ck_reconciliation_status CHECK (status IN ('OK', 'MISMATCH'))
);
CREATE INDEX idx_reconciliation_started ON reconciliation_runs (started_at DESC);
