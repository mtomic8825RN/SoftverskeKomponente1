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
    runtimeOnly(project(":repository-local"))
    testImplementation(kotlin("test"))
    // runtimeOnly(project(":repository-google-drive"))  // otkomentarisati kad Drive proradi

}

application {
    applicationName = "repo-upload"
    mainClass.set("cli.upload.MainKt")
}

kotlin {
    jvmToolchain(21)
}