package drive

import org.example.api.ComponentRepository
import org.example.api.RepositoryManager
import org.example.model.*
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

class GoogleDriveComponentRepository(
    private val storage: DriveStorage,
    private val rootFolderId: String
) : ComponentRepository, RepositoryManager {

    private val configName = "config.properties"
    private val infoName = "component.txt"

    private fun validatePart(value: String, what: String) {
        if (value.isBlank()) throw IllegalArgumentException("$what cannot be empty")
        if (value == "." || value == ".." || value.any { it == ',' || it == '/' || it == '\\' }) {
            throw IllegalArgumentException("$what contains illegal characters: '$value'")
        }
    }

    private fun validateId(id: ComponentId, what: String) {
        validatePart(id.organization, "$what organization")
        validatePart(id.project, "$what project")
        validatePart(id.version, "$what version")
    }

    private fun normalizeExtension(ext: String) = ext.trim().removePrefix(".").lowercase()

    private fun versionFolder(id: ComponentId): String? {
        validateId(id, "Component")
        val org = storage.findFolder(rootFolderId, id.organization) ?: return null
        val project = storage.findFolder(org, id.project) ?: return null
        return storage.findFolder(project, id.version)
    }

    override fun componentExists(id: ComponentId): Boolean {
        val folder = versionFolder(id) ?: return false
        return storage.findFile(folder, infoName) != null
    }

    override fun findComponent(id: ComponentId): Component? {
        val folder = versionFolder(id) ?: return null
        val infoId = storage.findFile(folder, infoName) ?: return null

        val lines = storage.readText(infoId).lines().filter { it.isNotBlank() }
        val componentId = ComponentId(lines[0], lines[1], lines[2])
        val scopes = if (lines.size > 3) getConfig().dependencyScopes else emptyMap()

        val dependencies = lines.drop(3).map { line ->
            val p = line.split(",")
            val scope = scopes[p[3]] ?: throw IllegalStateException("Unknown dependency scope: ${p[3]}")
            Dependency(ComponentId(p[0], p[1], p[2]), scope, p[4].toBoolean())
        }
        return Component(componentId, dependencies)
    }

    override fun findProjects(organisation: String): List<String> {
        validatePart(organisation, "Organization")
        val org = storage.findFolder(rootFolderId, organisation) ?: return emptyList()
        return storage.listFolderNames(org)
    }

    override fun findVersions(organisation: String, project: String): List<String> {
        validatePart(organisation, "Organization")
        validatePart(project, "Project")
        val org = storage.findFolder(rootFolderId, organisation) ?: return emptyList()
        val proj = storage.findFolder(org, project) ?: return emptyList()
        return storage.listFolderNames(proj)
    }

    override fun download(id: ComponentId, targetDirectory: Path): Path? {
        val folder = versionFolder(id) ?: return null
        if (storage.findFile(folder, infoName) == null) return null

        val file = storage.listFiles(folder).firstOrNull { it.name != infoName } ?: return null

        Files.createDirectories(targetDirectory)
        val destination = targetDirectory.resolve(file.name)
        if (!Files.exists(destination)) storage.download(file.id, destination)
        return destination
    }

    override fun initialize(config: RepositoryConfig) {
        if (storage.findFile(rootFolderId, configName) != null) {
            throw IllegalStateException("Repository is already initialized")
        }
        validateConfig(config)
        saveConfig(config)
    }

    override fun getConfig(): RepositoryConfig {
        val configId = storage.findFile(rootFolderId, configName)
            ?: throw IllegalStateException("Repository is not initialized")

        val properties = Properties().apply { load(StringReader(storage.readText(configId))) }

        val extensions = properties.getProperty("allowedExtensions", "")
            .split(",").map { normalizeExtension(it) }.filter { it.isNotEmpty() }.toSet()

        val scopeNames = properties.getProperty("dependencyScopes", "")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }

        val scopes = scopeNames.associateWith { name ->
            DependencyScope(name, properties.getProperty("scope.$name.transitive").toBoolean())
        }
        return RepositoryConfig(extensions, scopes)
    }

    override fun updateConfig(config: RepositoryConfig) {
        if (storage.findFile(rootFolderId, configName) == null) {
            throw IllegalStateException("Repository is not initialized")
        }
        validateConfig(config)
        saveConfig(config)
    }

    override fun upload(component: Component, file: Path) {
        validateId(component.id, "Component")

        if (!Files.isRegularFile(file)) throw IllegalArgumentException("Component file does not exist")

        val config = getConfig()
        val extension = normalizeExtension(file.fileName.toString().substringAfterLast(".", ""))
        if (extension !in config.allowedExtensions) {
            throw IllegalArgumentException("File extension is not allowed: '$extension'")
        }
        if (componentExists(component.id)) throw IllegalArgumentException("Component already exists")

        val checked = mutableSetOf<ComponentId>()
        for (dependency in component.dependencies) {
            validateId(dependency.componentId, "Dependency")

            if (dependency.componentId == component.id) {
                throw IllegalArgumentException("Component cannot depend on itself")
            }
            if (!checked.add(dependency.componentId)) throw IllegalArgumentException("Duplicate dependency")

            val configured = config.dependencyScopes[dependency.dependencyScope.name]
                ?: throw IllegalArgumentException("Unknown dependency scope")
            if (configured != dependency.dependencyScope) {
                throw IllegalArgumentException("Dependency scope does not match repository configuration")
            }
            if (!componentExists(dependency.componentId)) {
                throw IllegalArgumentException("Dependency does not exist")
            }
        }

        val orgFolder = storage.ensureFolder(rootFolderId, component.id.organization)
        val projectFolder = storage.ensureFolder(orgFolder, component.id.project)
        val versionFolder = storage.createFolder(projectFolder, component.id.version)

        try {
            storage.uploadFile(versionFolder, file)
            storage.writeText(versionFolder, infoName, componentInfo(component))
        } catch (e: Exception) {
            storage.delete(versionFolder)
            throw e
        }
    }

    private fun componentInfo(component: Component): String = buildString {
        appendLine(component.id.organization)
        appendLine(component.id.project)
        appendLine(component.id.version)
        for (d in component.dependencies) {
            appendLine(
                "${d.componentId.organization},${d.componentId.project},${d.componentId.version}," +
                        "${d.dependencyScope.name},${d.optional}"
            )
        }
    }

    private fun validateConfig(config: RepositoryConfig) {
        if (config.allowedExtensions.any { normalizeExtension(it).let { e -> e.isEmpty() || e.contains(',') } }) {
            throw IllegalArgumentException("Invalid allowed extension")
        }
        for ((key, scope) in config.dependencyScopes) {
            if (key != scope.name) throw IllegalArgumentException("Scope key '$key' does not match scope name '${scope.name}'")
            if (scope.name.isBlank() || scope.name.contains(',')) throw IllegalArgumentException("Invalid scope name: '${scope.name}'")
        }
    }

    private fun saveConfig(config: RepositoryConfig) {
        val p = Properties()
        p.setProperty("allowedExtensions", config.allowedExtensions.map { normalizeExtension(it) }.joinToString(","))
        p.setProperty("dependencyScopes", config.dependencyScopes.keys.joinToString(","))
        for ((name, scope) in config.dependencyScopes) {
            p.setProperty("scope.$name.transitive", scope.transitive.toString())
        }
        val out = StringWriter()
        p.store(out, "Repository configuration")
        storage.writeText(rootFolderId, configName, out.toString())
    }
}