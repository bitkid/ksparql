# ksparql

ksparql is a non-blocking sparql xml http client

## motivation

i have been writing code which uses rdf4j for talking to a stardog database. while rdf4j is a great library with tons of
features it does not integrate well with the ktor/coroutines world. this library aims at bridging that gap by using the
aalto-xml async xml parser fed by a ktor ByteReadChannel for processing the sparql result xml.

## limitations

this library has only been tested with stardog (7.4.4) but in theory it should handle all databases with query endpoints
that return sparql xml (https://www.w3.org/TR/rdf-sparql-XMLres/). be aware, that not the full XML tag set is supported
yet. update queries are totally untested, so for now, you could call it a read-only client.

## usage

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

assuming you have following triples in your database

```
<http://bob> <http://likes> <http://alice>
<http://alice> <http://likes> <http://trudy>
<http://trudy> <http://likes> <http://bob>
```

this is how the client can be used to execute queries

```kotlin
runBlocking {
    val client = KSparqlClient(
        "http://localhost:5280/test/query",
        "admin",
        "admin"
    )

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

## contribute

i totally accept PRs if i like them. run the tests with

```shell
./gradlew test
```

the stardog tests are set to @Disabled, if you want to run them in your IDE install docker and run

```shell
./start_stardog.sh
```

## license

[MIT](https://choosealicense.com/licenses/mit/)