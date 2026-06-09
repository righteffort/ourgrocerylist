Goal: list owners can add other users as editors.

Obey instructions in CLAUDE.md

**Assumption**: the new editor has already signed in to the app.

**Strategy**: UI dialog to add the editor + cloud function to add the editor's uid to the editors field in the list document (adjacent to owners and items, already initialized to the empty list at list creation).

**Out of scope**: removing editors; handling new editors who have never signed into the app; displaying current list owner and editors in the app; multi-list support in the app.

**Steps**

1. **Cloud Function**. Implement a deployable cloud function in typescript in the existing firebase/functions npm package. It is fine to replace src/index.ts. The function will accept a JSON object with the list id and the new editor email address. It should validate that the caller's uid matches the owner uid for the list. Using the Firebase Admin SDK it calls `admin.auth().getUserByEmail` to resolve the new editor's email address to a uid. If the call succeeds, it atomically appends to the editors array for the list using `FieldValue.arrayUnion`

2. **UI**. Add an ovefrlow menu to the top bar with a single 'share list' entry that prompts for the new editor (with a cancel option of course). This will invoke the cloud function and report the result back to the user.

Questions?

Review:

Both the app and the cloud function should disallow adding the owner as an editor.

index.ts:40:22 - error TS2304: Cannot find name 'FirebaseError'.

index.ts:28:23 - error TS4111: Property 'owner' comes from an index signature, so it must be accessed with ['owner'].

the function is single-purpose. let's assume we will add additional entry points to the function in the future. discuss options; don't implement a solution for that yet.


