stuff from deferred.md and elsewhere that is done (enough). see also implemented.md.
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
- logout
- initial launch is buggy: sometimes get permission denied 
- provide 'restart' button or something on crash screen
- enable firestore local persistence if it isn't already
- DONE Reduce integration test/code coupling: see claude session `integration-test-coupling`, which advises "Fix it in the ViewModel — emit _lists and _currentListId as an atomic pair so uiState never has a state where currentListName doesn't match a list in lists. Harder, but the tests become simpler."
- DONE "The Triple in the combine is a minor style thing. error-handling inconsistency in observeItems (it calls logAndEmitFatalError then emits emptyList() and continues"
- DONE make it easy to switch between prod, emulator via 127.0.0.1, emulator via magic ip address
- DONE import trader joe's fails weirdly and silently (partial import). This was due to other challenging races.
- DONE probably: spurious permission denied when importing list but import succeeds
- DONE adopt timber or something so we can drop printlns in prod and see Log.d
- DONE get first integration test working & write down how to run it: ./gradlew testDebugUnitTest -PrunIntegration
- DONE get rid of 'decrement below 0 == delete', disable '-' in stepper when quanity <= 1
- DONE probably: initial launch is buggy: sometimes get two 'Groceries' lists
- DONE probably: deleting last list results in null value error
- DONE get rid of delayed navigation to new list created when offline -- see TODO in ShoppingViewModel.kt
- DONE (nothing to do) integration test clearing state
  - No, hard to use from modern jvm. use httpclient for nicer status code check
  - No, clearpersistence though i'm not convinced it is necessary
- delete list says 'unauthenticated'
- DONE probably: race condition on import (and create?) ? we get a null failure in
  firestore rules when we try to observe the list, but the list is imported
- DONE probably: race condition (?) on delete list, continues to hang around in UI after it is gone.
- DONE default imported list name to basename of filename ('List.csv' -> 'List')
- DONE such is life: ugh: have to allow allUsers access to functions
