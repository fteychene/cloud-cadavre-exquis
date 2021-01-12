plugins {
    kotlin("jvm") version "1.4.21" apply false
    id("com.github.johnrengelman.shadow") version "6.1.0" apply false
    id("com.google.cloud.tools.jib") version "2.7.0" apply false
}

group = "fr.fteychene.teaching.cloud.cadavreexquis"
version = "1.0-SNAPSHOT"

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.gradle.application")
    apply(plugin = "com.github.johnrengelman.shadow")
    apply(plugin = "com.google.cloud.tools.jib")

    group = parent!!.group
    version = parent!!.version

    repositories {
        mavenCentral()
        jcenter()
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "11"
            targetCompatibility = "11"
        }
    }

    extensions.configure(JavaPluginExtension::class) {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(11))
        }
    }
}