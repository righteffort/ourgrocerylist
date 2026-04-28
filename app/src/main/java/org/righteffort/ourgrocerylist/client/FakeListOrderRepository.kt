package org.righteffort.ourgrocerylist.client

class FakeListOrderRepository : ListOrderRepository {
    val timestamps = mutableMapOf<String, Long>()

    override suspend fun loadAll() = timestamps.toMap()
    override suspend fun save(listId: String, timestamp: Long) { timestamps[listId] = timestamp }
    override suspend fun remove(listId: String) { timestamps.remove(listId) }
}
