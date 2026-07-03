package kli.project

data class PublishConfig(
    val registry: String? = null,
)

data class ProjectConfig(
    val name: String? = null,
    val version: String = "0.1.0",
    val target: String = "21",
    val deps: List<String> = emptyList(),
    val testDeps: List<String> = emptyList(),
    val sources: List<String> = emptyList(),
    val resources: List<String> = emptyList(),
    val publish: PublishConfig? = null,
)

data class ProjectConfigLoadResult(
    val config: ProjectConfig? = null,
    val errors: List<String> = emptyList(),
) {
    val isValid: Boolean
        get() = errors.isEmpty() && config != null
}
