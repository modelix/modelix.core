package org.modelix.authorization

import com.auth0.jwt.JWT
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.proc.BadJOSEException
import io.ktor.server.application.install
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.modelix.authorization.permissions.buildPermissionSchema
import java.io.File
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

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
        util.useKtorClient(client)
        util.addJwksUrl(URI("http://localhost/.well-known/jwks.json").toURL())
        val token = ModelixJWTUtil().also { it.setRSAPrivateKey(rsaPrivateKey) }.createAccessToken("unit-test@example.com", listOf())
        util.verifyToken(token)
    }

    @Test
    fun `verification with mismatching keys fails`() = runTest {
        val util = ModelixJWTUtil()
        util.useKtorClient(client)
        util.addJwksUrl(URI("http://localhost/.well-known/jwks.json").toURL())
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

        val publicKeyFile = File.createTempFile("modelix_rsa_test", ".pem")
        publicKeyFile.deleteOnExit()
        publicKeyFile.writeText(publicKeyPem)
        val privateKeyFile = File.createTempFile("modelix_rsa_test", ".pem")
        privateKeyFile.deleteOnExit()
        privateKeyFile.writeText(privateKeyPem)

        val signingUtil = ModelixJWTUtil()
        val verifyingUtil = ModelixJWTUtil()
        verifyingUtil.loadKeysFromFiles(publicKeyFile)
        signingUtil.loadKeysFromFiles(privateKeyFile)
        val tokenString = signingUtil.createAccessToken("units-test@example.com", listOf())
        val token = JWT.decode(tokenString)
        assertEquals("uDTdtRkJdv2y7WfxBVtXiV4jgxyJhPcQg-byYepLjGM", token.keyId)
        assertEquals(JWSAlgorithm.RS256.name, token.algorithm)
        verifyingUtil.verifyToken(tokenString)
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

        val publicKeyFile = File.createTempFile("modelix_rsa_test", ".json")
        publicKeyFile.deleteOnExit()
        publicKeyFile.writeText(publicKeyJson)
        val privateKeyFile = File.createTempFile("modelix_rsa_test", ".json")
        privateKeyFile.deleteOnExit()
        privateKeyFile.writeText(privateKeyJson)

        val signingUtil = ModelixJWTUtil()
        val verifyingUtil = ModelixJWTUtil()
        verifyingUtil.loadKeysFromFiles(publicKeyFile)
        signingUtil.loadKeysFromFiles(privateKeyFile)
        val tokenString = signingUtil.createAccessToken("units-test@example.com", listOf())
        val token = JWT.decode(tokenString)
        assertEquals("sig-1733907425", token.keyId)
        assertEquals(JWSAlgorithm.RS256.name, token.algorithm)
        verifyingUtil.verifyToken(tokenString)
    }

    @Test
    fun `key file changes are detected`() {
        val publicKeyPem1 = """
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

        val privateKeyPem1 = """
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

        val publicKeyPem2 = """
            -----BEGIN CERTIFICATE-----
            MIIDBDCCAeygAwIBAgIRAIhY21WHcprgWOeIwzOnWykwDQYJKoZIhvcNAQELBQAw
            HDEaMBgGA1UEAxMRd29ya3NwYWNlLW1hbmFnZXIwIBcNMjQxMjAyMTQxNjU4WhgP
            MjEyNDEyMDIxNDE2NThaMBwxGjAYBgNVBAMTEXdvcmtzcGFjZS1tYW5hZ2VyMIIB
            IjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxibvEUBG84RmrLME+j3tUL4r
            Mu/6ERMwKvRUCRCr1ipI/Sj6CZQjfJ4TYE+UzUu12Y73ixbwUW2BMg22CdiXLcgZ
            8thrWrepRt1JGxf4xdVKGeVUAFesIVx2sriviiaN5z27FzayPVJBOM1mkCJEO1bE
            YCEUO/4T6YyuGS8EYZewnHaYJuG+aU4rmLuHHlEH0gOzmfvh7dVj9QVr5TxPraMx
            G0ZtFg6/MN31HQi2aTcU4V4a9H57mPzWQyw9HTCEla/kgY8ehGxHuPS8IomdfuOR
            u9nPDD9/IvTn9JGpHSw6uy7bE2WKIdWsbKCil6+N4vCpIlAIvFxGvMiEc6rzjQID
            AQABoz8wPTAOBgNVHQ8BAf8EBAMCBaAwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsG
            AQUFBwMCMAwGA1UdEwEB/wQCMAAwDQYJKoZIhvcNAQELBQADggEBAFeao6ek117i
            fbYUlWlENtH+BG0MBwj7ISrmW3qBiTlKdnGA5b0yc+BzpLTnBYLDDMzEAruteXHp
            N2VOO9va0mA0lApeonc8p0s8Cqo4+emnQQ6jrEU/iXuu3SPbR9TisqNH2IwKOy8F
            yL5TPzCZlBrT2rd2G76sgU+F94eA/UQM0Jltj4mbSM1+OeyG2XJFvWILTt4ZTm2z
            6bkp/IwknK2mgV2cZnyPA8scPZNZKXm9jjAhkAk6Brq7bYsJh7+vEHSqpezPij0M
            xprunA5E+X76IafWObKJifGJOGewUa4U3czaSMJWTzgHDVPZR3HDDvqnb2yQpCDH
            pF3ByAzGKjk=
            -----END CERTIFICATE-----
        """.trimIndent().trim()

        val privateKeyPem2 = """
            -----BEGIN RSA PRIVATE KEY-----
            MIIEpQIBAAKCAQEAxibvEUBG84RmrLME+j3tUL4rMu/6ERMwKvRUCRCr1ipI/Sj6
            CZQjfJ4TYE+UzUu12Y73ixbwUW2BMg22CdiXLcgZ8thrWrepRt1JGxf4xdVKGeVU
            AFesIVx2sriviiaN5z27FzayPVJBOM1mkCJEO1bEYCEUO/4T6YyuGS8EYZewnHaY
            JuG+aU4rmLuHHlEH0gOzmfvh7dVj9QVr5TxPraMxG0ZtFg6/MN31HQi2aTcU4V4a
            9H57mPzWQyw9HTCEla/kgY8ehGxHuPS8IomdfuORu9nPDD9/IvTn9JGpHSw6uy7b
            E2WKIdWsbKCil6+N4vCpIlAIvFxGvMiEc6rzjQIDAQABAoIBAQCfSa8GtCAVJAsR
            qztGGsAKF0VMxkLEtSMUdKKVQvSPziAsemM9jftU8xHqay7YNZNy143BHuiC3L9t
            yD3c/mLRJ7lMUZNDMr7+O2bIQ+X0yretx39WYyP5EYZNt09NhB6wlBww1gREbToG
            +n8HQLSO6vojuJO2glHpffB6SCSCee3xlIR+bkDrqO8J4MEGVhcS/sGrHJRfn7ro
            UbPHtYHKRCEXKlBS7J4QXGbvLoV6hho3qm8AlnG9XSZcrvcstrlAsIPU37g65c6k
            h4WV0srHWS44bKi+abM10PeHLGhQGTOs9Lxd982F4J8Xj+ia0SEdR2d/pFoza2hL
            8WgLgnp1AoGBAM++JVCBSWV2Q/vxvqb5CG3T4BCFlr4S4EtGnnFHhMXrVHIqPSMo
            kw3/TJ3uniu9CYrxzRBvnAeP2ZSiqq9nWc7Kc0bKMNz41iZLQVeujP2qbndFQC2o
            h6BxPkwWRvsVWSKOwZaQqHDVxkPjJM+mL3veX7ue0jJrEVMfXyTfxOsPAoGBAPQu
            dXsfBPM2LQnFPhUU9zZAVTA/F5WDIXpuA6LMNDqeEpJvKTxptdLOIC/CncZBqkRx
            3AGi4ij9hk8GVcDA4mCatWPooisfJa4T9FS1GVWxDZk5QeuL1YXcFI0V9uimbtWm
            TLu9Hyu0nHVAuKMA2NqnhgPWOdUr//rGleOW5iejAoGBAIzCtBnmYEsFZW8zEBGn
            L9Tq+Sl4uvkzZRLcWMM8yHQqzl9Ey4QlG+8iC1H/uuC8B9lDmcUHOtvM1orl5W1Q
            RAPgHVfb7Fvtp3zvBOladmHyt0LNg3zscml+Ec4QUiwS/QBzZiyU++zojJy3Ldwd
            KJNvy8IfDSHodiayXQ9pJ851AoGAGVnJcKLjzKxPOLh1nZKzp7o+Hegu9qLKkv9g
            +UHiGkPXAcTwrwj6i4xC4zJ9VtvyZXC8up7ChCbuDr5FoOFln0nwkxLP41I0g0In
            F7RFkRP0qXe8VEwMOv2CVLN3EuhUkXHWfZdA6TSzGalCggnQecLysutGzc7noI2F
            ej9sXakCgYEAkAMgcu6zjJB/6DVymM7hAGFKPhw8tx1tkg0Gfmxqz3PEW30gB2v3
            OIw0VGx+VNTOJJV8D+NodzuIRulSW7iP4VZmraAT3icXfviEF0FZEmiSlTbIKGIo
            OfMfMlkQSCpfxZVYVddEE3qEse1ySeoq7EFau76RUu4uwu7brlhx+l8=
            -----END RSA PRIVATE KEY-----
        """.trimIndent().trim()

        val publicKeyFile = File.createTempFile("modelix_rsa_test", ".pem")
        publicKeyFile.deleteOnExit()
        publicKeyFile.writeText(publicKeyPem1)
        val privateKeyFile = File.createTempFile("modelix_rsa_test", ".pem")
        privateKeyFile.deleteOnExit()
        privateKeyFile.writeText(privateKeyPem1)

        val verifyingUtil = ModelixJWTUtil()
        verifyingUtil.fileRefreshTime = 50.milliseconds
        verifyingUtil.loadKeysFromFiles(publicKeyFile)
        run {
            val signingUtil = ModelixJWTUtil()
            signingUtil.loadKeysFromFiles(privateKeyFile)
            val tokenString = signingUtil.createAccessToken("units-test@example.com", listOf())
            val token = JWT.decode(tokenString)
            assertEquals("uDTdtRkJdv2y7WfxBVtXiV4jgxyJhPcQg-byYepLjGM", token.keyId)
            assertEquals(JWSAlgorithm.RS256.name, token.algorithm)
            verifyingUtil.verifyToken(tokenString)
        }

        publicKeyFile.writeText(publicKeyPem2)
        privateKeyFile.writeText(privateKeyPem2)

        run {
            val signingUtil = ModelixJWTUtil()
            signingUtil.loadKeysFromFiles(privateKeyFile)
            val tokenString = signingUtil.createAccessToken("units-test@example.com", listOf())
            val token = JWT.decode(tokenString)
            assertEquals("DYQKmzKbhTVbHQVj245QuuMI3syBK84xufQSp5JCgsE", token.keyId)
            assertEquals(JWSAlgorithm.RS256.name, token.algorithm)

            // New keys should only be loaded after the cached ones expired
            assertFailsWith<BadJOSEException> {
                verifyingUtil.verifyToken(tokenString)
            }
            Thread.sleep(50)
            verifyingUtil.verifyToken(tokenString)
        }
    }
}
