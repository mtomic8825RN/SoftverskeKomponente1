package local

import org.example.api.ComponentRepository
import org.example.api.RepositoryManager
import org.example.model.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

class LocalComponentRepository(
    rootDirectory: Path
) : ComponentRepository, RepositoryManager {

    private val root: Path = rootDirectory.toAbsolutePath().normalize()
    private val configFile: Path = root.resolve("config.properties")

    init {
        Files.createDirectories(root)
    }

    // validacija i putanje

    private fun validatePart(value: String, what: String) {
        if (value.isBlank()) {
            throw IllegalArgumentException("$what cannot be empty")
        }
        if (value == "." || value == ".." || value.any { it == ',' || it == '/' || it == '\\' }) {
            throw IllegalArgumentException("$what contains illegal characters: '$value'")
        }
    }

    private fun validateId(id: ComponentId, what: String) {
        validatePart(id.organization, "$what organization")
        validatePart(id.project, "$what project")
        validatePart(id.version, "$what version")
    }

    private fun getComponentDirectory(id: ComponentId): Path {
        validateId(id, "Component")
        val dir = root.resolve(id.organization).resolve(id.project).resolve(id.version).normalize()
        if (!dir.startsWith(root)) {
            throw IllegalArgumentException("Invalid component identification")
        }
        return dir
    }

    private fun normalizeExtension(ext: String) = ext.trim().removePrefix(".").lowercase()

    // cuvanje komponente

    private fun saveComponentInfo(component: Component) {
        val infoFile = getComponentDirectory(component.id).resolve("component.txt")

        // use{} zatvara fajl i kada dodje do greske
        Files.newBufferedWriter(infoFile).use { writer ->
            writer.write(component.id.organization)
            writer.newLine()
            writer.write(component.id.project)
            writer.newLine()
            writer.write(component.id.version)
            writer.newLine()

            for (dependency in component.dependencies) {
                writer.write(
                    "${dependency.componentId.organization}," +
                            "${dependency.componentId.project}," +
                            "${dependency.componentId.version}," +
                            "${dependency.dependencyScope.name}," +
                            "${dependency.optional}"
                )
                writer.newLine()
            }
        }
    }

    // ComponentRepository

    override fun findComponent(id: ComponentId): Component? {
        val file = getComponentDirectory(id).resolve("component.txt")
        if (!Files.exists(file)) return null

        val lines = Files.readAllLines(file)
        val componentId = ComponentId(lines[0], lines[1], lines[2])
        val dependencies = mutableListOf<Dependency>()

        // konfiguracija se cita jednom, a ne za svaku zavisnost
        val scopes = if (lines.size > 3) getConfig().dependencyScopes else emptyMap()

        for (i in 3 until lines.size) {
            if (lines[i].isBlank()) continue

            val parts = lines[i].split(",")
            val dependencyId = ComponentId(parts[0], parts[1], parts[2])
            val scope = scopes[parts[3]]
                ?: throw IllegalStateException("Unknown dependency scope: ${parts[3]}")

            dependencies.add(Dependency(dependencyId, scope, parts[4].toBoolean()))
        }
        return Component(componentId, dependencies)
    }

    override fun componentExists(id: ComponentId): Boolean {
        return Files.exists(getComponentDirectory(id).resolve("component.txt"))
    }

    override fun findProjects(organisation: String): List<String> {
        validatePart(organisation, "Organization")
        return listSubdirectories(root.resolve(organisation))
    }

    override fun findVersions(organisation: String, project: String): List<String> {
        validatePart(organisation, "Organization")
        validatePart(project, "Project")
        return listSubdirectories(root.resolve(organisation).resolve(project))
    }

    private fun listSubdirectories(directory: Path): List<String> {
        if (!Files.isDirectory(directory)) return emptyList()

        Files.list(directory).use { entries ->
            return entries
                .filter { Files.isDirectory(it) }
                .map { it.fileName.toString() }
                .sorted()
                .toList()
        }
    }

    override fun download(id: ComponentId, targetDirectory: Path): Path? {
        if (!componentExists(id)) return null

        val componentDirectory = getComponentDirectory(id)

        // fajl komponente je jedini fajl koji nije component.txt (sortirano zbog stabilnosti)
        val source = Files.list(componentDirectory).use { files ->
            files.filter { it.fileName.toString() != "component.txt" }
                .sorted()
                .findFirst()
                .orElse(null)
        } ?: return null

        Files.createDirectories(targetDirectory)
        val destination = targetDirectory.resolve(source.fileName)

        if (!Files.exists(destination)) {
            Files.copy(source, destination)
        }
        return destination
    }

    // RepositoryManager

    override fun initialize(config: RepositoryConfig) {
        if (Files.exists(configFile)) {
            throw IllegalStateException("Repository is already initialized")
        }
        validateConfig(config)
        saveConfig(config)
    }

    override fun getConfig(): RepositoryConfig {
        if (!Files.exists(configFile)) {
            throw IllegalStateException("Repository is not initialized")
        }
        val properties = Properties()
        Files.newInputStream(configFile).use { properties.load(it) }

        val extensions = properties.getProperty("allowedExtensions", "")
            .split(",")
            .map { normalizeExtension(it) }
            .filter { it.isNotEmpty() }
            .toSet()

        val scopes = mutableMapOf<String, DependencyScope>()
        val scopeNames = properties.getProperty("dependencyScopes", "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        for (name in scopeNames) {
            val transitive = properties.getProperty("scope.$name.transitive").toBoolean()
            scopes[name] = DependencyScope(name, transitive)
        }
        return RepositoryConfig(extensions, scopes)
    }

    override fun updateConfig(config: RepositoryConfig) {
        if (Files.notExists(configFile)) {
            throw IllegalStateException("Repository is not initialized")
        }
        validateConfig(config)
        saveConfig(config)
    }

    override fun upload(component: Component, file: Path) {
        validateId(component.id, "Component")

        if (!Files.isRegularFile(file)) {
            throw IllegalArgumentException("Component file does not exist")
        }

        val config = getConfig()
        val extension = normalizeExtension(file.fileName.toString().substringAfterLast(".", ""))

        if (extension !in config.allowedExtensions) {
            throw IllegalArgumentException("File extension is not allowed: '$extension'")
        }

        val componentDirectory = getComponentDirectory(component.id)
        if (Files.exists(componentDirectory)) {
            throw IllegalArgumentException("Component already exists")
        }

        val checkedDependencies = mutableSetOf<ComponentId>()

        for (dependency in component.dependencies) {
            validateId(dependency.componentId, "Dependency")

            if (dependency.componentId == component.id) {
                throw IllegalArgumentException("Component cannot depend on itself")
            }
            if (!checkedDependencies.add(dependency.componentId)) {
                throw IllegalArgumentException("Duplicate dependency")
            }

            val configuredScope = config.dependencyScopes[dependency.dependencyScope.name]
                ?: throw IllegalArgumentException("Unknown dependency scope")

            if (configuredScope != dependency.dependencyScope) {
                throw IllegalArgumentException("Dependency scope does not match repository configuration")
            }
            if (!componentExists(dependency.componentId)) {
                throw IllegalArgumentException("Dependency does not exist")
            }
        }

        Files.createDirectories(componentDirectory)
        try {
            Files.copy(file, componentDirectory.resolve(file.fileName))
            saveComponentInfo(component)
        } catch (e: Exception) {
            componentDirectory.toFile().deleteRecursively()
            throw e
        }
    }

    // konfiguracija

    private fun validateConfig(config: RepositoryConfig) {
        if (config.allowedExtensions.any { normalizeExtension(it).let { e -> e.isEmpty() || e.contains(',') } }) {
            throw IllegalArgumentException("Invalid allowed extension")
        }
        for ((key, scope) in config.dependencyScopes) {
            if (key != scope.name) {
                throw IllegalArgumentException("Scope key '$key' does not match scope name '${scope.name}'")
            }
            if (scope.name.isBlank() || scope.name.contains(',')) {
                throw IllegalArgumentException("Invalid scope name: '${scope.name}'")
            }
        }
    }

    private fun saveConfig(config: RepositoryConfig) {
        val properties = Properties()

        properties.setProperty(
            "allowedExtensions",
            config.allowedExtensions.map { normalizeExtension(it) }.joinToString(",")
        )
        properties.setProperty("dependencyScopes", config.dependencyScopes.keys.joinToString(","))

        for ((name, scope) in config.dependencyScopes) {
            properties.setProperty("scope.$name.transitive", scope.transitive.toString())
        }

        Files.newOutputStream(configFile).use {
            properties.store(it, "Repository configuration")
        }
    }
}




/*
package local

import org.example.api.ComponentRepository
import org.example.api.RepositoryManager
import org.example.model.*
import java.lang.IllegalArgumentException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

class LocalComponentRepository (
    private val rootDirectory: Path
) : ComponentRepository, RepositoryManager {

    init{
        Files.createDirectories(rootDirectory)
    }
    private val configFile: Path = rootDirectory.resolve("config.properties")

    private fun getComponentDirectory(id: ComponentId): Path {
        return rootDirectory.resolve(id.organization).resolve(id.project).resolve(id.version)
    }

    private fun saveComponentInfo(component: Component) {
        val infoFile = getComponentDirectory(component.id).resolve("component.txt")

        Files.newBufferedWriter(infoFile).use { writer -> // ovakav nacin automatski zatvara fajl moglo je i oldschool writer.close
            writer.write(component.id.organization)        // ali ovaj sigurno zatvara fajl iako dodje greska a to writer.close ne garantuje
            writer.newLine()
            writer.write(component.id.project)
            writer.newLine()
            writer.write(component.id.version)
            writer.newLine()

            for (dependency in component.dependencies) {
                writer.write(
                    "${dependency.componentId.organization}," +
                            "${dependency.componentId.project}," +
                            "${dependency.componentId.version}," +
                            "${dependency.dependencyScope.name}," +
                            "${dependency.optional}"
                )
                writer.newLine()
            }
        }
    }

    override fun findComponent(id: ComponentId): Component? {
        val directory = getComponentDirectory(id)
        val file = directory.resolve("component.txt")

        if (!Files.exists(file)) return null

        val lines = Files.readAllLines(file)
        val componentId = ComponentId(lines[0], lines[1], lines[2])
        val dependencies = mutableListOf<Dependency>()

        for (i in 3 until lines.size) {
            val parts = lines[i].split(",")
            val dependencyId = ComponentId(parts[0], parts[1], parts[2])
            val scopeName = parts[3]
            val scope = getConfig().dependencyScopes[scopeName]

            if (scope == null) {
                throw IllegalStateException("Unknown dependency scope")
            }

            val optional = parts[4].toBoolean()
            val dependency = Dependency(dependencyId, scope, optional)
            dependencies.add(dependency)
        }
        return Component(componentId, dependencies)
    }

    override fun componentExists(id: ComponentId): Boolean {
        val componentDirectory = getComponentDirectory(id)
        val infoFile = componentDirectory.resolve("component.txt")

        return Files.exists(infoFile)
    }

    override fun findProjects(organisation: String): List<String> {
        val projects = mutableListOf<String>()
        val organizationDirectory = rootDirectory.resolve(organisation)

        if (!Files.exists(organizationDirectory)) {
            return projects
        }
        Files.list(organizationDirectory).use { directories ->
            for (directory in directories.toList()) {
                if (Files.isDirectory(directory)) {
                    projects.add(directory.fileName.toString())
                }
            }
        }
        return projects
    }

    override fun findVersions(organisation: String, project: String): List<String> {
        val versions = mutableListOf<String>()
        val projectDirectory = rootDirectory.resolve(organisation).resolve(project)

        if (!Files.exists(projectDirectory)) {
            return versions
        }
        Files.list(projectDirectory).use { directories ->
            for (directory in directories.toList()) {
                if (Files.isDirectory(directory)) {
                    versions.add(directory.fileName.toString())
                }
            }
        }
        return versions
    }


    override fun download(id: ComponentId, targetDirectory: Path): Path? {
        val componentDirectory = getComponentDirectory(id)

        if (!componentExists(id)) return null

        Files.list(componentDirectory).use { files ->
            for (file in files.toList()) {
                if (file.fileName.toString() != "component.txt") {
                    Files.createDirectories(targetDirectory)

                    val destination = targetDirectory.resolve(file.fileName)

                    if (!Files.exists(destination)) {
                        Files.copy(file, destination)
                    }
                    return destination
                }
            }
        }
        return null
    }


    override fun initialize(config: RepositoryConfig) {
        if (Files.exists(configFile)){
            throw IllegalStateException("Repository is already initialized")
        }
            saveConfig(config)
    }

    override fun getConfig(): RepositoryConfig {
        if (!Files.exists(configFile)) {
            throw IllegalStateException("Repository is not initialized")
        }
        val properties = Properties()

        Files.newInputStream(configFile).use {
            properties.load(it)
        }

        val extensions = properties.getProperty("allowedExtensions").split(",")
        val scopes = mutableMapOf<String, DependencyScope>()
        val scopeNames = properties.getProperty("dependencyScopes").split(",")

        for (name in scopeNames) {
            val transitive = properties.getProperty("scope.$name.transitive").toBoolean()
            val scope = DependencyScope(name, transitive)

            scopes[name] = scope
        }
        return RepositoryConfig(extensions.toSet(), scopes)
    }

    override fun updateConfig(config: RepositoryConfig) {
        if(Files.notExists(configFile)){
            throw IllegalStateException("Repository is not initialized")
        }
        saveConfig(config)
    }

    override fun upload(component: Component, file: Path) {
        if (component.id.organization.isBlank() || component.id.project.isBlank() || component.id.version.isBlank()) {
            throw IllegalArgumentException("Component identification cannot be empty")
        }
        if(!Files.exists(file)){
            throw IllegalArgumentException("Component file does not exist")
        }
        val config = getConfig()
        val extension = file.fileName.toString().substringAfterLast(".","")

        if(!config.allowedExtensions.contains(extension)){
            throw IllegalArgumentException("File extension is not allowed")
        }
        val componentDirectory = getComponentDirectory(component.id)

        if (Files.exists(componentDirectory)) {
            throw IllegalArgumentException("Component already exists")
        }
        val checkedDependencies = mutableSetOf<ComponentId>()

        for (dependency in component.dependencies) {
            if (dependency.componentId.organization.isBlank() || dependency.componentId.project.isBlank() || dependency.componentId.version.isBlank()) {
                throw IllegalArgumentException("Dependency identification cannot be empty")
            }
            if (dependency.componentId == component.id) {
                throw IllegalArgumentException("Component cannot depend on itself")
            }
            if (checkedDependencies.contains(dependency.componentId)) {
                throw IllegalArgumentException("Duplicate dependency")
            }
            checkedDependencies.add(dependency.componentId)

            if (!config.dependencyScopes.containsKey(dependency.dependencyScope.name)) {
                throw IllegalArgumentException("Unknown dependency scope")
            }
            val configuredScope = config.dependencyScopes[dependency.dependencyScope.name]!!

            if (configuredScope != dependency.dependencyScope) {
                throw IllegalArgumentException("Dependency scope does not match repository configuration")
            }
            if (!componentExists(dependency.componentId)) {
                throw IllegalArgumentException("Dependency does not exist")
            }
        }

        Files.createDirectories(componentDirectory)
        try {
            val destination = componentDirectory.resolve(file.fileName)
            Files.copy(file, destination)

            saveComponentInfo(component)
        } catch (e: Exception) {
            componentDirectory.toFile().deleteRecursively()
            throw e
        }
    }

    private fun saveConfig(config: RepositoryConfig){
        val properties = Properties()

        properties.setProperty("allowedExtensions", config.allowedExtensions.joinToString(","))
        properties.setProperty("dependencyScopes", config.dependencyScopes.keys.joinToString(","))

        for ((name, scope) in config.dependencyScopes) {
            properties.setProperty("scope.$name.transitive", scope.transitive.toString())
        }

        Files.newOutputStream(configFile).use {
            properties.store(it, "Repository configuration")
        }
    }
}
*/