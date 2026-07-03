package kli.run

import java.net.URLClassLoader
import java.nio.file.Path

interface ProgramRunner {
    fun run(mainClass: String, classpath: List<Path>, programArgs: List<String>): Int
}

class ReflectiveProgramRunner : ProgramRunner {
    override fun run(mainClass: String, classpath: List<Path>, programArgs: List<String>): Int {
        val urls = classpath.map { it.toUri().toURL() }.toTypedArray()
        URLClassLoader(urls, ClassLoader.getSystemClassLoader()).use { classLoader ->
            val clazz = classLoader.loadClass(mainClass)
            val method = clazz.getMethod("main", Array<String>::class.java)
            method.invoke(null, programArgs.toTypedArray() as Any)
        }
        return 0
    }
}
