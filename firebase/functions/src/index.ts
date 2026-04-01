import { setGlobalOptions } from "firebase-functions";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";
import { addEditorCore } from "./addEditorCore.js";

admin.initializeApp();
setGlobalOptions({ maxInstances: 10 });

export const addEditor = onCall(async (request) => {
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
            const editors = (doc.data()?.["editors"] ?? {}) as Record<string, unknown>;
            if (editor.uid in editors) {
              throw Object.assign(new Error("Editor already added to this list"), { code: "already-exists" });
            }
            transaction.update(listRef, { [`editors.${editor.uid}`]: { email: editor.email } });
          });
        },
      },
      request.auth,
      listId,
      editorEmail,
    );
  } catch (e) {
    if (e instanceof HttpsError) throw e;
    throw new HttpsError("internal", e instanceof Error ? e.message : String(e));
  }
});
