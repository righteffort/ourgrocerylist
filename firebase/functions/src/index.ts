import { setGlobalOptions } from "firebase-functions";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";
import * as firebaseTools from "firebase-tools";
import { addEditorCore } from "./addEditorCore.js";
import { deleteListCore } from "./deleteListCore.js";

admin.initializeApp();
setGlobalOptions({ region: "us-west1", maxInstances: 10 });  // TODO: Don't hardcode

export const deleteList = onCall(async (request) => {
  console.log(`deleteList(${request.data})`);
  const { listId } = request.data;
  try {
    const isEmulator = process.env["FUNCTIONS_EMULATOR"] === 'true';
    console.log(`isEmulator: ${isEmulator}`);
    console.log(`auth host: ${process.env["FIREBASE_AUTH_EMULATOR_HOST"]}`);
    await deleteListCore(
      {
        getListOwnerUid: async (id) => {
          const doc = await admin.firestore().collection("lists").doc(id).get();
          if (!doc.exists) return undefined;
          const uid = doc.data()?.["owner"]?.["uid"];
          return typeof uid === "string" ? uid : undefined;
        },
        deleteList: async (id) => {
          await firebaseTools.firestore.delete(`lists/${id}`, {
            project: process.env["GCLOUD_PROJECT"],
            recursive: true,
            yes: true,
	    // @ts-expect-error: force is not exposed in the type definition
	    force: true,
	    ...( isEmulator ? {token: 'dummy-token'} : {})
          });
        },
      },
      request.auth,
      listId,
    );
  } catch (e) {
    const msg = e instanceof Error ? ("code" in e ? `${e.code}, ${e.message}` : e.message) : String(e);
    console.error(`Uh oh: ${msg}`);
    if (e instanceof HttpsError) throw e;
    throw new HttpsError(
      "internal",
      msg
    );
  }
});

export const addEditor = onCall(async (request) => {
  console.log(`addEditor(${request.data})`);
  const { listId, editorEmail } = request.data;
  try {
    await addEditorCore(
      {
        getListOwnerUid: async (id) => {
          const doc = await admin.firestore().collection("lists").doc(id).get();
          if (!doc.exists) return undefined;
          const uid = doc.data()?.["owner"]?.["uid"];
          return typeof uid === "string" ? uid : undefined;
        },
        resolveEmailToUid: async (email) => {
          return (await admin.auth().getUserByEmail(email)).uid;
        },
        appendEditor: async (id, editor) => {
          const listRef = admin.firestore().collection("lists").doc(id);
          await admin.firestore().runTransaction(async (transaction) => {
            const doc = await transaction.get(listRef);
            const editors = (doc.data()?.["editors"] ?? {}) as Record<
              string,
              unknown
            >;
            if (editor.uid in editors) {
              throw Object.assign(
                new Error("Editor already added to this list"),
                { code: "already-exists" },
              );
            }
            transaction.update(listRef, {
              [`editors.${editor.uid}`]: { email: editor.email },
            });
          });
        },
      },
      request.auth,
      listId,
      editorEmail,
    );
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    console.log(`Caught exception ${msg}`);
    if (e instanceof HttpsError) throw e;
    throw new HttpsError(
      "internal",
      msg
    );
  }
});
