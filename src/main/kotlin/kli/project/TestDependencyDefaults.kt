package kli.project

object TestDependencyDefaults {
    val defaults: List<String> = listOf(
        "org.jetbrains.kotlin:kotlin-test-junit5:2.0.21",
        "org.junit.jupiter:junit-jupiter-engine:5.13.4",
        "org.junit.platform:junit-platform-launcher:1.13.4",
    )

    fun withDefaults(testDeps: List<String>): List<String> {
        return if (testDeps.isEmpty()) defaults else testDeps
    }
}
