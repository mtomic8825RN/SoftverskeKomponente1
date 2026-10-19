package org.example.local

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
        val directories = Files.list(organizationDirectory)

        for (directory in directories.toList()) {
            if (Files.isDirectory(directory)) {
                projects.add(directory.fileName.toString())
            }
        }

        directories.close()
        return projects
    }


    override fun findVersions(organisation: String, project: String): List<String> {
        val versions = mutableListOf<String>()
        val projectDirectory = rootDirectory.resolve(organisation).resolve(project)

        if (!Files.exists(projectDirectory)) {
            return versions
        }
        val directories = Files.list(projectDirectory)

        for (directory in directories.toList()) {
            if (Files.isDirectory(directory)) {
                versions.add(directory.fileName.toString())
            }
        }

        directories.close()
        return versions
    }



    override fun download(id: ComponentId, targetDirectory: Path): Path? {
        val componentDirectory = getComponentDirectory(id)

        if (!componentExists(id)) {
            return null
        }
        val files = Files.list(componentDirectory)

        for (file in files.toList()) {
            if (file.fileName.toString() != "component.txt") {
                Files.createDirectories(targetDirectory)

                val destination = targetDirectory.resolve(file.fileName)
                Files.copy(file, destination)

                files.close()
                return destination
            }
        }

        files.close()
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
            if (dependency.componentId == component.id) {
                throw IllegalArgumentException("Component cannot depend on itself")
            }// ne moze da komponenta zavisi od same sebe
            if (checkedDependencies.contains(dependency.componentId)) {
                throw IllegalArgumentException("Duplicate dependency")
            }// duplirana zavisnost
            checkedDependencies.add(dependency.componentId)

            if (!config.dependencyScopes.containsKey(dependency.dependencyScope.name)) {
                throw IllegalArgumentException("Unknown dependency scope")
            }
            if (!componentExists(dependency.componentId)) {
                throw IllegalArgumentException("Dependency does not exist")
            }
        }

        Files.createDirectories(componentDirectory)
        val destination = componentDirectory.resolve(file.fileName)
        Files.copy(file,destination)

        saveComponentInfo(component)

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