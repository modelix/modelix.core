import com.github.gradle.node.npm.task.NpmTask

plugins {
  base
  alias(libs.plugins.node)
  id("org.jlleitschuh.gradle.ktlint") apply false
}

tasks.named("npm_run_build") {
  inputs.dir("src")
  inputs.file("package.json")
  inputs.file("package-lock.json")

  outputs.dir("dist")
}

val patchKotlinExternals =
  tasks.create("patchKotlinExternals") {
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

tasks.named("assemble") {
  dependsOn("npm_run_build")
  dependsOn("npm_run_generateKotlin")
  dependsOn(patchKotlinExternals)
}

tasks.named("npm_run_generateKotlin") {
  finalizedBy(patchKotlinExternals)
}

val updateVersion =
  tasks.register<NpmTask>("updateVersion") {
    args.set(listOf("version", "$version"))
  }

tasks.named("npm_publish") {
  dependsOn(updateVersion)
}

tasks.named("publish") {
  dependsOn("npm_publish")
}
