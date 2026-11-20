-- Finance Tracker: public user profiles (username login).
-- Supabase Auth only signs in by email, so the app maps a username to
-- "<username>@users.example.com" (reserved domain: mail can never be delivered).
-- A row here is created automatically for every new auth user.
-- Safe to re-run.

create table if not exists public.profiles (
  id           uuid        primary key references auth.users (id) on delete cascade,
  username     text        not null unique
                           check (username ~ '^[a-z0-9_.]{3,20}$'),
  display_name text        null,
  created_at   timestamptz not null default now()
);

alter table public.profiles enable row level security;

drop policy if exists profiles_select_own on public.profiles;
drop policy if exists profiles_update_own on public.profiles;
create policy profiles_select_own on public.profiles
  for select to authenticated
  using ((select auth.uid()) = id);
create policy profiles_update_own on public.profiles
  for update to authenticated
  using ((select auth.uid()) = id)
  with check ((select auth.uid()) = id);

revoke all on public.profiles from anon;
revoke all on public.profiles from authenticated;
grant select on public.profiles to authenticated;
grant update (display_name) on public.profiles to authenticated;

-- Trigger lives in a non-exposed schema so it isn't callable through the Data API.
create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create or replace function private.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  uname text := lower(split_part(coalesce(new.email, ''), '@', 1));
begin
  -- Only username accounts get a profile; never block other sign-ups (e.g. Google later).
  if split_part(coalesce(new.email, ''), '@', 2) = 'users.example.com'
     and uname ~ '^[a-z0-9_.]{3,20}$' then
    insert into public.profiles (id, username, display_name)
    values (new.id, uname, coalesce(new.raw_user_meta_data ->> 'display_name', uname))
    on conflict do nothing;
  end if;
  return new;
end;
$$;

revoke execute on function private.handle_new_user() from public, anon, authenticated;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function private.handle_new_user();

-- Security advisor fix for the 0001 trigger function (pin search_path).
alter function public.set_server_updated_at() set search_path = '';
