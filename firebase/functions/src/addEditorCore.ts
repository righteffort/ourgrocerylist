import { HttpsError } from "firebase-functions/v2/https";
import { FirebaseError } from "firebase-admin";

export type Editor = { uid: string; email: string };

export type AddEditorDeps = {
  getListOwnerUid: (listId: string) => Promise<string | undefined>;
  resolveEmailToUid: (email: string) => Promise<string>;
  appendEditor: (listId: string, editor: Editor) => Promise<void>;
};

export async function addEditorCore(
  deps: AddEditorDeps,
  auth: { uid: string } | undefined | null,
  listId: unknown,
  editorEmail: unknown,
): Promise<void> {
  if (!auth) {
    throw new HttpsError("unauthenticated", "Must be signed in");
  }
  if (!listId || typeof listId !== "string") {
    throw new HttpsError("invalid-argument", "listId is required");
  }
  if (!editorEmail || typeof editorEmail !== "string") {
    throw new HttpsError("invalid-argument", "editorEmail is required");
  }

  const ownerUid = await deps.getListOwnerUid(listId);
  if (ownerUid === undefined) {
    throw new HttpsError("not-found", "List not found");
  }
  if (ownerUid !== auth.uid) {
    throw new HttpsError(
      "permission-denied",
      "Only the list owner can add editors",
    );
  }

  let editorUid: string;
  try {
    editorUid = await deps.resolveEmailToUid(editorEmail);
  } catch (e) {
    if ((e as FirebaseError).code === "auth/user-not-found") {
      throw new HttpsError("not-found", `No account found for ${editorEmail}`);
    }
    throw e;
  }

  if (editorUid === auth.uid) {
    throw new HttpsError(
      "invalid-argument",
      "The list owner cannot be added as an editor",
    );
  }

  try {
    await deps.appendEditor(listId, { uid: editorUid, email: editorEmail });
  } catch (e) {
    if ((e as { code?: string }).code === "already-exists") {
      throw new HttpsError(
        "already-exists",
        `${editorEmail} is already an editor of this list`,
      );
    }
    throw e;
  }
}
