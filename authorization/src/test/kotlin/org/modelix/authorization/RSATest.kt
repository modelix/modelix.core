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

    @Test
    fun `can load keys in JWK format`() {
        // language=json
        val publicKeyJson = """
            {
                "kty": "RSA",
                "e": "AQAB",
                "use": "sig",
                "kid": "sig-1733907425",
                "alg": "RS256",
                "n": "jIRZzMHFd9tlVvqE3Ovh8DrhrVu7VCoxatCdUv1-kZJ1iFsE7gMIimdJNoqZumoFOOIV418bfHbu0yTWiYKZhqYTM2Q_VVPY3cavu74aYsbQ_j2O6P-CcC0IB1th2icesQRqC2j-z9CztFGfRsLMnFkmyalMAmXvABBxKV-auZm0BlRKyfJ_eacIwk0_rmQW0pEUafFGhOgU9NgxxV-tJXl1gxKn6kH3WNhpwaTJtH9q4nfB03kZHqwvvLKEQW6QVmyPuzfhVXOZRSjzwsdyzCFTzwaAVdaVFxOiEfH8zbOto-8CBWcXzcTlY9FXSq_1QQINkqnYxyPsZT4lHsRjjw"
            }
        """.trimIndent().trim()

        // language=json
        val privateKeyJson = """
            {
                "p": "xtB2RMru_9pBiF1kNJCngdqVvdIhsA4AzbZ44-rD6oMH0GMI9JYmbbmTYo6zu8qMa1CLJ4dLQcFgAyBNV8JkC4-WsRa-dWEdLeidvyH34xTKfOGTGlBeFMunvRAJYQHn4TcF7J74fRddxd2F99JYsoUmCRfu0gjLq03l3cFhFuc",
                "kty": "RSA",
                "q": "tO82HFPW8fDYuwM6nTOdF888lSv_zs4AGauqPFr9HN5hIpt2k35Xl7-gXdCzxEd8D7o7CWL_AcXy2qSfb0HGgQeavOEb8ELwLP0RHgNAB0LA3oShEnWAvpOzrr72M3aSL2dELdy9kNW-rJiF-RoDZ84BsBLfSArv6Qb7K3YzwRk",
                "d": "JP1iNkh8FwUmNDNWbmGZ5IdbiSswsQM6ZwfrokEg5GlNj0uGjLE3uldeKoFp3myyWzsI0AXlUmpsjCCSaTh7-boWK90j3u5nlFoNQLrWb1IvCf5idGtuhuETz_v6Ulch-S9USxSkn0gtRjaGWzZEbpP5ZfSvEaKLu9SYNW_5ZwnvJoplNK5RzbU48HHedVc8t1Ef0aWsxYW1tXz80j-NG4PNF-Ey7C7cqSbLgBTqIV513K3dm8S8bU8SCMuA-XPCGMEULj2ZpiVoHfIhqfBy113zOoTDgo6R7C8N-ameVXEJKMc0PEbidsVhG75vKD2i0CI9PenW1ZuOMyCPi9g0QQ",
                "e": "AQAB",
                "use": "sig",
                "kid": "sig-1733907425",
                "qi": "sM5Pog0X9GBdflzq4MNdDLZ-il6a-sMrhSVDx_2hZWjOs8aVKdnwZNJl7jx2UFgtKIBKCVCh473dNrrRi4dRu7FnAUaxXoCOdHABk-GIHQlPkO3BAZyMFp4UyrdlaZiA-zyUGYBPf1SEESb1enQYsCc8y3Fk8NqxxL5BFj6l2FI",
                "dp": "kwlXfrcrHRP4xXZ0hp-5EsNrXXDMM12X4IwkSkO1U3pGzCqCVAm8MAhAZXKuoKNDSJbP45Me6GmwrX81VENTJG20gBIXF86T-wD_sXzYzRvySXu3BI4Nlomr65qxpQn4yUqdWguUMUeXtZ-I1ei-aoEoyS7nFHUm0_GPoHrFaF8",
                "alg": "RS256",
                "dq": "HJUhdi4kaYoDot9qtgS-T1GUn3gY7CGM0IFW3jv9ej8DF0V54Oj3i2hhPBDJJTuptI5V3zC9WhlcOQACk7_PTPjXj_j7wePBL0o3FweqaLs53q0TCOh5EyIgI33VROH5S_XDRn91jtjFS1y45VYfrZlUmO0SSr43khdhPEdq-5k",
                "n": "jIRZzMHFd9tlVvqE3Ovh8DrhrVu7VCoxatCdUv1-kZJ1iFsE7gMIimdJNoqZumoFOOIV418bfHbu0yTWiYKZhqYTM2Q_VVPY3cavu74aYsbQ_j2O6P-CcC0IB1th2icesQRqC2j-z9CztFGfRsLMnFkmyalMAmXvABBxKV-auZm0BlRKyfJ_eacIwk0_rmQW0pEUafFGhOgU9NgxxV-tJXl1gxKn6kH3WNhpwaTJtH9q4nfB03kZHqwvvLKEQW6QVmyPuzfhVXOZRSjzwsdyzCFTzwaAVdaVFxOiEfH8zbOto-8CBWcXzcTlY9FXSq_1QQINkqnYxyPsZT4lHsRjjw"
            }
        """.trimIndent().trim()

        val publicJwk = ModelixJWTUtil().loadJwkFile(publicKeyJson)
        val privateJwk = ModelixJWTUtil().loadJwkFile(privateKeyJson)
        assertEquals("sig-1733907425", publicJwk.keyID)
        assertEquals(publicJwk.keyID, privateJwk.keyID)
        assertEquals(JWSAlgorithm.RS256, privateJwk.algorithm)
        assertEquals(publicJwk.algorithm, privateJwk.algorithm)
    }
}
