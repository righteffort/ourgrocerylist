import { setGlobalOptions } from "firebase-functions";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentDeleted } from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { getFirestore } from "firebase-admin/firestore";
import { addEditorCore } from "./addEditorCore.js";

admin.initializeApp();
setGlobalOptions({ region: "us-west1", maxInstances: 10 }); // TODO: Don't hardcode

export const cleanUpDeletedList = onDocumentDeleted(
  { document: "lists/{listId}", region: "us-west1" },
  async (event) => {
    console.log(`cleanUpDeletedList(${event.params.listId})`);
    await getFirestore().recursiveDelete(event.data!.ref);
  },
);

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
    throw new HttpsError("internal", msg);
  }
});
