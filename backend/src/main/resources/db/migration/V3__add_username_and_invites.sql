ALTER TABLE app_user ADD COLUMN username VARCHAR(50);

UPDATE app_user
SET username = lower(regexp_replace(split_part(email, '@', 1), '[^a-zA-Z0-9]', '_', 'g')) || '_' || id;

ALTER TABLE app_user ALTER COLUMN username SET NOT NULL;
ALTER TABLE app_user ADD CONSTRAINT uq_app_user_username UNIQUE (username);

CREATE TABLE group_invite (
    id               BIGSERIAL PRIMARY KEY,
    group_id         BIGINT NOT NULL REFERENCES app_group(id),
    invited_user_id  BIGINT NOT NULL REFERENCES app_user(id),
    invited_by       BIGINT NOT NULL REFERENCES app_user(id),
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    responded_at     TIMESTAMP
);

CREATE INDEX idx_group_invite_group ON group_invite(group_id);
CREATE INDEX idx_group_invite_invited_user ON group_invite(invited_user_id);

CREATE UNIQUE INDEX uq_group_invite_pending ON group_invite(group_id, invited_user_id) WHERE status = 'PENDING';
