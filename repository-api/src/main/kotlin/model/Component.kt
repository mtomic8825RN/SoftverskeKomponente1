package org.example.model

data class Component(
    val id: ComponentId,
    val dependencies: List<Dependency> = emptyList()
) {
}