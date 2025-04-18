package org.modelix.mps.gitimport

import jetbrains.mps.project.AbstractModule
import org.jetbrains.mps.openapi.module.ModelAccess
import org.jetbrains.mps.openapi.module.RepositoryAccess
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleId
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.module.SRepositoryListener
import org.jetbrains.mps.openapi.repository.CommandListener
import org.jetbrains.mps.openapi.repository.ReadActionListener
import org.jetbrains.mps.openapi.repository.WriteActionListener
import java.lang.AutoCloseable

class DummyRepo : SRepository, AutoCloseable {
    private val modelAccess = DummyModelAccess()
    private val registeredModules: MutableMap<SModuleId, SModule> = LinkedHashMap()

    fun register(module: SModule) {
        module as AbstractModule
        val existing = registeredModules[module.moduleId] as AbstractModule?
        check(existing == null) {
            "Already registered: ${module.moduleReference}, existing: ${existing?.descriptorFile?.path}, new: ${module.descriptorFile?.path}"
        }
        registeredModules[module.moduleId] = module
        module.attach(this)
    }

    fun unregister(module: SModule) {
        module as AbstractModule
        module.dispose()
        registeredModules.remove(module.moduleId)
    }

    override fun getParent(): SRepository? {
        return null
    }

    fun dispose() {
        for (entry in registeredModules) {
            (entry.value as AbstractModule).dispose()
        }
    }

    override fun close() {
        dispose()
    }

    override fun getModule(id: SModuleId): SModule? = registeredModules[id]

    override fun getModules(): Iterable<SModule> = registeredModules.values

    override fun getModelAccess(): ModelAccess {
        return modelAccess
    }

    override fun getRepositoryAccess(): RepositoryAccess? {
        TODO("Not yet implemented")
    }

    override fun saveAll() {
        TODO("Not yet implemented")
    }

    override fun addRepositoryListener(p0: SRepositoryListener) {
        TODO("Not yet implemented")
    }

    override fun removeRepositoryListener(p0: SRepositoryListener) {
        TODO("Not yet implemented")
    }
}

class DummyModelAccess : ModelAccess {
    override fun canRead(): Boolean = true

    override fun checkReadAccess() {}

    override fun canWrite(): Boolean = true

    override fun checkWriteAccess() {}

    override fun runReadAction(body: Runnable) {
        body.run()
    }

    override fun runReadInEDT(body: Runnable) {
        body.run()
    }

    override fun runWriteAction(body: Runnable) {
        body.run()
    }

    override fun runWriteInEDT(body: Runnable) {
        body.run()
    }

    override fun executeCommand(body: Runnable) {
        body.run()
    }

    override fun executeCommandInEDT(body: Runnable) {
        body.run()
    }

    override fun executeUndoTransparentCommand(body: Runnable) {
        body.run()
    }

    override fun isCommandAction(): Boolean {
        TODO("Not yet implemented")
    }

    override fun addCommandListener(listener: CommandListener) {
        TODO("Not yet implemented")
    }

    override fun removeCommandListener(p0: CommandListener?) {
        TODO("Not yet implemented")
    }

    override fun addWriteActionListener(p0: WriteActionListener) {
        TODO("Not yet implemented")
    }

    override fun removeWriteActionListener(p0: WriteActionListener) {
        TODO("Not yet implemented")
    }

    override fun addReadActionListener(p0: ReadActionListener) {
        TODO("Not yet implemented")
    }

    override fun removeReadActionListener(p0: ReadActionListener) {
        TODO("Not yet implemented")
    }
}
