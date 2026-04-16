These aspects have not yet been fully designed and implemented and are deferred to a later phase. See also unimplemented.md
- get rid of 'decrement below 0 == delete', disable '-' in stepper when quanity <= 1
- metadata in lists and items: at least creation and modification time
- initial launch is buggy: sometimes get two 'Groceries' lists
- deleting last list results in null value error
- provide 'restart' button or something on crash screen
- allowlist in firestore `authorizedUsers` collection of empty documents with email as id, then `function isAuthorized() { return request.auth != null && exists(/databases/$(database)/documents/authorizedUsers/$(request.auth.email)); }`
- get rid of delayed navigation to new list created when offline --
  see TODO in ShoppingViewModel.kt
- nail down ownership model for firebase-related objects and how to
  support multiple instances of ShoppingViewModel in integration tests.
  - in theory:
    - probably need to login -> authToken
	- probably need to invent clientId
	- need to make all firestore + functions calls use that authToken
	- SVM *might* need to plumb that stuff further down but you really hope not.
  - how it is [all f'ed up with weird injection points]
    - OurGroceryListApp seems to orchestrate
	  - OGLA owns Firestore observer, parameterized by User. User becomes non-null when auth completes.
	  - OGLA owns clientId (set async)
	  - OGLA owns listRepository which weirdly needs a firestore, the userflow (why?), callDeleteList (very weird)
	  - OGLA.onCreate is sort of main but just connects to emulators
	  - BTW I don't see any coordination of state initialization in OGLA. Ok StateFlow<User?> is one piece
	- MainActivity has a SVM initialized via a factory for DI (some jetpack compose magic)
    - SVM takes a whole pile of stuff for injection, why so much?
	  - currentUserFlow: StateFlow<User?>
	  - a ListRepository
	  - a ShoppingRepository factory (argument is string)
	  - a SharingRepository
	  - appErrors, just for bubbling up errors I think
	  - seems like it just organically grew over time?
- integration test clearing state
  - use httpclient for nicer status code check
  - clearpersistence though i'm not convinced it is necessary
- sits in a fast fail loop if unable to create initial list, no backoff at all
- check that https://console.cloud.google.com/run/detail/us-west1/deletelist/security?project=ourgrocerylist doesn't say public access
- delete list says 'unauthenticated'
- race condition on import (and create?) ? we get a null failure in
  firestore rules when we try to observe the list, but the list is imported
- race condition (?) on delete list, continues to hang around in UI after it is gone.
- default imported list name to basename of filename ('List.csv' -> 'List')
- coderabbit feedback
- bug: add editor succeeds but dialog box stays up
- ugh: have to allow allUsers access to functions
- persist undo/redo
- persist selected list
- sensible error handling
- conflict detection and notification
- email and or in-app notifications with invites to newly shared lists.
  - search for invitee in https://gemini.google.com/app/8aac15be2e66cec4 for some help
- removing editors; handling new editors who have never signed into
  the app; displaying current list owner and editors in the app.
- once we have authentication (including association of clientIds with
  user ids), conflict notifications can include the display name of
  the other party: "Ted overwrote your change", "You overwrote
  Claude's change".
- display fine-tuning, e.g. more compact view; light/dark/auto
- small/large fonts (is there a system setting that is conventional to
  follow instead of doing our own thing?)
- cleaner app handling when 'add editor' fails on the server in any way
- cleaner handling when user declines to login via Google or login fails
- app disallows sharing with improperly formatted email address
- in list selector indicate owner for lists shared with user
- prevent user from creating list that duplicates name of a list they
  own. ok to duplicate name of list shared with them.  user owns first
  followed by shared lists; sort those by owner email
- display name for users (and use that if available in place of email
  for list sorting; show email in UI as 'display name' when display
  name is unset)
- customizable display name for self
- release on f-droid
- use app check
- release on Play Store
- deploy cloud function & firestore.rules
- fancy animations
  - **Check:** Checkbox fills with accent color and checkmark. Once animation completes, row collapses and item cross-fades into its alphabetical position in the checked section. Viewport stays anchored to the unchecked section — does not follow the item.
  - **Uncheck:** Checkbox drains (checkmark disappears, border goes gray). Row collapses and item appears in the unchecked section. Viewport stays in the checked section.
  - **Delete:** Row flushes light red, then slides horizontally off the left edge while collapsing vertically. A trashcan icon materializes at the bottom of the screen, appears to receive the deleted item (lid opens and closes), then fades out. No permanent trash list.
  -Remote mutations (from another user) trigger the same animations as local ones, minus user-initiated affordances (e.g. no trashcan arc for remote deletes — just the red flush and slide)- autocomplete in add
  - basic (system-provided)
  - assist user to find exist checked items
  - maybe domain-aware
- logout
- account deletion
- export all my lists as zip
- unit tests for firestore security rules
- lists trash can
- items trash can (per list)
- integration tests
- re-order lists
- search (all lists? within list? selected lists?)
- play store

These are hygiene issues that may have been missed
- review for swallowed errors
- review for code that compromises strong typechecking (e.g. by using typescript syntax, overly permissive casts in any language)
- review for garbage unit tests (e.g. that just restate the implementation w/mocks instead of the effects)

These aspects might never be implemented
- build error reporting
- integration tests add test users to firestore whitelist, though realistically that means editing the string form of the rules one way or another
- user-selected CSV import headers
- detection of conflicts between mutations and deletes, presumably involving tombstones
- server-side validation of client-provided fingerprints 
- maintaining a list of 'invited editors' (email addresses) and
  generating invitations to install the app when lists are shared, and
  something like a 'login' function at app startup that updates
  'invited editors' and 'editors' referencing the user on their first
  login, and is a no-op afterward, or something (race conditions might
  make this a little complicated)
- Support running on an emulated device, which would required using
  the emulator loopback address (`10.0.2.2`) in place of `localhost`
  when configuring the app to connect to the emulator.
- internationalization
- accessibility beyond what we get for free
- clean up orphaned or abandoned state in Firestore
- "semantic conflict detection" and "semantic undo/redo pruning" -- see field-level pruning in conflict-detection-design.md
- write "bring your own google cloud project" instructions:
  - for all functions (e.g. is for emailToUid)
	- gcloud --project ourgrocerylist functions add-invoker-policy-binding emailToUid --region=us-west1   --member="allUsers"
	- gcloud run services get-iam-policy projects/ourgrocerylist/locations/us-west1/services/emailtouid
- write terraform etc. to bootstrap cloud project

These aspects will almost certainly never be implemented.
- Vestiges of obsolete design
  - Proxying mutations through a cloud function, along with a request queue. 
