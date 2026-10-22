package cli.upload

import org.example.api.RepositoryManager
import org.example.api.RepositoryManagerProvider
import org.example.model.*
import java.nio.file.Paths
import java.util.ServiceLoader
import kotlin.system.exitProcess

private const val USAGE = """
Usage: repo-upload <command> [options]

Connection options (all commands):
  --type <LOCAL|NETWORK|GOOGLE_DRIVE>   repository type (default LOCAL)
  --root <path>                         shortcut for --prop rootDirectory=<path>
  --prop <key=value>                    driver property, can be repeated

Commands:
  init           Initialize repository
                   --ext jar,txt   --scope compile=true  --scope test=false
  config         Show repository configuration
  update-config  Replace configuration (same options as init)
  upload         Upload a component
                   --component org:project:version
                   --file <path>
                   --dep org:project:version:scope[:optional]   (can be repeated)
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
    val manager = loadManager(options)

    when (command) {
        "init" -> {
            manager.initialize(readConfig(options))
            println("Repository initialized.")
        }
        "config" -> printConfig(manager.getConfig())
        "update-config" -> {
            manager.updateConfig(readConfig(options))
            println("Configuration updated.")
        }
        "upload" -> upload(manager, options)
        else -> throw IllegalArgumentException("Unknown command '$command'. Run with --help.")
    }
}

// Bira drajver preko ServiceLoader-a, bez ikakve veze sa konkretnom implementacijom.
private fun loadManager(options: Options): RepositoryManager {
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

    val provider = ServiceLoader.load(RepositoryManagerProvider::class.java)
        .firstOrNull { it.type == type }
        ?: throw IllegalStateException("No driver found for repository type $type")

    return provider.create(RepositoryConnectionConfig(type, properties))
}

private fun readConfig(options: Options): RepositoryConfig {
    val extensions = options.need("ext").split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    val scopes = options.all("scope").associate { s ->
        val parts = s.split("=")
        if (parts.size != 2 || parts[0].isBlank()) {
            throw IllegalArgumentException("Invalid --scope '$s', expected name=true|false")
        }
        val transitive = parts[1].lowercase().toBooleanStrictOrNull()
            ?: throw IllegalArgumentException("Invalid --scope '$s', value must be true or false")
        parts[0] to DependencyScope(parts[0], transitive)
    }
    return RepositoryConfig(extensions, scopes)
}

private fun printConfig(config: RepositoryConfig) {
    println("Allowed extensions: ${config.allowedExtensions.sorted().joinToString(", ")}")
    println("Dependency scopes:")
    for (scope in config.dependencyScopes.values.sortedBy { it.name }) {
        println("  ${scope.name} (transitive: ${scope.transitive})")
    }
}

private fun parseId(text: String): ComponentId {
    val p = text.split(":").map { it.trim() }
    if (p.size != 3 || p.any { it.isEmpty() }) {
        throw IllegalArgumentException("Invalid component '$text', expected organization:project:version")
    }
    return ComponentId(p[0], p[1], p[2])
}

private fun upload(manager: RepositoryManager, options: Options) {
    val id = parseId(options.need("component"))
    val file = Paths.get(options.need("file"))
    val scopes = manager.getConfig().dependencyScopes   // opsezi se uzimaju iz konfiguracije repozitorijuma

    val dependencies = options.all("dep").map { d ->
        val p = d.split(":").map { it.trim() }
        if (p.size !in 4..5) {
            throw IllegalArgumentException("Invalid --dep '$d', expected org:project:version:scope[:optional]")
        }
        val scope = scopes[p[3]] ?: throw IllegalArgumentException("Unknown scope '${p[3]}' in --dep '$d'")
        val optional = p.getOrNull(4)?.lowercase()?.let {
            if (it != "optional") throw IllegalArgumentException("Invalid --dep '$d', last part must be 'optional'")
            true
        } ?: false
        Dependency(ComponentId(p[0], p[1], p[2]), scope, optional)
    }

    manager.upload(Component(id, dependencies), file)
    println("Uploaded ${id.organization}:${id.project}:${id.version}")
}