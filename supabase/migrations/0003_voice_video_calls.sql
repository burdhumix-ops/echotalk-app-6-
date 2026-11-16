-- EchoTalks Phase 1: One-to-One Voice & Video Calling Schema, RLS, Realtime & Signaling RPCs
-- Strictly Free-Tier Architecture (Supabase Free + LiveKit Cloud $0 Build Plan)
-- Non-destructive & idempotent: apply AFTER 0001_initial_schema.sql and 0002_admin_role_security.sql

-- ============================================================================
-- 1. ADD allow_incoming_calls PREFERENCE TO public.user_preferences
-- ============================================================================

alter table public.user_preferences
    add column if not exists allow_incoming_calls boolean not null default true;

-- ============================================================================
-- 2. CALL SESSIONS TABLE (1-to-1 CALL INVITATIONS & SIGNALING STATE)
-- ============================================================================

create table if not exists public.call_sessions (
    id uuid primary key default gen_random_uuid(),
    room_name text not null unique,
    caller_id uuid not null references public.profiles(id) on delete cascade,
    callee_id uuid not null references public.profiles(id) on delete cascade,
    call_type text not null check (call_type in ('audio', 'video')),
    status text not null default 'ringing' check (
        status in (
            'initiating',
            'ringing',
            'accepted',
            'connecting',
            'connected',
            'rejected',
            'missed',
            'busy',
            'unavailable',
            'ended',
            'failed'
        )
    ),
    end_reason text,
    started_at timestamptz,
    answered_at timestamptz,
    ended_at timestamptz,
    duration_seconds integer not null default 0 check (duration_seconds >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint call_sessions_no_self check (caller_id <> callee_id)
);

create index if not exists idx_call_sessions_callee_status
    on public.call_sessions (callee_id, status, created_at desc);

create index if not exists idx_call_sessions_caller_created
    on public.call_sessions (caller_id, created_at desc);

drop trigger if exists trg_call_sessions_updated_at on public.call_sessions;
create trigger trg_call_sessions_updated_at
    before update on public.call_sessions
    for each row execute function public.touch_updated_at();

-- ============================================================================
-- 3. IMMUTABILITY & STATE TRANSITION GUARD ON public.call_sessions
-- ============================================================================

create or replace function public.protect_call_session_update()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if public.is_service_or_db_admin() then
        return new;
    end if;

    if auth.uid() is null or (auth.uid() <> old.caller_id and auth.uid() <> old.callee_id) then
        raise exception 'Not authorized to modify this call session' using errcode = '42501';
    end if;

    -- Prevent participant/room/type spoofing
    if new.id is distinct from old.id
       or new.room_name is distinct from old.room_name
       or new.caller_id is distinct from old.caller_id
       or new.callee_id is distinct from old.callee_id
       or new.call_type is distinct from old.call_type then
        raise exception 'Immutable call session fields cannot be modified' using errcode = '42501';
    end if;

    -- Prevent reopening already terminated call sessions
    if old.status in ('rejected', 'missed', 'busy', 'unavailable', 'ended', 'failed')
       and new.status is distinct from old.status then
        raise exception 'Terminated call sessions cannot transition back to active state' using errcode = '22023';
    end if;

    -- Only callee can accept or reject an incoming ringing call
    if new.status in ('accepted', 'rejected') and old.status <> new.status and auth.uid() <> old.callee_id then
        raise exception 'Only the callee may accept or reject an incoming call' using errcode = '42501';
    end if;

    -- Track timestamps reliably
    if new.status = 'connected' and old.answered_at is null and new.answered_at is null then
        new.answered_at := now();
    end if;
    if new.status in ('rejected', 'missed', 'busy', 'unavailable', 'ended', 'failed') and new.ended_at is null then
        new.ended_at := now();
    end if;

    return new;
end;
$$;

drop trigger if exists trg_protect_call_session_update on public.call_sessions;
create trigger trg_protect_call_session_update
    before update on public.call_sessions
    for each row execute function public.protect_call_session_update();

-- ============================================================================
-- 4. SECURE RPC: CREATE 1-TO-1 CALL INVITATION (WITH BUSY/UNAVAILABLE/DUP GUARD)
-- ============================================================================

create or replace function public.create_call_invitation(
    p_callee_id uuid,
    p_call_type text
)
returns public.call_sessions
language plpgsql
security definer
set search_path = public
as $$
declare
    v_caller uuid := auth.uid();
    v_allow_calls boolean := true;
    v_callee_busy boolean := false;
    v_caller_busy boolean := false;
    v_room text;
    v_status text := 'ringing';
    v_end_reason text := null;
    v_row public.call_sessions;
begin
    if v_caller is null then
        raise exception 'Authentication required to start a call' using errcode = '42501';
    end if;
    if p_callee_id is null or p_callee_id = v_caller then
        raise exception 'Valid callee user ID required' using errcode = '22023';
    end if;
    if p_call_type not in ('audio', 'video') then
        raise exception 'Invalid call_type: must be audio or video' using errcode = '22023';
    end if;
    if public.is_blocked_between(v_caller, p_callee_id) then
        raise exception 'Calling is blocked between these users' using errcode = '42501';
    end if;

    -- Prevent duplicate active session for caller
    select exists (
        select 1
        from public.call_sessions cs
        where (cs.caller_id = v_caller or cs.callee_id = v_caller)
          and cs.status in ('initiating', 'ringing', 'accepted', 'connecting', 'connected')
          and cs.updated_at > (now() - interval '2 minutes')
    ) into v_caller_busy;

    if v_caller_busy then
        raise exception 'You already have an active call in progress' using errcode = '22023';
    end if;

    -- Check callee incoming call availability preference
    select coalesce(up.allow_incoming_calls, true)
      into v_allow_calls
      from public.user_preferences up
     where up.user_id = p_callee_id;

    if coalesce(v_allow_calls, true) = false then
        v_status := 'unavailable';
        v_end_reason := 'callee_unavailable';
    else
        -- Check whether callee is already on an active call
        select exists (
            select 1
            from public.call_sessions cs
            where (cs.caller_id = p_callee_id or cs.callee_id = p_callee_id)
              and cs.status in ('initiating', 'ringing', 'accepted', 'connecting', 'connected')
              and cs.updated_at > (now() - interval '2 minutes')
        ) into v_callee_busy;

        if v_callee_busy then
            v_status := 'busy';
            v_end_reason := 'callee_busy';
        end if;
    end if;

    v_room := 'echocall_' || replace(gen_random_uuid()::text, '-', '');

    insert into public.call_sessions (
        room_name,
        caller_id,
        callee_id,
        call_type,
        status,
        end_reason,
        started_at,
        ended_at
    )
    values (
        v_room,
        v_caller,
        p_callee_id,
        p_call_type,
        v_status,
        v_end_reason,
        now(),
        case when v_status in ('busy', 'unavailable') then now() else null end
    )
    returning * into v_row;

    return v_row;
end;
$$;

revoke all on function public.create_call_invitation(uuid, text) from public, anon;
grant execute on function public.create_call_invitation(uuid, text) to authenticated;

-- ============================================================================
-- 5. ZERO-TRUST RLS POLICIES & REALTIME PUBLICATION
-- ============================================================================

alter table public.call_sessions enable row level security;

revoke all on table public.call_sessions from anon;
grant select, insert, update on table public.call_sessions to authenticated;

drop policy if exists "call_sessions_select_participant" on public.call_sessions;
create policy "call_sessions_select_participant" on public.call_sessions
    for select to authenticated
    using (auth.uid() = caller_id or auth.uid() = callee_id);

drop policy if exists "call_sessions_insert_caller" on public.call_sessions;
create policy "call_sessions_insert_caller" on public.call_sessions
    for insert to authenticated
    with check (
        caller_id = auth.uid()
        and caller_id <> callee_id
        and not public.is_blocked_between(caller_id, callee_id)
    );

drop policy if exists "call_sessions_update_participant" on public.call_sessions;
create policy "call_sessions_update_participant" on public.call_sessions
    for update to authenticated
    using (auth.uid() = caller_id or auth.uid() = callee_id)
    with check (auth.uid() = caller_id or auth.uid() = callee_id);

do $$
begin
    if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
        begin
            alter publication supabase_realtime add table public.call_sessions;
        exception when duplicate_object then null;
        end;
    end if;
end $$;
