package drive

import org.example.model.DependencyScope
import org.example.model.RepositoryConfig
import java.nio.file.Paths

fun main() {
    // putanje do fajlova u korenu projekta
    val credentialsPath = Paths.get("credentials.json")
    val tokensPath = Paths.get("tokens")

    val rootFolderId = "1GYjy14EwSWe64211MntKzZzRfIBZgDWe"

    println("Povezivanje sa Google Drive-om...")
    val storage = DriveStorage.connect(credentialsPath, tokensPath)
    val repo = GoogleDriveComponentRepository(storage, rootFolderId)

    // inicijalna konfiguracija repozitorijuma
    val config = RepositoryConfig(
        allowedExtensions = setOf("jar", "zip"),
        dependencyScopes = mapOf(
            "compile" to DependencyScope("compile", transitive = true),
            "test" to DependencyScope("test", transitive = false)
        )
    )

    // inicijalizacija na Drive-u (kreira config.properties unutar foldera)
    try {
        repo.initialize(config)
        println("Uspesno inicijalizovan repozitorijum na Google Drive-u!")
    } catch (e: Exception) {
        println("Napomena: ${e.message}")
    }

    // test citanja konfiguracije sa Drive-a
    try {
        val currentConfig = repo.getConfig()
        println("Povezivanje uspesno! Dozvoljene ekstenzije na Drive-u: ${currentConfig.allowedExtensions}")
    } catch (e: Exception) {
        println("Greska pri citanju konfiguracije: ${e.message}")
    }
}