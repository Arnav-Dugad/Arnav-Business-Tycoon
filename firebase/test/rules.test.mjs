// Firestore rules tests (docs/ARCHITECTURE.md §7.3): every allow and deny path. Run with `npm test` (Firestore emulator).
import { test, before, after, beforeEach } from "node:test";
import { readFileSync } from "node:fs";
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, updateDoc, deleteDoc, serverTimestamp, Bytes, Timestamp, setLogLevel } from "firebase/firestore";

let env;
const PROJECT = "demo-arnav-business-tycoon";
const alice = "alice", bob = "bob", eve = "eve";

before(async () => {
  setLogLevel("error");
  env = await initializeTestEnvironment({ projectId: PROJECT, firestore: { rules: readFileSync("firestore.rules", "utf8"), host: "127.0.0.1", port: 8080 } });
});
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); });

const verified = (uid) => env.authenticatedContext(uid, { email: `${uid}@example.com`, email_verified: true }).firestore();
const guest = (uid) => env.authenticatedContext(uid, { firebase: { sign_in_provider: "anonymous" } }).firestore();
const anon = () => env.unauthenticatedContext().firestore();
const seed = async (path, data) => env.withSecurityRulesDisabled(async (ctx) => { await setDoc(doc(ctx.firestore(), path), data); });

const save = (rev, extra = {}) => ({
  rev, schema: 1, contentHash: "abc", device: "Pixel", savedAt: serverTimestamp(), summary: { nwLog10: 6.2, gen: 1, playSec: 600 },
  sha: "a".repeat(64), blob: Bytes.fromUint8Array(new Uint8Array(1024)), ...extra,
});

// ---------------------------------------------------------------- saves
test("a player can create, read and advance their own save one revision at a time", async () => {
  const db = guest(alice);
  await assertSucceeds(setDoc(doc(db, "saves", alice), save(1)));
  await assertSucceeds(getDoc(doc(db, "saves", alice)));
  await assertSucceeds(setDoc(doc(db, "saves", alice), save(2)));
  await assertFails(setDoc(doc(db, "saves", alice), save(2)), "same revision twice");
  await assertFails(setDoc(doc(db, "saves", alice), save(5)), "skipping revisions");
});

test("saves are private, bounded and well-formed", async () => {
  await seed(`saves/${alice}`, { rev: 1 });
  await assertFails(getDoc(doc(guest(bob), "saves", alice)), "another player's save");
  await assertFails(getDoc(doc(anon(), "saves", alice)), "signed out");
  const db = guest(bob);
  await assertFails(setDoc(doc(db, "saves", bob), save(1, { blob: Bytes.fromUint8Array(new Uint8Array(901 * 1024)) })), "blob over 900 KB");
  await assertFails(setDoc(doc(db, "saves", bob), save(1, { schema: 99 })), "unknown schema");
  await assertFails(setDoc(doc(db, "saves", bob), save(1, { savedAt: Timestamp.fromMillis(0) })), "client-chosen time");
  await assertFails(setDoc(doc(db, "saves", bob), save(1, { cheat: true })), "extra fields");
});

// ---------------------------------------------------------------- profiles
const profile = (extra = {}) => ({ name: "Alice", friendCode: "ABCD1234", avatarId: "a1", title: "", gen: 1, nwLog10: 9.5, fairNwLog10: 9.5, boosted: false, showcase: [], updatedAt: serverTimestamp(), ...extra });

test("verified players publish profiles that everyone signed in can read", async () => {
  await assertSucceeds(setDoc(doc(verified(alice), "profiles", alice), profile()));
  await assertSucceeds(getDoc(doc(guest(bob), "profiles", alice)));
  await assertFails(getDoc(doc(anon(), "profiles", alice)));
  await assertFails(setDoc(doc(guest(bob), "profiles", bob), profile({ name: "Bob" })), "guests can't publish");
  await assertFails(setDoc(doc(verified(bob), "profiles", alice), profile()), "someone else's profile");
  await assertFails(setDoc(doc(verified(alice), "profiles", alice), profile({ name: "Al" })), "name too short");
});

test("profiles may carry an avatar frame, but only a short string", async () => {
  await assertSucceeds(setDoc(doc(verified(alice), "profiles", alice), profile({ avatarId: "rocket", frame: "frame_gold" })));
  await assertSucceeds(setDoc(doc(verified(alice), "profiles", alice), profile({ frame: "" })));
  await assertFails(setDoc(doc(verified(alice), "profiles", alice), profile({ frame: "x".repeat(41) })), "frame too long");
  await assertFails(setDoc(doc(verified(alice), "profiles", alice), profile({ frame: 7 })), "frame must be a string");
  await assertFails(setDoc(doc(verified(alice), "profiles", alice), profile({ email: "a@b.c" })), "unknown fields stay forbidden");
});

test("the Boosted flag can never be cleared", async () => {
  await seed(`profiles/${alice}`, { ...profile({ boosted: true }), updatedAt: Timestamp.now() });
  await assertFails(setDoc(doc(verified(alice), "profiles", alice), profile({ boosted: false })));
  await assertSucceeds(setDoc(doc(verified(alice), "profiles", alice), profile({ boosted: true })));
});

// ---------------------------------------------------------------- friend codes, requests, friends
test("friend codes are unique and create-only", async () => {
  await assertSucceeds(setDoc(doc(verified(alice), "friendCodes", "ABCD1234"), { uid: alice }));
  await assertFails(setDoc(doc(verified(bob), "friendCodes", "ABCD1234"), { uid: bob }), "taken");
  await assertFails(setDoc(doc(verified(bob), "friendCodes", "bad"), { uid: bob }), "bad format");
  await assertFails(setDoc(doc(verified(bob), "friendCodes", "WXYZ9876"), { uid: alice }), "for someone else");
});

test("friend requests are created by the sender and readable by both sides", async () => {
  const id = `${bob}_${alice}`;
  await assertSucceeds(setDoc(doc(verified(alice), "friendRequests", id), { from: alice, to: bob, createdAt: serverTimestamp() }));
  await assertSucceeds(getDoc(doc(verified(bob), "friendRequests", id)));
  await assertFails(getDoc(doc(verified(eve), "friendRequests", id)), "outsiders");
  await assertFails(setDoc(doc(verified(eve), "friendRequests", `${bob}_${alice}`), { from: alice, to: bob, createdAt: serverTimestamp() }), "forged sender");
  await assertSucceeds(deleteDoc(doc(verified(bob), "friendRequests", id)));
});

test("friend lists are private and capped at 100", async () => {
  await assertSucceeds(setDoc(doc(verified(alice), "social", alice), { friends: [bob] }));
  await assertFails(getDoc(doc(verified(bob), "social", alice)));
  await assertFails(setDoc(doc(verified(alice), "social", alice), { friends: Array.from({ length: 101 }, (_, i) => `u${i}`) }));
});

// ---------------------------------------------------------------- leaderboards
const entry = (lnw, extra = {}) => ({ n: "Alice", lnw, flnw: lnw, b: false, g: 1, t: serverTimestamp(), ...extra });

test("players write only their own leaderboard entry", async () => {
  const db = verified(alice);
  await assertSucceeds(setDoc(doc(db, "boards/global/shards/0"), { entries: { [alice]: entry(9) } }));
  await assertFails(setDoc(doc(db, "boards/global/shards/1"), { entries: { [bob]: entry(9, { n: "Bob" }) } }), "someone else's key");
  await assertFails(setDoc(doc(guest(eve), "boards/global/shards/2"), { entries: { [eve]: entry(9, { n: "Eve" }) } }), "guests");
  await assertFails(setDoc(doc(db, "boards/global/shards/7"), { entries: { [alice]: entry(9) } }), "bad shard");
});

test("leaderboard writes are rate-limited, plausible and keep the Boosted flag", async () => {
  const old = Timestamp.fromMillis(Date.now() - 2 * 3600 * 1000); // two hours ago
  await seed("boards/global/shards/0", { entries: { [alice]: { n: "Alice", lnw: 9, flnw: 9, b: true, g: 1, t: old } } });
  const db = verified(alice);
  await assertFails(updateDoc(doc(db, "boards/global/shards/0"), { [`entries.${alice}`]: entry(15, { b: true }) }), "implausible jump");
  await assertFails(updateDoc(doc(db, "boards/global/shards/0"), { [`entries.${alice}`]: entry(9.5, { b: false }) }), "clearing Boosted");
  await assertSucceeds(updateDoc(doc(db, "boards/global/shards/0"), { [`entries.${alice}`]: entry(9.6, { b: true }) }));
  await assertFails(updateDoc(doc(db, "boards/global/shards/0"), { [`entries.${alice}`]: entry(9.7, { b: true }) }), "within 10 minutes");
});

test("prestige (a lower net worth) is always allowed", async () => {
  await seed("boards/global/shards/0", { entries: { [alice]: { n: "Alice", lnw: 20, flnw: 20, b: false, g: 1, t: Timestamp.fromMillis(Date.now() - 3600 * 1000) } } });
  await assertSucceeds(updateDoc(doc(verified(alice), "boards/global/shards/0"), { [`entries.${alice}`]: entry(3, { g: 2 }) }));
});

// ---------------------------------------------------------------- gifts
const day = () => new Date().toISOString().slice(0, 10).replaceAll("-", "");

test("gifts: friends only, one per day, capped", async () => {
  await seed(`social/${bob}`, { friends: [alice] });
  const db = verified(alice);
  const ok = { from: alice, kind: "favor", amount: 10, createdAt: serverTimestamp() };
  await assertSucceeds(setDoc(doc(db, `gifts/${bob}/inbox/${alice}_${day()}`), ok));
  await assertFails(setDoc(doc(db, `gifts/${bob}/inbox/${alice}_${day()}`), ok), "a second gift the same day");
  await assertFails(setDoc(doc(verified(eve), `gifts/${bob}/inbox/${eve}_${day()}`), { ...ok, from: eve }), "not a friend");
  await assertFails(setDoc(doc(db, `gifts/${bob}/inbox/${alice}_20000101`), { ...ok, amount: 500 }), "over the cap");
  await assertSucceeds(getDoc(doc(verified(bob), `gifts/${bob}/inbox/${alice}_${day()}`)));
  await assertFails(getDoc(doc(verified(eve), `gifts/${bob}/inbox/${alice}_${day()}`)));
});

// ---------------------------------------------------------------- sealed lots
function monday(offsetWeeks) {
  const d = new Date(); d.setUTCHours(0, 0, 0, 0);
  d.setUTCDate(d.getUTCDate() - ((d.getUTCDay() + 6) % 7) + 7 * offsetWeeks);
  return d.toISOString().slice(0, 10);
}
const bid = { lotId: "lot1", amountLog10: 9.2, amount: "1500000000.00", createdAt: serverTimestamp() };

test("sealed bids stay sealed until the week closes", async () => {
  const week = monday(0);
  await assertSucceeds(setDoc(doc(verified(alice), `lots/${week}/bids/${alice}`), bid));
  await assertSucceeds(getDoc(doc(verified(alice), `lots/${week}/bids/${alice}`)));
  await assertFails(getDoc(doc(verified(bob), `lots/${week}/bids/${alice}`)), "sealed");
  await assertFails(setDoc(doc(verified(bob), `lots/${week}/bids/${alice}`), bid), "someone else's bid");
});

test("bids for a closed week are frozen but readable", async () => {
  const week = monday(-2);
  await seed(`lots/${week}/bids/${alice}`, { ...bid, createdAt: Timestamp.now() });
  await assertSucceeds(getDoc(doc(verified(bob), `lots/${week}/bids/${alice}`)));
  await assertFails(setDoc(doc(verified(bob), `lots/${week}/bids/${bob}`), bid), "closed");
});

test("everything else is denied", async () => {
  await assertFails(setDoc(doc(verified(alice), "admin/config"), { x: 1 }));
  await assertFails(getDoc(doc(verified(alice), "admin/config")));
});
