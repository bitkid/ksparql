import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinVersion = "1.4.20"
val ktorVersion = "1.4.3"
val rdf4jVersion = "3.4.4"

plugins {
    kotlin("jvm") version "1.4.20"
    id("com.github.johnrengelman.shadow") version "6.1.0"
    id("com.github.ben-manes.versions") version "0.36.0"
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
}

dependencies {
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-auth-jvm:$ktorVersion")

    implementation("com.fasterxml:aalto-xml:1.2.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.12.0")

    implementation("org.eclipse.rdf4j:rdf4j-repository-api:$rdf4jVersion")
    implementation("org.eclipse.rdf4j:rdf4j-query:$rdf4jVersion")
    implementation("org.eclipse.rdf4j:rdf4j-repository-sparql:$rdf4jVersion")
    implementation("org.eclipse.rdf4j:rdf4j-queryresultio-sparqljson:$rdf4jVersion")
    implementation("org.eclipse.rdf4j:rdf4j-queryresultio-text:$rdf4jVersion")

    testImplementation("io.projectreactor.tools:blockhound:1.0.4.RELEASE")
    testImplementation("io.ktor:ktor-server-netty:$ktorVersion")
    testImplementation("io.strikt:strikt-core:+")
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

