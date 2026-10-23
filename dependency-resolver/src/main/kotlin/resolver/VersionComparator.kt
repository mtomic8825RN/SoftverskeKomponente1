package resolver

/** Poredi verzije deo po deo (razdvaja ih `.` i `-`): `1.10.0` > `1.9.0`. */
object VersionComparator : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        val pa = a.split(".", "-")
        val pb = b.split(".", "-")
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { "0" }
            val y = pb.getOrElse(i) { "0" }
            val nx = x.toIntOrNull()
            val ny = y.toIntOrNull()
            val c = if (nx != null && ny != null) nx.compareTo(ny) else x.compareTo(y, ignoreCase = true)
            if (c != 0) return c
        }
        return 0
    }
}
