package kli.packaging

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalMavenInstallerTest {
    @Test
    fun installs_jar_and_writes_pom_and_metadata() {
        val home = Files.createTempDirectory("kli-home").toString()
        val sourceJar = Files.createTempFile("kli-artifact", ".jar")
        Files.writeString(sourceJar, "jar-content")

        val installer = LocalMavenInstaller()
        val installedJar = installer.install(
            jar = sourceJar,
            groupId = "io.kli.local",
            artifact = "demo",
            version = "1.2.3",
            userHome = home,
        )

        assertTrue(Files.isRegularFile(installedJar))
        assertTrue(installedJar.toString().contains("io/kli/local/demo/1.2.3"))

        val pomFile = installedJar.parent.resolve("demo-1.2.3.pom")
        assertTrue(Files.isRegularFile(pomFile))
        val pomText = Files.readString(pomFile)
        assertTrue(pomText.contains("<groupId>io.kli.local</groupId>"))
        assertTrue(pomText.contains("<artifactId>demo</artifactId>"))
        assertTrue(pomText.contains("<version>1.2.3</version>"))

        val metadata = installedJar.parent.parent.resolve("maven-metadata-local.xml")
        assertTrue(Files.isRegularFile(metadata))
        val metadataText = Files.readString(metadata)
        assertTrue(metadataText.contains("<latest>1.2.3</latest>"))
        assertTrue(metadataText.contains("<version>1.2.3</version>"))
        assertEquals("jar-content", Files.readString(installedJar))
    }
}
