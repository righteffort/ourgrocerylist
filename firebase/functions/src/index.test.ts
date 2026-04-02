import { describe, expect, it } from "vitest";
import {
  addEditorCore,
  type AddEditorDeps,
  type Editor,
} from "./addEditorCore.js";

const OWNER_UID = "owner-uid";
const EDITOR_UID = "editor-uid";
const EDITOR_EMAIL = "editor@example.com";
const LIST_ID = "list-1";

function makeDeps(overrides: Partial<AddEditorDeps> = {}): AddEditorDeps {
  return {
    getListOwnerUid: async (_listId) => OWNER_UID,
    resolveEmailToUid: async (_email) => EDITOR_UID,
    // TODO: fix!
    appendEditor: async (_listId, _editor) => {},  // eslint-disable-line @typescript-eslint/no-empty-function
    ...overrides,
  };
}

function userNotFoundError(): Error {
  return Object.assign(new Error("user not found"), {
    code: "auth/user-not-found",
  });
}

describe("addEditorCore", () => {
  it("throws unauthenticated when auth is absent", async () => {
    await expect(
      addEditorCore(makeDeps(), undefined, LIST_ID, EDITOR_EMAIL),
    ).rejects.toMatchObject({ code: "unauthenticated" });
  });

  it("throws invalid-argument when listId is missing", async () => {
    await expect(
      addEditorCore(makeDeps(), { uid: OWNER_UID }, undefined, EDITOR_EMAIL),
    ).rejects.toMatchObject({ code: "invalid-argument" });
  });

  it("throws invalid-argument when editorEmail is missing", async () => {
    await expect(
      addEditorCore(makeDeps(), { uid: OWNER_UID }, LIST_ID, undefined),
    ).rejects.toMatchObject({ code: "invalid-argument" });
  });

  it("throws not-found when list does not exist", async () => {
    const deps = makeDeps({ getListOwnerUid: async (_id) => undefined });
    await expect(
      addEditorCore(deps, { uid: OWNER_UID }, LIST_ID, EDITOR_EMAIL),
    ).rejects.toMatchObject({ code: "not-found" });
  });

  it("throws permission-denied when caller is not the list owner", async () => {
    const deps = makeDeps({ getListOwnerUid: async (_id) => "other-uid" });
    await expect(
      addEditorCore(deps, { uid: OWNER_UID }, LIST_ID, EDITOR_EMAIL),
    ).rejects.toMatchObject({ code: "permission-denied" });
  });

  it("throws invalid-argument when editor email resolves to the owner's uid", async () => {
    const deps = makeDeps({ resolveEmailToUid: async (_email) => OWNER_UID });
    await expect(
      addEditorCore(deps, { uid: OWNER_UID }, LIST_ID, EDITOR_EMAIL),
    ).rejects.toMatchObject({ code: "invalid-argument" });
  });

  it("throws not-found when no account exists for the editor email", async () => {
    const deps = makeDeps({
      resolveEmailToUid: async (_email) => {
        throw userNotFoundError();
      },
    });
    await expect(
      addEditorCore(deps, { uid: OWNER_UID }, LIST_ID, EDITOR_EMAIL),
    ).rejects.toMatchObject({ code: "not-found" });
  });

  it("appends the editor with uid and email on success", async () => {
    const appended: { listId: string; editor: Editor }[] = [];
    const deps = makeDeps({
      appendEditor: async (listId, editor) => {
        appended.push({ listId, editor });
      },
    });
    await addEditorCore(deps, { uid: OWNER_UID }, LIST_ID, EDITOR_EMAIL);
    expect(appended).toEqual([
      { listId: LIST_ID, editor: { uid: EDITOR_UID, email: EDITOR_EMAIL } },
    ]);
  });

  it("throws already-exists when appendEditor signals duplicate", async () => {
    const deps = makeDeps({
      appendEditor: async (_listId, _editor) => {
        throw Object.assign(new Error("Editor already added to this list"), {
          code: "already-exists",
        });
      },
    });
    await expect(
      addEditorCore(deps, { uid: OWNER_UID }, LIST_ID, EDITOR_EMAIL),
    ).rejects.toMatchObject({ code: "already-exists" });
  });
});
