package kli.packaging

import java.nio.file.Files
import java.util.jar.JarFile
import kotlin.test.Test
import kotlin.test.fail
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PackageServiceIntegrationTest {
    @Test
    fun builds_dispatcher_jar_and_installs_local_metadata() {
        val root = Files.createTempDirectory("kli-package-integration")
        Files.writeString(
            root.resolve("project.json"),
            """
            {
              "name": "demo",
              "version": "0.9.0",
              "deps": [],
              "resources": ["config/**"]
            }
            """.trimIndent(),
        )

        val toolsDir = Files.createDirectories(root.resolve("tools"))
        Files.writeString(
            toolsDir.resolve("Server.kt"),
            """
            package tools
            fun main(args: Array<String>) {
                println("server-ready")
            }
            """.trimIndent(),
        )

        val configDir = Files.createDirectories(root.resolve("config"))
        Files.writeString(configDir.resolve("app.conf"), "mode=test")

        val userHome = Files.createTempDirectory("kli-home").toString()
        val service = PackageService(
            cwd = { root },
            userHome = userHome,
        )

        val outcome = service.build(outputOverride = null)
        if (outcome is PackageOutcome.Failure) {
            fail("Package build failed: ${outcome.message}")
        }
        assertTrue(outcome is PackageOutcome.Success)
        outcome as PackageOutcome.Success

        assertTrue(Files.isRegularFile(outcome.outputJar))
        assertTrue(Files.isRegularFile(outcome.installedJar))

        JarFile(outcome.outputJar.toFile()).use { jar ->
            val mainClass = jar.manifest.mainAttributes.getValue("Main-Class")
            assertTrue(mainClass == "kli.dispatcher.MainDispatcherKt")
            assertNotNull(jar.getEntry("kli/dispatcher/MainDispatcherKt.class"))
            assertNotNull(jar.getEntry("tools/ServerKt.class"))
            assertNotNull(jar.getEntry("config/app.conf"))
        }

        val installDir = outcome.installedJar.parent
        val pomFile = installDir.resolve("demo-0.9.0.pom")
        val metadataFile = installDir.parent.resolve("maven-metadata-local.xml")
        assertTrue(Files.isRegularFile(pomFile))
        assertTrue(Files.isRegularFile(metadataFile))
    }
}
