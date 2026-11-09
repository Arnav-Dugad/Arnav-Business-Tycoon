// Live smoke test against the real Firebase project (not the emulator). Run: node firebase/test/live-smoke.mjs
// It reads the project id and API key from app/google-services.json (git-ignored; never printed), creates throwaway
// accounts, exercises the same paths the app uses through the public REST APIs, checks that the security rules allow
// and refuse what they should, and deletes everything it created. Spark plan only: Auth + Firestore, no Functions.
import { readFileSync } from 'node:fs';
import { createHash, randomBytes } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const gs = JSON.parse(readFileSync(join(root, 'app', 'google-services.json'), 'utf8'));
const project = gs.project_info.project_id;
const key = gs.client[0].api_key[0].current_key;
const UA = { 'User-Agent': 'abt-smoke-test/1.0' };
const auth = (path) => `https://identitytoolkit.googleapis.com/v1/${path}?key=${key}`;
const fs = `https://firestore.googleapis.com/v1/projects/${project}/databases/(default)/documents`;

let pass = 0, fail = 0;
const results = [];
function check(name, ok, detail = '') {
  ok ? pass++ : fail++;
  results.push(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` (${detail})` : ''}`);
}

async function post(url, body, token) {
  const r = await fetch(url, { method: 'POST', headers: { ...UA, 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: JSON.stringify(body) });
  return { status: r.status, json: await r.json().catch(() => ({})) };
}
async function get(url, token) {
  const r = await fetch(url, { headers: { ...UA, Authorization: `Bearer ${token}` } });
  return { status: r.status, json: await r.json().catch(() => ({})) };
}

// A save write exactly like CloudService.push: fields + savedAt = request.time.
function saveWrite(uid, rev, blob) {
  return {
    update: {
      name: `projects/${project}/databases/(default)/documents/saves/${uid}`,
      fields: {
        rev: { integerValue: String(rev) }, schema: { integerValue: '1' }, contentHash: { stringValue: 'smoke' },
        device: { stringValue: 'smoke-test' }, sha: { stringValue: createHash('sha256').update(blob).digest('hex') },
        blob: { bytesValue: blob.toString('base64') },
        summary: { mapValue: { fields: { netWorth: { doubleValue: 1234.5 }, chapter: { integerValue: '2' } } } },
      },
    },
    updateTransforms: [{ fieldPath: 'savedAt', setToServerValue: 'REQUEST_TIME' }],
  };
}
const commit = (writes, token) => post(`${fs}:commit`, { writes }, token);

const created = [];
try {
  // 1. Guest sign-in (the app's "Play as guest").
  const guest = await post(auth('accounts:signUp'), { returnSecureToken: true });
  check('anonymous sign-in', guest.status === 200 && !!guest.json.idToken, `HTTP ${guest.status}`);
  const g = guest.json; created.push(g);

  // 2. Cloud save: create rev 1, read back byte-for-byte.
  const blob = randomBytes(2048);
  const c1 = await commit([saveWrite(g.localId, 1, blob)], g.idToken);
  check('cloud save: create rev 1', c1.status === 200, `HTTP ${c1.status}`);
  const r1 = await get(`${fs}/saves/${g.localId}`, g.idToken);
  check('cloud save: read back identical bytes', r1.status === 200 && Buffer.from(r1.json.fields?.blob?.bytesValue ?? '', 'base64').equals(blob), `HTTP ${r1.status}`);

  // 3. Optimistic concurrency: skipping a revision is refused; the next revision is accepted.
  const skip = await commit([saveWrite(g.localId, 3, blob)], g.idToken);
  check('rules refuse a skipped revision', skip.status === 403, `HTTP ${skip.status}`);
  const c2 = await commit([saveWrite(g.localId, 2, randomBytes(512))], g.idToken);
  check('cloud save: update to rev 2', c2.status === 200, `HTTP ${c2.status}`);

  // 4. Another player's save is out of reach.
  const other = await post(auth('accounts:signUp'), { returnSecureToken: true });
  created.push(other.json);
  const steal = await get(`${fs}/saves/${g.localId}`, other.json.idToken);
  check("rules refuse reading someone else's save", steal.status === 403, `HTTP ${steal.status}`);
  const clobber = await commit([saveWrite(g.localId, 3, blob)], other.json.idToken);
  check("rules refuse writing someone else's save", clobber.status === 403, `HTTP ${clobber.status}`);

  // 5. Social features need a verified email or Google: a guest is refused.
  const prof = await commit([{
    update: { name: `projects/${project}/databases/(default)/documents/profiles/${g.localId}`, fields: { name: { stringValue: 'Smoke Test' }, nwLog10: { doubleValue: 3 }, showcase: { arrayValue: {} }, boosted: { booleanValue: false } } },
    updateTransforms: [{ fieldPath: 'updatedAt', setToServerValue: 'REQUEST_TIME' }],
  }], g.idToken);
  check('rules refuse a guest profile (needs verified email)', prof.status === 403, `HTTP ${prof.status}`);

  // 6. Email sign-up works; an unverified account is still refused on leaderboards.
  const email = `abt-smoke-${randomBytes(4).toString('hex')}@example.com`;
  const em = await post(auth('accounts:signUp'), { email, password: randomBytes(12).toString('hex'), returnSecureToken: true });
  check('email sign-up', em.status === 200 && !!em.json.idToken, `HTTP ${em.status}`);
  if (em.json.idToken) created.push(em.json);
  const board = await commit([{
    update: { name: `projects/${project}/databases/(default)/documents/boards/global/shards/0`, fields: { entries: { mapValue: { fields: { [em.json.localId]: { mapValue: { fields: { n: { stringValue: 'Smoke Test' }, lnw: { doubleValue: 3 }, flnw: { doubleValue: 3 }, b: { booleanValue: false }, g: { integerValue: '1' } } } } } } } } },
    updateMask: { fieldPaths: [`entries.\`${em.json.localId}\``] },
  }], em.json.idToken);
  check('rules refuse an unverified leaderboard entry', board.status === 403, `HTTP ${board.status}`);

  // 7. Leaderboards are readable by any signed-in player (404 just means no one has posted to that shard yet).
  const read = await get(`${fs}/boards/global/shards/0`, g.idToken);
  check('leaderboard shard readable', read.status === 200 || read.status === 404, `HTTP ${read.status}`);

  // 8. Clean up the save.
  const del = await fetch(`${fs}/saves/${g.localId}`, { method: 'DELETE', headers: { ...UA, Authorization: `Bearer ${g.idToken}` } });
  check('cloud save: delete', del.status === 200, `HTTP ${del.status}`);
} catch (e) {
  check('no unexpected errors', false, e.message);
} finally {
  // 9. Delete every throwaway account.
  let deleted = 0;
  for (const a of created) {
    if (!a?.idToken) continue;
    const d = await post(auth('accounts:delete'), { idToken: a.idToken });
    if (d.status === 200) deleted++;
  }
  check('throwaway accounts deleted', deleted === created.filter((a) => a?.idToken).length, `${deleted} of ${created.length}`);
  console.log(results.join('\n'));
  console.log(`\n${pass} passed, ${fail} failed (project ${project})`);
  process.exitCode = fail ? 1 : 0;
}
