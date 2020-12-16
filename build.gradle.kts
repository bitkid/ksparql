import com.jfrog.bintray.gradle.BintrayExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinVersion = "1.4.21"
val ktorVersion = "1.4.3"
val rdf4jVersion = "3.5.0"

group = "com.bitkid"
version = "0.0.2"

plugins {
    kotlin("jvm") version "1.4.21"
    `maven-publish`
    id("com.jfrog.bintray") version "1.8.5"
    id("com.github.ben-manes.versions") version "0.36.0"
}

repositories {
    mavenCentral()
    jcenter()
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "14"
    kotlinOptions.freeCompilerArgs += listOf("-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi")
}

val sourcesJar by tasks.creating(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.getByName("main").allSource)
}

dependencies {
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-auth-jvm:$ktorVersion")
    implementation("com.fasterxml:aalto-xml:1.2.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.3.3")
    implementation("org.eclipse.rdf4j:rdf4j-repository-sparql:$rdf4jVersion") {
        exclude("org.eclipse.rdf4j", "rdf4j-http-client")
    }

    testImplementation("org.eclipse.rdf4j:rdf4j-http-client:$rdf4jVersion")
    testImplementation("org.eclipse.rdf4j:rdf4j-queryresultio-text:$rdf4jVersion")
    testImplementation("ch.qos.logback:logback-classic:1.2.3")
    testImplementation("io.projectreactor.tools:blockhound:1.0.4.RELEASE")
    testImplementation("io.strikt:strikt-core:+")
    testImplementation("io.ktor:ktor-server-netty:$ktorVersion")

    testImplementation("org.junit.jupiter:junit-jupiter-api:+")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:+")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:+") {
        exclude(group = "junit")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(sourcesJar)
            groupId = project.group as String
            artifactId = project.name
            version = project.version as String
        }
    }
}

bintray {
    user = "bitkid"
    key = System.getenv("BINTRAY_API_KEY")
    publish = true
    setPublications("mavenJava")
    pkg(
        delegateClosureOf<BintrayExtension.PackageConfig> {
            repo = "maven"
            name = "ksparql"
            setLicenses("MIT")
            version(
                delegateClosureOf<BintrayExtension.VersionConfig> {
                    name = project.version as String
                    description = "ksparql is a non-blocking sparql xml http client"
                    githubRepo = "bitkid/ksparql"
                }
            )
        }
    )
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

