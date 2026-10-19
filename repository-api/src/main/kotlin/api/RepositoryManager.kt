package org.example.api

import org.example.model.Component
import org.example.model.RepositoryConfig
import java.nio.file.Path

interface RepositoryManager {
    fun initialize(config: RepositoryConfig)
    fun getConfig(): RepositoryConfig
    fun updateConfig(config: RepositoryConfig)
    fun upload(component: Component, file: Path)
}