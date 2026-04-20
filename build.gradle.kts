import java.io.ByteArrayOutputStream

plugins {
    id("java")
    application
    id("com.diffplug.spotless") version "6.25.0"
    id("org.jetbrains.dokka") version "1.9.0"
}

group = "net.twelfthengine"
version = "1.1.3"

// ------------------------------------------------------------
// Java toolchain
// ------------------------------------------------------------
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// ------------------------------------------------------------
// Versions
// ------------------------------------------------------------
val lwjglVersion = "3.4.1"
val lwjglNatives = "natives-windows"

// ------------------------------------------------------------
// Helpers
// ------------------------------------------------------------
fun lwjgl(module: String) = "org.lwjgl:$module:$lwjglVersion"
fun lwjglNative(module: String) = "org.lwjgl:$module:$lwjglVersion:$lwjglNatives"

// ------------------------------------------------------------
// Dokka Documentation
// ------------------------------------------------------------
// Simple Dokka configuration - can be enhanced later
tasks.register("dokka") {
    doLast {
        println("Dokka documentation generation would go here")
        println("For now, run: ./gradlew dokkaHtml")
    }
}

// ------------------------------------------------------------
// Repositories
// ------------------------------------------------------------
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

// ------------------------------------------------------------
// SourceSets
// ------------------------------------------------------------
fun SourceSet.setup(name: String) {
    java.srcDirs("src/$name/java")
    resources.srcDirs("src/$name/resources")
}

sourceSets {
    main {
        setup("main")
    }

    create("api") {
        setup("api")
    }

    create("game") {
        setup("game")
    }

    named("api") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }

    named("game") {
        compileClasspath += sourceSets.main.get().output
        compileClasspath += sourceSets["api"].output

        runtimeClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets["api"].output
    }
}

// ------------------------------------------------------------
// 🔥 CRITICAL FIX (THIS WAS MISSING)
// ------------------------------------------------------------

// THIS is what fixes:
// - GLFW missing
// - GL imports missing
// - ImGui missing in api/game

configurations {
    named("apiImplementation") {
        extendsFrom(configurations.implementation.get())
    }
    named("gameImplementation") {
        extendsFrom(configurations.implementation.get())
    }
}

// ------------------------------------------------------------
// Dependencies
// ------------------------------------------------------------
dependencies {

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("org.joml:joml:1.10.5")
    implementation("com.github.Vatuu:discord-rpc:1.6.2")
    implementation("org.luaj:luaj-jse:3.0.1")

    implementation("io.github.spair:imgui-java-binding:1.86.11")
    implementation("io.github.spair:imgui-java-lwjgl3:1.86.11")
    runtimeOnly("io.github.spair:imgui-java-natives-windows:1.86.11")

    implementation("org.graalvm.sdk:graal-sdk:24.1.1")
    implementation("org.graalvm.js:js:24.1.1")
    runtimeOnly("org.graalvm.js:js:24.1.1")

    implementation("io.github.classgraph:classgraph:4.8.179")

    dokkaPlugin("org.jetbrains.dokka:javadoc-plugin:1.9.0")

    val lwjglModules = listOf(
        "lwjgl",
        "lwjgl-glfw",
        "lwjgl-opengl",
        "lwjgl-stb"
    )

    lwjglModules.forEach { lib ->
        implementation(lwjgl(lib))
        runtimeOnly(lwjglNative(lib))
    }

    implementation("org.bytedeco:javacv:1.5.8")
    implementation("org.bytedeco:javacpp:1.5.8")
    implementation("org.bytedeco:javacpp:1.5.8:windows-x86_64")
    implementation("org.bytedeco:ffmpeg:5.1.2-1.5.8:windows-x86_64")
}

// ------------------------------------------------------------
// Application
// ------------------------------------------------------------
application {
    mainClass.set("com.mygame.MyGame")
}

// ------------------------------------------------------------
// Run configuration
// ------------------------------------------------------------
tasks.named<JavaExec>("run") {
    dependsOn("apiClasses", "gameClasses")

    classpath =
        sourceSets.main.get().runtimeClasspath +
                sourceSets["api"].output +
                sourceSets["game"].output

    mainClass.set("com.mygame.MyGame")
}

tasks.named("build") {
    doFirst {
        print("Made by bob...\n")
        print("Not actually :o")
    }
}

// Global JVM args
tasks.withType<JavaExec>().configureEach {
    jvmArgs("-Djava.library.path=./natives")
}

// ------------------------------------------------------------
// Tests
// ------------------------------------------------------------
tasks.test {
    useJUnitPlatform()
}

// ------------------------------------------------------------
// Resources
// ------------------------------------------------------------
tasks.named<ProcessResources>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

// ------------------------------------------------------------
// JAR
// ------------------------------------------------------------
tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.mygame.MyGame"
    }
}

// ------------------------------------------------------------
// Fat JAR
// ------------------------------------------------------------
tasks.register<Jar>("fatJar") {
    archiveClassifier.set("fat")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "com.mygame.MyGame"
    }

    from(sourceSets.main.get().output)
    from(sourceSets["api"].output)
    from(sourceSets["game"].output)

    val runtimeJars = configurations.runtimeClasspath.get()
        .filter { it.name.endsWith("jar") }

    from(runtimeJars.map { zipTree(it) })
}

// ------------------------------------------------------------
// jlink task
// ------------------------------------------------------------
tasks.register<Exec>("jlinkBuild") {
    dependsOn("build", "cleanRuntime")

    val outputDir = file("build/runtime")
    val javaHome = File(System.getProperty("java.home"))

    commandLine(
        "${javaHome}/bin/jlink.exe",
        "--module-path", "${javaHome}/jmods",
        "--add-modules",
        "java.base,java.desktop,java.logging,java.xml,java.management,jdk.unsupported",
        "--output", outputDir.absolutePath,
        "--strip-debug",
        "--compress=2",
        "--no-header-files",
        "--no-man-pages"
    )
}

// ------------------------------------------------------------
// jpackage task
// ------------------------------------------------------------
tasks.register<Exec>("packageApp") {
    dependsOn("jlinkBuild", "fatJar")

    val runtimeDir = file("build/runtime")
    val javaHome = File(System.getProperty("java.home"))
    val jpackage = "${javaHome}/bin/jpackage.exe"

    val jarName = tasks.named<Jar>("fatJar").get().archiveFileName.get()

    doFirst {
        if (!File(jpackage).exists()) {
            throw GradleException("jpackage not found: $jpackage")
        }
    }

    commandLine(
        jpackage,
        "--type", "app-image",
        "--input", "build/libs",
        "--name", "TwelfthEngine",
        "--main-jar", jarName,
        "--main-class", "com.mygame.MyGame",
        "--runtime-image", runtimeDir.absolutePath,
        "--icon", "src/main/resources/app-icon.ico",
        "--java-options", "--enable-native-access=ALL-UNNAMED"
    )
}

// ------------------------------------------------------------
// Git Status task (FINAL FINAL version)
// ------------------------------------------------------------
// ------------------------------------------------------------
// Git helpers (Exec tasks)
// ------------------------------------------------------------
val gitPorcelain = tasks.register<Exec>("gitPorcelain") {

    val output = ByteArrayOutputStream()

    commandLine("git", "status", "--porcelain")

    standardOutput = output

    doLast {
        file("$buildDir/git_porcelain.txt").writeText(output.toString())
    }
}

val gitIgnored = tasks.register<Exec>("gitIgnored") {

    val output = ByteArrayOutputStream()

    commandLine("git", "ls-files", "--others", "-i", "--exclude-standard")

    standardOutput = output

    doLast {
        file("$buildDir/git_ignored.txt").writeText(output.toString())
    }
}

// ------------------------------------------------------------
// Pretty Status task
// ------------------------------------------------------------
tasks.register("Status") {

    group = "git"
    description = "Prints simplified git status"
    dependsOn(gitPorcelain, gitIgnored)

    doLast {
        val porcelainFile = layout.buildDirectory.file("git_porcelain.txt").get().asFile
        val ignoredFile = layout.buildDirectory.file("git_ignored.txt").get().asFile

        val printed = mutableSetOf<String>()
        fun name(path: String) = path.substringAfterLast("/").substringAfterLast("\\")

        porcelainFile.readLines().forEach { line ->
            if (line.isBlank()) return@forEach
            val code = line.substring(0, 2).trim()
            val file = name(line.substring(3).trim())

            val label = when {
                code.startsWith("M") -> "[ MODIFIED ]"
                code.startsWith("A") -> "[ NEW ]"
                code == "??"         -> "[ NEW ]"
                code.startsWith("D") -> "[ DELETED ]"
                else -> null
            }

            if (label != null && printed.add(file)) {
                println("$label $file")
            }
        }

        ignoredFile.readLines().forEach { path ->
            val file = name(path)
            if (printed.add(file)) {
                //println("[ IGNORED ] $file")
            }
        }

        if (printed.isEmpty()) {
            println("Working tree clean ✔")
        }
    }
}

// ------------------------------------------------------------
// Clean extra build artifacts
// ------------------------------------------------------------
tasks.register<Exec>("packageClean") {
    commandLine("rm", "-rf", "TwelfthEngine")
}

tasks.register("cleanRuntime") {
    doLast {
        file("build/runtime").deleteRecursively()
    }
}

// ------------------------------------------------------------
// Spotless
// ------------------------------------------------------------
spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat("1.17.0")
    }
}

// ------------------------------------------------------------
// Dokka Documentation
// ------------------------------------------------------------
tasks.dokkaHtml.configure {

    dokkaSourceSets {

        register("main") {
            displayName.set("Main")
            sourceRoots.from(file("src/main/java"))
            //classpath.from(fileTree("build/classes/java/main"))
            reportUndocumented.set(true)
            skipEmptyPackages.set(false)
        }

        register("api") {
            displayName.set("API")
            sourceRoots.from(file("src/api/java"))
            //classpath.from(fileTree("build/classes/java/api"))
        }

        register("game") {
            displayName.set("Game")
            sourceRoots.from(file("src/game/java"))
            //classpath.from(fileTree("build/classes/java/game"))
        }
    }
}
