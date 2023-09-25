plugins {
    base
    alias(libs.plugins.node)
}

tasks.named("npm_run_build") {
    dependsOn(":npmInstall")

    inputs.dir("src")
    inputs.file("package.json")
    inputs.file("tsconfig.json")

    outputs.dir("dist")
}

val patchKotlinExternals = tasks.create("patchKotlinExternals") {
    dependsOn("npm_run_generateKotlin")
    doLast {
        val annotationLine = """@file:JsModule("@modelix/ts-model-api") @file:JsNonModule"""
        val dukatDir = buildDir.resolve("dukat")
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
        dukatDir.resolve("typealiases.kt")
            .writeText(allImports.joinToString("\n") + "\n\n" + typealiases.joinToString("\n"))
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

tasks.named("clean") {
    dependsOn("npm_run_clean")
}

tasks.named("check") {
    dependsOn("npm_run_prettier")
    dependsOn("npm_run_lint")
}

tasks.named("npm_publish") {
    dependsOn(":updateVersionForNpm")
}

tasks.named("publish") {
    dependsOn("npm_publish")
}
