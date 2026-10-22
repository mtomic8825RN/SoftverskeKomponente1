plugins {
    kotlin("jvm")
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":repository-api"))
    implementation(project(":dependency-resolver"))
    runtimeOnly(project(":repository-local"))
    // runtimeOnly(project(":repository-google-drive"))  // kad Drive proradi

    testImplementation(kotlin("test"))
}

application {
    applicationName = "repo-download"
    mainClass.set("cli.download.MainKt")
}

kotlin {
    jvmToolchain(21)
}