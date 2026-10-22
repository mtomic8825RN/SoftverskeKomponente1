package org.example.model

import java.io.Serializable

data class ComponentId(
    val organization: String,
    val project: String,
    val version: String
) : Serializable