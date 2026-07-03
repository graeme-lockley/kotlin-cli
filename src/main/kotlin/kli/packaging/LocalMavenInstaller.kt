package kli.packaging

import kli.cache.KliPaths
import java.nio.file.Files
import java.nio.file.Path

interface MavenInstaller {
        fun install(jar: Path, groupId: String, artifact: String, version: String, userHome: String): Path
}

class LocalMavenInstaller : MavenInstaller {
        override fun install(jar: Path, groupId: String, artifact: String, version: String, userHome: String): Path {
        val destinationDir = KliPaths.m2(userHome)
                        .resolve(groupId.replace('.', '/'))
            .resolve(artifact)
            .resolve(version)
        Files.createDirectories(destinationDir)

        val destination = destinationDir.resolve("$artifact-$version.jar")
        Files.copy(jar, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING)

                val pomFile = destinationDir.resolve("$artifact-$version.pom")
                Files.writeString(pomFile, pomXml(groupId, artifact, version))

                val metadataFile = destinationDir.parent.resolve("maven-metadata-local.xml")
                Files.createDirectories(metadataFile.parent)
                Files.writeString(metadataFile, metadataXml(groupId, artifact, version))

        return destination
    }

        private fun pomXml(groupId: String, artifact: String, version: String): String =
                """
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>$groupId</groupId>
                    <artifactId>$artifact</artifactId>
                    <version>$version</version>
                    <packaging>jar</packaging>
                </project>
                """.trimIndent()

        private fun metadataXml(groupId: String, artifact: String, version: String): String =
                """
                <metadata>
                    <groupId>$groupId</groupId>
                    <artifactId>$artifact</artifactId>
                    <versioning>
                        <latest>$version</latest>
                        <release>$version</release>
                        <versions>
                            <version>$version</version>
                        </versions>
                    </versioning>
                </metadata>
                """.trimIndent()
}
