-- EchoTalks Security Migration 0002: Admin/Owner Role Privilege Hardening
-- Non-destructive & idempotent: preserves all existing users, profiles, rooms, and messages.
-- Apply in Supabase Dashboard SQL Editor or via `supabase db push` AFTER `0001_initial_schema.sql`.

-- ============================================================================
-- 1. HARDENED AUTHORIZATION & PRIVILEGE CONTEXT HELPERS
-- ============================================================================

create or replace function public.is_service_or_db_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select coalesce(auth.role(), '') = 'service_role'
        or current_user in ('postgres', 'supabase_admin', 'supabase_auth_admin');
$$;

create or replace function public.is_owner()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.profiles
        where id = auth.uid()
          and role = 'owner'
    );
$$;

create or replace function public.is_admin_or_owner()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.profiles
        where id = auth.uid()
          and role in ('admin', 'owner')
    );
$$;

-- Recursion-free lookup used inside profiles RLS WITH CHECK policies
create or replace function public.current_profile_role(target_id uuid)
returns text
language sql
stable
security definer
set search_path = public
as $$
    select role
    from public.profiles
    where id = target_id;
$$;

-- ============================================================================
-- 2. COLUMN-LEVEL PRIVILEGE RESTRICTION ON public.profiles
-- ============================================================================
-- Even if a client sends {"role": "owner"} or {"is_verified": true} in a
-- PostgREST INSERT, UPSERT, or PATCH payload, PostgreSQL blocks column writes
-- for `authenticated` and `anon` roles at the privilege layer.

revoke all on table public.profiles from anon;
revoke insert, update, delete on table public.profiles from authenticated;

grant select on table public.profiles to authenticated;

grant insert (
    id,
    username,
    display_name,
    avatar_url,
    banner_url,
    photos,
    bio,
    country,
    country_code,
    gender,
    age,
    visibility_public,
    show_online_status,
    ghost_mode,
    dm_permissions,
    is_online,
    last_seen_at,
    audio_preview_title,
    profile_completed,
    onboarding_completed
) on table public.profiles to authenticated;

grant update (
    username,
    display_name,
    avatar_url,
    banner_url,
    photos,
    bio,
    country,
    country_code,
    gender,
    age,
    visibility_public,
    show_online_status,
    ghost_mode,
    dm_permissions,
    is_online,
    last_seen_at,
    audio_preview_title,
    profile_completed,
    onboarding_completed
) on table public.profiles to authenticated;

-- ============================================================================
-- 3. HARDENED RLS POLICIES ON public.profiles (INSERT & UPDATE)
-- ============================================================================

drop policy if exists "profiles_insert_own" on public.profiles;
create policy "profiles_insert_own" on public.profiles
    for insert to authenticated
    with check (
        id = auth.uid()
        and role = 'member'
        and is_verified = false
        and verification_status = 'none'
        and likes_count = 0
    );

drop policy if exists "profiles_update_own_or_admin" on public.profiles;
drop policy if exists "profiles_update_own" on public.profiles;
create policy "profiles_update_own" on public.profiles
    for update to authenticated
    using (id = auth.uid())
    with check (
        id = auth.uid()
        and role = public.current_profile_role(id)
    );

-- ============================================================================
-- 4. BEFORE INSERT OR UPDATE TRIGGER PROTECTION
-- ============================================================================
-- Closes the previous BEFORE UPDATE-only gap where INSERT/UPSERT could set
-- role='owner', and rejects client attempts to alter role even if executed
-- inside another policy or SQL path.

create or replace function public.protect_profile_sensitive_columns()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    -- Allow privileged backend contexts: service_role, auth signup trigger, migrations/SQL editor
    if public.is_service_or_db_admin() then
        return new;
    end if;

    if tg_op = 'INSERT' then
        if new.role is distinct from 'member' then
            raise exception 'Client cannot assign privileged profile role' using errcode = '42501';
        end if;
        if coalesce(new.is_verified, false) <> false
           or coalesce(new.verification_status, 'none') <> 'none'
           or coalesce(new.likes_count, 0) <> 0 then
            raise exception 'Client cannot assign privileged verification or likes fields' using errcode = '42501';
        end if;
        return new;
    end if;

    if tg_op = 'UPDATE' then
        if new.role is distinct from old.role then
            raise exception 'Modifying profile role from client session is not permitted' using errcode = '42501';
        end if;
        if not public.is_admin_or_owner() then
            if new.is_verified is distinct from old.is_verified
               or new.verification_status is distinct from old.verification_status
               or new.likes_count is distinct from old.likes_count then
                raise exception 'Modifying verification or likes_count is not permitted' using errcode = '42501';
            end if;
        end if;
        return new;
    end if;

    return new;
end;
$$;

drop trigger if exists trg_protect_profile_columns on public.profiles;
create trigger trg_protect_profile_columns
    before insert or update on public.profiles
    for each row execute function public.protect_profile_sensitive_columns();

-- Explicitly ensure auth signup trigger creates standard 'member' profiles
-- and never trusts raw_user_meta_data for roles or verification status.
create or replace function public.handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    v_name text;
begin
    v_name := coalesce(
        nullif(trim(new.raw_user_meta_data ->> 'full_name'), ''),
        nullif(trim(new.raw_user_meta_data ->> 'name'), ''),
        nullif(split_part(coalesce(new.email, ''), '@', 1), ''),
        'EchoTalker'
    );

    insert into public.profiles (id, display_name, avatar_url, role, is_verified, verification_status)
    values (
        new.id,
        v_name,
        coalesce(new.raw_user_meta_data ->> 'avatar_url', ''),
        'member',
        false,
        'none'
    )
    on conflict (id) do nothing;

    insert into public.user_preferences (user_id)
    values (new.id)
    on conflict (user_id) do nothing;

    return new;
end;
$$;

-- ============================================================================
-- 5. LEGITIMATE ADMIN VERIFICATION RPC & SERVER-ONLY ROLE PROMOTION RPC
-- ============================================================================

create or replace function public.admin_set_profile_verification(
    target_user_id uuid,
    approved boolean
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if not public.is_admin_or_owner() then
        raise exception 'Only verified administrators or owners can update verification status' using errcode = '42501';
    end if;

    update public.profiles
       set is_verified = approved,
           verification_status = case when approved then 'approved' else 'rejected' end,
           updated_at = now()
     where id = target_user_id;
end;
$$;

revoke all on function public.admin_set_profile_verification(uuid, boolean) from public, anon;
grant execute on function public.admin_set_profile_verification(uuid, boolean) to authenticated;

-- Strictly server-only role assignment function.
-- Never callable by `anon` or `authenticated` users, including existing admins.
create or replace function public.server_set_profile_role(
    target_user_id uuid,
    new_role text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if not public.is_service_or_db_admin() then
        raise exception 'Role assignment requires service_role or database admin privileges' using errcode = '42501';
    end if;

    if new_role not in ('member', 'admin', 'owner') then
        raise exception 'Invalid role: %', new_role using errcode = '22023';
    end if;

    update public.profiles
       set role = new_role,
           updated_at = now()
     where id = target_user_id;

    if not found then
        raise exception 'Profile not found for user id: %', target_user_id using errcode = 'P0002';
    end if;
end;
$$;

revoke all on function public.server_set_profile_role(uuid, text) from public, anon, authenticated;
grant execute on function public.server_set_profile_role(uuid, text) to service_role;
