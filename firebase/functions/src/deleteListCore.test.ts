import { describe, expect, it } from "vitest";
import { deleteListCore, type DeleteListDeps } from "./deleteListCore.js";

const OWNER_UID = "owner-uid";
const LIST_ID = "list-1";

function makeDeps(overrides: Partial<DeleteListDeps> = {}): DeleteListDeps {
  return {
    getListOwnerUid: async (_listId) => OWNER_UID,
    deleteList: async (_listId) => {},
    ...overrides,
  };
}

describe("deleteListCore", () => {
  it("throws unauthenticated when auth is absent", async () => {
    await expect(deleteListCore(makeDeps(), undefined, LIST_ID))
      .rejects.toMatchObject({ code: "unauthenticated" });
  });

  it("throws invalid-argument when listId is missing", async () => {
    await expect(deleteListCore(makeDeps(), { uid: OWNER_UID }, undefined))
      .rejects.toMatchObject({ code: "invalid-argument" });
  });

  it("throws not-found when list does not exist", async () => {
    const deps = makeDeps({ getListOwnerUid: async () => undefined });
    await expect(deleteListCore(deps, { uid: OWNER_UID }, LIST_ID))
      .rejects.toMatchObject({ code: "not-found" });
  });

  it("throws permission-denied when caller is not the list owner", async () => {
    const deps = makeDeps({ getListOwnerUid: async () => "other-uid" });
    await expect(deleteListCore(deps, { uid: OWNER_UID }, LIST_ID))
      .rejects.toMatchObject({ code: "permission-denied" });
  });

  it("calls deleteList with the correct listId on success", async () => {
    const deleted: string[] = [];
    const deps = makeDeps({ deleteList: async (id) => { deleted.push(id); } });
    await deleteListCore(deps, { uid: OWNER_UID }, LIST_ID);
    expect(deleted).toEqual([LIST_ID]);
  });
});
