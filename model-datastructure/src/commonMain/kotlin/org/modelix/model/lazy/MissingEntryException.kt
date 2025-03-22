package org.modelix.model.lazy

class MissingEntryException(val hash: String) : NoSuchElementException("Entry not found: $hash")
