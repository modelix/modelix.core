package org.modelix.model.api

import org.modelix.kotlin.utils.ContextValue

interface IMetaModelScope {

    fun tryResolve(ref: ConceptReference): IConcept?

    companion object {
        val contextScope = ContextValue<IMetaModelScope>()

        fun getCurrentScopes(): List<IMetaModelScope> {
            return when (val current = contextScope.getValueOrNull()) {
                null -> emptyList()
                is CompositeMetaModelScope -> current.scopes
                else -> listOf(current)
            }
        }

        private fun combineScopes(scopeToAdd: IMetaModelScope): IMetaModelScope {
            val newScopes = (listOf(scopeToAdd) + (getCurrentScopes() - scopeToAdd))
            return when (newScopes.size) {
                0 -> throw RuntimeException("Impossible case")
                1 -> newScopes.single()
                else -> CompositeMetaModelScope(newScopes)
            }
        }

        fun <T> runWithAdditionalScope(scope: IMetaModelScope, body: () -> T): T {
            return contextScope.computeWith(combineScopes(scope), body)
        }

        suspend fun <T> runWithAdditionalScopeInCoroutine(scope: IMetaModelScope, body: suspend () -> T): T {
            return contextScope.runInCoroutine(combineScopes(scope), body)
        }
    }
}

class CompositeMetaModelScope(val scopes: List<IMetaModelScope>) : IMetaModelScope {
    override fun tryResolve(ref: ConceptReference): IConcept? {
        return scopes.firstNotNullOf { it.tryResolve(ref) }
    }
}

fun IConceptReference.resolve(): IConcept = (this as ConceptReference).resolve()
fun IConceptReference.tryResolve(): IConcept? = (this as ConceptReference).tryResolve()

fun ConceptReference.resolve(): IConcept = tryResolve() ?: throw ConceptNotFoundException(this)
fun ConceptReference.tryResolve(): IConcept? = IMetaModelScope.contextScope.getValueOrNull()?.tryResolve(this) ?: ILanguageRepository.tryResolveConcept(this)

fun IChildLinkReference.tryResolve(concept: ConceptReference): IChildLinkDefinition? = concept.tryResolve()?.tryResolveChildLink(this)
fun IChildLinkReference.resolve(concept: ConceptReference): IChildLinkDefinition = concept.resolve().tryResolveChildLink(this) ?: throw ChildLinkNotFoundException(this)
fun IConcept.tryResolveChildLink(role: IChildLinkReference): IChildLinkDefinition? = getAllChildLinks().find { it.toReference().matches(role) }

fun IReferenceLinkReference.tryResolve(concept: ConceptReference): IReferenceLinkDefinition? = concept.tryResolve()?.tryResolveReferenceLink(this)
fun IReferenceLinkReference.resolve(concept: ConceptReference): IReferenceLinkDefinition = concept.resolve().tryResolveReferenceLink(this) ?: throw ReferenceLinkNotFoundException(this)
fun IConcept.tryResolveReferenceLink(role: IReferenceLinkReference): IReferenceLinkDefinition? = getAllReferenceLinks().find { it.toReference().matches(role) }

fun IPropertyReference.tryResolve(concept: ConceptReference): IPropertyDefinition? = concept.tryResolve()?.tryResolveProperty(this)
fun IPropertyReference.resolve(concept: ConceptReference): IPropertyDefinition = concept.resolve().tryResolveProperty(this) ?: throw PropertyNotFoundException(this)
fun IConcept.tryResolveProperty(role: IPropertyReference): IPropertyDefinition? = getAllProperties().find { it.toReference().matches(role) }

abstract class MetaModelElementNotFoundException(message: String) : IllegalStateException(message)
class ConceptNotFoundException(val conceptReference: ConceptReference) : MetaModelElementNotFoundException("Concept not found: $conceptReference")
abstract class RoleNotFoundException(message: String) : MetaModelElementNotFoundException(message) {
    abstract val role: IRoleReference
}
abstract class LinkNotFoundException(message: String) : RoleNotFoundException(message) {
    abstract override val role: ILinkReference
}
class PropertyNotFoundException(override val role: IPropertyReference) : RoleNotFoundException("Property not found: $role")
class ReferenceLinkNotFoundException(override val role: IReferenceLinkReference) : LinkNotFoundException("Reference link not found: $role")
class ChildLinkNotFoundException(override val role: IChildLinkReference) : LinkNotFoundException("Child link not found: $role")
