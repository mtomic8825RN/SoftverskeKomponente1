/*package org.example.model

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
*/
package org.example.model

/**
 * Podaci potrebni za povezivanje na konkretan repozitorijum.
 *
 * Koristi se pri pravljenju drajvera preko [org.example.api.ComponentRepositoryProvider]
 * i [org.example.api.RepositoryManagerProvider].
 *
 * Značenje [properties] zavisi od tipa repozitorijuma:
 * - [RepositoryType.LOCAL]: `rootDirectory` (putanja do korenskog foldera)
 * - [RepositoryType.NETWORK]: `url` (adresa servera, npr. `http://192.168.0.10:8080`)
 * - [RepositoryType.GOOGLE_DRIVE]: `rootFolderId`, a opciono `credentialsFile` i `tokensDirectory`
 *
 * Primer:
 * ```
 * val connection = RepositoryConnectionConfig(
 *     type = RepositoryType.LOCAL,
 *     properties = mapOf("rootDirectory" to "C:/repositories/my-repo")
 * )
 * ```
 *
 * @property type vrsta repozitorijuma, na osnovu koje se bira provider
 * @property properties parametri specifični za tu vrstu repozitorijuma
 */
data class RepositoryConnectionConfig(
    val type: RepositoryType,
    val properties: Map<String, String> = emptyMap()
)