package org.example.model

import java.io.Serializable

data class Component(
    val id: ComponentId,
    val dependencies: List<Dependency> = emptyList()
) : Serializable