-- Infinitum Nexus hosted state: Firmament ledger + CID/Spectre stores.
-- Only the private nexus-store edge function (service role) can read or write.

create table if not exists public.nexus_ledger (
  seq bigint primary key,
  entry jsonb not null,
  created_at timestamptz not null default now()
);

create table if not exists public.nexus_docs (
  id text primary key,
  version bigint not null,
  data jsonb not null,
  updated_at timestamptz not null default now()
);

-- SHA-256 hashes of keys allowed to call the gateway (raw keys live only in the app's env).
create table if not exists public.nexus_gateway_keys (
  key_hash text primary key,
  label text not null,
  created_at timestamptz not null default now()
);

alter table public.nexus_ledger enable row level security;
alter table public.nexus_docs enable row level security;
alter table public.nexus_gateway_keys enable row level security;
revoke all on public.nexus_ledger, public.nexus_docs, public.nexus_gateway_keys from anon, authenticated;

-- Ledger entries can never be changed or deleted, even by the service role.
create or replace function public.nexus_ledger_immutable() returns trigger
language plpgsql set search_path = '' as $$
begin
  raise exception 'Firmament ledger entries are append-only';
end $$;
drop trigger if exists nexus_ledger_no_update on public.nexus_ledger;
create trigger nexus_ledger_no_update before update or delete on public.nexus_ledger
  for each row execute function public.nexus_ledger_immutable();

-- Save changed stores and new ledger entries in one transaction.
-- Raises nexus_conflict if any store changed since the caller loaded it, or a ledger seq is taken.
create or replace function public.nexus_commit(p_entries jsonb, p_docs jsonb) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  d jsonb;
  cur bigint;
  result jsonb := '{}'::jsonb;
begin
  for d in select value from jsonb_array_elements(p_docs) loop
    select version into cur from public.nexus_docs where id = d->>'id' for update;
    if coalesce(cur, 0) <> (d->>'expected')::bigint then
      raise exception 'nexus_conflict' using errcode = 'P0001';
    end if;
    if cur is null then
      begin
        insert into public.nexus_docs (id, version, data) values (d->>'id', 1, d->'data');
      exception when unique_violation then
        raise exception 'nexus_conflict' using errcode = 'P0001'; -- another instance created it first
      end;
    else
      update public.nexus_docs set version = version + 1, data = d->'data', updated_at = now() where id = d->>'id';
    end if;
    result := result || jsonb_build_object(d->>'id', coalesce(cur, 0) + 1);
  end loop;
  begin
    insert into public.nexus_ledger (seq, entry)
      select (value->>'seq')::bigint, value from jsonb_array_elements(p_entries);
  exception when unique_violation then
    raise exception 'nexus_conflict' using errcode = 'P0001';
  end;
  return result;
end $$;

revoke execute on function public.nexus_commit(jsonb, jsonb) from public, anon, authenticated;
grant execute on function public.nexus_commit(jsonb, jsonb) to service_role;
revoke execute on function public.nexus_ledger_immutable() from public, anon, authenticated;
