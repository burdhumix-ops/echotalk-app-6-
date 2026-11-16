const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const EVE_UID = "eve_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// --- TESTS: 4 SHARED SOCIAL COLLECTIONS & DM ISOLATION ---

test("users: Unauthenticated user cannot read profiles", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("users: Authenticated user can create and update own public profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Alice Vance",
      username: "@alice",
      age: 24,
      country: "US",
      likesCount: 15,
      themePreference: "Pink Blush",
      online: true
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(bobDb.collection("users").doc(ALICE_UID).get());
});

test("users: Bob cannot overwrite Alice's public profile", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Spoofed",
      username: "@spoofed"
    })
  );
});

test("friendships: Alice can send friendship request to Bob", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("friendships").doc("alice_bob_rel").set({
      senderId: ALICE_UID,
      receiverId: BOB_UID,
      status: "PENDING"
    })
  );
});

test("friendships: Bob can accept friendship from Alice", async () => {
  // Seed initial pending request
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("friendships").doc("alice_bob_rel").set({
      senderId: ALICE_UID,
      receiverId: BOB_UID,
      status: "PENDING"
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("friendships").doc("alice_bob_rel").update({
      status: "ACCEPTED"
    })
  );
});

test("active_game_sessions: Players can create and update game sessions with WebRTC room codes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("active_game_sessions").doc("session_99").set({
      sessionId: "session_99",
      gameType: "chess",
      challenger: ALICE_UID,
      opponent: BOB_UID,
      currentTurn: "White",
      chessBoard: ["r", "n", "b", "q", "k", "b", "n", "r"],
      capturedWhite: [],
      capturedBlack: [],
      lastMoveDescription: "Match started",
      webrtcRoomCode: "webrtc_room_abc",
      updatedAt: Date.now()
    })
  );
});

test("admin_quarantine_vault: Silent upload of flagged media succeeds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("admin_quarantine_vault").doc("vault_rep_1").set({
      id: "vault_rep_1",
      senderId: ALICE_UID,
      flaggedReason: "Unencrypted media flagged",
      mediaUri: "https://example.com/media1",
      reportedUser: "@baduser",
      timestamp: "12:00 PM",
      resolved: false
    })
  );
});

test("Strict DM Isolation Rule: Direct chats and room message collections are strictly blocked from Firestore", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  // Any direct_chats or rooms collections must fail
  await assertFails(
    aliceDb.collection("direct_chats").doc("alice_bob").collection("messages").doc("m1").set({
      id: "m1",
      sender: ALICE_UID,
      text: "Secret private DM"
    })
  );

  await assertFails(
    aliceDb.collection("rooms").doc("lounge").collection("messages").doc("m1").set({
      id: "m1",
      sender: ALICE_UID,
      text: "Room message"
    })
  );
});
