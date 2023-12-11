/*
 * Copyright (c) 2023.
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

package org.modelix.mps.model.sync.bulk;

import org.modelix.model.mpsadapters.MPSRepositoryAsNode;
import java.util.Set;
import org.jetbrains.mps.openapi.module.SModule;
import jetbrains.mps.internal.collections.runtime.Sequence;
import jetbrains.mps.internal.collections.runtime.IWhereFilter;
import org.modelix.model.sync.bulk.UtilKt;
import org.modelix.model.sync.bulk.ModelExporter;
import org.modelix.model.mpsadapters.MPSModuleAsNode;
import java.io.File;
import org.jetbrains.mps.openapi.module.ModelAccess;
import jetbrains.mps.baseLanguage.closures.runtime.Wrappers;
import org.modelix.model.sync.bulk.ModelImporter;
import org.modelix.model.sync.bulk.PlatformSpecificKt;
import com.intellij.openapi.application.ApplicationManager;
import jetbrains.mps.internal.collections.runtime.SetSequence;
import java.util.HashSet;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.mps.openapi.module.SRepository;
import jetbrains.mps.ide.project.ProjectHelper;

public class MPSBulkSynchronizer {
    public static void exportRepository() throws Exception {
        final MPSRepositoryAsNode repoAsNode = getRepositoryAsNode();

        final Set<String> includedModuleNames = parseRawPropertySet(System.getProperty("modelix.mps.model.sync.bulk.output.modules"));

        final Set<String> includedModulePrefixes = parseRawPropertySet(System.getProperty("modelix.mps.model.sync.bulk.output.modules.prefixes"));

        repoAsNode.getRepository().getModelAccess().runReadAction(new Runnable() {
            public void run() {
                Iterable<SModule> allModules = repoAsNode.getRepository().getModules();
                Iterable<SModule> includedModules = Sequence.fromIterable(allModules).where(new IWhereFilter<SModule>() {
                    public boolean accept(SModule module) {
                        return UtilKt.isModuleIncluded(module.getModuleName(), includedModuleNames, includedModulePrefixes);
                    }
                });

                int current = 0;
                int numSelectedModules = Sequence.fromIterable(includedModules).count();
                String outputPath = System.getProperty("modelix.mps.model.sync.bulk.output.path");

                for (SModule module : Sequence.fromIterable(includedModules)) {
                    current += 1;
                    System.out.println("Exporting module " + current + " of " + numSelectedModules + ": '" + module.getModuleName() + "'");
                    ModelExporter exporter = new ModelExporter(new MPSModuleAsNode(module));
                    File outputFile = new File(outputPath + File.separator + module.getModuleName() + ".json");
                    exporter.export(outputFile);
                }
            }
        });
    }

    public static void importRepository() {
        final MPSRepositoryAsNode repoAsNode = getRepositoryAsNode();

        final Set<String> includedModuleNames = parseRawPropertySet(System.getProperty("modelix.mps.model.sync.bulk.input.modules"));
        final Set<String> includedModulePrefixes = parseRawPropertySet(System.getProperty("modelix.mps.model.sync.bulk.input.modules.prefixes"));

        final String inputPath = System.getProperty("modelix.mps.model.sync.bulk.input.path");
        final ModelAccess access = repoAsNode.getRepository().getModelAccess();
        access.runWriteInEDT(new Runnable() {
            public void run() {
                Iterable<SModule> allModules = repoAsNode.getRepository().getModules();
                final Iterable<SModule> includedModules = Sequence.fromIterable(allModules).where(new IWhereFilter<SModule>() {
                    public boolean accept(SModule it) {
                        return UtilKt.isModuleIncluded(it.getModuleName(), includedModuleNames, includedModulePrefixes);
                    }
                });

                final Wrappers._int current = new Wrappers._int(0);
                final int numIncludedModules = Sequence.fromIterable(includedModules).count();

                access.executeCommand(new Runnable() {
                    public void run() {
                        for (SModule module : Sequence.fromIterable(includedModules)) {
                            current.value += 1;

                            System.out.println("Importing module " + current.value + " of " + numIncludedModules + ": '" + module.getModuleName() + "'");

                            File moduleFile = new File(inputPath + File.separator + module.getModuleName() + ".json");
                            if (moduleFile.exists()) {
                                ModelImporter importer = new ModelImporter(new MPSModuleAsNode(module));
                                PlatformSpecificKt.importFile(importer, moduleFile);
                            }
                        }
                    }
                });
            }
        });
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
            public void run() {

                repoAsNode.getRepository().getModelAccess().runWriteAction(new Runnable() {
                    public void run() {
                        repoAsNode.getRepository().saveAll();
                    }
                });

            }
        });
    }

    private static Set<String> parseRawPropertySet(String rawProperty) {
        return (rawProperty.isEmpty() ? SetSequence.fromSet(new HashSet<String>()) : SetSequence.fromSetAndArray(new HashSet<String>(), rawProperty.split(",")));
    }

    private static MPSRepositoryAsNode getRepositoryAsNode() {
        String repoPath = System.getProperty("modelix.mps.model.sync.bulk.repo.path");
        Project project;
        try {
            project = ProjectManager.getInstance().loadAndOpenProject(repoPath);
        } catch (Exception e) {
            throw new RuntimeException("project not found");
        }
        SRepository repo = ProjectHelper.getProjectRepository(project);
        return new MPSRepositoryAsNode(repo);
    }
}
