import com.github.gradle.node.npm.task.NpmTask
import com.github.gradle.node.task.NodeTask

plugins {
  base
  id("com.github.node-gradle.node") version "3.4.0"
}

node {
    version.set("18.3.0")
    npmVersion.set("8.11.0")
    download.set(true)
}

tasks.named("npm_run_build") {
  inputs.dir("src")
  inputs.file("package.json")
  inputs.file("package-lock.json")

  outputs.dir("dist")
}

val updateVersion = tasks.register<NpmTask>("updateVersion") {
  args.set(listOf("version", "$version"))
}

tasks.named("npm_run_publish") {
  dependsOn(updateVersion)
}

tasks.named("publish") {
  dependsOn("npm_run_publish")
}
