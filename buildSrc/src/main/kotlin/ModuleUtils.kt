import org.gradle.api.Project
import java.io.File

fun Project.versionProperty(name: String): String = providers.gradleProperty(name).get()

fun Project.loaderVersion(moduleName: String = project.name): String {
    @Suppress("UNCHECKED_CAST")
    val versions = rootProject.extensions.extraProperties["loaderVersions"] as Map<String, String>
    return versions[moduleName] ?: error("Unknown loader module: $moduleName")
}

fun getLoaderModuleName(projectName: String): String {
    val minecraftFamily = projectName.substringBefore("-")
        .ifEmpty { error("Could not determine Minecraft family from: $projectName") }

    return when {
        projectName.contains("fabric") -> "fabric-core"
        projectName.contains("neoforge") -> when (minecraftFamily) {
            "1.21.8", "1.21.5", "1.21.4", "1.21.1" -> "neoforge-fml4"
            "1.21.10", "1.21.11" -> "neoforge-fml10"
            "26.1", "26.2", "26.3" -> "neoforge-fml11"
            else -> error("Unknown neoforge loader module for Minecraft family: $minecraftFamily")
        }
        projectName.contains("forge") -> "forge-fml47"
        else -> error("Unknown loader type")
    }
}

fun getAllDependentLoaderModules(name: String): List<String> {
    val loaderModule = getLoaderModuleName(name)
    val list = mutableListOf("core", "loader-core", "loader-$loaderModule")
    if (loaderModule == "fabric-core") { // Special case for fabric.
        list.add("loader-fabric-15")
        list.add("loader-fabric-16")
    }
    return list
}

// Stale jars from earlier versions stay in build/libs, so select by the current version instead
// of taking whatever happens to be there.
fun getMergedJarPath(buildDirLibs: File, version: String): File {
    val jars = buildDirLibs.listFiles()
        ?.filter { file -> file.isFile && !file.name.endsWith("-sources.jar") && file.name.endsWith("-$version.jar") }
        .orEmpty()

    return jars.singleOrNull()
        ?: error(
            "Expected exactly one $version jar to merge in ${buildDirLibs.absolutePath}, " +
                "found: ${jars.joinToString(", ") { it.name }.ifEmpty { "none" }}",
        )
}
