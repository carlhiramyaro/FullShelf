-- G7: an owner is linked to their shop by email. The admin creates the owner
-- as a row with an email and no Clerk id (a "pending" owner); the first
-- verified Clerk login with that email fills the Clerk id in.
alter table users add column email varchar(254);

-- Two shops waiting on the same email would make the first login ambiguous.
-- Once a row is linked (clerk_user_id set) its email no longer needs to be
-- unique, so a re-linked owner can come back under the same address.
create unique index users_pending_owner_email_unique_idx
    on users (lower(email))
    where email is not null and clerk_user_id is null;
