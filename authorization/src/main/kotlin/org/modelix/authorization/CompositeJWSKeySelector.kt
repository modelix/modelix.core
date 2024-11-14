package org.modelix.authorization

import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.proc.JWSKeySelector
import com.nimbusds.jose.proc.SecurityContext
import java.security.Key

internal class CompositeJWSKeySelector<C : SecurityContext>(val selectors: List<JWSKeySelector<C>>) : JWSKeySelector<C> {
    override fun selectJWSKeys(
        header: JWSHeader,
        context: C?,
    ): List<Key> {
        return selectors.flatMap { it.selectJWSKeys(header, context) }
    }
}
