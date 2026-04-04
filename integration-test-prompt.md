= Step 0 =

Set up just the necessary build and app configuration to be able to run integration tests separately from unit tests. Different tasks for each. Avoid having integration-test dependencies pollute the tasks and dependencies for the unit tests. Follow standard practices for this separation.

The integration tests will depend on roboelectric and turbine. You may need junit-vintage-engine because we're using the unit tests use JUnit 5/6 but roboelectric is Junit 4.  All 3 are already provided as dependencies.

I am not an Android developer so if I'm thinking about this wrong push back. I want to start small because in the last Claude Code session you completely failed at creating a gradlew task that would even run the integration test.

For now I've added a placeholder test in app/src/integrationTest/java/org/righteffort/ourgrocerylist/integration/DummyIntegrationTest.kt. If that is the wrong location for the test let me know.

= Step 1 =

Write the first integration test, for the following sunny-day scenario: User A
adds an item to a shared list, and User B sees the item appear.

Requirements:

- The test goes through the ViewModel layer — do not call the repository or Firestore directly to drive the scenario. User A's action is initiated via the ViewModel, and User B's observed state is read from the ViewModel.

- The test uses two ViewModel instances backed by two separate Firestore client instances pointing at the local emulator, representing two independent users.  Assume the Firebase emulator is already running. Do not start or stop it in the test.

- The shared list and both user sessions should be set up in the test itself. Consult the existing architecture and code to understand how to construct ViewModel instances with appropriate dependencies for an integration test context.

- User B's ViewModel should observe item state via its UiState flow. Use turbine or coroutine test utilities to collect emissions and assert that the new item appears in User B's list after User A adds it.

- The item added by User A should set name, quantity, and checked. Assert that B observes the identical state for the item.

- Clean up Firestore state before and after the test to ensure isolation.

- Place the test in the appropriate integration test source set, not the unit test source set.

- Do not mock Firestore. This is an integration test — all Firestore interactions must be real, against the emulator.

- Before writing any code, review CLAUDE.md and shopping-list-handoff.md to understand the architecture, then identify the correct way to construct test instances of all required dependencies. 

Proceed in stages
- Ask questions, point out issues, point out ambiguities in the requirements above
- Describe your plan of attack, including selection of third-party libraries
- If refactoring of the code under test is required, discuss that
