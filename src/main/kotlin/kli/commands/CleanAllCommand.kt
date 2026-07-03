package kli.commands

import com.github.ajalt.clikt.core.CliktCommand
import kli.cache.CacheCleaner

class CleanAllCommand : CliktCommand(name = "clean-all") {
    override fun run() {
        val removed = CacheCleaner.cleanAll()
        if (removed) {
            echo("Removed global cache")
        } else {
            echo("Global cache already clean")
        }
    }
}
