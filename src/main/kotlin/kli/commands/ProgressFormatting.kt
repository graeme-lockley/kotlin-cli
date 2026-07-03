package kli.commands

import java.nio.file.Path

private const val ANSI_LIGHT_GRAY = "\u001B[90m"
private const val ANSI_RESET = "\u001B[0m"

fun formatCompileProgress(projectRoot: Path, sourceFile: Path, durationMs: Long): String {
    val normalizedRoot = projectRoot.toAbsolutePath().normalize()
    val normalizedSource = sourceFile.toAbsolutePath().normalize()
    val displayPath = if (normalizedSource.startsWith(normalizedRoot)) {
        normalizedRoot.relativize(normalizedSource).toString()
    } else {
        normalizedSource.fileName.toString()
    }
    return colorizeLightGray("Compiling $displayPath (${durationMs}ms)")
}

fun formatDependencyProgress(coordinate: String, durationMs: Long): String {
    return colorizeLightGray("Downloading $coordinate (${durationMs}ms)")
}

private fun colorizeLightGray(message: String): String {
    return "$ANSI_LIGHT_GRAY$message$ANSI_RESET"
}
