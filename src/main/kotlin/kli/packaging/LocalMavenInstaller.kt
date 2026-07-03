package kli.packaging

import kli.cache.KliPaths
import java.nio.file.Files
import java.nio.file.Path

interface MavenInstaller {
    fun install(jar: Path, artifact: String, version: String, userHome: String): Path
}

class LocalMavenInstaller : MavenInstaller {
    override fun install(jar: Path, artifact: String, version: String, userHome: String): Path {
        val destinationDir = KliPaths.m2(userHome)
            .resolve("local")
            .resolve(artifact)
            .resolve(version)
        Files.createDirectories(destinationDir)
        val destination = destinationDir.resolve("$artifact-$version.jar")
        Files.copy(jar, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        return destination
    }
}
