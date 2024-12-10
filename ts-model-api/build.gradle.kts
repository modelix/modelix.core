import dev.petuska.npm.publish.task.NodeExecTask
import org.gradle.kotlin.dsl.withType

plugins {
  base
  alias(libs.plugins.node)
  alias(libs.plugins.npm.publish)
  `modelix-project-repositories`
}

val npmRunBuild = tasks.named("npm_run_build") {
  inputs.dir("src")
  inputs.file("package.json")
  inputs.file("package-lock.json")

  outputs.dir("dist")
}

val patchKotlinExternals = tasks.register("patchKotlinExternals") {
  dependsOn("npm_run_generateKotlin")
  doLast {
    val annotationLine = """@file:JsModule("@modelix/ts-model-api") @file:JsNonModule"""
    val dukatDir = project.layout.buildDirectory.dir("dukat").get().asFile
    val files = dukatDir.listFiles()?.toList() ?: emptyList()
    val matchingFiles = files.filter { it.name.contains("@modelix_ts-model-api") }
    if (matchingFiles.isEmpty()) throw RuntimeException("No files found for patching in $dukatDir")
    val typealiases = HashSet<String>()
    val allImports = HashSet<String>()
    for (file in matchingFiles) {
      var lines = file.readLines()
      if (lines.isEmpty()) continue
      if (lines.first() == annotationLine) continue
      lines = listOf(annotationLine) + lines
      typealiases += lines.filter { it.startsWith("typealias ") }
      allImports += lines.filter { it.startsWith("import ") }
      lines = lines.filterNot { it.startsWith("typealias ") }
      file.writeText(lines.joinToString("\n"))
    }
    dukatDir.resolve("typealiases.kt").writeText(allImports.joinToString("\n") + "\n\n" + typealiases.joinToString("\n"))
  }
}

tasks.assemble {
  dependsOn("npm_run_build")
  dependsOn("npm_run_generateKotlin")
  dependsOn(patchKotlinExternals)
}

tasks.named("npm_run_generateKotlin") {
  finalizedBy(patchKotlinExternals)
}

// To copy the files is a workaround for https://github.com/mpetuska/npm-publish/issues/87
// With `NpmPackage.files` we cannot copy the "dist" directory into `destinationDir`.
// We can only either copy the files from "dist" directly into `destinationDir`
// or copy all files from `projectDir` into `destinationDir`.
val copyBuildTypeScriptForPackaging = tasks.register<Copy>("copyBuildTypeScriptForPackaging") {
  dependsOn(npmRunBuild)
  from(projectDir)
  include("dist/**")
  into(layout.buildDirectory.dir("typeScriptForPackaging"))
}

npmPublish {
  registries {
    register("itemis-npm-open") {
      uri.set("https://artifacts.itemis.cloud/repository/npm-open")
      System.getenv("NODE_AUTH_TOKEN").takeIf { !it.isNullOrBlank() }?.let {
        authToken.set(it)
      }
    }
  }
  packages {
    create("js") {
      packageJsonTemplateFile.set(projectDir.resolve("package.json"))
      packageJson {
        version.set("${project.version}")
      }
      files {
        setFrom(copyBuildTypeScriptForPackaging.map { it.outputs })
      }
    }
  }
}

tasks.withType(NodeExecTask::class) {
  dependsOn(":setupNodeEverywhere")
}
