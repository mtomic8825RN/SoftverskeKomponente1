plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "SoftverskeKomponente1"

include("repository-api")
include("repository-local")
include("repository-google-drive")
include("dependency-resolver")
include("cli-upload")
include("cli-download")