package org.modelix.authorization

class NotLoggedInException : RuntimeException("No valid JWT token found in the request headers")
