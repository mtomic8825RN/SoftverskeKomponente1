package org.example.local

import org.example.api.ComponentRepository
import org.example.api.RepositoryManager
import org.example.model.Component
import org.example.model.ComponentId
import org.example.model.DependencyScope
import org.example.model.RepositoryConfig
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

    override fun findComponent(id: ComponentId): Component? {
        TODO("Not yet implemented")
    }

    override fun componentExists(id: ComponentId): Boolean {
        TODO("Not yet implemented")
    }

    override fun findProjects(organisation: String): List<String> {
        TODO("Not yet implemented")
    }

    override fun findVersions(organisation: String, project: String): List<String> {
        TODO("Not yet implemented")
    }

    override fun download(id: ComponentId, targetDirectory: Path): Path? {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
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