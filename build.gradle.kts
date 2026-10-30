plugins {
    id("io.papermc.paperweight.userdev").version("2.0.0-beta.21")
    id("maven-publish")
}

group = "fun.lofe"
version = "${property("version")}+${property("game_version")}"
val gameVersion = property("game_version")

allprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()

        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc"
        }
    }

    dependencies {
        compileOnly("io.papermc.paper:paper-api:${gameVersion}.build.+")
    }
}

dependencies {
    paperweight.paperDevBundle("${gameVersion}.build.+")
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

extensions.configure<PublishingExtension> {
    repositories {
        maven {
            name = "modoruReleases"
            url = uri("https://repository.modoru.fun/releases")

            credentials {
                username = System.getenv("MODORU_USERNAME") ?: ""
                password = System.getenv("MODORU_TOKEN") ?: ""
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(sourcesJar.get())

            group = project.group
            artifactId = "stop-spying-on-your-players-messages"
            version = project.version.toString()
        }
    }
}