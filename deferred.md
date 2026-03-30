These aspects have not yet been fully designed and implemented and are deferred to a later phase.
- undo/redo
- conflict detection and notification
- multiple-list support in the code (and hence non-hardcoded list ids -- currently the single list has the id "default"
- acls, authentication, authorization
  - authentication will simply be through integration of Google Auth into Firestore
  - "acls" will be simply two fields on each document in the top-level
    lists collection: owner (the firestore provided user id for the
    creator of the list) and editors (the user ids that the owner has
    shared the list with)
  - authorization will be via firestore security rules
  - email and or in-app notifications with invites to newly shared lists
- once we have authentication (including association of clientIds with
  user ids) we conflict notifications can include the display name of
  the other party: "Ted overwrote your change", "You overwrote
  Claude's change".
- display fine-tuning, e.g. more compact view; light/dark/auto
- small/large fonts (is there a system setting that is conventional to
  follow instead of doing our own thing?)

These are hygiene issues that may have been missed
- review for swallowed errors
- review for code that compromises strong typechecking (e.g. by using typescript syntax, overly permissive casts in any language)

These aspects might never be implemented

- detection of conflicts between mutations and deletes, presumably involving tombstones
- server-side validation of client-provided fingerprints 
- maintaining a list of 'invited editors' (email addresses) and
  generating invitations to install the app when lists are shared, and
  something like a 'login' function at app startup that updates
  'invited editors' and 'editors' referening the user on their first
  login, and is a no-op afterward, or something (race conditions might
  make this a little complicated)
- Support running on an emulated device, which would required using
  the emulator loopback address (`10.0.2.2`) in place of `localhost`
  when configuring the app to connect to the emulator.
- internationalization
- accessibility beyond what we get for free
These aspects will almost certainly never be implemented.
- Vestiges of obsolete design
  - Proxying mutations through a cloud function, along with a request queue. 
