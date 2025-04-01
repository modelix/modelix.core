package org.modelix.model.server.handlers

import io.ktor.http.HttpStatusCode
import org.modelix.model.lazy.BranchReference

/**
 * A namespace for problems we use as the first part of every `type` in an application/problem+json response.
 *
 * Follows the ideas from https://opensource.zalando.com/restful-api-guidelines/#176.
 */
const val PROBLEM_NAMESPACE = "/problems"

/**
 * Constructs a namespaces problem type for application/problem+json in the format /<namespace>/<suffix>.
 *
 * The generated format follows the ideas from https://opensource.zalando.com/restful-api-guidelines/#176
 *
 * @param suffix kebab-case identifier for a certain type of problem
 */
fun problemType(suffix: String) = "$PROBLEM_NAMESPACE/$suffix"

/**
 * An exception used to indicate a problem serving an HTTP request. It includes details used to construct a proper
 * response.
 *
 * @param problem the problem to use as the basis for this exception. In case the problem does not contain a status
 *                code, an internal server error 500 is assumed.
 * @param cause the exception that caused this problem or null if none.
 */
open class HttpException(problem: Problem, cause: Throwable? = null) : RuntimeException(problem.toString(), cause) {
    /**
     * A description of the problem this exception is reporting to the caller.
     */
    val problem = problem.copy(status = problem.status ?: HttpStatusCode.InternalServerError.value)

    /**
     * Creates a new instance of this exception based on a status code and other details contributing to a [Problem].
     */
    constructor(
        statusCode: HttpStatusCode = HttpStatusCode.InternalServerError,
        title: String? = null,
        details: String? = null,
        type: String? = null,
        instance: String? = null,
        cause: Throwable? = null,
    ) : this(
        Problem(status = statusCode.value, title = title, detail = details, type = type, instance = instance),
        cause,
    )
}

/**
 * A [HttpException] indicating that a branch was not found in a repository.
 *
 * @param branch name of the missing branch
 * @param repositoryId ID of the repository missing the branch
 * @param cause The causing exception for the bad request or null if none.
 */
class BranchNotFoundException(branch: String, repositoryId: String, cause: Throwable? = null) : HttpException(
    HttpStatusCode.NotFound,
    title = "Branch not found",
    details = "Branch '$branch' does not exist in repository '$repositoryId'",
    type = "/problems/branch-not-found",
    cause = cause,
) {
    constructor(branch: BranchReference, cause: Throwable? = null) : this(
        branch.branchName,
        branch.repositoryId.id,
        cause,
    )
}

/**
 * A [HttpException] indicating that a version inside a repository or branch was not found.
 *
 * @param versionHash hash of the missing version
 * @param cause The causing exception for the bad request or null if none.
 */
class VersionNotFoundException(versionHash: String, cause: Throwable? = null) :
    HttpException(
        HttpStatusCode.NotFound,
        title = "Version not found",
        details = "Version '$versionHash' doesn't exist",
        type = "/problems/version-not-found",
        cause = cause,
    )

/**
 * A [HttpException] indicating that a node was not found.
 *
 * @param nodeId id of the missing node
 * @param cause The causing exception for the bad request or null if none.
 */
class NodeNotFoundException(nodeId: Any?, cause: Throwable? = null) :
    HttpException(
        HttpStatusCode.NotFound,
        title = "Node not found",
        details = "Node with id $nodeId doesn't exist",
        type = "/problems/node-not-found",
        cause = cause,
    )

/**
 * An [HttpException] indicating that a provided repository name is not valid.
 *
 * @param invalidRepositoryId the invalid repository ID
 * @param cause The causing exception for the bad request or null if none.
 */
class InvalidRepositoryIdException(invalidRepositoryId: String, cause: Throwable? = null) :
    HttpException(
        HttpStatusCode.BadRequest,
        title = "Invalid repository ID.",
        details = "Repository ID `$invalidRepositoryId` is not valid.",
        type = "/problems/invalid-repository-id",
        cause = cause,
    )

/**
 * An [HttpException] indicating that an object key was uploaded without the corresponding value.
 *
 * @param objectKey the uploaded object key
 */
class ObjectKeyWithoutObjectValueException(objectKey: String) :
    HttpException(
        HttpStatusCode.BadRequest,
        title = "Uploaded object key without object value.",
        details = "Uploaded object key `$objectKey` without object value.",
        type = "/problems/object-key-without-object-value",
    )

/**
 * An [HttpException] indicating that an invalid object key was uploaded.
 *
 * @param objectKey the uploaded object key
 */
class InvalidObjectKeyException(objectKey: String) :
    HttpException(
        HttpStatusCode.BadRequest,
        title = "Uploaded invalid object key.",
        details = "Uploaded invalid object key `$objectKey`.",
        type = "/problems/invalid-object-key",
    )

/**
 * An [HttpException] indicating that an object key and value were uploaded that do not match.
 *
 * @param uploadedObjectKey the uploaded object key
 * @param expectedObjectKey the expected object key based on the object value
 * @param objectValue the uploaded object value
 */
class MismatchingObjectKeyAndValueException(
    uploadedObjectKey: String,
    expectedObjectKey: String,
    objectValue: String,
) :
    HttpException(
        HttpStatusCode.BadRequest,
        title = "Uploaded mismatching object key and value.",
        details = "Uploaded object key `$uploadedObjectKey` does not match expected object key `$expectedObjectKey` " +
            "for object value `$objectValue`.",
        type = "/problems/mismatching-object-and-value",
    )

/**
 * A [HttpException] indicating that an object value was not found.
 *
 * @param objectHash hash of the missing object
 * @param cause The causing exception for the bad request or null if none.
 */
class ObjectValueNotFoundException(objectHash: String, cause: Throwable? = null) :
    HttpException(
        HttpStatusCode.NotFound,
        title = "Object value not found.",
        details = "Object value with hash `$objectHash` does not exist.",
        type = "/problems/object-value-not-found",
        cause = cause,
    )
