package org.righteffort.ourgrocerylist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctionsException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
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
import org.righteffort.ourgrocerylist.util.CsvImporter
import org.righteffort.ourgrocerylist.util.CsvParseException
import timber.log.Timber
import kotlin.coroutines.ContinuationInterceptor

private val ITEM_COMPARATOR = compareBy<ShoppingItem> { it.fields.name.lowercase() }

class ShoppingViewModel(
    private val currentUserFlow: StateFlow<User?>,
    private val listRepository: ListRepository,
    private val repositoryFactory: (listId: String) -> ShoppingRepository,
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

    private val _currentListId = MutableStateFlow<String?>(null)

    private val _lists = MutableStateFlow<List<ListMetadata>>(emptyList())

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
    val importListDialogState: StateFlow<ImportListDialogState?> = _importListDialogState.asStateFlow()

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
                    _currentListId.value = null
                }
            }
        }
        viewModelScope.launch {
            listRepository.observeLists()
                .catch { e -> logAndEmitFatalError("Failed to observe lists", e) }
                .collect { lists ->
                    Timber.v("DEBUG SVM observeLists lists=${lists.map { it.id }}")
                    val newIds = lists.map { it.id }.toSet()

                    // Create resources for newly discovered lists and start remote-change listeners.
                    for (list in lists) {
                        if (list.id !in listResources) {
                            getOrCreateResources(list.id)
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
                                Timber.v("DEBUG SVM creating Groceries default list")
                                listRepository.createList(user, "Groceries")
                            } catch (e: Exception) {
                                logAndEmitFatalError("Failed to create initial list", e)
                            }
                        }
                        Timber.v("DEBUG SVM lists empty, updating _lists")
                        _lists.value = lists
                        return@collect
                    }

                    // Select a list if none is selected or the current one was removed.
                    val currentId = _currentListId.value
                    if (currentId == null || currentId !in newIds) {
                        val selected = lists.firstOrNull { it.isOwner } ?: lists.first()
                        Timber.v("DEBUG SVM auto-selecting list id=${selected.id}")
                        _currentListId.value = selected.id
                    }
                    Timber.v("DEBUG SVM lists non-empty, updating _lists=${lists.map { it.id }}")
                    _lists.value = lists
                }
        }
    }

    // Creates resources for a list on first access. Safe to call multiple times for the same id.
    private fun getOrCreateResources(listId: String): ListResources =
        listResources.getOrPut(listId) {
            val repo = repositoryFactory(listId)
            val undoRedoManager = UndoRedoManager(repo)
            val job = viewModelScope.launch {
                repo.observeRemotelyModifiedItemIds()
                    .catch { e -> logAndEmitError("Failed to observe remote changes for $listId", e) }
                    .collect { itemIds -> itemIds.forEach { undoRedoManager.pruneForRemoteWrite(it) } }
            }
            ListResources(repo, undoRedoManager, job)
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState> = combine(
        _currentListId.flatMapLatest { listId ->
            Timber.v("DEBUG SVM uiState listId=$listId")
            if (listId == null) {
                flowOf(Triple<String?, List<ShoppingItem>, UndoRedoState>(null, emptyList(), UndoRedoState()))
            } else {
                val resources = getOrCreateResources(listId)
                combine(
                    resources.repository.observeItems()
                        .catch { e ->
                            Timber.e(e, "DEBUG SVM observeItems terminated with error for listId=$listId — items observer is now dead")
                            logAndEmitFatalError("Failed to observe items", e)
                            emit(emptyList())
                        },
                    resources.undoRedoManager.state,
                ) { items, undoState -> Triple<String?, List<ShoppingItem>, UndoRedoState>(listId, items, undoState) }
            }
        },
        _lists,
        currentUserFlow,
    ) { (listId, items, undoState), lists, currentUser ->
        val currentList = lists.find { it.id == listId }
        Timber.v("DEBUG SVM building UiState lists=${lists.map { it.id }} currentList=${currentList?.id}")
        val (checked, unchecked) = items.partition { it.fields.checked }
        Timber.v("DEBUG SVM constructing UiState listId=$listId items=$items lists=$lists")
        UiState(
            uncheckedItems = unchecked.sortedWith(ITEM_COMPARATOR),
            checkedItems = checked.sortedWith(ITEM_COMPARATOR),
            undoAvailable = undoState.undoAvailable,
            redoAvailable = undoState.redoAvailable,
            currentListName = currentList?.name ?: "",
            lists = lists,
            isOwner = currentList?.isOwner ?: false,
            currentUserEmail = currentUser?.email ?: "",
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    // --- Item operations ---

    fun addItem(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val listId = _currentListId.value ?: return
        val repo = getOrCreateResources(listId).repository
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
        val listId = _currentListId.value ?: return
        viewModelScope.launch {
            try {
                getOrCreateResources(listId).undoRedoManager.undo()
            } catch (e: Exception) {
                logAndEmitError("Undo failed", e)
            }
        }
    }

    fun redo() {
        val listId = _currentListId.value ?: return
        viewModelScope.launch {
            try {
                getOrCreateResources(listId).undoRedoManager.redo()
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
                val listId = _currentListId.value ?: return@ItemDialogState
                val repo = getOrCreateResources(listId).repository
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
        _currentListId.value = listId
    }

    fun addList(name: String) {
        val user = currentUserFlow.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        Timber.v("DEBUG SVM launching but using what dispatcher and scheduler? context=${viewModelScope.coroutineContext}")
        Timber.v("DEBUG SVM addList main.immediate ${Dispatchers.Main.immediate.hashCode()} ${Dispatchers.Main.immediate}")

        val svmDispatcher = viewModelScope.coroutineContext[ContinuationInterceptor]
        Timber.v("svm dispatcher: $svmDispatcher (${"%x".format(System.identityHashCode(svmDispatcher))})")
        Timber.v("svm dispatcher class: ${svmDispatcher!!::class.java.name}")
        viewModelScope.launch {
            try {
                Timber.v("DEBUG SVM in ShoppingViewModel.addList launch i wish we could add")
                Timber.v("DEBUG SVM addList before createList name=$trimmed")
                val id = listRepository.createList(user, trimmed)
                Timber.v("DEBUG SVM addList after createList id=$id")
                Timber.v("RACE_DEBUG in ShoppingViewModel.addList call getOrCreateResource listId=${id}")
                // Pre-warm resources to start the remote-change listener before navigating.
                getOrCreateResources(id)
                Timber.v("DEBUG SVM addList completing: overwriting _currentListId from ${_currentListId.value} to $id (name=$trimmed)")
                _currentListId.value = id
                Timber.v("DEBUG SVM created list name=$trimmed id=$id")
            } catch (e: Exception) {
                logAndEmitError("Failed to create list", e)
            }
        }
    }

    fun renameCurrentList(name: String) {
        val listId = _currentListId.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                listRepository.renameList(listId, trimmed)
            } catch (e: Exception) {
                logAndEmitError("Failed to rename list", e)
            }
        }
    }

    fun deleteCurrentList() {
        val listId = _currentListId.value ?: return
        viewModelScope.launch {
            try {
                listRepository.deleteList(listId)
                // observeLists() will emit without this list; init block selects the next one.
            } catch (e: Exception) {
                logAndEmitError("Failed to delete list", e)
            }
        }
    }

    // --- List dialogs ---

    fun openAddListDialog() { _addListDialogVisible.value = true }
    fun dismissAddListDialog() { _addListDialogVisible.value = false }

    fun openRenameListDialog() { _renameListDialogVisible.value = true }
    fun dismissRenameListDialog() { _renameListDialogVisible.value = false }

    fun openDeleteListDialog() { _deleteListDialogVisible.value = true }
    fun dismissDeleteListDialog() { _deleteListDialogVisible.value = false }

    // --- Import list ---

    fun openImportListDialog() { _importListDialogState.value = ImportListDialogState() }
    fun dismissImportListDialog() { _importListDialogState.value = null }

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
                val repo = getOrCreateResources(listId).repository
                for (fields in items) {
                    repo.apply(Command.AddItem(ShoppingItem(id = repo.newItemId(), fields = fields)))
                }
                _currentListId.value = listId
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

    fun openShareListDialog() { _shareListDialogState.value = ShareListDialogState() }
    fun dismissShareListDialog() { _shareListDialogState.value = null }

    fun shareList(email: String) {
        Timber.v("sharing some list with $email")
        val listId = _currentListId.value ?: return
        viewModelScope.launch {
            try {
                listRepository.addEditor(listId, email)
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
        val listId = _currentListId.value ?: return
        Timber.v("DEBUG SVM applyCommand $command listId=$listId")
        viewModelScope.launch {
            try {
                getOrCreateResources(listId).undoRedoManager.execute(command)
                Timber.v("DEBUG SVM applyCommand completed $command")
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
