package resolver

import org.example.model.ComponentId

/** Pravi [ComponentId] iz teksta `organizacija:projekat:verzija` ili iz tri odvojena podatka. */
object ComponentIdParser {
    fun parse(text: String): ComponentId {
        val parts = text.trim().split(":").map { it.trim() }
        require(parts.size == 3 && parts.all { it.isNotEmpty() }) {
            "Invalid component '$text', expected organization:project:version"
        }
        return ComponentId(parts[0], parts[1], parts[2])
    }

    fun of(organization: String, project: String, version: String) =
        ComponentId(organization, project, version)
}
