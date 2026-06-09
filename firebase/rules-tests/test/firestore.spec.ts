// Moderately flaky, hard to diagnose the flakiness.

console_warn = console.warn
console.warn = (...args) => {
  const msg = args.join(' ') + '\n'
  if (msg.match(/PERMISSION_DENIED/)) {
    process.stderr.write(msg)
  } else {
    console_warn(msg)
  }
}
console.error = (...args) => {
  process.stderr.write(args.join(' ') + '\n')
};

import { beforeAll, beforeEach, afterAll, describe, expect, test } from '@jest/globals'
import {
  initializeTestEnvironment,
  type RulesTestEnvironment,
  assertFails,
  assertSucceeds,
} from '@firebase/rules-unit-testing'
import {
  collection,
  collectionGroup,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  setDoc,
  updateDoc,
  where,
  type DocumentData,
  type Firestore,
  type Query,
} from 'firebase/firestore'
import { createWriteStream, readFileSync } from 'node:fs'
import { get } from 'node:http'
import { resolve } from 'node:path'
import { getFirestoreCoverageMeta } from './utils'

// ---------------------------------------------------------------------------
// Principals and document IDs
//
// Principals are the 3 test users. Roles are defined relative to list_ab,
// which is the primary document used in table-driven tests:
//   alice  — owner of list_ab and list_a
//   bob    — editor on list_ab, owner of list_b
//   carol  — owner of list_c; no access to alice's or bob's lists
//
// Table-driven tests use role names (owner, editor, non_member). List-query
// tests use principal names (alice, bob, carol) directly.
// ---------------------------------------------------------------------------

const ALICE_UID   = 'alice_uid'
const BOB_UID     = 'bob_uid'
const CAROL_UID   = 'carol_uid'
const FOREIGN_UID = 'foreign_uid'   // uid used to test 'other owner' writes

const FIREBASE_JSON   = resolve(__dirname, '../../../firebase.json')
const FIRESTORE_RULES = resolve(__dirname, '../../firestore.rules')
const PROJECT_ID: string = (JSON.parse(readFileSync(resolve(__dirname, '../../../.firebaserc'), 'utf8')) as { projects: { default: string } }).projects.default

// ---------------------------------------------------------------------------
// Per-test document IDs
//
// A fresh suffix is generated in every beforeEach, before clearFirestore().
// This means the trigger cleanup that fires for the previous test's deleted
// documents targets stale IDs that do not exist in the current test, so it
// cannot race with the new seed writes.
// ---------------------------------------------------------------------------

function make_ids(suffix: string) {
  return {
    list_ab:   `list_ab_${suffix}`,
    list_a:    `list_a_${suffix}`,
    list_b:    `list_b_${suffix}`,
    list_c:    `list_c_${suffix}`,
    new_list:  `new_list_${suffix}`,
    item:      `item_1_${suffix}`,
    new_item:  `new_item_${suffix}`,
    client:    `client_1_${suffix}`,
    notif:     `notif_1_${suffix}`,
    new_notif: `new_notif_${suffix}`,
  }
}
type TestIds = ReturnType<typeof make_ids>

let ids: TestIds = make_ids('init')  // not used, just so we always have an instance

// ---------------------------------------------------------------------------
// ObjectDef — one level of indirection between the spec table and concrete paths.
//
// BaseObjectDef: paths only — for get/list/delete where write payload is
//   irrelevant.
// WritableObjectDef: additionally carries create/update data — for rows where
//   the payload explicitly determines allow vs deny (list_doc_self_owner,
//   list_doc_other_owner).
// ---------------------------------------------------------------------------

interface BaseObjectDef {
  readonly doc_path:   string
  readonly coll_path:  string
  readonly list_query?: (db: Firestore) => Query<DocumentData>
}

interface WritableObjectDef extends BaseObjectDef {
  readonly create_path: string
  readonly create_data: (actor_uid: string) => DocumentData
  readonly update_data: (actor_uid: string) => DocumentData
}

type ObjectDef = BaseObjectDef | WritableObjectDef

function is_writable(def: ObjectDef): def is WritableObjectDef {
  return 'create_path' in def
}

function make_object_defs(t: TestIds): Record<string, ObjectDef> {
  return {
    // ---- list documents ----

    // list_doc: used for read/delete rows and deny-expected create/update rows.
    // create_data/update_data use ALICE_UID so the payload is structurally valid;
    // the deny outcome for unauthenticated/non_member is driven by auth, not data.
    // list_query constrains to list_ab via editorUids so that owner and editor
    // both get a non-empty, accessible result; non_member gets PERMISSION_DENIED.
    list_doc: {
      doc_path:    `users/${ALICE_UID}/lists/${t.list_ab}`,
      coll_path:   `users/${ALICE_UID}/lists`,
      create_path: `users/${ALICE_UID}/lists/${t.new_list}`,
      create_data: () => ({ owner: { uid: ALICE_UID }, editors: {}, editorUids: [] }),
      update_data: () => ({ 'owner.uid': ALICE_UID }),
      // Constrain to list_ab (the only list where BOB_UID is an editor).
      // Owner and editor can both read the result; non_member cannot.
      list_query:  (db) => query(
        collection(db, `users/${ALICE_UID}/lists`),
        where('editorUids', 'array-contains', BOB_UID),
      ),
    },

    // list_doc_self_owner: write data explicitly names the actor as owner.
    list_doc_self_owner: {
      doc_path:    `users/${ALICE_UID}/lists/${t.list_ab}`,
      coll_path:   `users/${ALICE_UID}/lists`,
      create_path: `users/${ALICE_UID}/lists/${t.new_list}`,
      create_data: (uid) => ({ owner: { uid }, editors: {}, editorUids: [] }),
      update_data: (uid) => ({ 'owner.uid': uid }),
    },

    // list_doc_other_owner: write data names a foreign uid as owner.
    list_doc_other_owner: {
      doc_path:    `users/${ALICE_UID}/lists/${t.list_ab}`,
      coll_path:   `users/${ALICE_UID}/lists`,
      create_path: `users/${ALICE_UID}/lists/${t.new_list}`,
      create_data: () => ({ owner: { uid: FOREIGN_UID }, editors: {}, editorUids: [] }),
      update_data: () => ({ 'owner.uid': FOREIGN_UID }),
    },

    // ---- item documents ----

    item_doc: {
      doc_path:    `users/${ALICE_UID}/lists/${t.list_ab}/items/${t.item}`,
      coll_path:   `users/${ALICE_UID}/lists/${t.list_ab}/items`,
      create_path: `users/${ALICE_UID}/lists/${t.list_ab}/items/${t.new_item}`,
      create_data: () => ({ fields: { name: 'milk', quantity: 1, checked: false } }),
      update_data: () => ({ 'fields.name': 'bread' }),
    },

    // ---- notification documents ----

    notification_doc: {
      doc_path:    `users/${ALICE_UID}/lists/${t.list_ab}/notifications/${t.client}/pending/${t.notif}`,
      // coll_path is one level up (the clientId collection) so that unconstrained
      // list queries hit a path with no rule and are denied.  list_query scopes
      // down to the specific client's pending subcollection, which is covered by
      // hasAccess() and should be allowed for owner/editor.
      coll_path:   `users/${ALICE_UID}/lists/${t.list_ab}/notifications`,
      create_path: `users/${ALICE_UID}/lists/${t.list_ab}/notifications/${t.client}/pending/${t.new_notif}`,
      create_data: () => ({ message: 'conflict' }),
      update_data: () => ({ message: 'updated' }),
      list_query:  (db) => collection(db, `users/${ALICE_UID}/lists/${t.list_ab}/notifications/${t.client}/pending`),
    },
  }
}

let OBJECT_DEFS = make_object_defs(ids)

// ---------------------------------------------------------------------------
// Actors
// ---------------------------------------------------------------------------

type ActorUids = {
  readonly unauthenticated: null
  readonly owner:           string
  readonly editor:          string
  readonly non_member:      string
}

const ACTOR_UIDS: ActorUids = {
  unauthenticated: null,
  owner:           ALICE_UID,
  editor:          BOB_UID,
  non_member:      CAROL_UID,
}

function actor_db(actor: string, env: RulesTestEnvironment): Firestore {
  if (!(actor in ACTOR_UIDS)) {
    throw new Error(`unknown actor: "${actor}"`)
  }
  const uid = ACTOR_UIDS[actor as keyof ActorUids]
  if (uid === null) {
    return env.unauthenticatedContext().firestore()
  }
  return env.authenticatedContext(uid, { email: `${actor}@test.invalid` }).firestore()
}

// ---------------------------------------------------------------------------
// Actions
// ---------------------------------------------------------------------------

const ATOMIC_ACTIONS = ['get', 'list', 'list_constrained', 'create', 'update', 'delete'] as const
type Action = typeof ATOMIC_ACTIONS[number]

function expand_action(action_spec: string): Action[] {
  switch (action_spec) {
    case 'read': return ['get', 'list', 'list_constrained']
    case 'any':  return [...ATOMIC_ACTIONS]
    default: {
      if ((ATOMIC_ACTIONS as readonly string[]).includes(action_spec)) {
        return [action_spec as Action]
      }
      throw new Error(`unknown action: "${action_spec}"`)
    }
  }
}

// ---------------------------------------------------------------------------
// Table parsing
// ---------------------------------------------------------------------------

type TableRow = readonly [string, string, string, string]

function parse_table(table: string): TableRow[] {
  return table
    .split('\n')
    .map(line => line.replace(/(\/\/|#).*/, "").trim())
    .filter(line => line.length > 0)
    .map(line => {
      const parts = line.split(/\s+/)
      if (parts.length !== 4) {
        throw new Error(`malformed table row: "${line}"`)
      }
      // Safe: runtime length check above validates the shape.
      return parts as unknown as TableRow
    })
}

// ---------------------------------------------------------------------------
// Tables
// ---------------------------------------------------------------------------

const LIST_DOC_TABLE = `
 unauthenticated  any              list_doc             deny
 owner            list             list_doc             allow  // path is users/alice/lists — owner-scoped
 owner            list_constrained list_doc             allow
 owner            get              list_doc             allow
 owner            create           list_doc_self_owner  allow
 owner            create           list_doc_other_owner deny
 owner            update           list_doc_self_owner  allow
 owner            update           list_doc_other_owner deny
 owner            delete           list_doc             allow
 editor           list             list_doc             deny   // can't list all of alice's lists
 editor           list_constrained list_doc             allow  // constrained by editorUids
 editor           get              list_doc             allow
 editor           create           list_doc             deny
 editor           update           list_doc             deny
 editor           delete           list_doc             deny
 non_member       any              list_doc             deny
`

const ITEM_DOC_TABLE = `
 unauthenticated  any  item_doc  deny
 owner            any  item_doc  allow
 editor           any  item_doc  allow
 non_member       any  item_doc  deny
`

const NOTIFICATION_DOC_TABLE = `
 unauthenticated  any     notification_doc  deny
 owner            list    notification_doc  deny
 owner            list_constrained    notification_doc  allow
 owner            get    notification_doc  allow
 owner            delete  notification_doc  allow
 owner            create  notification_doc  deny
 owner            update  notification_doc  deny
 editor           list    notification_doc  deny
 editor           list_constrained    notification_doc  allow
 editor           get    notification_doc  allow
 editor           delete  notification_doc  allow
 editor           create  notification_doc  deny
 editor           update  notification_doc  deny
 non_member       any     notification_doc  deny
`

// ---------------------------------------------------------------------------
// Test runner
// ---------------------------------------------------------------------------

let testEnv: RulesTestEnvironment

async function run_case(actor: string, action: Action, object_label: string): Promise<unknown> {
  const db  = actor_db(actor, testEnv)
  const def = OBJECT_DEFS[object_label]
  if (def === undefined) {
    throw new Error(`unknown object label: "${object_label}"`)
  }
  const uid = ACTOR_UIDS[actor as keyof ActorUids] ?? ''

  switch (action) {
    case 'get':
      return getDoc(doc(db, def.doc_path))

    case 'list': {
      return getDocs(collection(db, def.coll_path))
    }

    case 'list_constrained': {
      const q: Query<DocumentData> = def.list_query
        ? def.list_query(db)
        : collection(db, def.coll_path)
      return getDocs(q)
    }

    case 'create': {
      if (!is_writable(def)) {
        throw new Error(`object "${object_label}" has no create_data`)
      }
      return setDoc(doc(db, def.create_path), def.create_data(uid))
    }

    case 'update': {
      if (!is_writable(def)) {
        throw new Error(`object "${object_label}" has no update_data`)
      }
      return updateDoc(doc(db, def.doc_path), def.update_data(uid))
    }

    case 'delete':
      return deleteDoc(doc(db, def.doc_path))
  }
}

function generate_tests(table: string): void {
  for (const [actor, action_spec, object_label, expected] of parse_table(table)) {
    for (const action of expand_action(action_spec)) {
      test(`${actor} ${action} ${object_label} -> ${expected}`, async () => {
        const op = run_case(actor, action, object_label)
        try {
          await (expected === 'allow' ? assertSucceeds(op) : assertFails(op))
          // process.stderr.write(`passed: ${actor} ${action} ${object_label} -> ${expected}\n`)
        } catch (e) {
          // process.stderr.write(`failed: ${actor} ${action} ${object_label} -> ${expected}: ${e}\n`)
          throw e
        }
      })
    }
  }
}

// ---------------------------------------------------------------------------
// Environment setup
// ---------------------------------------------------------------------------

beforeAll(async () => {
  let { host, port } = getFirestoreCoverageMeta(PROJECT_ID, FIREBASE_JSON)
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      port,
      host,
      rules: readFileSync(FIRESTORE_RULES, 'utf8'),
    },
  })
})

beforeEach(async () => {
  await testEnv.clearFirestore()
  ids = make_ids(Math.random().toString(36).slice(2, 10))
  OBJECT_DEFS = make_object_defs(ids)

  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore()
    await Promise.all([
      setDoc(doc(db, 'users', ALICE_UID, 'lists', ids.list_ab), {
        owner:      { uid: ALICE_UID },
        editors:    { [BOB_UID]: { email: 'bob@test.invalid' } },
        editorUids: [BOB_UID],
      }),
      setDoc(doc(db, 'users', ALICE_UID, 'lists', ids.list_a), {
        owner:      { uid: ALICE_UID },
        editors:    {},
        editorUids: [],
      }),
      setDoc(doc(db, 'users', BOB_UID, 'lists', ids.list_b), {
        owner:      { uid: BOB_UID },
        editors:    {},
        editorUids: [],
      }),
      setDoc(doc(db, 'users', CAROL_UID, 'lists', ids.list_c), {
        owner:      { uid: CAROL_UID },
        editors:    {},
        editorUids: [],
      }),
      setDoc(doc(db, 'users', ALICE_UID, 'lists', ids.list_ab, 'items', ids.item), {
        fields: { name: 'milk', quantity: 1, checked: false },
      }),
      setDoc(
        doc(db, 'users', ALICE_UID, 'lists', ids.list_ab, 'notifications', ids.client, 'pending', ids.notif),
        { message: 'conflict' },
      ),
    ])
  })
})

afterEach(async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore()
    // Delete subcollection documents before list documents so that when the
    // trigger fires on list deletion it finds empty subcollections and exits
    // without writing anything — eliminating the race with the next beforeEach.
    await Promise.all([
      deleteDoc(doc(db, 'users', ALICE_UID, 'lists', ids.list_ab, 'items', ids.item)),
      deleteDoc(doc(db, 'users', ALICE_UID, 'lists', ids.list_ab, 'items', ids.new_item)),
      deleteDoc(doc(db, 'users', ALICE_UID, 'lists', ids.list_ab, 'notifications', ids.client, 'pending', ids.notif)),
      deleteDoc(doc(db, 'users', ALICE_UID, 'lists', ids.list_ab, 'notifications', ids.client, 'pending', ids.new_notif)),
    ])
    await Promise.all([
      deleteDoc(doc(db, 'users', ALICE_UID, 'lists', ids.list_ab)),
      deleteDoc(doc(db, 'users', ALICE_UID, 'lists', ids.list_a)),
      deleteDoc(doc(db, 'users', BOB_UID,   'lists', ids.list_b)),
      deleteDoc(doc(db, 'users', CAROL_UID, 'lists', ids.list_c)),
      deleteDoc(doc(db, 'users', ALICE_UID, 'lists', ids.new_list)),
    ])
  })
})

afterAll(async () => {
  const { coverageUrl } = getFirestoreCoverageMeta(PROJECT_ID, FIREBASE_JSON)
  const fstream = createWriteStream('./firestore-coverage.html')
  await new Promise<void>((done, reject) => {
    get(coverageUrl, (res) => {
      res.pipe(fstream, { end: true })
      res.on('end', done)
      res.on('error', reject)
    })
  })
  console.log(`View firestore rule coverage information at ./firestore-coverage.html\n`)
  await testEnv.cleanup()
})

// ---------------------------------------------------------------------------
// Table-driven tests
// ---------------------------------------------------------------------------

describe('list documents',         () => { generate_tests(LIST_DOC_TABLE) })
describe('item documents',         () => { generate_tests(ITEM_DOC_TABLE) })
describe('notification documents', () => { generate_tests(NOTIFICATION_DOC_TABLE) })

// ---------------------------------------------------------------------------
// List query tests (non-table-driven)
//
// Validates that the security rules correctly scope list queries to the
// documents each principal can access. The app runs two queries per user —
// one on the owner-scoped subcollection and one collectionGroup filtered by
// editorUids — and merges the results client-side. These tests mirror that pattern.
// ---------------------------------------------------------------------------

describe('list queries', () => {
  function owned_query(uid: string): Query<DocumentData> {
    const db = testEnv.authenticatedContext(uid, { email: `${uid}@test.invalid` }).firestore()
    return collection(db, `users/${uid}/lists`)
  }

  function editor_query(uid: string): Query<DocumentData> {
    const db = testEnv.authenticatedContext(uid, { email: `${uid}@test.invalid` }).firestore()
    return query(collectionGroup(db, 'lists'), where('editorUids', 'array-contains', uid))
  }

  async function sorted_ids(q: Query<DocumentData>): Promise<string[]> {
    const snap = await getDocs(q)
    return snap.docs.map(d => d.id).sort()
  }

  test('alice owned query returns list_ab and list_a', async () => {
    expect(await sorted_ids(owned_query(ALICE_UID))).toEqual([ids.list_a, ids.list_ab].sort())
  })
  test('alice editor query returns no lists', async () => {
    // process.stderr.write('alice editor...\n')
    expect(await sorted_ids(editor_query(ALICE_UID))).toEqual([])
    // process.stderr.write('...alice editor\n')
  })
  test('bob owned query returns list_b', async () => {
    // process.stderr.write('bob owned...\n')
    expect(await sorted_ids(owned_query(BOB_UID))).toEqual([ids.list_b])
    // process.stderr.write('...bob owned\n')
  })
  test('bob editor query returns list_ab', async () => {
    // process.stderr.write('bob editor...\n')
    expect(await sorted_ids(editor_query(BOB_UID))).toEqual([ids.list_ab])
    // process.stderr.write('...bob editor\n')
  })
  test('carol owned query returns list_c', async () => {
    // process.stderr.write('carol owned...\n')
    expect(await sorted_ids(owned_query(CAROL_UID))).toEqual([ids.list_c])
    // process.stderr.write('...carol owned\n')
  })
  test('carol editor query returns no lists', async () => {
    // process.stderr.write('carol editor...\n')
    expect(await sorted_ids(editor_query(CAROL_UID))).toEqual([])
    // process.stderr.write('...carol editor\n')
  })
  test('authenticated owner can get a non-existent list', async () => {
    const db = actor_db('owner', testEnv)
    const snap = await assertSucceeds(getDoc(doc(db, 'users', ALICE_UID, 'lists', ids.new_list)))
    expect(snap.exists()).toBe(false)
  })
})
