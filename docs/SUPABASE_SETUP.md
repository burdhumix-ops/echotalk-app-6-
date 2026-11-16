# EchoTalks — Zero-Cost Supabase + FCM Backend Setup Guide

This guide walks you step by step through connecting EchoTalks to a **100% free** live cloud backend using **Supabase Free** + **Firebase Cloud Messaging (FCM Spark)**. No credit card or paid plan is required.

---

## 1. Create a Free Supabase Project

1. Go to [https://supabase.com](https://supabase.com) and sign in with GitHub or email.
2. Click **New project**.
3. Choose the **Free** plan (`$0/month`).
4. Enter a project name (for example `echotalks`) and choose a strong **Database Password**.
   - **Important:** Keep your Database Password private. **Never** put it into the Android app, `.env`, or Git.
5. Select the region closest to your users and click **Create new project** (takes ~2 minutes).

---

## 2. Create the Database Schema, RLS Policies & Storage Bucket

1. In your Supabase Dashboard sidebar, open **SQL Editor**.
2. Click **New query**.
3. Open `supabase/migrations/0001_initial_schema.sql` from this repository, copy the entire file, paste it into the SQL Editor, and click **Run**.
4. Then open `supabase/migrations/0002_admin_role_security.sql`, copy the entire file, paste it into a **New query** in the SQL Editor, and click **Run**. This locks down `profiles.role` against client `INSERT`/`UPSERT`/`UPDATE` escalation and installs the server-only `server_set_profile_role` function.
5. Finally, open `supabase/migrations/0003_voice_video_calls.sql`, copy the entire file, paste it into a **New query** in the SQL Editor, and click **Run**. This creates `public.call_sessions`, adds `user_preferences.allow_incoming_calls`, enables Realtime on `call_sessions`, and installs `create_call_invitation`.
6. Verify all migrations finish with no errors. This creates:
   - Tables (`profiles`, `user_blocks`, `profile_likes`, `friendships`, `rooms`, `room_kicks`, `room_messages`, `direct_conversations`, `conversation_participants`, `direct_messages`, `user_preferences`, `device_tokens`, `arcade_sessions`, `call_sessions`)
   - Row-Level Security (**Zero-Trust RLS**) and column-level role protections on every table
   - RPC helpers (`get_or_create_direct_conversation`, `prune_expired_room_messages`, `cleanup_room_messages`, `admin_set_profile_verification`, `create_call_invitation`)
   - Realtime publication for `room_messages`, `direct_messages`, and `call_sessions`
   - Public `profile-images` storage bucket capped at **1 MB** per upload (`image/jpeg`, `image/png`, `image/webp`)
7. To promote your own account to `owner` or `admin` safely on the server, sign into the app once to create your `auth.users` / `profiles` row, then run `supabase/scripts/promote_user_role.sql` in the **Supabase Dashboard SQL Editor** with your email.
8. *(Optional)* Run `supabase/tests/rls_security_test.sql` in the SQL Editor (or via `supabase db test`) to verify RLS isolation and role-escalation protection pass.

---

## 3. Configure Authentication (Email + Google Sign-In)

1. In your Supabase Dashboard, open **Authentication → Providers**.
2. Ensure **Email** is enabled.
   - *Free-tier tip:* Supabase's built-in default SMTP is rate-limited (~2–4 emails/hour). During testing, you can temporarily toggle off **Confirm email** under **Authentication → Providers → Email** if you hit rate limits, or connect a free custom SMTP later.
3. Expand **Google** and enable it:
   - In Google Cloud Console (**APIs & Services → Credentials**), create an **OAuth 2.0 Web Client ID** and add your Android app SHA-1 + package name `co.znxdnd.echotalk`.
   - Paste your Google **Client ID** and **Client Secret** into the Supabase Google provider settings and save.
   - Add `default_web_client_id` to `google-services.json` / `app/src/main/res/values/strings.xml` so Android Credential Manager can request Google ID Tokens.
4. Under **Authentication → URL Configuration**, add your app redirect URI if needed (`co.znxdnd.echotalk://auth-callback`).

---

## 4. Set Up Free Firebase Cloud Messaging (FCM Only — Spark Plan)

EchoTalks uses Firebase **only** for free Android push notifications (FCM), while all database, auth, realtime, and storage stay on Supabase Free.

1. Go to [https://console.firebase.google.com](https://console.firebase.google.com) and create or open your **Spark (Free)** project. **Do not upgrade to Blaze.**
2. Add an Android app with package name `co.znxdnd.echotalk`, download `google-services.json`, and place it in `app/google-services.json`.
3. In Firebase Console → **Project Settings → Service Accounts**, click **Generate new private key** to download a Firebase Admin JSON file.
   - **CRITICAL SECURITY RULE:** Keep this JSON file completely off the Android app and out of Git! It belongs **only** inside Supabase Edge Function Secrets.

---

## 5. Set Up LiveKit Cloud — Build ($0 Free) Plan & Deploy Edge Functions

EchoTalks uses **LiveKit Cloud Build ($0/month Free Plan)** for real-time 1-to-1 WebRTC voice and video media transport.
- **Verified Build Plan Limits:** `$0/month`, `5,000` WebRTC participant-minutes/month, `50 GB/month` downstream data transfer, `100` concurrent connections. Do **not** add billing details or upgrade to Ship/Scale.
- **Zero-Secret Rule:** `LIVEKIT_API_KEY` and `LIVEKIT_API_SECRET` must ONLY be stored in Supabase Edge Function secrets — NEVER inside Android source code or `.env`.

1. Sign up/in at [https://cloud.livekit.io](https://cloud.livekit.io) and select the **Build ($0 Free)** plan.
2. Create a project and copy its **WebSocket URL** (`wss://your-project.livekit.cloud`), **API Key**, and **API Secret**.
3. Install the Supabase CLI and link your project (`tifdmhqhoeiqcbqjcchu`):
   ```bash
   npm install -g supabase
   supabase login
   supabase link --project-ref tifdmhqhoeiqcbqjcchu
   ```
4. Store your Firebase Service Account JSON and LiveKit credentials as Supabase Edge Function secrets:
   ```bash
   supabase secrets set FCM_SERVICE_ACCOUNT_JSON="$(cat path/to/firebase-service-account.json)"
   supabase secrets set LIVEKIT_URL="wss://YOUR-PROJECT.livekit.cloud" LIVEKIT_API_KEY="YOUR_KEY" LIVEKIT_API_SECRET="YOUR_SECRET"
   ```
5. Deploy both Edge Functions:
   ```bash
   supabase functions deploy send-push-notification
   supabase functions deploy livekit-token
   ```

---

## 6. Connect the Android App via AI Studio Secrets

Only **two public client-safe values** are placed into the Android app configuration:

1. In Supabase Dashboard → **Project Settings → API**, copy:
   - **Project URL** (`https://tifdmhqhoeiqcbqjcchu.supabase.co`)
   - **Publishable / anon key** (public key protected by RLS — **NEVER** use the `service_role` secret key in Android!)
2. Open the **Secrets** panel in AI Studio (or create an untracked `.env` file locally from `.env.example`) and add:
   - `SUPABASE_URL=https://tifdmhqhoeiqcbqjcchu.supabase.co`
   - `SUPABASE_PUBLISHABLE_KEY=YOUR_PUBLISHABLE_OR_ANON_KEY`
3. Rebuild and run the app. Offline queued messages, rooms, DMs, profile photo uploads, FCM pushes, and 1-to-1 voice/video calls will sync live.

---

## 7. Free-Tier Guardrails Built Into EchoTalks

- **Calling Free-Plan Protection:**
  - `livekit-token` tracks monthly participant-minutes and stops issuing tokens at **5,000 participant-minutes/month** (`429 free_limit_reached`), displaying an explicit allowance notice without triggering billing upgrades.
  - Recording (`roomRecord: false`), transcription, SIP, and AI agent features are explicitly disabled in every minted token.
  - Video uses adaptive stream, Dynacast, and 540p (`VideoPreset169.H540`) capture defaults to protect bandwidth and lower-powered devices.
- **Storage Protection:** All gallery photos selected via Android Photo Picker are automatically compressed on-device (`<= 720px` longest edge, `<= 300 KB` JPEG) before uploading to `profile-images/{uid}/...` (enforced by a strict 1 MB bucket cap).
- **Database Protection:**
  - DM threads load in pages of **30 messages** with a **"Load older messages"** button.
  - Public room messages automatically expire (**24 hours** for standard rooms, **48 hours** for Special VIP Lounge) and are pruned lazily via `prune_expired_room_messages`.
  - Presence heartbeats are throttled to once every **25 seconds**.
  - Idempotent `client_msg_id` keys prevent duplicate sends when reconnecting from offline mode.
