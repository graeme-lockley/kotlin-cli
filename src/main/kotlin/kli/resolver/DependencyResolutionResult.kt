package kli.resolver

import java.nio.file.Path

data class DependencyResolutionResult(
    val runtimeClasspath: List<Path>,
    val testClasspath: List<Path>,
)
