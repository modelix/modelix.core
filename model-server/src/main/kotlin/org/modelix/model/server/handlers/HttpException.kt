/*
 * Copyright (c) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.model.server.handlers

import io.ktor.http.HttpStatusCode
import org.modelix.api.v1.Problem
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
 * Indicates a bad request from the client with missing or invalid information provided.
 *
 * @param details the detailed message to expose to the caller
 * @param typeSuffix A detailed type making this bad request instance uniquely identifiable. [PROBLEM_NAMESPACE] will
 *                   automatically be prepended. This field should be written in kebab-case.
 * @param cause The causing exception for the bad request or null if none.
 */
class BadRequestException(details: String, typeSuffix: String, cause: Throwable? = null) : HttpException(
    HttpStatusCode.BadRequest,
    title = "Bad request",
    details = details,
    type = problemType(typeSuffix),
    cause = cause,
)

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
 * @param versionHash has of the missing version
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
