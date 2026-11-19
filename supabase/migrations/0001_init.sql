-- Finance Tracker: cloud backup schema. Mirrors the Room tables (snake_case).
-- Safe to re-run (idempotent). Run in the Supabase SQL editor or via `supabase db push`.

-- ---------------------------------------------------------------- categories
create table if not exists public.categories (
  user_id           uuid        not null default auth.uid() references auth.users (id) on delete cascade,
  id                text        not null,
  name              text        not null,
  icon              text        not null,
  type              text        not null,            -- TxType.name
  updated_at        timestamptz not null,            -- client clock; used for last-write-wins
  deleted_at        timestamptz null,                -- soft-delete tombstone
  server_updated_at timestamptz not null default clock_timestamp(),  -- pull cursor (server clock)
  primary key (user_id, id)
);

-- -------------------------------------------------------------- transactions
create table if not exists public.transactions (
  user_id           uuid        not null default auth.uid() references auth.users (id) on delete cascade,
  id                text        not null,
  amount_minor      bigint      not null,            -- paise
  currency          text        not null default 'INR',
  type              text        not null,            -- TxType.name
  title             text        not null,
  note              text        null,
  category_id       text        not null,
  occurred_on       date        not null,
  created_at        timestamptz not null,
  updated_at        timestamptz not null,
  deleted_at        timestamptz null,
  source            text        not null,            -- TxSource.name
  server_updated_at timestamptz not null default clock_timestamp(),
  primary key (user_id, id)
);

-- Pull-cursor indexes.
create index if not exists categories_user_server_updated_idx
  on public.categories (user_id, server_updated_at);
create index if not exists transactions_user_server_updated_idx
  on public.transactions (user_id, server_updated_at);

-- ------------------------------------------------- server_updated_at trigger
-- Always stamped by the server so device clock skew can never hide a row from
-- another device's incremental pull. clock_timestamp() (not now()) so that
-- later-committing statements get later stamps.
create or replace function public.set_server_updated_at()
returns trigger
language plpgsql
as $fn$
begin
  new.server_updated_at := clock_timestamp();
  return new;
end;
$fn$;

drop trigger if exists categories_set_server_updated_at on public.categories;
create trigger categories_set_server_updated_at
  before insert or update on public.categories
  for each row execute function public.set_server_updated_at();

drop trigger if exists transactions_set_server_updated_at on public.transactions;
create trigger transactions_set_server_updated_at
  before insert or update on public.transactions
  for each row execute function public.set_server_updated_at();

-- ------------------------------------------------------------------- RLS
alter table public.categories   enable row level security;
alter table public.transactions enable row level security;

-- No DELETE policy on purpose: the app uses soft deletes (deleted_at).
-- Upsert (INSERT ... ON CONFLICT DO UPDATE) needs select + insert + update.
drop policy if exists categories_select_own on public.categories;
drop policy if exists categories_insert_own on public.categories;
drop policy if exists categories_update_own on public.categories;
create policy categories_select_own on public.categories
  for select to authenticated using (user_id = auth.uid());
create policy categories_insert_own on public.categories
  for insert to authenticated with check (user_id = auth.uid());
create policy categories_update_own on public.categories
  for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists transactions_select_own on public.transactions;
drop policy if exists transactions_insert_own on public.transactions;
drop policy if exists transactions_update_own on public.transactions;
create policy transactions_select_own on public.transactions
  for select to authenticated using (user_id = auth.uid());
create policy transactions_insert_own on public.transactions
  for insert to authenticated with check (user_id = auth.uid());
create policy transactions_update_own on public.transactions
  for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Signed-out (anon) clients get nothing; signed-in users get no DELETE.
revoke all on public.categories   from anon;
revoke all on public.transactions from anon;
revoke delete on public.categories   from authenticated;
revoke delete on public.transactions from authenticated;
grant select, insert, update on public.categories   to authenticated;
grant select, insert, update on public.transactions to authenticated;
