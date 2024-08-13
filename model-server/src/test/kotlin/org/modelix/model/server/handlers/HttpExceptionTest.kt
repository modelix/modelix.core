package org.modelix.model.server.handlers

import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import kotlin.test.Test

class HttpExceptionTest {

    @Test
    fun `test problem of InvalidRepositoryIdException`() {
        val problem = InvalidRepositoryIdException("invalid ID").problem

        val expectedProblem = Problem(
            type = "/problems/invalid-repository-id",
            title = "Invalid repository ID.",
            status = HttpStatusCode.BadRequest.value,
            detail = "Repository ID `invalid ID` is not valid.",
        )
        problem shouldBe expectedProblem
    }

    @Test
    fun `test problem of ObjectKeyWithoutObjectValueException`() {
        val problem = ObjectKeyWithoutObjectValueException("someKey").problem

        val expectedProblem = Problem(
            type = "/problems/object-key-without-object-value",
            title = "Uploaded object key without object value.",
            status = HttpStatusCode.BadRequest.value,
            detail = "Uploaded object key `someKey` without object value.",
        )
        problem shouldBe expectedProblem
    }

    @Test
    fun `test problem of InvalidObjectKeyException`() {
        val problem = InvalidObjectKeyException("someKey").problem

        val expectedProblem = Problem(
            type = "/problems/invalid-object-key",
            title = "Uploaded invalid object key.",
            status = HttpStatusCode.BadRequest.value,
            detail = "Uploaded invalid object key `someKey`.",
        )
        problem shouldBe expectedProblem
    }

    @Test
    fun `test problem of MismatchingObjectKeyAndValueException`() {
        val problem = MismatchingObjectKeyAndValueException(
            "wrongObjectKey",
            "correctObjectKey",
            "objectValue",
        ).problem

        val expectedDetailMsg = "Uploaded object key `wrongObjectKey` does not match " +
            "expected object key `correctObjectKey` for object value `objectValue`."
        val expectedProblem = Problem(
            type = "/problems/mismatching-object-and-value",
            title = "Uploaded mismatching object key and value.",
            status = HttpStatusCode.BadRequest.value,
            detail = expectedDetailMsg,
        )
        problem shouldBe expectedProblem
    }

    @Test
    fun `test problem of ObjectValueNotFoundException`() {
        val problem = ObjectValueNotFoundException("objectValue").problem

        val expectedProblem = Problem(
            type = "/problems/object-value-not-found",
            title = "Object value not found.",
            status = HttpStatusCode.NotFound.value,
            detail = "Object value with hash `objectValue` does not exist.",
        )
        problem shouldBe expectedProblem
    }
}
