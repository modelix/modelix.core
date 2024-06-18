import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    alias(libs.plugins.node)
}

/**
 * This task is needed because the listed packages have been rebuilt (locally or CI).
 * As a result, the verification hashes in package-lock.json are out of date.
 * Reinstalling them will update their hashes.
 * This issue only arises because we are using the packaged libraries.
 *
 * If you want to install a local package, it makes a difference whether you
 * (a) link the folder with the package.json and the built files,
 * (b) or pack it into a `tgz' using `npm pack'.
 *
 * We use packaged libraries (b) for the following reason:
 *
 * Packaged local packages (b) behave like packages pulled from NPM.
 * In case (a), Node.js doesn't behave like packages pulled by NPM.
 * Such packages will be loaded more than once if they are imported from different locations.
 * This is relevant when working with global declarations.
 *
 * This includes obvious things like global variables but also class declarations.
 * For example, loading the same class twice breaks `instanceof` checks
 * when objects are pass across JS module boundaries.
 * Another problem arises when the far-reaching `LanguageRegistry.INSTANCE` singleton is duplicated.
 */
val updateDependencies = tasks.register<NpmTask>("updateDependencies") {
    dependsOn(":typescript-generation:packJsPackage")
    args.set(
        listOf(
            "install",
            "../../model-client/build/npmDevPackage/model-client.tgz",
            "../../vue-model-api/build/npmDevPackage/vue-model-api.tgz",
            "../typescript-generation/build/typescript-generation-0.0.0.tgz",
        ),
    )
}

tasks.npmInstall {
    dependsOn(updateDependencies)
}

tasks.check {
    dependsOn("npm_run_test")
}
