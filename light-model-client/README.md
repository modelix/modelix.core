# Light Model Client

This client is designed to connect to MPS or the Modelix model server.
It is implemented in Kotlin multi-platform so that it can run in the browser.

While the "advanced model client" provides more features and should be used for long-running processes,
the "light model client" is optimized for a lower resource consumption and short living processes like in a browser tab.
The server is responsible for resolving conflicts and to keep the client side model in a valid state.

Creating an instance that loads the entire model from the server can be done like this:

```
val client = LightModelClientJVM.builder()
    .url("ws://localhost/json/v2/test-repo/ws") // optional, by default it connects to the MPS plugin
    .build()
client.changeQuery(buildModelQuery { 
    root { 
        descendants {  }
    }
})
val rootNode = client.waitForRootNode()!!
client.runRead { 
    val modules = rootNode.getChildren("modules")
    // ...
}
```

You have to set a *model query* using `changeQuery()` to tell the server in what data you are interested in.
Without a query the client will not receive any data.
If you try to access a node that is not included in you *model query* an exception is thrown.
You can use `INode.isLoaded()` to check is a node was loaded on the client to prevent this exception.
You can also filter a list of nodes like this: `node.getChildren("modules").filterLoaded()`,
to iterate only over the nodes that are included in your query.

To read/write any nodes you have to start a read/write transaction by using `runRead {}`/`runWrite {}`.
An exception is thrown when you try to access a node outside a transaction. 