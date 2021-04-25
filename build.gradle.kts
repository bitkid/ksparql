import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinVersion = "1.4.32"
val ktorVersion = "1.5.3"
val rdf4jVersion = "3.6.3"

group = "com.bitkid"
version = "0.0.2"

plugins {
    kotlin("jvm") version "1.4.32"
    `maven-publish`
    id("com.github.ben-manes.versions") version "0.38.0"
}

repositories {
    mavenCentral()
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
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.3.4")
    implementation("org.eclipse.rdf4j:rdf4j-repository-sparql:$rdf4jVersion") {
        exclude("org.eclipse.rdf4j", "rdf4j-http-client")
    }

    testImplementation("org.eclipse.rdf4j:rdf4j-http-client:$rdf4jVersion")
    testImplementation("org.eclipse.rdf4j:rdf4j-queryresultio-text:$rdf4jVersion")
    testImplementation("ch.qos.logback:logback-classic:1.2.3")
    testImplementation("io.projectreactor.tools:blockhound:1.0.6.RELEASE")
    testImplementation("io.strikt:strikt-core:+")
    testImplementation("io.ktor:ktor-server-netty:$ktorVersion")

    testImplementation("org.apache.jena:jena-fuseki-embedded:3.8.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:+")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:+")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:+") {
        exclude(group = "junit")
    }
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
    val filtered =
        listOf("alpha", "beta", "rc", "cr", "m", "preview", "dev", "eap")
            .map { qualifier -> Regex("(?i).*[.-]$qualifier[.\\d-]*.*") }
    resolutionStrategy {
        componentSelection {
            all {
                if (filtered.any { it.matches(candidate.version) }) {
                    reject("Release candidate")
                }
            }
        }
        checkForGradleUpdate = true
        outputFormatter = "json"
        outputDir = "build/dependencyUpdates"
        reportfileName = "report"
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

