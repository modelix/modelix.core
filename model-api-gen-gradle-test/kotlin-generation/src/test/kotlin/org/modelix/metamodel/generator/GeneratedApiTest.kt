import jetbrains.mps.baseLanguage.C_ClassConcept
import jetbrains.mps.baseLanguage.ClassConcept
import jetbrains.mps.baseLanguage.jdk8.C_SuperInterfaceMethodCall_old
import jetbrains.mps.baseLanguage.jdk8.SuperInterfaceMethodCall_old
import jetbrains.mps.lang.behavior.C_ConceptMethodDeclaration
import jetbrains.mps.lang.behavior.ConceptMethodDeclaration
import jetbrains.mps.lang.core.C_BaseConcept
import jetbrains.mps.lang.core.L_jetbrains_mps_lang_core
import jetbrains.mps.lang.editor.C_FontStyleStyleClassItem
import jetbrains.mps.lang.editor.L_jetbrains_mps_lang_editor
import jetbrains.mps.lang.editor._FontStyle_Enum
import org.modelix.apigen.test.IMetaConceptProperties
import org.modelix.metamodel.IPropertyValueEnum
import org.modelix.metamodel.TypedLanguagesRegistry
import org.modelix.metamodel.typed
import org.modelix.metamodel.untyped
import org.modelix.model.ModelFacade
import org.modelix.model.api.INode
import org.modelix.model.api.conceptAlias
import org.modelix.model.api.getRootNode
import org.modelix.model.data.ConceptData
import org.modelix.model.data.ModelData
import java.io.File
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeneratedApiTest {

    @Test
    fun `can handle enums`() {
        val branch = ModelFacade.toLocalBranch(ModelFacade.newLocalTree())
        TypedLanguagesRegistry.register(L_jetbrains_mps_lang_editor)
        TypedLanguagesRegistry.register(L_jetbrains_mps_lang_core)
        val data = ModelData.fromJson(File("../metamodel-export/build/metamodel/exported-modules/jetbrains.mps.baseLanguage.blTypes.json").readText())
        branch.runWrite {
            data.load(branch)
            val node = findNodeWithStyleAttribute(branch.getRootNode())!!.typed(C_FontStyleStyleClassItem.getInstanceInterface())
            assertTrue(_FontStyle_Enum::class.isSubclassOf(IPropertyValueEnum::class))
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

        val foundDeprecatedNodeWrapper = SuperInterfaceMethodCall_old::class.hasDeprecationWithMessage()
        val foundDeprecatedNodeProperty = ConceptMethodDeclaration::class.members.any { it.hasDeprecationWithMessage() }
        val foundDeprecatedNodeChildLink = ClassConcept::class.members.any { it.hasDeprecationWithMessage() }
        val foundDeprecatedNodeReference = SuperInterfaceMethodCall_old::class.members.any { it.hasDeprecationWithMessage() }

        assertTrue(foundDeprecatedConcept)
        assertTrue(foundDeprecatedProperty)
        assertTrue(foundDeprecatedChildLink)
        assertTrue(foundDeprecatedReference)

        assertTrue(foundDeprecatedNodeWrapper)
        assertTrue(foundDeprecatedNodeProperty)
        assertTrue(foundDeprecatedNodeChildLink)
        assertTrue(foundDeprecatedNodeReference)
    }

    @Test
    fun `metaProperty alias is generated`() {
        val hasAlias = IMetaConceptProperties::class.members.any { it.name == ConceptData.ALIAS_KEY }
        assertTrue(hasAlias)
        assertNull(C_BaseConcept.alias)
    }

    @Test
    fun `metaProperty alias has value`() {
        val alias = C_ClassConcept.alias
        assertEquals("class", alias)
    }

    @Test
    fun `alias can be retrieved via conceptAlias`() {
        assertEquals(C_ClassConcept.alias, C_ClassConcept.untyped().conceptAlias())
    }

    private fun KAnnotatedElement.hasDeprecationWithMessage() =
        findAnnotation<Deprecated>()?.message?.isNotEmpty() ?: false

    private fun findNodeWithStyleAttribute(node: INode): INode? {
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
