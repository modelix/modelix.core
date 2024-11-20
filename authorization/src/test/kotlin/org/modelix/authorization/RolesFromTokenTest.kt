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

package org.modelix.authorization

import com.auth0.jwt.JWT
import kotlin.test.Test
import kotlin.test.assertEquals

class RolesFromTokenTest {

    @Test
    fun `extract roles from token created by Keycloak`() {
        val token = JWT.decode("eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICI5T1VVR2pRa3ZQRE1OMmJjcFA1RDRLUnpOM3l2elRWOV9TZFVrdlpUUG1NIn0.eyJleHAiOjE3MzIwNTgzODcsImlhdCI6MTczMjAyMjM4NywiYXV0aF90aW1lIjoxNzMyMDA1Njk5LCJqdGkiOiI3MjI4ZTkxMS03YmY3LTQ3YWMtODYxMy1jNDQyNTYwODJjZDkiLCJpc3MiOiJodHRwczovL2xvY2FsaG9zdC9yZWFsbXMvbW9kZWxpeCIsImF1ZCI6WyJtb2RlbGl4IiwiYWNjb3VudCJdLCJzdWIiOiIzZmYwYWMxNi00NjU4LTRjOTItOGUyZS01NTIwNTM1YzFhN2YiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJtb2RlbGl4Iiwibm9uY2UiOiI3ZmtBMFJ6ZUhSNkpNa0ZZeV9qTEQ1cE5aSmdRdVVBNUszcVFyUjdTeVR3Iiwic2Vzc2lvbl9zdGF0ZSI6IjU2NDIzYTZiLTM2OGUtNDZjYS04ZDM0LWI5YTA1ZjExM2Q0NyIsImFjciI6IjEiLCJhbGxvd2VkLW9yaWdpbnMiOlsiKiJdLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsibW9kZWxpeC11c2VyIiwib2ZmbGluZV9hY2Nlc3MiLCJ1bWFfYXV0aG9yaXphdGlvbiIsImRlZmF1bHQtcm9sZXMtbW9kZWxpeCJdfSwicmVzb3VyY2VfYWNjZXNzIjp7Im1vZGVsaXgiOnsicm9sZXMiOlsidW1hX3Byb3RlY3Rpb24iXX0sImFjY291bnQiOnsicm9sZXMiOlsibWFuYWdlLWFjY291bnQiLCJtYW5hZ2UtYWNjb3VudC1saW5rcyIsInZpZXctcHJvZmlsZSJdfX0sInNjb3BlIjoib3BlbmlkIHByb2ZpbGUgZW1haWwiLCJzaWQiOiI1NjQyM2E2Yi0zNjhlLTQ2Y2EtOGQzNC1iOWEwNWYxMTNkNDciLCJlbWFpbF92ZXJpZmllZCI6ZmFsc2UsIm5hbWUiOiJTIEwiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJzbEBxNjAuZGUiLCJnaXZlbl9uYW1lIjoiUyIsImZhbWlseV9uYW1lIjoiTCIsImVtYWlsIjoic2xAcTYwLmRlIn0.m-PW8gNmrjQhLJw6BJez-pQSUk8jMZ5QB2HuPv-pyJZon6idsxp5sSpMelWb_3Cb78BEf5AeSbzxB_yZJEf7uFbAYURsRAumaiq8u5HofHuwIoofyCoJjGKlBYnkZpL1mNRPy1sHZfdMre3Yh6bKsztz0PWaEVlSx8wGyXPup84p2uy5-k0eThAI2zKmIa-YxGXmCwb0IbQakp5Q77mQeWa1e_ozr4zf72ScbvB80ourRJEY6YwkZyEbIoM015CvlE3hgN5fL0AVg9Zr18pY4oSwwNYbIiaIbWlUN29QcelDq1jX969fIQw2O1GJEusU3K_ZtWZJsMZdPWYpxf-uiw")
        val roles = ModelixJWTUtil().extractUserRoles(token)
        assertEquals(listOf("modelix-user", "offline_access", "uma_authorization", "default-roles-modelix"), roles)
    }
}
