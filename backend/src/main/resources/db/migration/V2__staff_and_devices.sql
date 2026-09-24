-- PIN lockout tracking. A staff PIN locks after too many wrong tries and
-- stays locked until the owner resets it (no auto-expiry), so no separate
-- "locked_until" timestamp is needed.
alter table users
    add column failed_pin_attempts int     not null default 0,
    add column pin_locked          boolean not null default false;

-- One-time codes the owner generates to pair a new shop device. Short-lived
-- and single-use; the code itself is hashed like every other token in this
-- schema so a database leak doesn't hand out live credentials.
create table device_pairing_codes (
    id          bigint generated always as identity primary key,
    code_hash   varchar(255) not null unique,
    created_by  bigint       not null references users (id),
    expires_at  timestamptz  not null,
    redeemed_at timestamptz,
    created_at  timestamptz  not null default now()
);

-- A staff PIN session, scoped to the device it was opened on. Mirrors
-- devices' own shape (hashed opaque token + revoked flag) rather than a JWT,
-- since revocation (idle-lock, switch-user, logout) is the only lifecycle
-- event that matters here.
create table staff_sessions (
    id         bigint generated always as identity primary key,
    user_id    bigint       not null references users (id),
    device_id  bigint       not null references devices (id),
    token_hash varchar(255) not null unique,
    revoked    boolean      not null default false,
    created_at timestamptz  not null default now()
);

create index idx_staff_sessions_user_id on staff_sessions (user_id);
