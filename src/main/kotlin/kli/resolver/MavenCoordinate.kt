package kli.resolver

data class MavenCoordinate(
    val group: String,
    val artifact: String,
    val version: String,
) {
    override fun toString(): String = "$group:$artifact:$version"

    companion object {
        fun parse(input: String): MavenCoordinate {
            val parts = input.split(':')
            if (parts.size != 3 || parts.any { it.isBlank() }) {
                throw IllegalArgumentException("Invalid Maven coordinate: $input")
            }
            return MavenCoordinate(parts[0], parts[1], parts[2])
        }
    }
}
