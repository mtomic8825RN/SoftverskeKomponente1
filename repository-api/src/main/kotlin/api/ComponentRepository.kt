package org.example.api

import org.example.model.Component
import org.example.model.ComponentId
import java.nio.file.Path

interface ComponentRepository {

    fun upload(component: Component, file: Path)
    fun findDocument(id: ComponentId): Component?
    fun findProjects(organisation: String): List<String>
    fun findVersions(organisation: String,project: String): List<String>
    fun download(id: ComponentId, targetDirectory: Path): Path?

}