To test changes during development run the task `buildPlugin`
and then in MPS install a plugin from disk and choose the one in the
folder `mps-model-server/build/distributions/`.

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

# Plugin auto reload

- Add `-Didea.auto.reload.plugins=true` to your MPS vmoptions.
- Specify the MPS plugin directory in the `build.gradle.kts` file
- Run the `installMpsPlugin` task

