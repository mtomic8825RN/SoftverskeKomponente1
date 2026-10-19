package org.example.api

import org.example.model.RepositoryConnectionConfig
import org.example.model.RepositoryType

interface ComponentRepositoryProvider {
    val type: RepositoryType
    fun create(config: RepositoryConnectionConfig): ComponentRepository
}