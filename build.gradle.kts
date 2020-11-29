import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinVersion = "1.4.10"
val ktorVersion = "1.4.1"

plugins {
    kotlin("jvm") version "1.4.10"
    id("com.github.johnrengelman.shadow") version "6.1.0"
    id("com.github.ben-manes.versions") version "0.36.0"
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.freeCompilerArgs += listOf("-Xuse-experimental=io.ktor.locations.KtorExperimentalLocationsAPI")
}

dependencies {
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("com.fasterxml:aalto-xml:1.2.2")

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

