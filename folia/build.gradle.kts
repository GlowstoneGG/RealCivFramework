plugins {
    kotlin("jvm")
    id("com.gradleup.shadow")
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    implementation(project(":core"))
    testImplementation(kotlin("test"))
    testImplementation("io.papermc.paper:paper-api:26.3.build.49-alpha")
    compileOnly("io.papermc.paper:paper-api:26.3.build.49-alpha")
    // The shade artifact already contains its CommandAPI modules; its published POM also
    // references internal modules that are not available from Maven Central.
    implementation("dev.jorel:commandapi-paper-shade:12.1.0") {
        isTransitive = false
    }

    implementation("gg.glowstone.rivet:rivet-core:0.1.0")
    implementation("com.github.shynixn.mccoroutine:mccoroutine-folia-api:2.23.0")
    implementation("com.github.shynixn.mccoroutine:mccoroutine-folia-core:2.23.0")

    // Adventure is provided by Folia/Canvas. Paper 26.3 uses 5.2.0.
    compileOnly("net.kyori:adventure-api:5.2.0")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
    filteringCharset = "UTF-8"
}

tasks.shadowJar {
    archiveBaseName.set("RealCivFramework")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    relocate("dev.jorel.commandapi", "gg.glowstone.rcf.libs.commandapi")
    from(rootProject.file("LICENSE")) {
        into("META-INF")
        rename { "LICENSE-RealCivFramework" }
    }
    // Server classes must retain their own class identity.
    dependencies {
        exclude(dependency("net.kyori:.*:.*"))
        exclude(dependency("org.slf4j:slf4j-api:.*"))
    }
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
