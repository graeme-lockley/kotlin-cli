package kli.packaging

import java.io.BufferedInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.io.path.readText
import kotlin.io.path.writeText

interface JarBuilder {
    fun build(
        classesDir: Path,
        runtimeDependencies: List<Path>,
        additionalEntries: Map<String, Path>,
        outputJar: Path,
        mainClass: String,
    )
}

class SimpleJarBuilder : JarBuilder {
    override fun build(
        classesDir: Path,
        runtimeDependencies: List<Path>,
        additionalEntries: Map<String, Path>,
        outputJar: Path,
        mainClass: String,
    ) {
        Files.createDirectories(outputJar.parent)
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes[Attributes.Name.MAIN_CLASS] = mainClass
        }

        val writtenEntries = mutableSetOf<String>()
        val serviceFiles = mutableMapOf<String, MutableSet<String>>() // servicePath -> set of lines
        
        JarOutputStream(Files.newOutputStream(outputJar), manifest).use { jar ->
            // Write compiled classes
            Files.walk(classesDir).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) }
                    .forEach { file ->
                        val relative = classesDir.relativize(file).toString().replace('\\', '/')
                        if (!writtenEntries.add(relative)) {
                            return@forEach
                        }
                        jar.putNextEntry(JarEntry(relative))
                        BufferedInputStream(Files.newInputStream(file)).use { input ->
                            input.copyTo(jar)
                        }
                        jar.closeEntry()
                    }
            }

            // Write dependencies and collect service files
            for (dependency in runtimeDependencies) {
                if (!Files.isRegularFile(dependency) || !dependency.toString().endsWith(".jar")) {
                    continue
                }
                JarFile(dependency.toFile()).use { depJar ->
                    depJar.entries().asSequence()
                        .filter { !it.isDirectory }
                        .filter { it.name != "META-INF/MANIFEST.MF" }
                        .forEach { entry ->
                            when {
                                entry.name.startsWith("META-INF/services/") -> {
                                    // Collect service file lines for merging
                                    depJar.getInputStream(entry).use { input ->
                                        val lines = input.bufferedReader().readLines()
                                        serviceFiles.getOrPut(entry.name) { mutableSetOf() }.addAll(lines)
                                    }
                                }
                                !writtenEntries.add(entry.name) -> {
                                    // Skip duplicate
                                }
                                else -> {
                                    // Write non-service entry normally
                                    jar.putNextEntry(JarEntry(entry.name))
                                    depJar.getInputStream(entry).use { input ->
                                        input.copyTo(jar)
                                    }
                                    jar.closeEntry()
                                }
                            }
                        }
                }
            }

            // Write additional entries (resources and dispatcher classes)
            for ((entryName, file) in additionalEntries) {
                if (!writtenEntries.add(entryName)) {
                    continue
                }
                jar.putNextEntry(JarEntry(entryName))
                Files.newInputStream(file).use { input ->
                    input.copyTo(jar)
                }
                jar.closeEntry()
            }

            // Write merged service files
            for ((servicePath, lines) in serviceFiles) {
                if (writtenEntries.add(servicePath)) {
                    jar.putNextEntry(JarEntry(servicePath))
                    val mergedContent = lines.sorted().joinToString("\n")
                    jar.write(mergedContent.toByteArray())
                    jar.closeEntry()
                }
            }
        }
    }
}
