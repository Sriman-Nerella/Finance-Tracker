-- Performance advisor fix (auth_rls_initplan): wrap auth.uid() in a scalar subquery so
-- Postgres evaluates it once per statement instead of once per row. Same rules, faster.
-- alter policy changes the policies in place. Safe to re-run.

alter policy categories_select_own on public.categories
  using (user_id = (select auth.uid()));
alter policy categories_insert_own on public.categories
  with check (user_id = (select auth.uid()));
alter policy categories_update_own on public.categories
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

alter policy transactions_select_own on public.transactions
  using (user_id = (select auth.uid()));
alter policy transactions_insert_own on public.transactions
  with check (user_id = (select auth.uid()));
alter policy transactions_update_own on public.transactions
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
