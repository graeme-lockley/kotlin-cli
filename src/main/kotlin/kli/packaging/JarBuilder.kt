package kli.packaging

import java.io.BufferedInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

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
        JarOutputStream(Files.newOutputStream(outputJar), manifest).use { jar ->
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

            for (dependency in runtimeDependencies) {
                if (!Files.isRegularFile(dependency) || !dependency.toString().endsWith(".jar")) {
                    continue
                }
                JarFile(dependency.toFile()).use { depJar ->
                    depJar.entries().asSequence()
                        .filter { !it.isDirectory }
                        .filter { it.name != "META-INF/MANIFEST.MF" }
                        .forEach { entry ->
                            if (!writtenEntries.add(entry.name)) {
                                return@forEach
                            }
                            jar.putNextEntry(JarEntry(entry.name))
                            depJar.getInputStream(entry).use { input ->
                                input.copyTo(jar)
                            }
                            jar.closeEntry()
                        }
                }
            }

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
        }
    }
}
