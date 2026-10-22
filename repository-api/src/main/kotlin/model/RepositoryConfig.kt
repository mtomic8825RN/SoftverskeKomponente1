package org.example.model

import java.io.Serializable

data class RepositoryConfig(
    val allowedExtensions: Set<String>,                 // set da se izbegne ponavljanje zato ne lista
    val dependencyScopes: Map<String,DependencyScope>   // mapa bolja zbog pretrage,
                                                        // ako nam je  kljuc "compile" dace nam scope za compile,
                                                        // da je bila lista isli bi svaki put kroz celu
) : Serializable