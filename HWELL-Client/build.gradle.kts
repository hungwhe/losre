plugins { id("fabric-loom") version "1.14-SNAPSHOT" }

version = property("mod_version") as String
group = property("maven_group") as String
base { archivesName.set(property("archives_base_name") as String) }

repositories { maven("https://maven.fabricmc.net/"); mavenCentral() }

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)); withSourcesJar() }
tasks.withType<JavaCompile>().configureEach { options.release.set(21); options.encoding = "UTF-8" }
tasks.processResources { inputs.property("version", project.version); filesMatching("fabric.mod.json") { expand("version" to project.version) } }
