import { HttpsError } from "firebase-functions/v2/https";

export type DeleteListDeps = {
  getListOwnerUid: (listId: string) => Promise<string | undefined>;
  deleteList: (listId: string) => Promise<void>;
};

export async function deleteListCore(
  deps: DeleteListDeps,
  auth: { uid: string } | undefined | null,
  listId: unknown,
): Promise<void> {
  if (!auth) {
    throw new HttpsError("unauthenticated", "Must be signed in");
  }
  if (!listId || typeof listId !== "string") {
    throw new HttpsError("invalid-argument", "listId is required");
  }

  const ownerUid = await deps.getListOwnerUid(listId);
  if (ownerUid === undefined) {
    throw new HttpsError("not-found", "List not found");
  }
  if (ownerUid !== auth.uid) {
    throw new HttpsError("permission-denied", "Only the list owner can delete the list");
  }

  await deps.deleteList(listId);
}
