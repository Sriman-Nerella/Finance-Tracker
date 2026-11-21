-- Finance Tracker: recurring budgets. Mirrors the Room `budgets` table (snake_case).
-- A budget is a spending limit that repeats every calendar week (Mon-Sun), month or year.
-- category_id null = overall budget across all expense categories.
-- rollover = leftover (or overspend) from earlier periods adjusts the current limit.
-- Synced like categories/transactions: client-stamped updated_at for last-write-wins,
-- server-stamped server_updated_at as the pull cursor, soft deletes via deleted_at.
-- Safe to re-run.

create table if not exists public.budgets (
  user_id           uuid        not null default auth.uid() references auth.users (id) on delete cascade,
  id                text        not null,
  category_id       text        null,
  amount_minor      bigint      not null check (amount_minor > 0),   -- paise per period
  currency          text        not null default 'INR',
  period            text        not null check (period in ('WEEKLY', 'MONTHLY', 'YEARLY')),
  rollover          boolean     not null default false,
  start_on          date        not null,            -- first day of the first period counted
  created_at        timestamptz not null,
  updated_at        timestamptz not null,
  deleted_at        timestamptz null,
  server_updated_at timestamptz not null default clock_timestamp(),
  primary key (user_id, id)
);

create index if not exists budgets_user_server_updated_idx
  on public.budgets (user_id, server_updated_at);

drop trigger if exists budgets_set_server_updated_at on public.budgets;
create trigger budgets_set_server_updated_at
  before insert or update on public.budgets
  for each row execute function public.set_server_updated_at();

alter table public.budgets enable row level security;

-- No DELETE policy: soft deletes only. Upsert needs select + insert + update.
drop policy if exists budgets_select_own on public.budgets;
drop policy if exists budgets_insert_own on public.budgets;
drop policy if exists budgets_update_own on public.budgets;
create policy budgets_select_own on public.budgets
  for select to authenticated using (user_id = (select auth.uid()));
create policy budgets_insert_own on public.budgets
  for insert to authenticated with check (user_id = (select auth.uid()));
create policy budgets_update_own on public.budgets
  for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

revoke all on public.budgets from anon;
revoke all on public.budgets from authenticated;
grant select, insert, update on public.budgets to authenticated;
