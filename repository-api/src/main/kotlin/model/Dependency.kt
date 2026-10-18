package org.example.model

data class Dependency(
    val componentId: ComponentId,
    val dependencyScope: DependencyScope,
    val optional: Boolean = false
) {
}