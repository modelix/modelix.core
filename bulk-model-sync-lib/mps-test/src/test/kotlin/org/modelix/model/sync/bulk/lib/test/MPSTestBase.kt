package org.modelix.model.sync.bulk.lib.test

import com.intellij.testFramework.HeavyPlatformTestCase
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.project.MPSProject
import jetbrains.mps.smodel.adapter.structure.concept.InvalidConcept
import jetbrains.mps.smodel.language.LanguageRegistry
import org.jetbrains.mps.openapi.language.SAbstractConcept
import java.io.File
import java.nio.file.Path
import java.util.regex.Pattern

private val TESTDATA_PATTERN = Pattern.compile(".*\\(testdata (.*)\\)")

abstract class MPSTestBase : HeavyPlatformTestCase() {

    protected lateinit var projectDir: Path

    protected val mpsProject: MPSProject
        get() {
            return requireNotNull(ProjectHelper.fromIdeaProject(project)) {
                "MPS project not loaded"
            }
        }

    fun runWrite(body: () -> Unit) {
        val repository = mpsProject.repository
        val access = repository.modelAccess
        access.runWriteAction {
            body()
        }
    }

    override fun isCreateDirectoryBasedProject(): Boolean = true

    override fun getProjectDirOrFile(isDirectoryBasedProject: Boolean): Path {
        val projectDir = super.getProjectDirOrFile(isDirectoryBasedProject)
        /*
            > A widespread pattern in IntelliJ Platform tests is to use the test method's name being executed
            > as the base for building the testdata file paths.
            > This allows us to reuse most of the code between different test methods that test various aspects
            > of the same feature, and this approach is also recommended for third-party plugin tests.
            > The name of the test method can be retrieved using UsefulTestCase.getTestName().

            see https://plugins.jetbrains.com/docs/intellij/test-project-and-testdata-directories.html#testdata-files
         */
        // The test data is chosen by the test name.
        // This is a recommended approach.
        val testSpecificDataName = TESTDATA_PATTERN.matcher(getTestName(false))
            .takeIf { it.matches() }?.group(1)

        if (testSpecificDataName != null) {
            val sourceDir = File("testdata/$testSpecificDataName")
            sourceDir.copyRecursively(projectDir.toFile(), overwrite = true)
        }
        this.projectDir = projectDir
        return projectDir
    }

    fun resolveMPSConcept(conceptFqName: String): SAbstractConcept {
        return resolveMPSConcept(conceptFqName.substringBeforeLast("."), conceptFqName.substringAfterLast("."))
    }

    fun resolveMPSConcept(languageName: String, conceptName: String): SAbstractConcept {
        val baseLanguage =
            LanguageRegistry.getInstance(mpsProject.repository).allLanguages.single { it.qualifiedName == languageName }
        val classConcept = baseLanguage.concepts.single { it.name == conceptName }
        check(classConcept !is InvalidConcept)
        return classConcept
    }
}
