**Goal**: create new list by importing CSV via new item in overflow
menu. Dialog should prompt for name and provide file picker to select
CSV file. Columns are name, quantity, checked in any order, with
header. Column header values are case-insensitive. In the code
represent the alternatives for each header name as a set (of size 1
for now). If list with the same name already exists, do not create,
but instead propose the name with suffix (1) [or (2), (3), etc.]. Also
use the max existing suffix + 1, e.g. if user wants to name the
imported list "New list" but if "New list" and "New list (2)" both
exist, propose "New list (3)".

Re-review ourgrocerylist-handoff.md and CLAUDE.md for context and rules.

Questions?

Work in stages and get my approval before proceeding with the next stage.

1. address spec questions and issues you identify, and decide on overall approach. 

2. decide together on the implementation plan

3. implement.

On name collision update the name field in the dialog. include a
message saying "New list" already exists.

On malformed csv, fail the import with details -- missing columns,
duplicate columns, badly formatted rows, with first row number. Ignore
unknown columns. Extra blank rows are fine.

Only valid values for checked column are case-insensitive {true,false} and blank (false).

Reject the whole import on invalid value, as with other parse errors.

No undo.

Plan for unit tests and add them. Write meaningful tests of the effect
of the operations, don't use mocks to "test" that the implementation
makes specific calls.

Case-insensitive for duplicate name logic.

 If quantity column is present, treat blank quantities as 1.0; if
checked column is present, treat blank values as false. Place import
list after share list.
