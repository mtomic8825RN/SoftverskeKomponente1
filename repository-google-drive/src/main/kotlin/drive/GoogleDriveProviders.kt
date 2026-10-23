package drive

import org.example.api.ComponentRepository
import org.example.api.ComponentRepositoryProvider
import org.example.api.RepositoryManager
import org.example.api.RepositoryManagerProvider
import org.example.model.RepositoryConnectionConfig
import org.example.model.RepositoryType
import java.nio.file.Paths

class GoogleDriveComponentRepositoryProvider : ComponentRepositoryProvider {
    override val type: RepositoryType = RepositoryType.GOOGLE_DRIVE

    override fun create(config: RepositoryConnectionConfig): ComponentRepository {
        val credentialsPath = config.properties["credentialsPath"] ?: "credentials.json"
        val tokensDirectory = config.properties["tokensDirectory"] ?: "tokens"
        val rootFolderId = config.properties["rootDirectory"] ?: config.properties["rootFolderId"] ?: "root"

        val storage = DriveStorage.connect(Paths.get(credentialsPath), Paths.get(tokensDirectory))
        return GoogleDriveComponentRepository(storage, rootFolderId)
    }
}

class GoogleDriveRepositoryManagerProvider : RepositoryManagerProvider {
    override val type: RepositoryType = RepositoryType.GOOGLE_DRIVE

    override fun create(config: RepositoryConnectionConfig): RepositoryManager {
        val credentialsPath = config.properties["credentialsPath"] ?: "credentials.json"
        val tokensDirectory = config.properties["tokensDirectory"] ?: "tokens"
        val rootFolderId = config.properties["rootDirectory"] ?: config.properties["rootFolderId"] ?: "root"

        val storage = DriveStorage.connect(Paths.get(credentialsPath), Paths.get(tokensDirectory))
        return GoogleDriveComponentRepository(storage, rootFolderId)
    }
}