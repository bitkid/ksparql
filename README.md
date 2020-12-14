# ksparql

ksparql is a non-blocking sparql xml http client

## motivation

i have been writing code which uses rdf4j for talking to a stardog database. while rdf4j is a great library with tons of
features it does not integrate well with the ktor/coroutines world. this library aims at bridging that gap by using the
aalto-xml async xml parser fed by a ktor ByteReadChannel for processing the sparql result xml.

## limitations

this library has only been tested with stardog (7.4.4) and no other triple stores but in theory it should handle all
databases with query endpoints that return sparql xml (https://www.w3.org/TR/rdf-sparql-XMLres/). be aware, that not the
full XML tag set is supported yet. transactions will probably only work with stardog.

## usage

### setup

get the package with gradle / maven

```kotlin
implementation("com.bitkid:ksparql:0.0.1")
```

```xml
<dependency>
    <groupId>com.bitkid</groupId>
    <artifactId>ksparql</artifactId>
    <version>0.0.1</version>
    <type>pom</type>
</dependency>
```

and create the client

```kotlin
val client = KSparqlClient(ClientConfig(
    databaseHost = "http://localhost",
    databasePort = 5820,
    databaseName = "test",
    user = "admin",
    password = "admin"
))
```

### add data

```kotlin
val model = ModelBuilder().subject("http://someEntity")
    .add(iri("http://prop1"), "bla")
    .add(iri("http://prop2"), 5)
    .build()

val anotherModel = ModelBuilder().subject("http://otherEntity")
    .add(iri("http://prop3"), "bla")
    .add(iri("http://prop4"), 5)
    .build()

runBlocking {
    // atomic add of all statements in the model
    client.add(model)
    
    // using a transaction explicitly
    val transaction = client.begin()
    transaction.add(model)
    transaction.add(anotherModel)
    client.commit(transaction)
    
    // or the closure
    client.transaction {
        add(model)
        add(anotherModel)
    }
}
```

### query data

assuming you have following triples in your database

```
<http://bob> <http://likes> <http://alice>
<http://alice> <http://likes> <http://trudy>
<http://trudy> <http://likes> <http://bob>
```

this is how the client can be used to execute queries

```kotlin
runBlocking {
    client.query("SELECT ?a ?b ?c WHERE { ?a ?b ?c }") { valueFactory ->
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

### clear data

```kotlin
runBlocking {
    // delete everything
    client.clear()
    
    // delete named graph
    client.clear(iri("http://my-named-graph"))
}
```

## contribute

i totally accept PRs if i like them. run the tests with

```shell
./gradlew test
```

the stardog tests are set to @Disabled, if you want to run them in your IDE install docker, run

```shell
./start_stardog.sh
```

and create a database called test (i use stardog studio for that)

## license

[MIT](https://choosealicense.com/licenses/mit/)