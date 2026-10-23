plugins {
    kotlin("jvm") version "2.1.10"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

tasks.register<Copy>("packageAll") {
    group = "distribution"
    description = "Builds both command line programs and API docs into build/dist"

    dependsOn(
        ":cli-upload:installDist",
        ":cli-download:installDist",
        ":repository-api:dokkaHtml"
    )
    from(project(":cli-upload").layout.buildDirectory.dir("install"))
    from(project(":cli-download").layout.buildDirectory.dir("install"))
    from(project(":repository-api").layout.buildDirectory.dir("dokka/html")) {
        into("docs")
    }
    into(layout.buildDirectory.dir("dist"))
}