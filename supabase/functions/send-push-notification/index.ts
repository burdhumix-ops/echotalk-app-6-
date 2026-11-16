// EchoTalks Zero-Cost Push Dispatcher (Supabase Edge Function -> FCM HTTP v1 API)
// Deploy with: supabase functions deploy send-push-notification
// Required Secret (in Supabase Edge Function Secrets ONLY): FCM_SERVICE_ACCOUNT_JSON

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";

interface PushRequestBody {
  recipient_id?: string;
  conversation_id?: string;
  room_id?: string;
  message_id?: string;
  client_msg_id?: string;
  call_id?: string;
  call_type?: "audio" | "video";
  preview_text?: string;
  notification_type?: "dm" | "msg_req" | "conn_req" | "room_mentions" | "room_invite" | "incoming_call" | "missed_call";
}

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function base64UrlEncode(data: Uint8Array | string): string {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : data;
  let binary = "";
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const cleaned = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s+/g, "");
  const binary = atob(cleaned);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

async function getFcmAccessToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = base64UrlEncode(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claimSet = base64UrlEncode(
    JSON.stringify({
      iss: sa.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    }),
  );
  const signingInput = `${header}.${claimSet}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(signingInput),
  );
  const jwt = `${signingInput}.${base64UrlEncode(new Uint8Array(signature))}`;

  const tokenResp = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  if (!tokenResp.ok) {
    const errText = await tokenResp.text();
    throw new Error(`Google OAuth token exchange failed: ${tokenResp.status} ${errText}`);
  }
  const tokenJson = await tokenResp.json();
  return tokenJson.access_token as string;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    const fcmSaRaw = Deno.env.get("FCM_SERVICE_ACCOUNT_JSON") ?? "";

    if (!supabaseUrl || !anonKey || !serviceRoleKey) {
      return new Response(JSON.stringify({ error: "Supabase environment not initialized" }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }
    if (!fcmSaRaw) {
      return new Response(
        JSON.stringify({
          sent: 0,
          skipped: true,
          reason: "FCM_SERVICE_ACCOUNT_JSON secret not configured in Supabase Edge Function secrets",
        }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    const authHeader = req.headers.get("Authorization") ?? "";
    if (!authHeader.startsWith("Bearer ")) {
      return new Response(JSON.stringify({ error: "Missing Bearer authorization token" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Verify caller identity using their JWT
    const userClient = createClient(supabaseUrl, anonKey, {
      global: { headers: { Authorization: authHeader } },
    });
    const {
      data: { user },
      error: userErr,
    } = await userClient.auth.getUser();
    if (userErr || !user) {
      return new Response(JSON.stringify({ error: "Unauthorized caller" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const body: PushRequestBody = await req.json();
    const recipientId = body.recipient_id?.trim();
    if (!recipientId || recipientId === user.id) {
      return new Response(JSON.stringify({ sent: 0, reason: "Invalid or self recipient" }), {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const adminClient = createClient(supabaseUrl, serviceRoleKey);

    // Authorization check: Verify caller participates in DM or room (or friendship request)
    if (body.conversation_id) {
      const { data: participants } = await adminClient
        .from("conversation_participants")
        .select("user_id")
        .eq("conversation_id", body.conversation_id);
      const memberIds = (participants ?? []).map((p) => p.user_id);
      if (!memberIds.includes(user.id) || !memberIds.includes(recipientId)) {
        return new Response(JSON.stringify({ error: "Caller is not an authorized participant" }), {
          status: 403,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }
    }

    if (body.call_id) {
      const { data: callRow } = await adminClient
        .from("call_sessions")
        .select("caller_id, callee_id")
        .eq("id", body.call_id)
        .maybeSingle();
      if (
        !callRow ||
        !((callRow.caller_id === user.id && callRow.callee_id === recipientId) ||
          (callRow.callee_id === user.id && callRow.caller_id === recipientId))
      ) {
        return new Response(JSON.stringify({ error: "Caller is not an authorized participant of this call" }), {
          status: 403,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }
    }

    // Respect recipient notification preferences & blocks
    const { data: blockRow } = await adminClient
      .from("user_blocks")
      .select("blocker_id")
      .or(
        `and(blocker_id.eq.${user.id},blocked_id.eq.${recipientId}),and(blocker_id.eq.${recipientId},blocked_id.eq.${user.id})`,
      )
      .limit(1)
      .maybeSingle();
    if (blockRow) {
      return new Response(JSON.stringify({ sent: 0, skipped: true, reason: "Blocked" }), {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const [{ data: prefs }, { data: senderProfile }, { data: tokens }] = await Promise.all([
      adminClient.from("user_preferences").select("*").eq("user_id", recipientId).maybeSingle(),
      adminClient.from("profiles").select("display_name").eq("id", user.id).maybeSingle(),
      adminClient.from("device_tokens").select("id, token").eq("user_id", recipientId),
    ]);

    const nType = body.notification_type ?? "dm";
    const allowedByPrefs =
      !prefs ||
      (nType === "dm" && prefs.notify_direct_messages !== false) ||
      (nType === "msg_req" && prefs.notify_message_requests !== false) ||
      (nType === "conn_req" && prefs.notify_connection_requests !== false) ||
      (nType === "room_invite" && prefs.notify_room_invitations !== false) ||
      (nType === "room_mentions" && prefs.notify_room_mentions !== false) ||
      (nType === "incoming_call" && prefs.notify_incoming_calls !== false && prefs.allow_incoming_calls !== false) ||
      (nType === "missed_call" && prefs.notify_missed_calls !== false);

    if (!allowedByPrefs) {
      return new Response(JSON.stringify({ sent: 0, skipped: true, reason: "Muted by recipient preferences" }), {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    if (!tokens || tokens.length === 0) {
      return new Response(JSON.stringify({ sent: 0, skipped: true, reason: "No registered device tokens" }), {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const sa: ServiceAccount = JSON.parse(fcmSaRaw);
    const accessToken = await getFcmAccessToken(sa);
    const senderName = senderProfile?.display_name ?? "EchoTalks Member";
    const preview = (body.preview_text ?? "Sent you a new message").slice(0, 140);
    const callLabel = body.call_type === "video" ? "Video Call" : "Voice Call";
    const title =
      nType === "incoming_call"
        ? `Incoming ${callLabel} from ${senderName}`
        : nType === "missed_call"
          ? `Missed ${callLabel} from ${senderName}`
          : nType === "conn_req"
            ? `${senderName} wants to connect`
            : `New message from ${senderName}`;
    const androidChannelId =
      nType === "incoming_call" ? "echotalk_calls_channel" : "echotalk_messages_channel";

    let sentCount = 0;
    const staleTokenIds: string[] = [];

    for (const row of tokens) {
      const fcmResp = await fetch(
        `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            message: {
              token: row.token,
              notification: {
                title,
                body: preview,
              },
              data: {
                sender_id: user.id,
                sender_name: senderName,
                message_id: body.message_id ?? body.client_msg_id ?? "",
                conversation_id: body.conversation_id ?? "",
                room_id: body.room_id ?? "",
                call_id: body.call_id ?? "",
                call_type: body.call_type ?? "",
                notification_type: nType,
              },
              android: {
                priority: "high",
                collapse_key: body.call_id ?? body.client_msg_id ?? body.conversation_id ?? user.id,
                notification: {
                  channel_id: androidChannelId,
                  tag: body.call_id ?? body.client_msg_id ?? body.message_id ?? undefined,
                },
              },
            },
          }),
        },
      );

      if (fcmResp.ok) {
        sentCount++;
      } else {
        const errBody = await fcmResp.text();
        if (fcmResp.status === 404 || errBody.includes("UNREGISTERED")) {
          staleTokenIds.push(row.id);
        }
      }
    }

    if (staleTokenIds.length > 0) {
      await adminClient.from("device_tokens").delete().in("id", staleTokenIds);
    }

    return new Response(
      JSON.stringify({ sent: sentCount, cleaned_stale_tokens: staleTokenIds.length }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } },
    );
  } catch (err) {
    return new Response(JSON.stringify({ error: String(err) }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
