package cli.upload

//Parsira argumente oblika `--kljuc vrednost`. Kljuc se moze ponavljati.
class Options(args: List<String>) {
    private val values = mutableMapOf<String, MutableList<String>>()

    init {
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            if (!arg.startsWith("--")) throw IllegalArgumentException("Unexpected argument '$arg'")
            if (i + 1 >= args.size) throw IllegalArgumentException("Missing value for $arg")
            values.getOrPut(arg.removePrefix("--")) { mutableListOf() }.add(args[i + 1])
            i += 2
        }
    }

    fun get(key: String): String? = values[key]?.last()

    fun need(key: String): String =
        get(key) ?: throw IllegalArgumentException("Missing required option --$key")

    fun all(key: String): List<String> = values[key] ?: emptyList()
}