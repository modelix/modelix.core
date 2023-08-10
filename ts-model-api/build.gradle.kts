import com.github.gradle.node.npm.task.NpmTask

plugins {
  base
  id("com.github.node-gradle.node") version "5.0.0"
  id("org.jlleitschuh.gradle.ktlint") apply false
}

node {
  version.set("18.12.1")
  npmVersion.set("8.19.2")
  download.set(true)
}

tasks.named("npm_run_build") {
  inputs.dir("src")
  inputs.file("package.json")
  inputs.file("package-lock.json")

  outputs.dir("dist")
}

tasks.named("assemble") {
  dependsOn("npm_run_build")
  dependsOn("npm_run_generateKotlin")
}


val updateVersion = tasks.register<NpmTask>("updateVersion") {
  args.set(listOf("version", "$version"))
}

tasks.named("npm_publish") {
  dependsOn(updateVersion)
}

tasks.named("publish") {
  dependsOn("npm_publish")
}
