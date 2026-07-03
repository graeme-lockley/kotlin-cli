package kli.resolver

import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

data class MavenVersionInfo(
    val versions: List<String> = emptyList(),
    val latest: String? = null,
    val error: String? = null,
)

interface VersionResolver {
    fun resolveVersions(coordinate: String, registryUrl: String): MavenVersionInfo
}

class MavenCentralVersionResolver : VersionResolver {
    override fun resolveVersions(coordinate: String, registryUrl: String): MavenVersionInfo {
        val parts = coordinate.split(":")
        if (parts.size < 2) {
            return MavenVersionInfo(error = "Invalid coordinate: $coordinate")
        }
        
        val groupId = parts[0]
        val artifactId = parts[1]
        
        return try {
            val metadataUrl = buildMetadataUrl(registryUrl, groupId, artifactId)
            val metadata = fetchMetadata(metadataUrl)
            parseVersions(metadata)
        } catch (ex: Exception) {
            MavenVersionInfo(error = "Failed to resolve versions: ${ex.message}")
        }
    }
    
    private fun buildMetadataUrl(registry: String, groupId: String, artifactId: String): String {
        val groupPath = groupId.replace(".", "/")
        val registryBase = registry.trimEnd('/')
        return "$registryBase/$groupPath/$artifactId/maven-metadata.xml"
    }
    
    private fun fetchMetadata(url: String): String {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().readText()
            } else {
                ""
            }
        } catch (ex: Exception) {
            ""
        }
    }
    
    private fun parseVersions(metadata: String): MavenVersionInfo {
        if (metadata.isEmpty()) {
            return MavenVersionInfo(error = "No metadata found")
        }
        
        val versions = mutableListOf<String>()
        val versionRegex = Regex("<version>([^<]+)</version>")
        
        versionRegex.findAll(metadata).forEach { match ->
            versions.add(match.groupValues[1])
        }
        
        if (versions.isEmpty()) {
            return MavenVersionInfo(error = "No versions found in metadata")
        }
        
        // Get latest version (last one usually is)
        val latest = versions.lastOrNull()
        
        return MavenVersionInfo(versions = versions, latest = latest)
    }
}
