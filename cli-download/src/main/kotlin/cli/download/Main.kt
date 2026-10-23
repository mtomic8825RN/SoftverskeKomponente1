package cli.download

import org.example.api.ComponentRepository
import org.example.api.ComponentRepositoryProvider
import org.example.model.ComponentId
import org.example.model.RepositoryConnectionConfig
import org.example.model.RepositoryType
import resolver.ComponentDownloader
import resolver.ComponentIdParser
import resolver.ConflictResolutionStrategy
import resolver.DependencyListReader
import resolver.DependencyResolver
import java.nio.file.Paths
import java.util.ServiceLoader
import kotlin.system.exitProcess

private const val USAGE = """
Usage: repo-download <command> [options]

Connection options (all commands):
  --type <LOCAL|NETWORK|GOOGLE_DRIVE>   repository type (default LOCAL)
  --root <path>                         shortcut for --prop rootDirectory=<path>
  --prop <key=value>                    driver property, can be repeated

Commands:
  download   Resolve dependencies and download all components
               --list <file>                    text file, one org:project:version per line
               --component org:project:version  (alternative to --list, can be repeated)
               --target <dir>                   target directory (default: downloads)
               --strategy <nearest|newest>      conflict resolution (default: nearest)
  resolve    Same options as download, but only prints what would be downloaded
  projects   List projects of an organization:   --org <organization>
  versions   List versions of a project:         --org <organization> --project <project>
  show       Show component and its dependencies: --component org:project:version
"""

fun main(args: Array<String>) {
    if (args.isEmpty() || args[0] in setOf("help", "--help", "-h")) {
        println(USAGE.trim())
        return
    }
    try {
        execute(args[0], Options(args.drop(1)))
    } catch (e: IllegalArgumentException) {
        System.err.println("Error: ${e.message}")
        exitProcess(1)
    } catch (e: Exception) {
        System.err.println("Error: ${e.message ?: e.javaClass.simpleName}")
        exitProcess(2)
    }
}

private fun execute(command: String, options: Options) {
    val repository = loadRepository(options)

    when (command) {
        "download" -> {
            val roots = readRoots(options)
            val strategy = ConflictResolutionStrategy.of(options.get("strategy") ?: "nearest")
            val target = Paths.get(options.get("target") ?: "downloads")
            val files = ComponentDownloader(repository, strategy).download(roots, target)
            println("Downloaded ${files.size} component(s) to ${target.toAbsolutePath()}:")
            files.forEach { println("  $it") }
        }
        "resolve" -> {
            val roots = readRoots(options)
            val strategy = ConflictResolutionStrategy.of(options.get("strategy") ?: "nearest")
            val resolved = DependencyResolver(repository, strategy).resolve(roots)
            println("Resolved ${resolved.size} component(s):")
            for (r in resolved) {
                val id = r.component.id
                println("  [depth ${r.depth}] ${id.organization}:${id.project}:${id.version}")
            }
        }
        "projects" -> {
            val projects = repository.findProjects(options.need("org"))
            if (projects.isEmpty()) println("No projects found.") else projects.forEach { println(it) }
        }
        "versions" -> {
            val versions = repository.findVersions(options.need("org"), options.need("project"))
            if (versions.isEmpty()) println("No versions found.") else versions.forEach { println(it) }
        }
        "show" -> show(repository, ComponentIdParser.parse(options.need("component")))
        else -> throw IllegalArgumentException("Unknown command '$command'. Run with --help.")
    }
}

/** Bira drajver preko ServiceLoader-a, bez ikakve veze sa konkretnom implementacijom. */
private fun loadRepository(options: Options): ComponentRepository {
    val typeName = options.get("type")?.uppercase() ?: "LOCAL"
    val type = RepositoryType.entries.firstOrNull { it.name == typeName }
        ?: throw IllegalArgumentException("Unknown repository type '$typeName'")

    val properties = mutableMapOf<String, String>()
    options.get("root")?.let { properties["rootDirectory"] = it }
    for (p in options.all("prop")) {
        val parts = p.split("=", limit = 2)
        if (parts.size != 2) throw IllegalArgumentException("Invalid --prop '$p', expected key=value")
        properties[parts[0]] = parts[1]
    }

    val provider = ServiceLoader.load(ComponentRepositoryProvider::class.java)
        .firstOrNull { it.type == type }
        ?: throw IllegalStateException("No driver found for repository type $type")

    return provider.create(RepositoryConnectionConfig(type, properties))
}

/** Koreni stabla zavisnosti: iz fajla (--list) i/ili direktno (--component). */
private fun readRoots(options: Options): List<ComponentId> {
    val fromFile = options.get("list")?.let { DependencyListReader.read(Paths.get(it)) } ?: emptyList()
    val fromArgs = options.all("component").map { ComponentIdParser.parse(it) }
    val roots = fromFile + fromArgs
    if (roots.isEmpty()) throw IllegalArgumentException("Specify --list <file> or --component org:project:version")
    return roots
}

private fun show(repository: ComponentRepository, id: ComponentId) {
    val component = repository.findComponent(id)
        ?: throw IllegalArgumentException("Component not found: ${id.organization}:${id.project}:${id.version}")
    println("${id.organization}:${id.project}:${id.version}")
    if (component.dependencies.isEmpty()) {
        println("  (no dependencies)")
    }
    for (d in component.dependencies) {
        val c = d.componentId
        val flags = buildString {
            append("scope=${d.dependencyScope.name}")
            if (!d.dependencyScope.transitive) append(", not transitive")
            if (d.optional) append(", optional")
        }
        println("  -> ${c.organization}:${c.project}:${c.version} ($flags)")
    }
}
