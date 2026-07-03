package kli.packaging

import java.nio.file.Files
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SimpleJarBuilderTest {
    @Test
    fun builds_fat_jar_with_manifest_and_dependency_entries() {
        val classesDir = Files.createTempDirectory("kli-jar-classes")
        val pkgDir = Files.createDirectories(classesDir.resolve("tools"))
        Files.writeString(pkgDir.resolve("ServerKt.class"), "bytecode")

        val depJar = Files.createTempFile("kli-dep", ".jar")
        JarOutputStream(Files.newOutputStream(depJar)).use { jar ->
            jar.putNextEntry(java.util.jar.JarEntry("dep/Lib.class"))
            jar.write("lib-bytecode".toByteArray())
            jar.closeEntry()
        }

        val resourceFile = Files.createTempFile("kli-resource", ".txt")
        Files.writeString(resourceFile, "resource-content")

        val outputJar = Files.createTempDirectory("kli-jar-out").resolve("app.jar")
        val builder = SimpleJarBuilder()

        builder.build(
            classesDir = classesDir,
            runtimeDependencies = listOf(depJar),
            additionalEntries = mapOf("config/app.conf" to resourceFile),
            outputJar = outputJar,
            mainClass = "kli.dispatcher.MainDispatcherKt",
        )

        JarFile(outputJar.toFile()).use { jar ->
            val manifestMain = jar.manifest.mainAttributes.getValue("Main-Class")
            assertEquals("kli.dispatcher.MainDispatcherKt", manifestMain)
            assertNotNull(jar.getEntry("tools/ServerKt.class"))
            assertNotNull(jar.getEntry("dep/Lib.class"))
            assertNotNull(jar.getEntry("config/app.conf"))
            assertTrue(jar.getEntry("META-INF/MANIFEST.MF") != null)
        }
    }
}
