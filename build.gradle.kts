import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java-library`
    `maven-publish`
    id("com.gradleup.shadow") version "9.6.1"
}

group = "net.frankheijden.serverutils"
val dependencyDir = "${group}.dependencies"
version = "3.5.5-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")
    apply(plugin = "checkstyle")
    apply(plugin = "com.gradleup.shadow")

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://libraries.minecraft.net")
    }

    dependencies {
        implementation("cloud.commandframework:cloud-core:${VersionConstants.cloudVersion}")
        implementation("cloud.commandframework:cloud-brigadier:${VersionConstants.cloudVersion}")
        implementation("com.github.FrankHeijden:MinecraftReflection:1.0.0")
        implementation("com.google.code.gson:gson:2.14.0")
        compileOnly("com.mojang:brigadier:1.3.10")
        compileOnly("org.projectlombok:lombok:1.18.48")
        annotationProcessor("org.projectlombok:lombok:1.18.48")
        testCompileOnly("org.projectlombok:lombok:1.18.48")
        testAnnotationProcessor("org.projectlombok:lombok:1.18.48")

        testImplementation("org.assertj:assertj-core:3.27.7")
        testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
        testImplementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
        testImplementation("org.junit.jupiter:junit-jupiter-engine:5.11.4")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    }

    tasks {
        build {
            dependsOn("shadowJar", "checkstyleMain", "checkstyleTest", "test")
        }

        compileJava {
            options.encoding = Charsets.UTF_8.name()
            options.isDeprecation = true
        }

        javadoc {
            options.encoding = Charsets.UTF_8.name()
        }

        processResources {
            filteringCharset = Charsets.UTF_8.name()
        }

        test {
            useJUnitPlatform()
        }
    }

    tasks.withType<Checkstyle>().configureEach {
        configFile = file("${rootDir}/config/checkstyle/checkstyle.xml")
        ignoreFailures = false
        maxErrors = 0
        maxWarnings = 0
    }

    tasks.withType<ShadowJar> {
        exclude("com/mojang/**")
        exclude("javax/annotation/**")
        exclude("org/checkerframework/**")
        relocate("com.google.gson", "${dependencyDir}.gson")
        relocate("dev.frankheijden.minecraftreflection", "${dependencyDir}.minecraftreflection")
        relocate("cloud.commandframework", "${dependencyDir}.cloud")
        relocate("io.leangen.geantyref", "${dependencyDir}.typetoken")
        if (project.name != "Velocity") {
            relocate("net.kyori.adventure", "${dependencyDir}.adventure")
            relocate("net.kyori.examination", "${dependencyDir}.examination")
        }
        relocate("net.kyori.adventure.text.minimessage", "${dependencyDir}.adventure.text.minimessage")
    }

    publishing {
        repositories {
            maven {
                name = "fvdh"
                url = if (rootProject.version.toString().endsWith("-SNAPSHOT")) {
                    uri("https://repo.fvdh.dev/snapshots")
                } else {
                    uri("https://repo.fvdh.dev/releases")
                }

                credentials {
                    username = System.getenv("FVDH_USERNAME")
                    password = System.getenv("FVDH_TOKEN")
                }
            }
        }

        publications {
            create<MavenPublication>("ServerUtils") {
                artifact(tasks["shadowJar"]) {
                    classifier = ""
                }
                artifactId = "ServerUtils-$artifactId"
            }
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":Common", "shadow"))
    implementation(project(":Velocity", "shadow"))
    implementation("net.kyori:adventure-text-serializer-gson:${VersionConstants.adventureVersion}") {
        exclude("net.kyori", "adventure-api")
        exclude("com.google.code.gson", "gson")
    }
}

tasks {
    clean {
        dependsOn("cleanJars")
    }

    build {
        dependsOn("shadowJar", "copyJars")
    }
}

tasks.withType<ShadowJar> {
    relocate("net.kyori.adventure.text.serializer.gson", "${dependencyDir}.impl.adventure.text.serializer.gson")
}

fun outputTasks(): List<Task> {
    return listOf(
        ":Velocity:shadowJar",
    ).map { tasks.findByPath(it)!! }
}

tasks.register("cleanJars") {
    delete(file("jars"))
}

tasks.register<Copy>("copyJars") {
    outputTasks().forEach {
        from(it) {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
    }
    into(file("jars"))
    rename("(.*)-all.jar", "$1.jar")
}

publishing {
    repositories {
        maven {
            name = "fvdh"
            url = if (version.toString().endsWith("-SNAPSHOT")) {
                uri("https://repo.fvdh.dev/snapshots")
            } else {
                uri("https://repo.fvdh.dev/releases")
            }

            credentials {
                username = System.getenv("FVDH_USERNAME")
                password = System.getenv("FVDH_TOKEN")
            }
        }
    }

    publications {
        create<MavenPublication>("ServerUtils") {
            artifact(tasks["shadowJar"]) {
                classifier = ""
            }
            artifactId = "ServerUtils"
        }
    }
}
