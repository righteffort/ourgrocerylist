Handling race conditions around firestore state and handling failures
communicating with firebase was implemented in an ad hoc fashion, not
designed deliberately, resulting in bugs.

Let's reason through it together carefully. Only *as examples*, here
are some of the behaviors I've observed. But to be clear, we need to
address the fundamentals, not just add more bandaids case-by-case.

The goal is that we have a clear design of how the application should
interact with Firestore, whether through direct reads and writes or
through the addEditor and deleteList functions in the presence of
failures and requirements for state transitions.

Is the goal clear? Let's proceed in small steps, don't try to write a
detailed design doc in one step. Let's start by establishede with the
core principles that *this app* needs to follow with respect to *this
app's data*, not generic nice-sounding principles.
