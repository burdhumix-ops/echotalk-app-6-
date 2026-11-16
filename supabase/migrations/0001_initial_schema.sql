-- EchoTalks Supabase Free-Tier Backend Migration
-- Strictly Zero-Cost Architecture: PostgreSQL + RLS + Realtime + Storage + Edge Functions
-- Safe to run in Supabase SQL Editor or via `supabase db push`

create extension if not exists pgcrypto;

-- ============================================================================
-- 1. CORE TABLES
-- ============================================================================

create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    username text unique,
    display_name text not null default 'EchoTalker',
    avatar_url text not null default '',
    banner_url text not null default '',
    photos text[] not null default '{}',
    bio text not null default '',
    country text not null default 'Global',
    country_code text not null default 'GL',
    gender text not null default 'Non-Binary',
    age integer not null default 24 check (age >= 18 and age <= 120),
    visibility_public boolean not null default true,
    show_online_status boolean not null default true,
    ghost_mode boolean not null default false,
    dm_permissions text not null default 'everyone' check (dm_permissions in ('everyone', 'friends', 'nobody')),
    is_online boolean not null default false,
    last_seen_at timestamptz not null default now(),
    is_verified boolean not null default false,
    verification_status text not null default 'none' check (verification_status in ('none', 'pending', 'approved', 'rejected')),
    role text not null default 'member' check (role in ('member', 'admin', 'owner')),
    audio_preview_title text not null default 'Ambient Chillout (5s)',
    profile_completed boolean not null default false,
    onboarding_completed boolean not null default false,
    likes_count integer not null default 0 check (likes_count >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint profiles_username_format check (
        username is null or username ~ '^[a-z0-9_]{3,20}$'
    )
);

create index if not exists idx_profiles_online_country_gender
    on public.profiles (is_online desc, country, gender)
    where visibility_public = true;

create index if not exists idx_profiles_username
    on public.profiles (username);

create table if not exists public.user_blocks (
    blocker_id uuid not null references public.profiles(id) on delete cascade,
    blocked_id uuid not null references public.profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (blocker_id, blocked_id),
    constraint user_blocks_no_self check (blocker_id <> blocked_id)
);

create table if not exists public.profile_likes (
    liker_id uuid not null references public.profiles(id) on delete cascade,
    liked_id uuid not null references public.profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (liker_id, liked_id),
    constraint profile_likes_no_self check (liker_id <> liked_id)
);

create table if not exists public.friendships (
    id text primary key,
    user_a uuid not null references public.profiles(id) on delete cascade,
    user_b uuid not null references public.profiles(id) on delete cascade,
    requester_id uuid not null references public.profiles(id) on delete cascade,
    status text not null default 'pending' check (status in ('pending', 'accepted', 'declined')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint friendships_canonical_order check (user_a < user_b),
    constraint friendships_requester_member check (requester_id = user_a or requester_id = user_b)
);

create index if not exists idx_friendships_users on public.friendships (user_a, user_b);

create table if not exists public.rooms (
    id text primary key,
    name text not null,
    subtitle text not null default '',
    description text not null default '',
    room_type text not null default 'text' check (room_type in ('voice', 'text', 'vip')),
    badge text not null default 'Global',
    is_special boolean not null default false,
    is_locked boolean not null default false,
    is_open boolean not null default true,
    retention_hours integer not null default 24 check (retention_hours in (24, 48)),
    created_by uuid references public.profiles(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint rooms_only_special_locked check ((not is_locked) or is_special),
    constraint rooms_special_retention check ((is_special and retention_hours = 48) or (not is_special and retention_hours = 24))
);

create table if not exists public.room_kicks (
    room_id text not null references public.rooms(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    kicked_by uuid references public.profiles(id) on delete set null,
    kicked_until timestamptz not null default (now() + interval '24 hours'),
    created_at timestamptz not null default now(),
    primary key (room_id, user_id)
);

create table if not exists public.room_messages (
    id uuid primary key default gen_random_uuid(),
    client_msg_id text,
    room_id text not null references public.rooms(id) on delete cascade,
    sender_id uuid not null references public.profiles(id) on delete cascade,
    sender_name text not null,
    content text not null check (char_length(content) between 1 and 4000),
    message_type text not null default 'text' check (message_type in ('text', 'audio', 'system', 'sticker', 'gif', 'image')),
    media_url text,
    audio_duration text,
    reply_to_id uuid references public.room_messages(id) on delete set null,
    reply_to_sender text,
    reply_to_text text,
    reactions jsonb not null default '{}'::jsonb,
    user_reactions jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null default (now() + interval '24 hours')
);

create unique index if not exists idx_room_messages_idempotency
    on public.room_messages (sender_id, room_id, client_msg_id)
    where client_msg_id is not null;

create index if not exists idx_room_messages_room_created
    on public.room_messages (room_id, created_at desc);

create index if not exists idx_room_messages_expires_at
    on public.room_messages (expires_at);

create table if not exists public.direct_conversations (
    id uuid primary key default gen_random_uuid(),
    canonical_key text not null unique,
    created_at timestamptz not null default now(),
    last_activity_at timestamptz not null default now()
);

create table if not exists public.conversation_participants (
    conversation_id uuid not null references public.direct_conversations(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    joined_at timestamptz not null default now(),
    last_read_at timestamptz,
    pinned boolean not null default false,
    archived boolean not null default false,
    primary key (conversation_id, user_id)
);

create index if not exists idx_conv_participants_user
    on public.conversation_participants (user_id, conversation_id);

create table if not exists public.direct_messages (
    id uuid primary key default gen_random_uuid(),
    client_msg_id text,
    conversation_id uuid not null references public.direct_conversations(id) on delete cascade,
    sender_id uuid not null references public.profiles(id) on delete cascade,
    sender_name text not null,
    content text not null check (char_length(content) between 1 and 4000),
    message_type text not null default 'text' check (message_type in ('text', 'audio', 'image', 'doodle', 'sticker', 'gif', 'challenge', 'gift')),
    media_url text,
    audio_duration text,
    audio_waveform jsonb not null default '[]'::jsonb,
    challenge_data jsonb,
    sticker_pack_id text,
    reply_to_id uuid references public.direct_messages(id) on delete set null,
    reply_to_sender text,
    reply_to_text text,
    is_view_once boolean not null default false,
    viewed boolean not null default false,
    is_forwarded boolean not null default false,
    forwarded_from text,
    delivery_status text not null default 'sent' check (delivery_status in ('sent', 'delivered', 'read')),
    reactions jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    edited_at timestamptz,
    unsent_at timestamptz
);

create unique index if not exists idx_direct_messages_idempotency
    on public.direct_messages (sender_id, conversation_id, client_msg_id)
    where client_msg_id is not null;

create index if not exists idx_direct_messages_conv_created
    on public.direct_messages (conversation_id, created_at desc);

create table if not exists public.user_preferences (
    user_id uuid primary key references auth.users(id) on delete cascade,
    theme_id text not null default 'ios_glass',
    chat_wallpaper text not null default 'default',
    chat_text_size text not null default 'medium',
    notify_direct_messages boolean not null default true,
    notify_message_requests boolean not null default true,
    notify_connection_requests boolean not null default true,
    notify_room_invitations boolean not null default true,
    notify_room_mentions boolean not null default true,
    notify_incoming_calls boolean not null default true,
    notify_missed_calls boolean not null default true,
    notify_system boolean not null default true,
    notification_sound boolean not null default true,
    notification_vibration boolean not null default true,
    read_receipts boolean not null default true,
    typing_indicators boolean not null default true,
    personalization_enabled boolean not null default true,
    extra_json jsonb not null default '{}'::jsonb,
    updated_at timestamptz not null default now()
);

create table if not exists public.device_tokens (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    token text not null,
    platform text not null default 'android' check (platform in ('android', 'ios', 'web')),
    updated_at timestamptz not null default now(),
    unique (user_id, token)
);

create index if not exists idx_device_tokens_user on public.device_tokens (user_id);

create table if not exists public.arcade_sessions (
    id text primary key,
    game_type text not null default 'chess',
    challenger_id uuid references public.profiles(id) on delete set null,
    opponent_id uuid references public.profiles(id) on delete set null,
    challenger_name text not null default '',
    opponent_name text not null default '',
    current_turn text not null default 'White',
    chess_board jsonb not null default '[]'::jsonb,
    captured_white jsonb not null default '[]'::jsonb,
    captured_black jsonb not null default '[]'::jsonb,
    last_move_description text not null default '',
    winner text,
    updated_at timestamptz not null default now()
);

-- ============================================================================
-- 2. SEED DEFAULT PUBLIC ROOMS (IDEMPOTENT)
-- ============================================================================

insert into public.rooms (id, name, subtitle, description, room_type, badge, is_special, is_locked, is_open, retention_hours)
values
    ('r-voice', 'Voice Room', 'Spatial Live Audio Stage', 'Live audio lounge: speak on stage, listen in spatial fidelity, and send instant soundboard reactions.', 'voice', 'Live Audio', false, false, true, 24),
    ('r-chat1', 'Public Chat Room 1', 'Global Chit-Chat & Trends', 'Worldwide open crew discussion floor for drop-in messaging, memes, and game lobby matchmaking.', 'text', 'Global', false, false, true, 24),
    ('r-chat2', 'Public Chat Room 2', 'International Creative Hub', 'Design concepts, photography portfolios, doodles, and music recommendations.', 'text', 'Creative', false, false, true, 24),
    ('r-special', 'Special 2-Day Lounge', 'Exclusive 48-Hour Pop-Up Room', 'Admin lockable VIP lounge with automatic 48-hour message cleanup timer.', 'vip', 'Special VIP', true, false, true, 48)
on conflict (id) do nothing;

-- ============================================================================
-- 3. SECURITY HELPERS & TRIGGERS
-- ============================================================================

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

create or replace function public.is_conversation_participant(target_conv_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.conversation_participants
        where conversation_id = target_conv_id
          and user_id = auth.uid()
    );
$$;

create or replace function public.is_blocked_between(user_1 uuid, user_2 uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.user_blocks
        where (blocker_id = user_1 and blocked_id = user_2)
           or (blocker_id = user_2 and blocked_id = user_1)
    );
$$;

create or replace function public.can_post_in_room(target_room_id text, target_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.rooms r
        where r.id = target_room_id
          and r.is_open = true
          and r.is_locked = false
    )
    and not exists (
        select 1
        from public.room_kicks k
        where k.room_id = target_room_id
          and k.user_id = target_user_id
          and k.kicked_until > now()
    );
$$;

create or replace function public.touch_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

drop trigger if exists trg_profiles_updated_at on public.profiles;
create trigger trg_profiles_updated_at
    before update on public.profiles
    for each row execute function public.touch_updated_at();

drop trigger if exists trg_friendships_updated_at on public.friendships;
create trigger trg_friendships_updated_at
    before update on public.friendships
    for each row execute function public.touch_updated_at();

drop trigger if exists trg_rooms_updated_at on public.rooms;
create trigger trg_rooms_updated_at
    before update on public.rooms
    for each row execute function public.touch_updated_at();

drop trigger if exists trg_prefs_updated_at on public.user_preferences;
create trigger trg_prefs_updated_at
    before update on public.user_preferences
    for each row execute function public.touch_updated_at();

-- Prevent non-admin users from escalating role, verification badge, or likes_count
create or replace function public.protect_profile_sensitive_columns()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is not null and not public.is_admin_or_owner() then
        new.role := old.role;
        new.is_verified := old.is_verified;
        new.verification_status := old.verification_status;
        new.likes_count := old.likes_count;
    end if;
    return new;
end;
$$;

drop trigger if exists trg_protect_profile_columns on public.profiles;
create trigger trg_protect_profile_columns
    before update on public.profiles
    for each row execute function public.protect_profile_sensitive_columns();

-- Maintain profile_likes -> profiles.likes_count
create or replace function public.sync_profile_likes_count()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if tg_op = 'INSERT' then
        update public.profiles
        set likes_count = likes_count + 1
        where id = new.liked_id;
        return new;
    elsif tg_op = 'DELETE' then
        update public.profiles
        set likes_count = greatest(0, likes_count - 1)
        where id = old.liked_id;
        return old;
    end if;
    return null;
end;
$$;

drop trigger if exists trg_sync_profile_likes on public.profile_likes;
create trigger trg_sync_profile_likes
    after insert or delete on public.profile_likes
    for each row execute function public.sync_profile_likes_count();

-- Compute room_messages.expires_at deterministically (24h normal, 48h special room)
create or replace function public.set_room_message_expiry()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    v_is_special boolean := false;
    v_retention integer := 24;
begin
    select is_special, retention_hours
      into v_is_special, v_retention
      from public.rooms
     where id = new.room_id;

    if new.created_at is null then
        new.created_at := now();
    end if;

    if coalesce(v_is_special, false) or coalesce(v_retention, 24) = 48 then
        new.expires_at := new.created_at + interval '48 hours';
    else
        new.expires_at := new.created_at + interval '24 hours';
    end if;

    return new;
end;
$$;

drop trigger if exists trg_room_message_expiry on public.room_messages;
create trigger trg_room_message_expiry
    before insert on public.room_messages
    for each row execute function public.set_room_message_expiry();

-- Update direct_conversations.last_activity_at on new DM
create or replace function public.bump_conversation_activity()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    update public.direct_conversations
       set last_activity_at = coalesce(new.created_at, now())
     where id = new.conversation_id;
    return new;
end;
$$;

drop trigger if exists trg_bump_conversation_activity on public.direct_messages;
create trigger trg_bump_conversation_activity
    after insert on public.direct_messages
    for each row execute function public.bump_conversation_activity();

-- Auto-create baseline profile & preferences row on Supabase Auth signup
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

    insert into public.profiles (id, display_name, avatar_url)
    values (
        new.id,
        v_name,
        coalesce(new.raw_user_meta_data ->> 'avatar_url', '')
    )
    on conflict (id) do nothing;

    insert into public.user_preferences (user_id)
    values (new.id)
    on conflict (user_id) do nothing;

    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_auth_user();

-- ============================================================================
-- 4. RPCs (FREE-TIER CONVERSATION RESOLUTION & LAZY MESSAGE CLEANUP)
-- ============================================================================

create or replace function public.get_or_create_direct_conversation(partner_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    v_me uuid := auth.uid();
    v_key text;
    v_conv_id uuid;
begin
    if v_me is null then
        raise exception 'Authentication required';
    end if;
    if partner_id is null or partner_id = v_me then
        raise exception 'Valid partner_id required';
    end if;
    if public.is_blocked_between(v_me, partner_id) then
        raise exception 'Messaging blocked between these users';
    end if;

    v_key := least(v_me::text, partner_id::text) || '_' || greatest(v_me::text, partner_id::text);

    select id into v_conv_id
      from public.direct_conversations
     where canonical_key = v_key;

    if v_conv_id is null then
        insert into public.direct_conversations (canonical_key)
        values (v_key)
        on conflict (canonical_key) do update set last_activity_at = public.direct_conversations.last_activity_at
        returning id into v_conv_id;
    end if;

    insert into public.conversation_participants (conversation_id, user_id)
    values (v_conv_id, v_me), (v_conv_id, partner_id)
    on conflict (conversation_id, user_id) do nothing;

    return v_conv_id;
end;
$$;

create or replace function public.prune_expired_room_messages(max_batch integer default 200)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    v_deleted integer := 0;
    v_limit integer := greatest(1, least(coalesce(max_batch, 200), 1000));
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;

    with doomed as (
        select id
        from public.room_messages
        where expires_at <= now()
        order by expires_at asc
        limit v_limit
    )
    delete from public.room_messages m
    using doomed d
    where m.id = d.id;

    get diagnostics v_deleted = row_count;
    return v_deleted;
end;
$$;

create or replace function public.cleanup_room_messages(target_room_id text)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    v_deleted integer := 0;
begin
    if not public.is_admin_or_owner() then
        raise exception 'Only administrators can clean up room messages';
    end if;

    delete from public.room_messages
    where room_id = target_room_id;

    get diagnostics v_deleted = row_count;
    return v_deleted;
end;
$$;

-- Enforce 3-message pending limit + DM privacy settings + block check at DB level
create or replace function public.can_send_direct_message(target_conv_id uuid, target_sender_id uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_partner_id uuid;
    v_dm_perm text;
    v_is_friend boolean := false;
    v_consecutive integer := 0;
begin
    if not exists (
        select 1 from public.conversation_participants
        where conversation_id = target_conv_id and user_id = target_sender_id
    ) then
        return false;
    end if;

    select user_id into v_partner_id
    from public.conversation_participants
    where conversation_id = target_conv_id
      and user_id <> target_sender_id
    limit 1;

    if v_partner_id is null then
        return false;
    end if;

    if public.is_blocked_between(target_sender_id, v_partner_id) then
        return false;
    end if;

    select dm_permissions into v_dm_perm
    from public.profiles
    where id = v_partner_id;

    select exists (
        select 1
        from public.friendships
        where user_a = least(target_sender_id, v_partner_id)
          and user_b = greatest(target_sender_id, v_partner_id)
          and status = 'accepted'
    ) into v_is_friend;

    if coalesce(v_dm_perm, 'everyone') = 'nobody' then
        return false;
    end if;

    if coalesce(v_dm_perm, 'everyone') = 'friends' and not v_is_friend then
        return false;
    end if;

    if not v_is_friend then
        -- Enforce 3-message cap before acceptance if partner hasn't replied yet
        select count(*) into v_consecutive
        from (
            select sender_id
            from public.direct_messages
            where conversation_id = target_conv_id
              and unsent_at is null
            order by created_at desc
            limit 4
        ) recent
        where recent.sender_id = target_sender_id;

        -- Only block when last 3+ messages were all from target_sender_id without reply
        if v_consecutive >= 3 and not exists (
            select 1
            from public.direct_messages dm
            where dm.conversation_id = target_conv_id
              and dm.sender_id = v_partner_id
              and dm.created_at > coalesce(
                  (select max(dm2.created_at) from public.direct_messages dm2 where dm2.conversation_id = target_conv_id and dm2.sender_id = target_sender_id),
                  to_timestamp(0)
              )
        ) then
            -- Count consecutive tail strictly
            declare
                v_tail integer := 0;
                rec record;
            begin
                for rec in
                    select sender_id
                    from public.direct_messages
                    where conversation_id = target_conv_id
                      and unsent_at is null
                    order by created_at desc
                    limit 4
                loop
                    exit when rec.sender_id <> target_sender_id;
                    v_tail := v_tail + 1;
                end loop;
                if v_tail >= 3 then
                    return false;
                end if;
            end;
        end if;
    end if;

    return true;
end;
$$;

-- ============================================================================
-- 5. ZERO-TRUST ROW LEVEL SECURITY (RLS)
-- ============================================================================

alter table public.profiles enable row level security;
alter table public.user_blocks enable row level security;
alter table public.profile_likes enable row level security;
alter table public.friendships enable row level security;
alter table public.rooms enable row level security;
alter table public.room_kicks enable row level security;
alter table public.room_messages enable row level security;
alter table public.direct_conversations enable row level security;
alter table public.conversation_participants enable row level security;
alter table public.direct_messages enable row level security;
alter table public.user_preferences enable row level security;
alter table public.device_tokens enable row level security;
alter table public.arcade_sessions enable row level security;

-- Revoke broad anonymous access
revoke all on all tables in schema public from anon;
grant usage on schema public to authenticated;
grant select, insert, update, delete on all tables in schema public to authenticated;
grant execute on function public.get_or_create_direct_conversation(uuid) to authenticated;
grant execute on function public.prune_expired_room_messages(integer) to authenticated;
grant execute on function public.cleanup_room_messages(text) to authenticated;

-- PROFILES
drop policy if exists "profiles_select_visible" on public.profiles;
create policy "profiles_select_visible" on public.profiles
    for select to authenticated
    using (
        id = auth.uid()
        or (
            visibility_public = true
            and not public.is_blocked_between(auth.uid(), id)
        )
        or public.is_admin_or_owner()
    );

drop policy if exists "profiles_insert_own" on public.profiles;
create policy "profiles_insert_own" on public.profiles
    for insert to authenticated
    with check (id = auth.uid());

drop policy if exists "profiles_update_own_or_admin" on public.profiles;
create policy "profiles_update_own_or_admin" on public.profiles
    for update to authenticated
    using (id = auth.uid() or public.is_admin_or_owner())
    with check (id = auth.uid() or public.is_admin_or_owner());

-- USER BLOCKS
drop policy if exists "blocks_manage_own" on public.user_blocks;
create policy "blocks_manage_own" on public.user_blocks
    for all to authenticated
    using (blocker_id = auth.uid())
    with check (blocker_id = auth.uid());

-- PROFILE LIKES
drop policy if exists "likes_select_auth" on public.profile_likes;
create policy "likes_select_auth" on public.profile_likes
    for select to authenticated
    using (liker_id = auth.uid() or liked_id = auth.uid());

drop policy if exists "likes_insert_own" on public.profile_likes;
create policy "likes_insert_own" on public.profile_likes
    for insert to authenticated
    with check (liker_id = auth.uid() and liked_id <> auth.uid());

drop policy if exists "likes_delete_own" on public.profile_likes;
create policy "likes_delete_own" on public.profile_likes
    for delete to authenticated
    using (liker_id = auth.uid());

-- FRIENDSHIPS
drop policy if exists "friendships_select_participant" on public.friendships;
create policy "friendships_select_participant" on public.friendships
    for select to authenticated
    using (auth.uid() = user_a or auth.uid() = user_b);

drop policy if exists "friendships_insert_requester" on public.friendships;
create policy "friendships_insert_requester" on public.friendships
    for insert to authenticated
    with check (
        requester_id = auth.uid()
        and (auth.uid() = user_a or auth.uid() = user_b)
        and not public.is_blocked_between(user_a, user_b)
    );

drop policy if exists "friendships_update_participant" on public.friendships;
create policy "friendships_update_participant" on public.friendships
    for update to authenticated
    using (auth.uid() = user_a or auth.uid() = user_b)
    with check (auth.uid() = user_a or auth.uid() = user_b);

drop policy if exists "friendships_delete_participant" on public.friendships;
create policy "friendships_delete_participant" on public.friendships
    for delete to authenticated
    using (auth.uid() = user_a or auth.uid() = user_b);

-- ROOMS
drop policy if exists "rooms_select_authenticated" on public.rooms;
create policy "rooms_select_authenticated" on public.rooms
    for select to authenticated
    using (true);

drop policy if exists "rooms_admin_modify" on public.rooms;
create policy "rooms_admin_modify" on public.rooms
    for all to authenticated
    using (public.is_admin_or_owner())
    with check (public.is_admin_or_owner());

-- ROOM KICKS
drop policy if exists "room_kicks_select" on public.room_kicks;
create policy "room_kicks_select" on public.room_kicks
    for select to authenticated
    using (user_id = auth.uid() or public.is_admin_or_owner());

drop policy if exists "room_kicks_admin_manage" on public.room_kicks;
create policy "room_kicks_admin_manage" on public.room_kicks
    for all to authenticated
    using (public.is_admin_or_owner())
    with check (public.is_admin_or_owner());

-- ROOM MESSAGES (Automatically filters expired messages & enforces lock/open/kick rules)
drop policy if exists "room_messages_select_active" on public.room_messages;
create policy "room_messages_select_active" on public.room_messages
    for select to authenticated
    using (
        expires_at > now()
        and not exists (
            select 1 from public.room_kicks k
            where k.room_id = room_messages.room_id
              and k.user_id = auth.uid()
              and k.kicked_until > now()
        )
    );

drop policy if exists "room_messages_insert_allowed" on public.room_messages;
create policy "room_messages_insert_allowed" on public.room_messages
    for insert to authenticated
    with check (
        sender_id = auth.uid()
        and public.can_post_in_room(room_id, auth.uid())
    );

drop policy if exists "room_messages_update_reactions_or_owner" on public.room_messages;
create policy "room_messages_update_reactions_or_owner" on public.room_messages
    for update to authenticated
    using (
        expires_at > now()
        and (
            sender_id = auth.uid()
            or public.is_admin_or_owner()
            or public.can_post_in_room(room_id, auth.uid())
        )
    )
    with check (
        sender_id = auth.uid()
        or public.is_admin_or_owner()
        or public.can_post_in_room(room_id, auth.uid())
    );

drop policy if exists "room_messages_delete_sender_or_admin" on public.room_messages;
create policy "room_messages_delete_sender_or_admin" on public.room_messages
    for delete to authenticated
    using (sender_id = auth.uid() or public.is_admin_or_owner());

-- DIRECT CONVERSATIONS & PARTICIPANTS
drop policy if exists "conversations_select_participant" on public.direct_conversations;
create policy "conversations_select_participant" on public.direct_conversations
    for select to authenticated
    using (public.is_conversation_participant(id));

drop policy if exists "participants_select_own_conversations" on public.conversation_participants;
create policy "participants_select_own_conversations" on public.conversation_participants
    for select to authenticated
    using (public.is_conversation_participant(conversation_id));

drop policy if exists "participants_update_own" on public.conversation_participants;
create policy "participants_update_own" on public.conversation_participants
    for update to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

-- DIRECT MESSAGES
drop policy if exists "dm_select_participant" on public.direct_messages;
create policy "dm_select_participant" on public.direct_messages
    for select to authenticated
    using (
        public.is_conversation_participant(conversation_id)
        and unsent_at is null
    );

drop policy if exists "dm_insert_participant" on public.direct_messages;
create policy "dm_insert_participant" on public.direct_messages
    for insert to authenticated
    with check (
        sender_id = auth.uid()
        and public.can_send_direct_message(conversation_id, auth.uid())
    );

drop policy if exists "dm_update_participant" on public.direct_messages;
create policy "dm_update_participant" on public.direct_messages
    for update to authenticated
    using (public.is_conversation_participant(conversation_id))
    with check (
        public.is_conversation_participant(conversation_id)
    );

drop policy if exists "dm_delete_sender" on public.direct_messages;
create policy "dm_delete_sender" on public.direct_messages
    for delete to authenticated
    using (sender_id = auth.uid());

-- USER PREFERENCES
drop policy if exists "prefs_manage_own" on public.user_preferences;
create policy "prefs_manage_own" on public.user_preferences
    for all to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

-- DEVICE TOKENS
drop policy if exists "tokens_manage_own" on public.device_tokens;
create policy "tokens_manage_own" on public.device_tokens
    for all to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

-- ARCADE SESSIONS
drop policy if exists "arcade_select_participants_or_open" on public.arcade_sessions;
create policy "arcade_select_participants_or_open" on public.arcade_sessions
    for select to authenticated
    using (true);

drop policy if exists "arcade_upsert_participant" on public.arcade_sessions;
create policy "arcade_upsert_participant" on public.arcade_sessions
    for insert to authenticated
    with check (challenger_id = auth.uid() or opponent_id = auth.uid() or challenger_id is null);

drop policy if exists "arcade_update_participant" on public.arcade_sessions;
create policy "arcade_update_participant" on public.arcade_sessions
    for update to authenticated
    using (challenger_id = auth.uid() or opponent_id = auth.uid() or challenger_id is null or opponent_id is null)
    with check (challenger_id = auth.uid() or opponent_id = auth.uid() or challenger_id is null or opponent_id is null);

-- ============================================================================
-- 6. SUPABASE REALTIME PUBLICATION
-- ============================================================================

do $$
begin
    if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
        begin
            alter publication supabase_realtime add table public.room_messages;
        exception when duplicate_object then null;
        end;
        begin
            alter publication supabase_realtime add table public.direct_messages;
        exception when duplicate_object then null;
        end;
        begin
            alter publication supabase_realtime add table public.rooms;
        exception when duplicate_object then null;
        end;
        begin
            alter publication supabase_realtime add table public.profiles;
        exception when duplicate_object then null;
        end;
        begin
            alter publication supabase_realtime add table public.arcade_sessions;
        exception when duplicate_object then null;
        end;
    end if;
end $$;

-- ============================================================================
-- 7. SUPABASE STORAGE BUCKET & STRICT POLICIES (FREE-TIER PROTECTED)
-- ============================================================================

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'profile-images',
    'profile-images',
    true,
    1048576, -- 1 MB max per image to protect free-tier storage quota
    array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "profile_images_public_read" on storage.objects;
create policy "profile_images_public_read" on storage.objects
    for select to authenticated
    using (bucket_id = 'profile-images');

drop policy if exists "profile_images_owner_upload" on storage.objects;
create policy "profile_images_owner_upload" on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'profile-images'
        and (storage.foldername(name))[1] = auth.uid()::text
    );

drop policy if exists "profile_images_owner_update" on storage.objects;
create policy "profile_images_owner_update" on storage.objects
    for update to authenticated
    using (
        bucket_id = 'profile-images'
        and (storage.foldername(name))[1] = auth.uid()::text
    )
    with check (
        bucket_id = 'profile-images'
        and (storage.foldername(name))[1] = auth.uid()::text
    );

drop policy if exists "profile_images_owner_delete" on storage.objects;
create policy "profile_images_owner_delete" on storage.objects
    for delete to authenticated
    using (
        bucket_id = 'profile-images'
        and (storage.foldername(name))[1] = auth.uid()::text
    );
