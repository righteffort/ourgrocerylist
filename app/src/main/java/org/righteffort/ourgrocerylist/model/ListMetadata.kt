package org.righteffort.ourgrocerylist.model

data class ListMetadata(
    val id: String,
    val name: String,
    val isOwner: Boolean,
    val ownerUid: String,
)
