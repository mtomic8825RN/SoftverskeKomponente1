package resolver

import org.example.api.ComponentRepository
import org.example.model.Component
import org.example.model.ComponentId
import org.example.model.Dependency

/** Strategija razrešavanja konflikta verzija (ista organizacija i projekat, različite verzije). */
interface ConflictResolutionStrategy {
    fun resolve(roots: List<ComponentId>, repository: ComponentRepository): List<ResolvedComponent>

    companion object {
        /** Bira strategiju po imenu: `newest` ili `nearest`. */
        fun of(name: String): ConflictResolutionStrategy = when (name.trim().lowercase()) {
            "newest" -> NewestVersionStrategy()
            "nearest" -> NearestToRootStrategy()
            else -> throw IllegalArgumentException("Unknown strategy '$name' (use 'newest' or 'nearest')")
        }
    }
}

internal data class ArtifactKey(val organization: String, val project: String)

internal fun ComponentId.key() = ArtifactKey(organization, project)

/** Zavisnosti koje se povlače: ne-opcione, i samo iz opsega koji su tranzitivni. */
internal fun Component.pulledDependencies(): List<Dependency> =
    dependencies.filter { !it.optional && it.dependencyScope.transitive }

internal fun ComponentRepository.mustFind(id: ComponentId): Component =
    findComponent(id) ?: throw IllegalStateException("Component not found in repository: $id")

/** Mavenovo pravilo: pobeđuje komponenta najbliža korenu, a pri istoj dubini ona koja je prva naišla. */
class NearestToRootStrategy : ConflictResolutionStrategy {
    override fun resolve(roots: List<ComponentId>, repository: ComponentRepository): List<ResolvedComponent> {
        val selected = LinkedHashMap<ArtifactKey, ResolvedComponent>()
        val queue = ArrayDeque<Pair<ComponentId, Int>>()
        roots.forEach { queue.addLast(it to 0) }

        while (queue.isNotEmpty()) {          // BFS, pa se nivoi obilaze redom
            val (id, depth) = queue.removeFirst()
            if (id.key() in selected) continue

            val component = repository.mustFind(id)
            selected[id.key()] = ResolvedComponent(component, depth)
            component.pulledDependencies().forEach { queue.addLast(it.componentId to depth + 1) }
        }
        return selected.values.toList()
    }
}

/** Pobeđuje najnovija verzija, bez obzira na dubinu. */
class NewestVersionStrategy : ConflictResolutionStrategy {
    override fun resolve(roots: List<ComponentId>, repository: ComponentRepository): List<ResolvedComponent> {
        val chosen = HashMap<ArtifactKey, String>()   // najnovija viđena verzija po projektu

        while (true) {
            val visited = LinkedHashMap<ArtifactKey, ResolvedComponent>()
            var changed = false
            val queue = ArrayDeque<Pair<ComponentId, Int>>()
            roots.forEach { queue.addLast(it to 0) }

            while (queue.isNotEmpty()) {
                val (id, depth) = queue.removeFirst()
                val key = id.key()

                val current = chosen[key]
                if (current == null || VersionComparator.compare(id.version, current) > 0) {
                    chosen[key] = id.version
                    changed = true
                }
                if (key in visited) continue

                val component = repository.mustFind(id.copy(version = chosen[key]!!))
                visited[key] = ResolvedComponent(component, depth)
                component.pulledDependencies().forEach { queue.addLast(it.componentId to depth + 1) }
            }
            // ako se neka verzija povecala, obilazak se ponavlja (verzije samo rastu, pa staje)
            if (!changed) return visited.values.toList()
        }
    }
}
