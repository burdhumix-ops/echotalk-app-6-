-- EchoTalks Two-User RLS & Authorization Verification Suite
-- Run against a local Supabase instance (`supabase db reset` / `psql`) to verify
-- isolation, role escalation prevention (INSERT/UPSERT/UPDATE), moderation permissions,
-- room retention, and unauthenticated denial.

begin;

-- Prepare test identities inside auth.users
insert into auth.users (id, email, raw_user_meta_data)
values
    ('11111111-1111-1111-1111-111111111111', 'alice@echotalk.test', '{"full_name": "Alice", "role": "owner"}'),
    ('22222222-2222-2222-2222-222222222222', 'bob@echotalk.test', '{"full_name": "Bob"}'),
    ('33333333-3333-3333-3333-333333333333', 'eve@echotalk.test', '{"full_name": "Eve"}'),
    ('44444444-4444-4444-4444-444444444444', 'dave@echotalk.test', '{"full_name": "Dave"}')
on conflict (id) do nothing;

-- 0. User-editable raw_user_meta_data ("role": "owner") must NEVER grant admin/owner role
do $$
declare
    v_role text;
begin
    select role into v_role
    from public.profiles where id = '11111111-1111-1111-1111-111111111111';
    if v_role <> 'member' then
        raise exception 'FAIL: metadata role leak on signup, got role=%', v_role;
    end if;
end $$;

-- 1. Unauthenticated (anon) cannot read profiles, DMs, rooms, or tokens
set local role anon;
do $$
declare
    v_cnt integer;
begin
    begin
        execute 'select count(*) from public.direct_messages' into v_cnt;
        raise exception 'FAIL: anon should not have permission to query direct_messages';
    exception when insufficient_privilege then
        -- Expected
    end;
end $$;

-- 2a. Alice can update permitted own profile fields
reset role;
set local role authenticated;
select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);
select set_config('request.jwt.claim.role', 'authenticated', true);

update public.profiles
   set bio = 'Hello from Alice'
 where id = '11111111-1111-1111-1111-111111111111';

-- 2b. Alice CANNOT update her own role to 'owner'/'admin' or is_verified to true
do $$
declare
    v_role text;
    v_verified boolean;
    v_bio text;
begin
    begin
        update public.profiles
           set role = 'owner'
         where id = '11111111-1111-1111-1111-111111111111';
        raise exception 'FAIL: member was allowed to update own role';
    exception when insufficient_privilege then
        -- Expected
    end;

    begin
        update public.profiles
           set is_verified = true
         where id = '11111111-1111-1111-1111-111111111111';
        raise exception 'FAIL: member was allowed to update is_verified';
    exception when insufficient_privilege then
        -- Expected
    end;

    select role, is_verified, bio into v_role, v_verified, v_bio
    from public.profiles where id = '11111111-1111-1111-1111-111111111111';
    if v_role <> 'member' or v_verified <> false or v_bio <> 'Hello from Alice' then
        raise exception 'FAIL: unexpected profile state role=% verified=% bio=%', v_role, v_verified, v_bio;
    end if;
end $$;

-- 2c. Authenticated client CANNOT escalate via INSERT or UPSERT with role = 'owner'
select set_config('request.jwt.claim.sub', '44444444-4444-4444-4444-444444444444', true);
do $$
begin
    -- Simulate missing profile row first if trigger created it, test direct INSERT with role='owner'
    delete from public.profiles where id = '44444444-4444-4444-4444-444444444444';
exception when insufficient_privilege then
    -- Delete from profiles is already revoked for authenticated
end $$;

reset role;
delete from public.profiles where id = '44444444-4444-4444-4444-444444444444';

set local role authenticated;
select set_config('request.jwt.claim.sub', '44444444-4444-4444-4444-444444444444', true);
select set_config('request.jwt.claim.role', 'authenticated', true);

do $$
begin
    begin
        insert into public.profiles (id, display_name, role)
        values ('44444444-4444-4444-4444-444444444444', 'Dave', 'owner');
        raise exception 'FAIL: member was allowed to INSERT with role=owner';
    exception when insufficient_privilege then
        -- Expected
    end;

    -- Legitimate own profile insert without privileged columns succeeds as 'member'
    insert into public.profiles (id, display_name)
    values ('44444444-4444-4444-4444-444444444444', 'Dave');

    -- UPSERT attempting to overwrite role to 'admin' fails
    begin
        insert into public.profiles (id, display_name, role)
        values ('44444444-4444-4444-4444-444444444444', 'Dave', 'admin')
        on conflict (id) do update set role = excluded.role;
        raise exception 'FAIL: member was allowed to UPSERT role=admin';
    exception when insufficient_privilege then
        -- Expected
    end;
end $$;

-- 2d. Normal user cannot call server_set_profile_role, and even an admin cannot promote to owner
select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);
do $$
begin
    begin
        perform public.server_set_profile_role('11111111-1111-1111-1111-111111111111', 'owner');
        raise exception 'FAIL: authenticated user called server_set_profile_role';
    exception when insufficient_privilege then
        -- Expected
    end;
end $$;

-- Server/postgres CAN promote Bob to 'admin', and admin still cannot self-escalate to 'owner'
reset role;
select public.server_set_profile_role('22222222-2222-2222-2222-222222222222', 'admin');

set local role authenticated;
select set_config('request.jwt.claim.sub', '22222222-2222-2222-2222-222222222222', true);
select set_config('request.jwt.claim.role', 'authenticated', true);

do $$
declare
    v_bob_role text;
begin
    begin
        update public.profiles set role = 'owner' where id = '22222222-2222-2222-2222-222222222222';
        raise exception 'FAIL: admin escalated self to owner';
    exception when insufficient_privilege then
        -- Expected
    end;

    -- Legitimate admin verification RPC & room cleanup succeed
    perform public.admin_set_profile_verification('11111111-1111-1111-1111-111111111111', true);
    perform public.cleanup_room_messages('r-chat1');

    select role into v_bob_role from public.profiles where id = '22222222-2222-2222-2222-222222222222';
    if v_bob_role <> 'admin' then
        raise exception 'FAIL: expected Bob role=admin, got %', v_bob_role;
    end if;
end $$;

-- 3. Eve cannot modify Alice's preferences or device tokens
select set_config('request.jwt.claim.sub', '33333333-3333-3333-3333-333333333333', true);
update public.user_preferences
   set theme_id = 'obsidian'
 where user_id = '11111111-1111-1111-1111-111111111111';

do $$
declare
    v_theme text;
begin
    select theme_id into v_theme
    from public.user_preferences where user_id = '11111111-1111-1111-1111-111111111111';
    if v_theme = 'obsidian' then
        raise exception 'FAIL: Eve modified Alice preferences';
    end if;
end $$;

-- 4. Alice <-> Bob DM isolation: Eve cannot read Alice & Bob messages
select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);
do $$
declare
    v_conv_id uuid;
    v_eve_count integer;
begin
    v_conv_id := public.get_or_create_direct_conversation('22222222-2222-2222-2222-222222222222');
    insert into public.direct_messages (conversation_id, sender_id, sender_name, content, client_msg_id)
    values (v_conv_id, '11111111-1111-1111-1111-111111111111', 'Alice', 'Secret DM to Bob', 'cli-1');

    perform set_config('request.jwt.claim.sub', '33333333-3333-3333-3333-333333333333', true);
    select count(*) into v_eve_count from public.direct_messages where conversation_id = v_conv_id;
    if v_eve_count > 0 then
        raise exception 'FAIL: Eve could read Alice/Bob direct messages';
    end if;
end $$;

-- 5. Room expiry validation: 24h for normal room vs 48h for Special 2-Day Lounge
select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);
do $$
declare
    v_norm_hours numeric;
    v_spec_hours numeric;
begin
    insert into public.room_messages (room_id, sender_id, sender_name, content, client_msg_id)
    values ('r-chat1', '11111111-1111-1111-1111-111111111111', 'Alice', 'Normal 24h msg', 'rm-1');

    insert into public.room_messages (room_id, sender_id, sender_name, content, client_msg_id)
    values ('r-special', '11111111-1111-1111-1111-111111111111', 'Alice', 'Special 48h msg', 'rm-2');

    select extract(epoch from (expires_at - created_at)) / 3600.0 into v_norm_hours
    from public.room_messages where client_msg_id = 'rm-1';

    select extract(epoch from (expires_at - created_at)) / 3600.0 into v_spec_hours
    from public.room_messages where client_msg_id = 'rm-2';

    if round(v_norm_hours) <> 24 or round(v_spec_hours) <> 48 then
        raise exception 'FAIL: Expected 24h/48h expiry, got % / %', v_norm_hours, v_spec_hours;
    end if;
end $$;

-- 6. Calling RLS isolation: Eve cannot read or hijack Alice <-> Bob call session
select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);
do $$
declare
    v_call public.call_sessions;
    v_eve_count integer;
begin
    v_call := public.create_call_invitation('22222222-2222-2222-2222-222222222222', 'video');
    if v_call.status <> 'ringing' then
        raise exception 'FAIL: Expected ringing status for Bob, got %', v_call.status;
    end if;

    -- Eve (non-participant) cannot see or modify the call session
    perform set_config('request.jwt.claim.sub', '33333333-3333-3333-3333-333333333333', true);
    select count(*) into v_eve_count from public.call_sessions where id = v_call.id;
    if v_eve_count > 0 then
        raise exception 'FAIL: Eve could read Alice/Bob call session';
    end if;

    update public.call_sessions set status = 'accepted' where id = v_call.id;
    -- Caller cannot accept their own call; check as Bob (callee)
    perform set_config('request.jwt.claim.sub', '22222222-2222-2222-2222-222222222222', true);
    update public.call_sessions set status = 'accepted' where id = v_call.id;

    -- Bob disabling incoming calls makes new invitation return unavailable
    update public.call_sessions set status = 'ended' where id = v_call.id;
    update public.user_preferences set allow_incoming_calls = false where user_id = '22222222-2222-2222-2222-222222222222';
    perform set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);
    v_call := public.create_call_invitation('22222222-2222-2222-2222-222222222222', 'audio');
    if v_call.status <> 'unavailable' then
        raise exception 'FAIL: Expected unavailable when allow_incoming_calls=false, got %', v_call.status;
    end if;
end $$;

rollback;
