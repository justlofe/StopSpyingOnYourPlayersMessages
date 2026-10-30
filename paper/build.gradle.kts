plugins {
    id("io.papermc.paperweight.userdev").version("2.0.0-beta.21")
    id("com.gradleup.shadow").version("9.0.0-beta4")
}

group = rootProject.group
version = rootProject.version

dependencies {
    paperweight.paperDevBundle("${rootProject.property("game_version")}.build.+")
    implementation(rootProject)
}

tasks {
    jar {
        enabled = false
    }

    shadowJar {
        archiveBaseName = rootProject.name
        archiveClassifier = "paper"
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        filesMatching("paper-plugin.yml") {
            expand(mapOf("version" to project.version))
        }
    }
}