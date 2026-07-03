package kli.packaging

import java.io.BufferedInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

interface JarBuilder {
    fun build(classesDir: Path, outputJar: Path)
}

class SimpleJarBuilder : JarBuilder {
    override fun build(classesDir: Path, outputJar: Path) {
        Files.createDirectories(outputJar.parent)
        JarOutputStream(Files.newOutputStream(outputJar)).use { jar ->
            Files.walk(classesDir).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) }
                    .forEach { file ->
                        val relative = classesDir.relativize(file).toString().replace('\\', '/')
                        jar.putNextEntry(JarEntry(relative))
                        BufferedInputStream(Files.newInputStream(file)).use { input ->
                            input.copyTo(jar)
                        }
                        jar.closeEntry()
                    }
            }
        }
    }
}
