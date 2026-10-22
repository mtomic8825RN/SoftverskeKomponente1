package org.example.model

import java.io.Serializable

data class DependencyScope (
    val name: String,
    val transitive: Boolean
) : Serializable


