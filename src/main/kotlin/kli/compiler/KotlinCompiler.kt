package kli.compiler

import java.nio.file.Path

data class CompilationResult(
    val success: Boolean,
    val message: String? = null,
)

interface KotlinCompiler {
    fun compile(
        sourceFiles: List<Path>,
        outputDirectory: Path,
        classpath: List<Path>,
        jvmTarget: String,
    ): CompilationResult
}
