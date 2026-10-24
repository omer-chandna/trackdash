import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

// Kept separate from the application build so attribution has one review point.
val packagedNotices = layout.buildDirectory.dir("generated/openSourceAssets")
val runtimeArtifacts = providers.provider {
    configurations.getByName("releaseRuntimeClasspath").resolvedConfiguration.resolvedArtifacts
        .filter { it.id.componentIdentifier is org.gradle.api.artifacts.component.ModuleComponentIdentifier }
}
val prepareOpenSourceNotices = tasks.register<Sync>("prepareOpenSourceNotices") {
    group = "verification"
    description = "Packages project and third-party license texts with every APK."
    into(packagedNotices.map { it.dir("opensource") })
    from(rootProject.file("LICENSE"), rootProject.file("NOTICE"), rootProject.file("THIRD_PARTY_NOTICES.md"))
    from(rootProject.file("licenses"))
    inputs.files(runtimeArtifacts.map { artifacts -> artifacts.map { it.file } })
    doLast {
        val embeddedDirectory = packagedNotices.get().dir("opensource/embedded").asFile
        runtimeArtifacts.get().forEach { artifact ->
            val component = artifact.moduleVersion.id.toString().replace(':', '_')
            fun collectNotices(bytes: ByteArray, prefix: String = "") {
                ZipInputStream(ByteArrayInputStream(bytes)).use { archive ->
                    while (true) {
                        val entry = archive.nextEntry ?: break
                        if (entry.isDirectory) continue
                        val leaf = entry.name.substringAfterLast('/').uppercase()
                        if (leaf.startsWith("LICENSE") || leaf.startsWith("NOTICE") || leaf.startsWith("COPYING")) {
                            val text = archive.readNBytes(1_048_577)
                            check(text.size <= 1_048_576) { "Oversized attribution in $component" }
                            // Flatten names to prevent archive path traversal. Keep original
                            // archive entry in a header for provenance; never execute content.
                            val safeName = (prefix + entry.name).replace(Regex("[^A-Za-z0-9._-]"), "_")
                            val target = embeddedDirectory.resolve(component).resolve(safeName)
                            target.parentFile.mkdirs()
                            target.writeBytes(text)
                        } else if (entry.name == "classes.jar") {
                            collectNotices(archive.readAllBytes(), "classes.jar_")
                        }
                    }
                }
            }
            if (artifact.extension in setOf("aar", "jar")) collectNotices(artifact.file.readBytes())
        }
    }
}

tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) dependsOn(prepareOpenSourceNotices)
    if (name.contains("Lint", ignoreCase = true)) dependsOn(prepareOpenSourceNotices)
}

// A changed runtime graph requires an explicit attribution review, not a guess
// that every transitive library has the same license as its direct dependency.
tasks.register("verifyOpenSourceInventory") {
    group = "verification"
    description = "Checks the reviewed release-runtime dependency inventory."
    inputs.file(rootProject.file("licenses/runtime-dependencies.txt"))
    doLast {
        val actual = configurations.getByName("releaseRuntimeClasspath").resolvedConfiguration
            .resolvedArtifacts.filter { it.id.componentIdentifier is org.gradle.api.artifacts.component.ModuleComponentIdentifier }
            .map { it.moduleVersion.id.toString() }.distinct().sorted()
        val reviewed = rootProject.file("licenses/runtime-dependencies.txt").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.sorted()
        check(actual == reviewed) {
            "Runtime dependency attribution needs review. Added: ${actual - reviewed.toSet()}; " +
                "removed: ${reviewed - actual.toSet()}. See THIRD_PARTY_NOTICES.md."
        }
        logger.lifecycle("Verified ${actual.size} reviewed runtime dependency coordinates.")
    }
}

tasks.register("reportOpenSourceRuntimeArtifacts") {
    group = "help"
    description = "Prints resolved runtime coordinates and artifact paths for attribution review."
    doLast {
        configurations.getByName("releaseRuntimeClasspath").resolvedConfiguration.resolvedArtifacts
            .filter { it.id.componentIdentifier is org.gradle.api.artifacts.component.ModuleComponentIdentifier }
            .sortedBy { it.moduleVersion.id.toString() }.forEach {
                logger.lifecycle("ATTRIBUTION ${it.moduleVersion.id}|${it.file.absolutePath}")
            }
    }
}
