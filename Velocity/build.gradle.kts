import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

group = "${rootProject.group}"
val dependencyDir = "${group}.velocity.dependencies"
version = rootProject.version
java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}
base {
    archivesName.set("${rootProject.name}-Velocity")
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://libraries.minecraft.net")
}

dependencies {
    implementation("cloud.commandframework:cloud-velocity:${VersionConstants.cloudVersion}")
    implementation("org.bstats:bstats-velocity:${VersionConstants.bstatsVersion}")
    implementation(project(":Common"))
    implementation("net.kyori:adventure-text-minimessage:${VersionConstants.adventureMinimessageVersion}") {
        exclude("net.kyori", "adventure-api")
    }
    implementation("net.kyori:examination-api:1.3.0")
    implementation("net.kyori:examination-string:1.3.0")
    compileOnly("com.velocitypowered:velocity-api:4.2.0")
    compileOnly("com.velocitypowered:velocity-brigadier:1.0.0-SNAPSHOT")
    compileOnly("com.electronwill.night-config:toml:3.9.0")
    annotationProcessor("com.velocitypowered:velocity-api:4.2.0")
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("")
    relocate("org.bstats", "${dependencyDir}.bstats")
}

tasks.jar {
    enabled = false
}
