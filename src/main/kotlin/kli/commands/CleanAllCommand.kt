package kli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import kli.cache.CacheCleaner

class CleanAllCommand : CliktCommand(name = "clean-all") {
    override fun help(context: Context): String {
        return "Remove cache artifacts for all projects"
    }

    override fun run() {
        val removed = CacheCleaner.cleanAll()
        if (removed) {
            echo("Removed global cache")
        } else {
            echo("Global cache already clean")
        }
    }
}
