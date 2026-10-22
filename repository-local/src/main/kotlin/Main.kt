
import local.LocalComponentRepository
import org.example.model.Component
import org.example.model.ComponentId
import org.example.model.Dependency
import org.example.model.DependencyScope
import java.nio.file.Files
import java.nio.file.Paths

fun main() {
    val repository = LocalComponentRepository(
        Paths.get("test-repository")
    )

    // Test postojece komponente
    val id = ComponentId(
        "org.example",
        "my-library",
        "1.0.0"
    )

    println("Component exists: ${repository.componentExists(id)}")

    val component = repository.findComponent(id)
    println("Found component: $component")

    println(repository.findProjects("org.example"))
    println(repository.findVersions("org.example", "my-library"))

    // Test komponente koja ne postoji
    val missingId = ComponentId(
        "org.example",
        "missing-library",
        "1.0.0"
    )

    val missingComponent = repository.findComponent(missingId)
    println("Missing component: $missingComponent")

    val missingFile = repository.download(
        missingId,
        Paths.get("download-test")
    )

    println("Missing component download: $missingFile")

    // Pravljenje fajla za pomocnu komponentu
    val helperFile = Paths.get("helper-library.jar")
    Files.writeString(helperFile, "Helper library")

    val helperId = ComponentId(
        "org.example",
        "helper-library",
        "1.0.0"
    )

    val helperComponent = Component(helperId)

    // Upload pomocne komponente samo ako ne postoji
    if (!repository.componentExists(helperId)) {
        repository.upload(helperComponent, helperFile)
        println("Helper component uploaded!")
    } else {
        println("Helper component already exists!")
    }

    // Definisanje zavisnosti
    val compileScope = DependencyScope(
        "compile",
        true
    )

    val mainComponent = Component(
        id = ComponentId(
            "org.example",
            "main-library",
            "1.0.0"
        ),
        dependencies = listOf(
            Dependency(
                helperId,
                compileScope,
                false
            )
        )
    )

    println("Main component before upload: $mainComponent")

    // Upload glavne komponente samo ako ne postoji
    if (!repository.componentExists(mainComponent.id)) {
        repository.upload(mainComponent, helperFile)
        println("Main component uploaded!")
    } else {
        println("Main component already exists!")
    }

    // Ucitavanje glavne komponente iz repozitorijuma
    val foundMainComponent = repository.findComponent(
        mainComponent.id
    )

    println("Found main component: $foundMainComponent")
    val invalidComponent = Component(
        id = ComponentId(
            "org.example",
            "invalid-library",
            "1.0.0"
        ),
        dependencies = listOf(
            Dependency(
                ComponentId(
                    "org.example",
                    "does-not-exist",
                    "1.0.0"
                ),
                compileScope,
                false
            )
        )
    )

    try {
        repository.upload(invalidComponent, helperFile)
        println("ERROR: Invalid component was uploaded!")
    } catch (e: IllegalArgumentException) {
        println("Upload rejected: ${e.message}")
    }

   /* val providers = java.util.ServiceLoader.load(org.example.api.RepositoryManagerProvider::class.java)
    for (p in providers) println("Found provider: ${p.type}")*/
}
