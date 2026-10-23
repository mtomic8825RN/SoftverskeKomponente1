package resolver

import org.example.model.ComponentId
import java.nio.file.Files
import java.nio.file.Path

/** Čita fajl sa jednom komponentom po liniji (`org:project:version`). Prazne linije i linije sa `#` se ignorišu. */
object DependencyListReader {
    fun read(file: Path): List<ComponentId> =
        Files.readAllLines(file)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { ComponentIdParser.parse(it) }
}
