/*package local

import org.example.api.ComponentRepository
import org.example.api.ComponentRepositoryProvider
import org.example.api.RepositoryManager
import org.example.api.RepositoryManagerProvider
import org.example.model.RepositoryConnectionConfig
import org.example.model.RepositoryType
import java.nio.file.Paths

private fun rootOf(config: RepositoryConnectionConfig) =
    Paths.get(
        config.properties["rootDirectory"]
            ?: throw IllegalArgumentException("Missing property 'rootDirectory'")
    )

class LocalComponentRepositoryProvider : ComponentRepositoryProvider {
    override val type = RepositoryType.LOCAL
    override fun create(config: RepositoryConnectionConfig): ComponentRepository =
        LocalComponentRepository(rootOf(config))
}

class LocalRepositoryManagerProvider : RepositoryManagerProvider {
    override val type = RepositoryType.LOCAL
    override fun create(config: RepositoryConnectionConfig): RepositoryManager =
        LocalComponentRepository(rootOf(config))
}*/