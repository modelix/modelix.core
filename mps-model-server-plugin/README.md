To test changes during development
add the property `mps.plugins.dir` to `~/.gradle.gradle.properties`, for example
when using the JetBrains toolbox on Mac this would be similar to this:
```
mps.plugins.dir=/Users/yourUserName/Library/Application Support/JetBrains/Toolbox/apps/MPS/ch-2/211.7628.1509/MPS 2021.1.app.plugins/
```

Then run the task `installMpsPlugin` and restart MPS.
Automatically reloading the plugin is not supported yet, 
because failing to unloading the classes of the ktor server prevents that.

Alternatively you can install the plugin manually by first running the task `buildPlugin`
and then choosing the folder `mps-model-server/build/distributions/` in MPS.

To execute a query you can create a Kotlin scratch file with the classpath
of the module `modelql-client.jvmMain` and some code like this:

```kotlin
import kotlinx.coroutines.runBlocking
import org.modelix.modelql.client.ModelQLClient
import org.modelix.modelql.core.count
import org.modelix.modelql.untyped.children

val client = ModelQLClient.builder().url("http://localhost:48305/query").build()
val result = runBlocking {
    client.query {
        it.children("modules").count()
    }
}
println(result)
```