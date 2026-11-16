-- EchoTalks Server-Only Role Promotion Script
-- Run this ONLY in the Supabase Dashboard SQL Editor (postgres) or via a trusted
-- server script using the service_role key. NEVER call or ship this in Android code.

-- Promote an existing account by email to 'owner' (or 'admin' / 'member'):
do $$
declare
    v_target_email text := 'REPLACE_WITH_OWNER_EMAIL@example.com';
    v_new_role     text := 'owner'; -- allowed: 'owner', 'admin', 'member'
    v_user_id      uuid;
begin
    select id
      into v_user_id
      from auth.users
     where lower(email) = lower(trim(v_target_email))
     limit 1;

    if v_user_id is null then
        raise exception 'No auth.users account found for email: %', v_target_email;
    end if;

    perform public.server_set_profile_role(v_user_id, v_new_role);
    raise notice 'Updated user % (%) to role=%', v_target_email, v_user_id, v_new_role;
end $$;
