package kli.cache

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class ManifestStore(
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create(),
) {
    fun load(manifestFile: Path): CompilationManifest? {
        if (!Files.isRegularFile(manifestFile)) {
            return null
        }

        return try {
            gson.fromJson(manifestFile.readText(), CompilationManifest::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun save(manifestFile: Path, manifest: CompilationManifest) {
        Files.createDirectories(manifestFile.parent)
        manifestFile.writeText(gson.toJson(manifest))
    }
}
