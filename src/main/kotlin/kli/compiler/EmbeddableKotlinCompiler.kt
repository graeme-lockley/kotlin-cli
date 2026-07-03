package kli.compiler

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

class EmbeddableKotlinCompiler(
    private val errorStream: PrintStream = System.err,
) : KotlinCompiler {
    override fun compile(
        sourceFiles: List<Path>,
        outputDirectory: Path,
        classpath: List<Path>,
        jvmTarget: String,
    ): CompilationResult {
        if (sourceFiles.isEmpty()) {
            return CompilationResult(success = false, message = "No source files found")
        }

        Files.createDirectories(outputDirectory)

        val args = K2JVMCompilerArguments().apply {
            freeArgs = sourceFiles.map { it.toString() }
            destination = outputDirectory.toString()
            this.jvmTarget = jvmTarget
            this.classpath = classpath.joinToString(File.pathSeparator) { it.toString() }
        }

        val collector = PrintingMessageCollector(errorStream, MessageRenderer.PLAIN_RELATIVE_PATHS, true)
        val exitCode = K2JVMCompiler().exec(collector, Services.EMPTY, args)

        return when (exitCode) {
            ExitCode.OK -> CompilationResult(success = true)
            ExitCode.COMPILATION_ERROR -> CompilationResult(success = false, message = "Compilation failed")
            ExitCode.INTERNAL_ERROR -> CompilationResult(success = false, message = "Compiler internal error")
            ExitCode.SCRIPT_EXECUTION_ERROR -> CompilationResult(success = false, message = "Script execution error")
            ExitCode.OOM_ERROR -> CompilationResult(success = false, message = "Compiler out of memory")
        }
    }
}
