package org.righteffort.ourgrocerylist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctionsException
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.righteffort.ourgrocerylist.model.Command
import org.righteffort.ourgrocerylist.model.ItemFields
import org.righteffort.ourgrocerylist.model.ListMetadata
import org.righteffort.ourgrocerylist.model.ShoppingItem
import org.righteffort.ourgrocerylist.model.User
import org.righteffort.ourgrocerylist.repository.ListRepository
import org.righteffort.ourgrocerylist.repository.ShoppingRepository
import org.righteffort.ourgrocerylist.undo.UndoRedoManager
import org.righteffort.ourgrocerylist.undo.UndoRedoState
import org.righteffort.ourgrocerylist.util.CsvExporter
import org.righteffort.ourgrocerylist.util.CsvImporter
import org.righteffort.ourgrocerylist.util.CsvParseException
import timber.log.Timber

private val ITEM_COMPARATOR = compareBy<ShoppingItem> { it.fields.name.lowercase() }

class ShoppingViewModel(
    private val currentUserFlow: StateFlow<User?>,
    private val listRepository: ListRepository,
    private val repositoryFactory: (ownerUid: String, listId: String) -> ShoppingRepository,
    appErrors: Flow<String> = emptyFlow(),
) : ViewModel() {

    private data class ListResources(
        val repository: ShoppingRepository,
        val undoRedoManager: UndoRedoManager,
        val observationJob: Job,
    )

    // Per-list resources: created on first access, kept alive for all known lists
    // so that remote-change listeners and undo stacks persist across list switches.
    private val listResources = mutableMapOf<String, ListResources>()

    // lists and currentListId are kept in a single StateFlow so they always update
    // atomically. This prevents uiState from ever emitting an inconsistent state where
    // currentListName doesn't match a list in lists.
    private data class ListSelectionState(
        val lists: List<ListMetadata> = emptyList(),
        val currentListId: String? = null,
    ) {
        fun currentListName() =
            lists.firstOrNull { it.id == currentListId }?.name  // convenience function for logging

        fun currentList(): ListMetadata? = lists.firstOrNull { it.id == currentListId }
    }

    private val _listSelection = MutableStateFlow(ListSelectionState())

    // IDs added by addList that have not yet appeared in an observeLists snapshot.
    // Used to suppress auto-select when a notification from an earlier local write arrives
    // before the notification for this list's own write has been delivered.
    private val _pendingAddListIds = mutableSetOf<String>()

    // Carries the per-list items and undo state together with the list snapshot that was
    // current when the inner combine was set up. Consumed only inside uiState.
    private data class ActiveListState(
        val listId: String?,
        val lists: List<ListMetadata>,
        val items: List<ShoppingItem>,
        val undoState: UndoRedoState,
    )

    private val _dialogState = MutableStateFlow<ItemDialogState?>(null)
    val dialogState: StateFlow<ItemDialogState?> = _dialogState.asStateFlow()

    private val _errors = MutableSharedFlow<String>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val _fatalError = MutableStateFlow<String?>(null)
    val fatalError: StateFlow<String?> = _fatalError.asStateFlow()

    private val _shareListDialogState = MutableStateFlow<ShareListDialogState?>(null)
    val shareListDialogState: StateFlow<ShareListDialogState?> = _shareListDialogState.asStateFlow()

    private val _addListDialogVisible = MutableStateFlow(false)
    val addListDialogVisible: StateFlow<Boolean> = _addListDialogVisible.asStateFlow()

    private val _renameListDialogVisible = MutableStateFlow(false)
    val renameListDialogVisible: StateFlow<Boolean> = _renameListDialogVisible.asStateFlow()

    private val _deleteListDialogVisible = MutableStateFlow(false)
    val deleteListDialogVisible: StateFlow<Boolean> = _deleteListDialogVisible.asStateFlow()

    private val _importListDialogState = MutableStateFlow<ImportListDialogState?>(null)
    val importListDialogState: StateFlow<ImportListDialogState?> =
        _importListDialogState.asStateFlow()

    init {
        viewModelScope.launch {
            appErrors.collect { message ->
                Timber.e("Fatal app init error: $message")
                _fatalError.value = message
            }
        }
        viewModelScope.launch {
            currentUserFlow.collect { user ->
                if (user == null) {
                    Timber.v("DEBUG SVM user signed out, clearing current list selection")
                    _listSelection.update { it.copy(currentListId = null) }
                }
            }
        }
        viewModelScope.launch {
            listRepository.observeLists()
                .catch { e -> logAndEmitFatalError("Failed to observe lists", e) }
                .collect { lists ->
                    Timber.v("DEBUG SVM ${currentUserFlow.value?.email} observeLists lists=${lists.map { it }}")
                    val newIds = lists.map { it.id }.toSet()

                    // Create resources for newly discovered lists and start remote-change listeners.
                    for (list in lists) {
                        _pendingAddListIds.remove(list.id)
                        if (list.id !in listResources) {
                            getOrCreateResources(list)
                        }
                    }

                    // Cancel and remove resources for lists no longer accessible.
                    val removedIds = listResources.keys.toSet() - newIds
                    for (id in removedIds) {
                        listResources[id]?.observationJob?.cancel()
                        listResources.remove(id)
                    }

                    // If no lists exist (new user or all lists deleted), recreate the default.
                    if (lists.isEmpty()) {
                        val user = currentUserFlow.value
                        if (user != null) {
                            try {
                                Timber.v("DEBUG SVM ${currentUserFlow.value?.email} creating Groceries default list")
                                listRepository.createList(user, "Groceries")
                            } catch (e: Exception) {
                                logAndEmitFatalError("Failed to create initial list", e)
                            }
                        }
                        Timber.v("DEBUG SVM ${currentUserFlow.value?.email} lists empty, updating _listSelection")
                        _listSelection.update { it.copy(lists = lists) }
                        return@collect
                    }

                    // Atomically update lists and currentListId so uiState never emits a state
                    // where currentListName doesn't match a list in lists.
                    Timber.v("DEBUG SVM ${currentUserFlow.value?.email} time to update _listSelection")
                    _listSelection.update { current ->
                        Timber.v("DEBUG SVM ${currentUserFlow.value?.email} maybe change selected to ${current.currentListName()} current.currentListId=${current.currentListId}; newIds=$newIds _pendingAddListIds=$_pendingAddListIds")
                        val newCurrentId =
                            if (current.currentListId == null ||
                                (current.currentListId !in newIds && current.currentListId !in _pendingAddListIds)
                            ) {
                                // Auto-select when there is no current selection, or when the current
                                // list is absent from this snapshot AND was not added by a pending
                                // addList call. With latency compensation, each local write queues its
                                // own snapshot notification; those are delivered in order when the
                                // looper is next pumped, so a snapshot queued by an earlier addList
                                // call will not yet include the list created by a later one.
                                // _pendingAddListIds holds IDs written by addList that haven't yet
                                // appeared in any snapshot; it is cleared entry-by-entry above as each
                                // list is confirmed. This is distinct from listResources, which is
                                // removed during cleanup before this lambda runs.
                                val selected = lists.firstOrNull { it.isOwner } ?: lists.first()
                                Timber.v("DEBUG SVM ${currentUserFlow.value?.email} preferred auto-selecting list=$selected over current.currentListId")
                                selected.id
                            } else {
                                Timber.v("DEBUG SVM ${currentUserFlow.value?.email} went with ${current.currentListName()} current.currentListId")
                                current.currentListId
                            }
                        ListSelectionState(lists, newCurrentId)
                    }
                    Timber.v("DEBUG SVM ${currentUserFlow.value?.email} lists non-empty, updated _listSelection ${_listSelection.value.currentListName()} currentListId=${_listSelection.value.currentListId} lists=${lists.map { it }}")
                }
        }
    }

    // Creates resources for a list on first access. Safe to call multiple times for the same id.
    private fun getOrCreateResources(list: ListMetadata): ListResources =
        listResources.getOrPut(list.id) {
            val repo = repositoryFactory(list.ownerUid, list.id)
            val undoRedoManager = UndoRedoManager(repo)

            // Note FirestoreException from downstream
            // observeRemotelyModifiedItems should eventually succeed,
            // at which point this flow will begin.  In general
            // modifications during the gap would show up as ADDED,
            // but there won't actually be modifications, since they
            // can only occur is only possible after the list creation
            // reaches the server.
            val job = viewModelScope.launch {
                repo.observeRemotelyModifiedItemIds()
                    .catch { e ->
                        Timber.d("SVM ${currentUserFlow.value?.email} failed to observe remote changes for ${list.id}, can't trust undo/redo!");
                        logAndEmitError(
                            "can't trust undo/redo: failed to observe remote changes for ${list.id}",
                            e
                        )
                        // TODO: turn off undo/redo in this case???
                    }
                    .collect { itemIds -> itemIds.forEach { undoRedoManager.pruneForRemoteWrite(it) } }
            }
            ListResources(repo, undoRedoManager, job)
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState> = combine(
        // distinctUntilChanged on listId means the items subscription only restarts when the
        // selected list actually changes — not when lists metadata updates (e.g. a new list
        // shared to you). The inner combine re-subscribes to _listSelection for lists so that
        // metadata changes are still reflected without restarting the items subscription.
        _listSelection.map { it.currentListId }.distinctUntilChanged().flatMapLatest { listId ->
            Timber.v("DEBUG SVM ${currentUserFlow.value?.email} uiState flow listId=$listId")
            if (listId == null) {
                _listSelection.map { ActiveListState(null, it.lists, emptyList(), UndoRedoState()) }
            } else {
                // By the time a listId is selected, getOrCreateResources has already been called
                // for it (in the observeLists collect or addList). checkNotNull should never fire.
                val resources = checkNotNull(listResources[listId]) {
                    "Resources not found for listId=$listId — this is a bug"
                }
                combine(
                    resources.repository.observeItems()
                        .catch { e ->
                            Timber.e(
                                e,
                                "DEBUG SVM ${currentUserFlow.value?.email} observeItems terminated with error for listId=$listId — items observer is now dead"
                            )
                            logAndEmitFatalError("Failed to observe items", e)
                            // TODO: this is a bad place to be, probably should not allow the user to proceed! ...
                            emit(emptyList())  // TODO: ... because we don't know the true state of the list.
                        },
                    resources.undoRedoManager.state,
                    _listSelection,
                ) { items, undoState, listSelection ->
                    Timber.v("DEBUG SVM ${currentUserFlow.value?.email} uiState flow i think emitting state that includes listId=$listId")
                    ActiveListState(listId, listSelection.lists, items, undoState)
                }
            }
        },
        currentUserFlow,
    ) { activeListState, currentUser ->
        val currentList = activeListState.lists.find { it.id == activeListState.listId }
        Timber.v("DEBUG SVM ${currentUserFlow.value?.email} building UiState lists=${activeListState.lists.map { it }} currentList=${currentList}")
        val (checked, unchecked) = activeListState.items.partition { it.fields.checked }
        Timber.v("DEBUG SVM ${currentUserFlow.value?.email} constructing UiState listId=${activeListState.listId} items=${activeListState.items} lists=${activeListState.lists}")
        UiState(
            uncheckedItems = unchecked.sortedWith(ITEM_COMPARATOR),
            checkedItems = checked.sortedWith(ITEM_COMPARATOR),
            undoAvailable = activeListState.undoState.undoAvailable,
            redoAvailable = activeListState.undoState.redoAvailable,
            currentListName = currentList?.name ?: "",
            lists = activeListState.lists.sortedBy { it.name.lowercase() },
            isOwner = currentList?.isOwner ?: false,
            currentUserEmail = currentUser?.email ?: "",
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())
    // TODO: what does the stopTimeoutMillis above do?
    // --- Item operations ---

    fun addItem(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val listId = _listSelection.value.currentListId ?: return
        val repo = checkNotNull(listResources[listId]).repository
        val item = ShoppingItem(id = repo.newItemId(), fields = ItemFields(name = trimmed))
        applyCommand(Command.AddItem(item))
    }

    fun deleteItem(item: ShoppingItem) {
        applyCommand(Command.DeleteItem(item))
    }

    fun editItem(previousSnapshot: ShoppingItem, newFields: ItemFields) {
        applyCommand(Command.EditItem(previousSnapshot, newFields))
    }

    fun checkItem(item: ShoppingItem) {
        applyCommand(Command.CheckItem(item))
    }

    fun uncheckItem(item: ShoppingItem) {
        applyCommand(Command.UncheckItem(item))
    }

    fun undo() {
        val listId = _listSelection.value.currentListId ?: return
        viewModelScope.launch {
            try {
                checkNotNull(listResources[listId]).undoRedoManager.undo()
            } catch (e: Exception) {
                logAndEmitError("Undo failed", e)
            }
        }
    }

    fun redo() {
        val listId = _listSelection.value.currentListId ?: return
        viewModelScope.launch {
            try {
                checkNotNull(listResources[listId]).undoRedoManager.redo()
            } catch (e: Exception) {
                logAndEmitError("Redo failed", e)
            }
        }
    }

    // --- Item dialogs ---

    fun openEditDialog(item: ShoppingItem) {
        _dialogState.value = ItemDialogState(
            title = "Edit item",
            initialFields = item.fields,
            showDelete = true,
            onSave = { newFields ->
                editItem(item, newFields)
                dismissDialog()
            },
            onDelete = {
                deleteItem(item)
                dismissDialog()
            },
            onCancel = { dismissDialog() },
        )
    }

    fun openAddDialog(initialName: String) {
        _dialogState.value = ItemDialogState(
            title = "Add item",
            initialFields = ItemFields(name = initialName),
            showDelete = false,
            onSave = { newFields ->
                val listId = _listSelection.value.currentListId ?: return@ItemDialogState
                val repo = checkNotNull(listResources[listId]).repository
                val item = ShoppingItem(
                    id = repo.newItemId(),
                    fields = newFields.copy(name = newFields.name.trim()),
                )
                applyCommand(Command.AddItem(item))
                dismissDialog()
            },
            onDelete = null,
            onCancel = { dismissDialog() },
        )
    }

    fun dismissDialog() {
        _dialogState.value = null
    }

    // --- List operations ---

    fun selectList(listId: String) {
        _listSelection.update { it.copy(currentListId = listId) }
    }

    fun addList(name: String) {
        val user = currentUserFlow.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                Timber.v("DEBUG SVM ${currentUserFlow.value?.email} addList before createList name=$trimmed")
                val id = listRepository.createList(user, trimmed)
                Timber.v("DEBUG SVM ${currentUserFlow.value?.email} addList after createList name=$trimmed id=$id")
                Timber.v("RACE_DEBUG ${currentUserFlow.value?.email} in ShoppingViewModel.addList call getOrCreateResource name=$trimmed listId=${id}")
                // Pre-warm resources to start the remote-change listener before navigating.
                getOrCreateResources(ListMetadata(id = id, name = trimmed, isOwner = true, ownerUid = user.uid))
                _pendingAddListIds.add(id)
                Timber.v("DEBUG SVM ${currentUserFlow.value?.email} addList completing: updating _currentListId from ${_listSelection.value.currentListId} to $id (name=$trimmed)")
                _listSelection.update { it.copy(currentListId = id) }
                Timber.v("DEBUG SVM ${currentUserFlow.value?.email} created list name=$trimmed id=$id")
            } catch (e: Exception) {
                logAndEmitError("Failed to create list", e)
            }
        }
    }

    fun renameCurrentList(name: String) {
        val list = _listSelection.value.currentList() ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                listRepository.renameList(list, trimmed)
            } catch (e: Exception) {
                logAndEmitError("Failed to rename list", e)
            }
        }
    }

    fun deleteCurrentList() {
        val list = _listSelection.value.currentList() ?: return
        viewModelScope.launch {
            try {
                listRepository.deleteList(list)
                // observeLists() will emit without this list; init block selects the next one.
            } catch (e: Exception) {
                logAndEmitError("Failed to delete list", e)
            }
        }
    }

    // --- List dialogs ---

    fun openAddListDialog() {
        _addListDialogVisible.value = true
    }

    fun dismissAddListDialog() {
        _addListDialogVisible.value = false
    }

    fun openRenameListDialog() {
        _renameListDialogVisible.value = true
    }

    fun dismissRenameListDialog() {
        _renameListDialogVisible.value = false
    }

    fun openDeleteListDialog() {
        _deleteListDialogVisible.value = true
    }

    fun dismissDeleteListDialog() {
        _deleteListDialogVisible.value = false
    }

    // --- Import list ---

    fun openImportListDialog() {
        _importListDialogState.value = ImportListDialogState()
    }

    fun dismissImportListDialog() {
        _importListDialogState.value = null
    }

    fun exportCurrentListToCsv(): String {
        val state = uiState.value
        val items = state.uncheckedItems + state.checkedItems
        return CsvExporter.export(items)
    }

    fun importListFromCsv(name: String, csvContent: String) {
        val user = currentUserFlow.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return

        val existingNames = uiState.value.lists.map { it.name }.toSet()
        val uniqueName = uniqueListName(trimmed, existingNames)
        if (uniqueName != trimmed) {
            _importListDialogState.value = ImportListDialogState(
                proposedName = uniqueName,
                errorMessage = "\"$trimmed\" already exists.",
            )
            return
        }

        val items = try {
            CsvImporter.parse(csvContent)
        } catch (e: CsvParseException) {
            _importListDialogState.value = ImportListDialogState(errorMessage = e.message)
            return
        }

        viewModelScope.launch {
            try {
                val listId = listRepository.createList(user, uniqueName)
                val list = ListMetadata(id = listId, name = uniqueName, isOwner = true, ownerUid = user.uid)
                val repo = getOrCreateResources(list).repository
                for (fields in items) {
                    repo.apply(
                        Command.AddItem(
                            ShoppingItem(
                                id = repo.newItemId(),
                                fields = fields
                            )
                        )
                    )
                }
                _listSelection.update { it.copy(currentListId = listId) }
                dismissImportListDialog()
            } catch (e: Exception) {
                logAndEmitError("Failed to import list", e)
            }
        }
    }

    private fun uniqueListName(desired: String, existingNames: Set<String>): String {
        val lowerExisting = existingNames.map { it.lowercase() }.toSet()
        if (desired.lowercase() !in lowerExisting) return desired
        val suffixPattern = Regex(
            "^${Regex.escape(desired)} \\((\\d+)\\)$",
            RegexOption.IGNORE_CASE,
        )
        val maxSuffix = existingNames
            .mapNotNull { suffixPattern.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull() ?: 0
        return "$desired (${maxSuffix + 1})"
    }

    // --- Share list ---

    fun openShareListDialog() {
        _shareListDialogState.value = ShareListDialogState()
    }

    fun dismissShareListDialog() {
        _shareListDialogState.value = null
    }

    fun shareList(email: String) {
        Timber.v("sharing some list with $email")
        val list = _listSelection.value.currentList() ?: return
        viewModelScope.launch {
            try {
                listRepository.addEditor(list, email)
                _shareListDialogState.value = null
                _errors.tryEmit("Editor added")
                Timber.v("shared some list with $email")
            } catch (e: Exception) {
                Timber.v("shared some list failed $email $e")
                Timber.e(e, "Failed to add editor")
                val msg = if (isNetworkError(e))
                    "No internet connection. Adding an editor requires being online."
                else
                    e.message ?: "Failed to add editor"
                _shareListDialogState.value = ShareListDialogState(errorMessage = msg)
            }
        }
    }

    // --- Helpers ---

    private fun applyCommand(command: Command) {
        val listId = _listSelection.value.currentListId ?: return
        Timber.v("DEBUG SVM ${currentUserFlow.value?.email} applyCommand $command list=${_listSelection.value.currentListName()} listId=$listId")
        viewModelScope.launch {
            try {
                checkNotNull(listResources[listId]).undoRedoManager.execute(command)
                Timber.v("DEBUG SVM ${currentUserFlow.value?.email} applyCommand completed $command")
            } catch (e: Exception) {
                logAndEmitError("Command failed: ${command::class.simpleName}", e)
            }
        }
    }

    private fun isNetworkError(e: Throwable): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause is IOException) return true
            cause = cause.cause
        }
        return false
    }

    private fun logAndEmitError(message: String, e: Throwable) {
        Timber.e(e, message)
        _errors.tryEmit(e.message ?: message)
    }

    private fun logAndEmitFatalError(message: String, e: Throwable) {
        if (e.message != null) {
            Timber.e(e.message!!)
        }
        // TODO: should push this into Firebase code probably
        if (e is FirebaseFunctionsException && e.details != null) {
            Timber.e(e, e.details.toString())
        } else {
            Timber.e(e, message)
        }
        _fatalError.value = e.message ?: message
    }
}
