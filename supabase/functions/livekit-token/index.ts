// EchoTalks Secure LiveKit Token Minting Edge Function (Build Free Plan)
// Deploy with: supabase functions deploy livekit-token
// Required Secrets (in Supabase Edge Function Secrets ONLY — NEVER place in Android APK):
//   LIVEKIT_URL=wss://<your-project>.livekit.cloud
//   LIVEKIT_API_KEY=<livekit-api-key>
//   LIVEKIT_API_SECRET=<livekit-api-secret>

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";

interface TokenRequestBody {
  call_id?: string;
}

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

// Free Build-plan safeguard: 5,000 WebRTC participant-minutes per calendar month
const FREE_BUILD_PLAN_MAX_PARTICIPANT_MINUTES = 5000;
const SHORT_LIVED_TOKEN_TTL_SECONDS = 300; // 5-minute TTL

function base64UrlEncode(data: Uint8Array | string): string {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : data;
  let binary = "";
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function signLiveKitJwt(
  apiKey: string,
  apiSecret: string,
  identity: string,
  displayName: string,
  roomName: string,
  ttlSeconds: number,
): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = base64UrlEncode(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const payload = base64UrlEncode(
    JSON.stringify({
      iss: apiKey,
      sub: identity,
      name: displayName,
      nbf: now - 5,
      exp: now + ttlSeconds,
      video: {
        roomJoin: true,
        room: roomName,
        canPublish: true,
        canSubscribe: true,
        canPublishData: true,
        canUpdateOwnMetadata: false,
        roomCreate: false,
        roomList: false,
        roomAdmin: false,
        roomRecord: false,
        hidden: false,
      },
    }),
  );

  const signingInput = `${header}.${payload}`;
  const cryptoKey = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(apiSecret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "HMAC",
    cryptoKey,
    new TextEncoder().encode(signingInput),
  );
  return `${signingInput}.${base64UrlEncode(new Uint8Array(signature))}`;
}

function jsonResponse(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return jsonResponse({ error: "method_not_allowed", message: "POST required" }, 405);
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    const livekitUrl = (Deno.env.get("LIVEKIT_URL") ?? "").trim();
    const livekitApiKey = (Deno.env.get("LIVEKIT_API_KEY") ?? "").trim();
    const livekitApiSecret = (Deno.env.get("LIVEKIT_API_SECRET") ?? "").trim();

    if (!supabaseUrl || !anonKey || !serviceRoleKey) {
      return jsonResponse(
        { error: "supabase_not_initialized", message: "Supabase server environment missing." },
        500,
      );
    }

    if (!livekitUrl || !livekitApiKey || !livekitApiSecret) {
      return jsonResponse(
        {
          error: "livekit_not_configured",
          message:
            "LiveKit server secrets (LIVEKIT_URL, LIVEKIT_API_KEY, LIVEKIT_API_SECRET) are not configured in Supabase Edge Function secrets.",
        },
        503,
      );
    }

    const authHeader = req.headers.get("Authorization") ?? "";
    if (!authHeader.startsWith("Bearer ")) {
      return jsonResponse(
        { error: "unauthorized", message: "Missing Bearer authorization token." },
        401,
      );
    }

    // 1. Verify authenticated user identity strictly from Supabase Auth JWT
    const userClient = createClient(supabaseUrl, anonKey, {
      global: { headers: { Authorization: authHeader } },
    });
    const {
      data: { user },
      error: userErr,
    } = await userClient.auth.getUser();

    if (userErr || !user) {
      return jsonResponse(
        { error: "unauthorized", message: "Invalid or expired user session." },
        401,
      );
    }

    // 2. Validate call_id ONLY (never trust client-supplied room_name or user_id)
    const body: TokenRequestBody = await req.json();
    const callId = (body.call_id ?? "").trim();
    if (!callId) {
      return jsonResponse(
        { error: "invalid_request", message: "call_id is required." },
        400,
      );
    }

    const adminClient = createClient(supabaseUrl, serviceRoleKey);

    const { data: callSession, error: callErr } = await adminClient
      .from("call_sessions")
      .select("id, room_name, caller_id, callee_id, call_type, status")
      .eq("id", callId)
      .maybeSingle();

    if (callErr || !callSession) {
      return jsonResponse(
        { error: "call_not_found", message: "Call session was not found." },
        404,
      );
    }

    // 3. Strictly authorize only the caller or callee of this call session
    if (user.id !== callSession.caller_id && user.id !== callSession.callee_id) {
      return jsonResponse(
        {
          error: "forbidden_participant",
          message: "Unauthorized: only the caller or intended recipient may join this call.",
        },
        403,
      );
    }

    // 4. Reject if call has already terminated
    const activeStatuses = ["initiating", "ringing", "accepted", "connecting", "connected"];
    if (!activeStatuses.includes(callSession.status)) {
      return jsonResponse(
        {
          error: "call_inactive",
          message: `Call is no longer active (status: ${callSession.status}).`,
        },
        409,
      );
    }

    // 5. Reject if users blocked one another
    const otherUserId =
      user.id === callSession.caller_id ? callSession.callee_id : callSession.caller_id;
    const { data: blockRow } = await adminClient
      .from("user_blocks")
      .select("blocker_id")
      .or(
        `and(blocker_id.eq.${user.id},blocked_id.eq.${otherUserId}),and(blocker_id.eq.${otherUserId},blocked_id.eq.${user.id})`,
      )
      .limit(1)
      .maybeSingle();

    if (blockRow) {
      return jsonResponse(
        { error: "user_blocked", message: "Calling is unavailable because a block is active." },
        403,
      );
    }

    // 6. Enforce Free Build-Plan Monthly Allowance Guardrail (no auto-upgrades or overages)
    const monthStartIso = new Date(
      Date.UTC(new Date().getUTCFullYear(), new Date().getUTCMonth(), 1, 0, 0, 0),
    ).toISOString();
    const { data: monthCalls } = await adminClient
      .from("call_sessions")
      .select("duration_seconds")
      .gte("created_at", monthStartIso);

    const totalParticipantMinutes = (monthCalls ?? []).reduce((sum, row) => {
      const secs = typeof row.duration_seconds === "number" ? row.duration_seconds : 0;
      return sum + Math.ceil(secs / 60) * 2; // 2 participants per 1-on-1 call
    }, 0);

    if (totalParticipantMinutes >= FREE_BUILD_PLAN_MAX_PARTICIPANT_MINUTES) {
      return jsonResponse(
        {
          error: "free_limit_reached",
          message:
            "Monthly free calling allowance (5,000 participant-minutes on LiveKit Build plan) has been reached. Automatic billing upgrades are disabled.",
        },
        429,
      );
    }

    const { data: callerProfile } = await adminClient
      .from("profiles")
      .select("display_name")
      .eq("id", user.id)
      .maybeSingle();

    const displayName = (callerProfile?.display_name ?? "EchoTalks Member").slice(0, 64);

    // 7. Mint short-lived LiveKit token scoped strictly to server-verified room_name & identity
    const token = await signLiveKitJwt(
      livekitApiKey,
      livekitApiSecret,
      user.id,
      displayName,
      callSession.room_name,
      SHORT_LIVED_TOKEN_TTL_SECONDS,
    );

    return jsonResponse(
      {
        token,
        ws_url: livekitUrl,
        room_name: callSession.room_name,
        call_id: callSession.id,
        expires_in: SHORT_LIVED_TOKEN_TTL_SECONDS,
        plan: "build_free",
      },
      200,
    );
  } catch (err) {
    return jsonResponse(
      { error: "token_generation_failed", message: String(err) },
      500,
    );
  }
});
