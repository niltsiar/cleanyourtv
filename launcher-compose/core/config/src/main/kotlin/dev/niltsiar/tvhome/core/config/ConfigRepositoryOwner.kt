package dev.niltsiar.tvhome.core.config

/**
 * Implemented by the Application. The key service cannot depend on the app module, and the store allows one repository
 * per process, so it takes the app's.
 */
public interface ConfigRepositoryOwner {
    public val configRepository: ConfigRepository
}
