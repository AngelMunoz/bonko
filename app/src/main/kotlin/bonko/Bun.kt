package bonko

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.*
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.zip.*
import kotlin.io.path.Path
import com.google.common.io.Resources

const val RELEASES_URL_TPL = "https://github.com/oven-sh/bun/releases/download"

sealed class Platform {
    object Windows : Platform()
    object MacOS : Platform()
    object Linux : Platform()

    object Unknown : Platform()

    companion object Helpers {
        val current
            get(): Platform {
                val osName = System.getProperty("os.name").lowercase()
                val isWindows = osName.contains("win")
                val isMac = osName.contains("mac")
                val isLinux = osName.contains("nux") || osName.contains("nix")
                if (isWindows) return Windows
                if (isMac) return MacOS
                if (isLinux) return Linux
                return Unknown
            }
    }

    fun asString(): String {
        return when (this) {
            is Windows -> "windows"
            is Linux -> "linux"
            is MacOS -> "darwin"
            is Unknown -> "unknown"
        }
    }

}

sealed class Arch {

    object X86 : Arch()
    object X64 : Arch()
    object Aarm32 : Arch()
    object Aarm64 : Arch()

    object Unknown : Arch()

    companion object Helpers {
        val current
            get(): Arch {
                val osArch = System.getProperty("os.arch").lowercase()
                val isX64 = osArch.matches(Regex("^(x8664|amd64|ia32e|em64t|x64)$"))
                val isX86 = osArch.matches(Regex("^(x8632|x86|i[3-6]86|ia32|x32)$"))
                val isAarm32 = osArch.contains("arm") || osArch.contains("arm32")
                val isAarm64 = osArch.contains("aarch64")
                if (isX64) return X64
                if (isX86) return X86
                if (isAarm32) return Aarm32
                if (isAarm64) return Aarm64
                return Unknown
            }
    }

    fun asString(): String {
        return when (this) {
            Aarm64 -> "aarm64"
            Aarm32 -> "arm"
            Unknown -> "unknown"
            X64 -> "x64"
            X86 -> "x86"
        }
    }
}

fun unzip(parentDir: Path, zip: ZipInputStream): String {
    var firstPath: String? = null
    var entry = zip.nextEntry

    while (entry != null) {
        val filePath = Path.of(parentDir.toString(), entry.name)
        val file = filePath.toFile()

        if (firstPath == null) {
            firstPath = file.absolutePath
        }

        if (entry.isDirectory) {
            file.mkdirs()
        } else {
            val parentDirs = file.parentFile
            if (parentDirs != null && !parentDirs.isDirectory) file.mkdirs()
            FileOutputStream(file).use {
                zip.copyTo(it)
            }
        }
        entry = zip.nextEntry
    }

    if (firstPath != null) {
        val path = "$firstPath/bun"
        try {
            val file = File(path)
            file.setExecutable(true)
        } catch (err: Exception) {
            println("We couldn't make '$path' Executable...s")
        }
    }

    return firstPath.orEmpty()
}

suspend fun downloadBun(tag: String, os: String, arch: String, isBaseline: Boolean = false): ByteArray {
    val client = HttpClient(CIO)
    val baseline = if (isBaseline) "-baseline" else ""
    val target = "/$tag/bun-$os-$arch$baseline.zip"
    val response: HttpResponse = client.get("$RELEASES_URL_TPL$target")
    val channel = response.bodyAsChannel().toByteArray()
    client.close()
    return channel
}

suspend fun setupBun(targetPath: String, version: String, requestBaseline: Boolean = false): String {
    val tag = "bun-v$version"
    val os = Platform.current.asString()
    val arch: String = Arch.current.asString()
    val baseline = if (requestBaseline) "-baseline" else ""
    val targetName = "bun-$os-$arch$baseline"

    val downloadPath = Path("$targetPath/v$version")

    val binPath = Path("$downloadPath/$targetName/bun").toAbsolutePath()
    val file = binPath.toFile()

    println("Checking for bun at: '$binPath'")

    val bunPath =
        if (!file.exists()) {
            println("Bun Not found, downloading...")
            val zipContent = downloadBun(tag, os, arch, requestBaseline)
            val zip = ZipInputStream(ByteArrayInputStream(zipContent))
            val path = unzip(downloadPath, zip)
            "$path/bun"
        } else {
            println("Bun is alredy there!")
            file.absolutePath
        }

    return bunPath
}

data class Bun(val path: String)

fun Bun.transpile(loader: String, code: String): Pair<String, String> {
    val resource = Resources.getResource("index.js")
    val indexFile = Path.of(resource.toURI())
    val transpileProcess =
        ProcessBuilder(this.path, indexFile.toAbsolutePath().toString(), loader, code)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .start()
    transpileProcess.waitFor()
    val resulting = transpileProcess.inputStream.readAllBytes().decodeToString()
    val resultingErrors = transpileProcess.errorStream.readAllBytes().decodeToString()

    return Pair(resulting, resultingErrors)
}

fun Bun.transpile(file: File): Pair<String, String> {
    val loader = file.extension.lowercase()
    val isValidFile = loader.matches(Regex("^(js|ts|jsx|tsx)$"))
    if (!isValidFile) {
        return Pair("", "The '${loader}' is not a valid file extension.")
    }
    val content = file.readText()
    return this.transpile(loader, content)
}

