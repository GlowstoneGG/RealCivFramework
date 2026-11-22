plugins {
    kotlin("jvm") version "2.4.10" apply false
    id("com.gradleup.shadow") version "9.6.1" apply false
}

allprojects {
    group = "gg.glowstone.rcf"
    version = providers.gradleProperty("projectVersion").get()

    repositories {
        mavenCentral()
    }
}
