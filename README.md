# ksparql

ksparql is a non-blocking sparql xml http client

## usage

Assuming you have following triples in your database

```
<http://bob> <http://likes> <http://alice>
<http://alice> <http://likes> <http://trudy>
<http://trudy> <http://likes> <http://bob>
```

this is how the client can be used execute queries

```kotlin
runBlocking {
    val client = KSparqlClient(
        "http://localhost:5280/test/query",
        "admin",
        "admin"
    )

    client.query("select ?a ?b ?c") { valueFactory ->
        addBinding("a", valueFactory.createIRI("http://bob"))
    }.collect { rdfResult ->
        // do something with the rdfResult
    }

    // returns true
    client.ask("ASK {?a ?b ?c}") { valueFactory ->
        addBinding("a", valueFactory.createIRI("http://bob"))
        addBinding("b", valueFactory.createIRI("http://likes"))
        addBinding("c", valueFactory.createIRI("http://alice"))
    }
}
```
