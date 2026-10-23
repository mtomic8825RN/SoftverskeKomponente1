package resolver

import org.example.api.ComponentRepository
import org.example.model.ComponentId

/** Razrešava tranzitivne zavisnosti i konflikte verzija, bez preuzimanja fajlova. */
class DependencyResolver(
    private val repository: ComponentRepository,
    private val strategy: ConflictResolutionStrategy = NearestToRootStrategy()
) {
    fun resolve(roots: List<ComponentId>): List<ResolvedComponent> =
        strategy.resolve(roots, repository)
}
