package org.example.api

import org.example.model.Component
import org.example.model.ComponentId
import java.nio.file.Path

interface ComponentRepository {

    fun findComponent(id: ComponentId): Component?
    fun componentExists(id: ComponentId): Boolean
    fun findProjects(organisation: String): List<String>
    fun findVersions(organisation: String,project: String): List<String>
    fun download(id: ComponentId, targetDirectory: Path): Path?

}