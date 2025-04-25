package org.modelix.model.lazy

open class MissingEntryException(val hash: String) : NoSuchElementException("Entry not found: $hash")
