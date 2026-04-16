stuff from deferred.md and elsewhere that is done (enough). see also done.md.
- multiple-list support in the code (and hence non-hardcoded list ids
  in fake repo -- currently the single list has the id "default"
- acls, authentication, authorization
  - authentication will simply be through integration of Google Auth into Firestore
  - "acls" will be simply two fields on each document in the top-level
    lists collection: owner (the firestore provided user id for the
    creator of the list) and editors (the user ids that the owner has
    shared the list with)
  - authorization will be via firestore security rules
- read about kotlin firestore sdk regarding state / mutation observer -- is set up "atomic" ? how about when mediated by cache?
  - it's fine. 
- persist username across invocations when using emulator
