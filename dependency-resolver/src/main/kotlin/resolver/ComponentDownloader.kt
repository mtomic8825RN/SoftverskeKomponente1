package resolver

import org.example.api.ComponentRepository
import org.example.model.ComponentId
import java.nio.file.Path

/**
 * Razrešava zavisnosti i preuzima sve izabrane komponente u zadati direktorijum.
 * Svaka komponenta ide u podfolder `<organizacija>/<projekat>-<verzija>`.
 */
class ComponentDownloader(
    private val repository: ComponentRepository,
    private val strategy: ConflictResolutionStrategy = NearestToRootStrategy()
) {
    fun download(roots: List<ComponentId>, targetDirectory: Path): List<Path> {
        val resolved = DependencyResolver(repository, strategy).resolve(roots)
        return resolved.map { r ->
            val id = r.component.id
            val folder = targetDirectory.resolve(id.organization).resolve("${id.project}-${id.version}")
            repository.download(id, folder)
                ?: throw IllegalStateException("Download failed for $id")
        }
    }

    fun downloadFromList(listFile: Path, targetDirectory: Path): List<Path> =
        download(DependencyListReader.read(listFile), targetDirectory)
}
