CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE app_group (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(150) NOT NULL,
    created_by   BIGINT REFERENCES app_user(id),
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE group_member (
    user_id    BIGINT NOT NULL REFERENCES app_user(id),
    group_id   BIGINT NOT NULL REFERENCES app_group(id),
    joined_at  TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, group_id)
);

CREATE TABLE expense (
    id            BIGSERIAL PRIMARY KEY,
    group_id      BIGINT NOT NULL REFERENCES app_group(id),
    paid_by       BIGINT NOT NULL REFERENCES app_user(id),
    amount        NUMERIC(12,2) NOT NULL,
    currency      VARCHAR(10) NOT NULL DEFAULT 'INR',
    category      VARCHAR(100),
    description   VARCHAR(255),
    expense_date  TIMESTAMP NOT NULL DEFAULT now(),
    is_recurring  BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE expense_split (
    expense_id    BIGINT NOT NULL REFERENCES expense(id),
    user_id       BIGINT NOT NULL REFERENCES app_user(id),
    share_amount  NUMERIC(12,2) NOT NULL,
    PRIMARY KEY (expense_id, user_id)
);

CREATE TABLE settlement (
    id            BIGSERIAL PRIMARY KEY,
    group_id      BIGINT NOT NULL REFERENCES app_group(id),
    from_user_id  BIGINT NOT NULL REFERENCES app_user(id),
    to_user_id    BIGINT NOT NULL REFERENCES app_user(id),
    amount        NUMERIC(12,2) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    settled_at    TIMESTAMP
);

CREATE TABLE notification (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES app_user(id),
    message     VARCHAR(500) NOT NULL,
    is_read     BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE audit_log (
    id               BIGSERIAL PRIMARY KEY,
    group_id         BIGINT REFERENCES app_group(id),
    performed_by     BIGINT REFERENCES app_user(id),
    action           VARCHAR(500) NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_expense_group ON expense(group_id);
CREATE INDEX idx_expense_split_user ON expense_split(user_id);
CREATE INDEX idx_settlement_group ON settlement(group_id);
CREATE INDEX idx_notification_user ON notification(user_id);
CREATE INDEX idx_audit_group ON audit_log(group_id);
