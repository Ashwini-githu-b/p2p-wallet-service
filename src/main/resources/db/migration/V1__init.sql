CREATE TABLE users (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE wallets (
    id             UUID        PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES users(id),
    balance_paise  BIGINT      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_wallets_user UNIQUE (user_id),
    CONSTRAINT ck_wallets_balance_non_negative CHECK (balance_paise >= 0)
);

CREATE TABLE wallet_transactions (
    id               UUID        PRIMARY KEY,
    type             VARCHAR(16) NOT NULL,
    from_wallet_id   UUID        REFERENCES wallets(id),
    to_wallet_id     UUID        NOT NULL REFERENCES wallets(id),
    amount_paise     BIGINT      NOT NULL,
    idempotency_key  VARCHAR(64) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_wallet_transactions_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_wallet_transactions_type CHECK (type IN ('TOPUP', 'TRANSFER')),
    CONSTRAINT ck_wallet_transactions_amount_positive CHECK (amount_paise > 0),
    CONSTRAINT ck_wallet_transactions_shape CHECK (
        (type = 'TOPUP'    AND from_wallet_id IS NULL) OR
        (type = 'TRANSFER' AND from_wallet_id IS NOT NULL AND from_wallet_id <> to_wallet_id)
    )
);

CREATE TABLE ledger_entries (
    id                      UUID        PRIMARY KEY,
    transaction_id          UUID        NOT NULL REFERENCES wallet_transactions(id),
    wallet_id               UUID        NOT NULL REFERENCES wallets(id),
    direction               VARCHAR(8)  NOT NULL,
    amount_paise            BIGINT      NOT NULL,
    balance_after_paise     BIGINT      NOT NULL,
    counterparty_wallet_id  UUID        REFERENCES wallets(id),
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ledger_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_amount_positive CHECK (amount_paise > 0),
    CONSTRAINT ck_ledger_balance_non_negative CHECK (balance_after_paise >= 0)
);

CREATE INDEX idx_ledger_wallet_created ON ledger_entries (wallet_id, created_at DESC, id DESC);
CREATE INDEX idx_ledger_transaction    ON ledger_entries (transaction_id);

