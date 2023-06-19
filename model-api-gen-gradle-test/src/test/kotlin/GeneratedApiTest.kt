import jetbrains.mps.baseLanguage.C_ClassConcept
import jetbrains.mps.baseLanguage.jdk8.C_SuperInterfaceMethodCall_old
import jetbrains.mps.lang.behavior.C_ConceptMethodDeclaration
import jetbrains.mps.lang.core.L_jetbrains_mps_lang_core
import jetbrains.mps.lang.editor.*
import jetbrains.mps.lang.smodel.query.CustomScope_old
import org.junit.jupiter.api.BeforeAll
import org.modelix.metamodel.TypedLanguagesRegistry
import org.modelix.metamodel.typed
import org.modelix.metamodel.untyped
import org.modelix.model.ModelFacade
import org.modelix.model.api.INode
import org.modelix.model.api.getRootNode
import org.modelix.model.data.ModelData
import java.io.File
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class GeneratedApiTest {

    @Test
    fun `can handle enums`() {
        val branch = ModelFacade.toLocalBranch(ModelFacade.newLocalTree())
        TypedLanguagesRegistry.register(L_jetbrains_mps_lang_editor)
        TypedLanguagesRegistry.register(L_jetbrains_mps_lang_core)
        val data = ModelData.fromJson(File("build/metamodel/exported-modules/jetbrains.mps.baseLanguage.blTypes.json").readText())
        branch.runWrite {
            data.load(branch)
            val node = findNodeWithStyleAttribute(branch.getRootNode())!!.typed(C_FontStyleStyleClassItem.getInstanceInterface())
            assertContains(_FontStyle_Enum.values(), node.style)
            val enumValue = _FontStyle_Enum.BOLD_ITALIC
            node.style = enumValue
            assertEquals(enumValue, node.untyped().typed(C_FontStyleStyleClassItem.getInstanceInterface()).style)
        }
    }

    @Test
    fun `propagates deprecations`() {
        val foundDeprecatedConcept = C_SuperInterfaceMethodCall_old::class.hasDeprecationWithMessage()
        val foundDeprecatedProperty = C_ConceptMethodDeclaration::class.members.any { it.hasDeprecationWithMessage() }
        val foundDeprecatedChildLink = C_ClassConcept::class.members.any { it.hasDeprecationWithMessage() }
        val foundDeprecatedReference = C_SuperInterfaceMethodCall_old::class.members.any { it.hasDeprecationWithMessage() }
        assert(foundDeprecatedConcept)
        assert(foundDeprecatedProperty)
        assert(foundDeprecatedChildLink)
        assert(foundDeprecatedReference)
    }

    private fun KAnnotatedElement.hasDeprecationWithMessage() =
        findAnnotation<Deprecated>()?.message?.isNotEmpty() ?: false

    private fun findNodeWithStyleAttribute(node: INode) : INode? {
        var found = node.allChildren.find { it.getPropertyRoles().contains("style") }
        if (found != null) return found

        for (child in node.allChildren) {
            found = findNodeWithStyleAttribute(child)
            if (found != null) {
                return found
            }
        }
        return null
    }

}