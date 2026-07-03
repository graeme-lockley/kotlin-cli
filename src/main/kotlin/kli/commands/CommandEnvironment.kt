package kli.commands

import java.nio.file.Path
import java.nio.file.Paths

object CommandEnvironment {
    fun cwd(): Path = Paths.get("").toAbsolutePath().normalize()
}
