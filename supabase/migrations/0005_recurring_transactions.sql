-- Finance Tracker: recurring entries (bills, subscriptions, income), e.g. Netflix ₹499 on the 15th monthly.
-- Mirrors the Room `recurring` table. Each due occurrence is added on the device as an ordinary
-- transaction (id "rec_<recurring id>_<yyyy-mm-dd>"), so this table only holds the schedules.
-- posted_through = last occurrence date already added to transactions (stops other devices re-adding).
-- Synced like the other tables: client updated_at for last-write-wins, server_updated_at as pull cursor,
-- soft deletes via deleted_at. Safe to re-run.
--
-- Replaces the spending-limit `budgets` table from 0003, which the app no longer uses.
-- (Dropping it is left as a manual step: drop table if exists public.budgets;)

create table if not exists public.recurring_transactions (
  user_id           uuid        not null default auth.uid() references auth.users (id) on delete cascade,
  id                text        not null,
  title             text        not null check (length(title) between 1 and 200),
  amount_minor      bigint      not null check (amount_minor > 0),
  currency          text        not null default 'INR',
  type              text        not null check (type in ('EXPENSE', 'INCOME')),
  category_id       text        not null,
  note              text        null,
  frequency         text        not null check (frequency in ('DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'HALF_YEARLY', 'YEARLY')),
  start_on          date        not null,
  posted_through    date        null,
  created_at        timestamptz not null,
  updated_at        timestamptz not null,
  deleted_at        timestamptz null,
  server_updated_at timestamptz not null default clock_timestamp(),
  primary key (user_id, id)
);

create index if not exists recurring_transactions_user_server_updated_idx
  on public.recurring_transactions (user_id, server_updated_at);

drop trigger if exists recurring_transactions_set_server_updated_at on public.recurring_transactions;
create trigger recurring_transactions_set_server_updated_at
  before insert or update on public.recurring_transactions
  for each row execute function public.set_server_updated_at();

alter table public.recurring_transactions enable row level security;

-- No DELETE policy: soft deletes only. Upsert needs select + insert + update.
drop policy if exists recurring_select_own on public.recurring_transactions;
drop policy if exists recurring_insert_own on public.recurring_transactions;
drop policy if exists recurring_update_own on public.recurring_transactions;
create policy recurring_select_own on public.recurring_transactions
  for select to authenticated using (user_id = (select auth.uid()));
create policy recurring_insert_own on public.recurring_transactions
  for insert to authenticated with check (user_id = (select auth.uid()));
create policy recurring_update_own on public.recurring_transactions
  for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

revoke all on public.recurring_transactions from anon;
revoke all on public.recurring_transactions from authenticated;
grant select, insert, update on public.recurring_transactions to authenticated;
