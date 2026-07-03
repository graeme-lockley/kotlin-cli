package kli.cache

import java.nio.file.Path
import java.nio.file.Paths

object KliPaths {
    fun home(userHome: String = System.getProperty("user.home")): Path =
        Paths.get(userHome).resolve(".kli").toAbsolutePath().normalize()

    fun m2(userHome: String = System.getProperty("user.home")): Path =
        home(userHome).resolve("m2")

    fun cacheRoot(userHome: String = System.getProperty("user.home")): Path =
        home(userHome).resolve("cache")
}
