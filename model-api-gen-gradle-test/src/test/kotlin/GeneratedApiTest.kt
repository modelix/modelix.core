import jetbrains.mps.lang.core.L_jetbrains_mps_lang_core
import jetbrains.mps.lang.editor.*
import org.modelix.metamodel.TypedLanguagesRegistry
import org.modelix.metamodel.typed
import org.modelix.metamodel.untyped
import org.modelix.model.ModelFacade
import org.modelix.model.api.INode
import org.modelix.model.api.getRootNode
import org.modelix.model.data.ModelData
import java.io.File
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