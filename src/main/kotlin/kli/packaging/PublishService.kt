package kli.packaging

import kli.project.ProjectConfig
import kli.project.ProjectConfigParser
import kli.project.ProjectRootFinder
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.readBytes

sealed interface PublishOutcome {
    data class Success(
        val registry: String,
        val artifact: String,
    ) : PublishOutcome

    data class Failure(
        val message: String,
    ) : PublishOutcome
}

class PublishService(
    private val cwd: () -> Path,
    private val userHome: String = System.getProperty("user.home"),
) {
    fun publish(registryOverride: String?): PublishOutcome {
        val projectRoot = ProjectRootFinder.find(cwd())
            ?: return PublishOutcome.Failure("No project.json found in current directory or parents")

        val configResult = ProjectConfigParser.load(projectRoot.resolve("project.json"), strictUnknownFields = false)
        if (!configResult.isValid) {
            return PublishOutcome.Failure(configResult.errors.joinToString("; "))
        }
        val config = configResult.config ?: return PublishOutcome.Failure("Unable to parse project.json")

        // Determine registry URL
        val registry = registryOverride ?: config.publish?.registry ?: DEFAULT_REGISTRY
        
        // Look for already-built package in ./dist/
        val artifact = config.name ?: projectRoot.fileName.toString()
        val packageJar = projectRoot.resolve("dist").resolve("$artifact-${config.version}.jar")
        
        if (!Files.exists(packageJar)) {
            return PublishOutcome.Failure("Package not found at ${packageJar}. Run 'kli package' first.")
        }

        // Generate POM content
        val pomContent = generatePom(config, artifact)
        
        // Deploy JAR
        val jarDeployResult = deployArtifact(
            registry = registry,
            groupId = DEFAULT_GROUP_ID,
            artifactId = artifact,
            version = config.version,
            classifier = null,
            packaging = "jar",
            file = packageJar,
        )
        
        if (jarDeployResult != null) {
            return PublishOutcome.Failure(jarDeployResult)
        }

        // Deploy POM
        val pomFile = projectRoot.resolve("dist").resolve("pom.xml")
        Files.writeString(pomFile, pomContent)
        
        val pomDeployResult = deployArtifact(
            registry = registry,
            groupId = DEFAULT_GROUP_ID,
            artifactId = artifact,
            version = config.version,
            classifier = null,
            packaging = "pom",
            file = pomFile,
        )
        
        if (pomDeployResult != null) {
            return PublishOutcome.Failure(pomDeployResult)
        }

        return PublishOutcome.Success(registry = registry, artifact = artifact)
    }

    private fun deployArtifact(
        registry: String,
        groupId: String,
        artifactId: String,
        version: String,
        classifier: String?,
        packaging: String,
        file: Path,
    ): String? {
        val groupPath = groupId.replace(".", "/")
        val filename = if (classifier != null) {
            "$artifactId-$version-$classifier.$packaging"
        } else {
            "$artifactId-$version.$packaging"
        }
        
        val deployUrl = "$registry/$groupPath/$artifactId/$version/$filename"
        
        return try {
            val connection = URL(deployUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "PUT"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            
            BufferedOutputStream(connection.outputStream).use { output ->
                Files.newInputStream(file).use { input ->
                    input.copyTo(output)
                }
            }
            
            val responseCode = connection.responseCode
            when {
                responseCode in 200..299 -> null // Success
                responseCode == 401 -> "Unauthorized: Check credentials for registry $registry"
                responseCode == 403 -> "Forbidden: Unable to publish to $registry"
                responseCode == 404 -> "Not found: Repository path not found at $registry"
                else -> "HTTP $responseCode: Failed to deploy to $deployUrl"
            }
        } catch (ex: Exception) {
            "Failed to publish: ${ex.message}"
        }
    }

    private fun generatePom(config: ProjectConfig, artifactId: String): String {
        val deps = (config.deps + config.testDeps).distinct()
        val depsXml = deps.joinToString("\n") { coord ->
            val parts = coord.split(":")
            if (parts.size >= 2) {
                val groupId = parts[0]
                val artifactId = parts[1]
                val version = parts.getOrNull(2) ?: "1.0.0"
                """    <dependency>
      <groupId>$groupId</groupId>
      <artifactId>$artifactId</artifactId>
      <version>$version</version>
    </dependency>"""
            } else {
                ""
            }
        }

        return """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  
  <groupId>$DEFAULT_GROUP_ID</groupId>
  <artifactId>$artifactId</artifactId>
  <version>${config.version}</version>
  <packaging>jar</packaging>
  
  <name>$artifactId</name>
  <description>Kotlin CLI application built with kli</description>
  
  <dependencies>
$depsXml
  </dependencies>
  
  <properties>
    <maven.compiler.source>${config.target}</maven.compiler.source>
    <maven.compiler.target>${config.target}</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
</project>
"""
    }

    private companion object {
        const val DEFAULT_REGISTRY = "https://repo.maven.apache.org/maven2"
        const val DEFAULT_GROUP_ID = "io.kli.local"
    }
}
