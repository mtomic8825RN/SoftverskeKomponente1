package org.example.model

import java.io.Serializable

data class Dependency(
    val componentId: ComponentId,
    val dependencyScope: DependencyScope,
    val optional: Boolean = false
) : Serializable