import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinVersion = "1.4.21"
val ktorVersion = "1.4.3"
val rdf4jVersion = "3.4.4"

plugins {
    kotlin("jvm") version "1.4.21"
    id("com.github.johnrengelman.shadow") version "6.1.0"
    id("com.github.ben-manes.versions") version "0.36.0"
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "14"
    kotlinOptions.freeCompilerArgs += listOf("-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi")
}

dependencies {
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-auth-jvm:$ktorVersion")
    implementation("com.fasterxml:aalto-xml:1.2.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.3.3")
    implementation("org.eclipse.rdf4j:rdf4j-repository-sparql:$rdf4jVersion") {
        exclude("org.eclipse.rdf4j", "rdf4j-http-client")
    }

    testImplementation("org.eclipse.rdf4j:rdf4j-http-client:$rdf4jVersion")
    testImplementation("org.eclipse.rdf4j:rdf4j-queryresultio-text:$rdf4jVersion")
    testImplementation("io.ktor:ktor-client-apache:$ktorVersion")
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

repositories {
    mavenCentral()
    jcenter()
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

