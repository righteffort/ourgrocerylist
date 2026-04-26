import { setGlobalOptions } from "firebase-functions";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentDeleted } from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { getFirestore } from "firebase-admin/firestore";

admin.initializeApp();
setGlobalOptions({ region: "us-west1", maxInstances: 10 }); // TODO: Don't hardcode

export const cleanUpDeletedList = onDocumentDeleted(
  { document: "users/{ownerId}/lists/{listId}", region: "us-west1" },
  async (event) => {
    console.log(`cleanUpDeletedList(${event.params.ownerId}/${event.params.listId})`);
    await getFirestore().recursiveDelete(event.data!.ref);
  },
);

export const emailToUid = onCall(async (request) => {
  const { email } = request.data;
  try {
    return (await admin.auth().getUserByEmail(email)).uid;
  } catch (e) {
    if ((e as { code?: string }).code === "auth/user-not-found") {
      throw new HttpsError("not-found", `No account found for ${email}`);
    }
    throw e;
  }
});
