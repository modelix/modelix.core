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

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.proc.BadJOSEException
import io.ktor.server.application.install
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.modelix.authorization.permissions.buildPermissionSchema
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RSATest {

    private val rsaPrivateKey = ModelixJWTUtil().generateRSAPrivateKey()

    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ModelixAuthorization) {
                permissionSchema = buildPermissionSchema { }
                ownPublicKey = rsaPrivateKey.toPublicJWK()
            }
        }
        block()
    }

    @Test
    fun `verify signature against public key provided by server`() = runTest {
        val util = ModelixJWTUtil()
        util.addJwksUrl(URI("http://localhost/.well-known/jwks.json").toURL())
        util.useKtorClient(client)
        val token = ModelixJWTUtil().also { it.setRSAPrivateKey(rsaPrivateKey) }.createAccessToken("unit-test@example.com", listOf())
        util.verifyToken(token)
    }

    @Test
    fun `verification with mismatching keys fails`() = runTest {
        val util = ModelixJWTUtil()
        util.addJwksUrl(URI("http://localhost/.well-known/jwks.json").toURL())
        util.useKtorClient(client)
        util.generateRSAPrivateKey()
        val token = ModelixJWTUtil().also { it.generateRSAPrivateKey() }.createAccessToken("unit-test@example.com", listOf())
        val ex = assertFailsWith(BadJOSEException::class) {
            util.verifyToken(token)
        }
        assertTrue((ex.message ?: "").contains("no matching key"))
    }

    @Test
    fun `can load keys in pem format`() {
        val publicKeyPem = """
            -----BEGIN CERTIFICATE-----
            MIIDBDCCAeygAwIBAgIRAMsOxfAGx0Q8gryFhrNZoNowDQYJKoZIhvcNAQELBQAw
            HDEaMBgGA1UEAxMRd29ya3NwYWNlLW1hbmFnZXIwIBcNMjQxMTE1MTMyNzQyWhgP
            MjEyNDExMTUxMzI3NDJaMBwxGjAYBgNVBAMTEXdvcmtzcGFjZS1tYW5hZ2VyMIIB
            IjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyB+2c/hRX7lhcTKHOom13F7d
            Vnujy1XndcYp4y42NIxRZDuimOU/inkH6tJsclIftPeYSWnSTWRc5ZG268pRMjD6
            rMCxCTyo1S7VGuXtdPbfL1makCYfpKALBZdLgrYVkor49CP2cBdKPldYUT7+EpqF
            xXkaeL073bS3vPPdxN/riuYu3df3tLe9+st6Tr6+rv1+HK+dRegPok8ryMOogT96
            QyF7ygLDQ1WW/v/CZI5y+jW1xEpWnHRkRqHWTtIMjWN6WK+ez1kg4tlQDWmMn4by
            wmTPRs38weLEMnTUrjfrOxOc59rWOyE7b186RrDf1F1ezLiVUlLA9L7ThydM3QID
            AQABoz8wPTAOBgNVHQ8BAf8EBAMCBaAwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsG
            AQUFBwMCMAwGA1UdEwEB/wQCMAAwDQYJKoZIhvcNAQELBQADggEBAE+fIPlFYLiP
            4QoxWBVIaQVC1o/DMtfDe7qSzd561+4fsgqbTE07DnKSX1Y7hHHSoUOOI42UUzyR
            wcqTMqkoF4fdoT9onPCDldc6SJQHrRmH7l3YFiVk+bM2NR7QuL9/9Dn5sqzoaWEh
            9zB8fk6T/g/56OPyvzs4tzC1Pvmz4JfwX9hTKIbqh3duUBfov2m3nkzbmoMF987x
            0hdxnMqzOWq9y4dBOLQNheCkVDctImDNIPLQ1IJuzm3GpIpPxuOSLgDi7Nh1QHnI
            S3F48Kap0hI/OhqgM3mBUGs56Fc5THNh0zVuGqsIAW7jUiYH+lrnmWzNC/Uf9CpH
            SZWUy6UZNS4=
            -----END CERTIFICATE-----
        """.trimIndent().trim()

        val privateKeyPem = """
            -----BEGIN RSA PRIVATE KEY-----
            MIIEogIBAAKCAQEAyB+2c/hRX7lhcTKHOom13F7dVnujy1XndcYp4y42NIxRZDui
            mOU/inkH6tJsclIftPeYSWnSTWRc5ZG268pRMjD6rMCxCTyo1S7VGuXtdPbfL1ma
            kCYfpKALBZdLgrYVkor49CP2cBdKPldYUT7+EpqFxXkaeL073bS3vPPdxN/riuYu
            3df3tLe9+st6Tr6+rv1+HK+dRegPok8ryMOogT96QyF7ygLDQ1WW/v/CZI5y+jW1
            xEpWnHRkRqHWTtIMjWN6WK+ez1kg4tlQDWmMn4bywmTPRs38weLEMnTUrjfrOxOc
            59rWOyE7b186RrDf1F1ezLiVUlLA9L7ThydM3QIDAQABAoIBAEXspsCgrDYpPP3j
            bNKsWWn1j5rvOo0KqARDyFEDzZbQzIOcPrTzrR8CKR0IhzHutftyY7iLDBtUjQz9
            vA9pMrO532zLK1CR7GAIrBdo7W5n8BXIVjQ1zeqkrRU4Bv9WBfWdL12Gz03dJWjg
            9g/1VatEaKdWKES1whw2T9jq0Ls/7/uRTtL31g6SnI/UW5RnZe4TQhNtnTltts6T
            eHUU7MjKIlB4VQrHx8up/QdsMIvXihv72jm374nZe6U3e8HmuGb71qXA4YPFju5c
            Aict16PVNUTb2ZAylH33NB0k1LlHaCbkQM+Cy3jhhtb1XERXt7tDyS/hiC++HG6b
            jlAvqzUCgYEA27OjEbEbw60ca9goC/mafZoDofZWA3aNI+TR15EsFAYQHtoE4DLy
            Nrlm0syqqJJwf117jLhu+KpKrJtb36XqfUqnwwISAilnr6OnPT47qs8dbrRIxnap
            COh9yw0YerLFPuJ9HTPZMCWs7ufDcXJyuRfjL25lq/kv7jGD6jHRvnMCgYEA6TAG
            PK/OyIizT4OtdzNbejQ7W+9wi4tfhjF8OMmgQb6kpsmSmhoaFCQ5SAg9MwqbL2q1
            3XSEkPXljONqWmkQZ/2Eo4WHveOKoKj/07LiRucs5jjHyr5pea80z5lTnE8i7MJX
            eNSTqi3b9WnV0J0EHhg7qgAbH/q+c5gtiqgkI28CgYB9z0ONSQdmKUaCNzjPirK+
            RCjaYW7l8shmCo1jzT0ZhlNK53wtSt9LGSZZhlwfxiPnu4eZkK/zc8jpSNn2m1NJ
            RiwFTrUzSbSXbrbBKlcOvCXVlCWsiJzJfiEy2p/u+1paZWZSB7PSj3CVKmDQIUKy
            3Yv6SFSugzbARtiMjtTWIwKBgGFKDyAcvap/FkjTiHkWLVFkH2vxD0S5RoaHeOt8
            e+dSMgIAUbEHuN+0aU27WkVEZJC49d3KclDEtxw7+bB060pnxIIxAPxhxgHX4Lyj
            grLQWrRG9lyJaxpA1kjTEMZDYi/juXkJP/6dmYrfuDyMdh5UP/hiiO6jv/gcgsu5
            8THzAoGAUGCnccd4JAXK3/rmkCLT++M18G6ik+qaTMdhGnC9opTDWDvxWF6jHj7w
            4/wol7RQf0qmWZr6sSg+dg/cEOvAxBDiayl7WALnEpGhh2+aKkDVIy7JSTOm3fkO
            P1Z2sotIDXrYJrdKl/BvWh80ifVYjHp9J/cOhMSyj/HCMhxexhY=
            -----END RSA PRIVATE KEY-----
        """.trimIndent().trim()

        val publicJwk = ModelixJWTUtil().loadPemFile(publicKeyPem)
        val privateJwk = ModelixJWTUtil().loadPemFile(privateKeyPem)
        println(publicJwk)
        println(privateJwk)
        assertEquals("uDTdtRkJdv2y7WfxBVtXiV4jgxyJhPcQg-byYepLjGM", publicJwk.keyID)
        assertEquals(publicJwk.keyID, privateJwk.keyID)
        assertEquals(JWSAlgorithm.RS256, privateJwk.algorithm)
        assertEquals(publicJwk.algorithm, privateJwk.algorithm)
    }
}
