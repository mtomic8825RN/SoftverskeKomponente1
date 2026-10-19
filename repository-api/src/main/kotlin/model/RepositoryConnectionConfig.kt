package org.example.model

data class RepositoryConnectionConfig(
    val type: RepositoryType,
    val properties: Map<String,String> = emptyMap()

    // evo primer za mapu
    //val connection = RepositoryConnectionConfig(
    //    type = RepositoryType.LOCAL,
    //    properties = mapOf(
    //        "rootDirectory" to "C:/repositories/my-repo"
    //    )
    //)
) {
}