These aspects have not yet been fully designed and implemented and are deferred to a later phase.
- list import
- WIP list import
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
- DONE multiple-list support in the code (and hence non-hardcoded list ids
  in fake repo -- currently the single list has the id "default"
- DONE acls, authentication, authorization
  - DONE authentication will simply be through integration of Google Auth into Firestore
  - DONE "acls" will be simply two fields on each document in the top-level
    lists collection: owner (the firestore provided user id for the
    creator of the list) and editors (the user ids that the owner has
    shared the list with)
  - DONE authorization will be via firestore security rules
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
- many things from ourshoppinglist-handoff.md
- autocomplete in add
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

These are hygiene issues that may have been missed
- review for swallowed errors
- review for code that compromises strong typechecking (e.g. by using typescript syntax, overly permissive casts in any language)
- review for garbage unit tests (e.g. that just restate the implementation w/mocks instead of the effects)

These aspects might never be implemented
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
These aspects will almost certainly never be implemented.
- Vestiges of obsolete design
  - Proxying mutations through a cloud function, along with a request queue. 
