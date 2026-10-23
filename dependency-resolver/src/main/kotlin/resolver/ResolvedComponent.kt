package resolver

import org.example.model.Component

/** Komponenta izabrana za preuzimanje i njena dubina u stablu (0 = navedena u fajlu). */
data class ResolvedComponent(val component: Component, val depth: Int)
