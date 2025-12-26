import org.gradle.internal.os.OperatingSystem

plugins {
    id("java")
    id("application")
}

group = "org.aouessar"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val lwjglVersion = "3.3.6"
val gsonVersion = "2.13.2"

val lwjglNatives = when {
    OperatingSystem.current().isWindows -> "natives-windows"
    OperatingSystem.current().isMacOsX -> "natives-macos"
    else -> "natives-linux"
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))

    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-opengl")
    implementation("org.lwjgl:lwjgl-stb")

    implementation("com.google.code.gson:gson:$gsonVersion")
    implementation("org.joml:joml:1.10.8")

    // LWJGL natives (correct)
    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-stb::$lwjglNatives")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("org.aouessar.app.Main")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

// Make sure you SEE the real exception/stacktrace from your app
tasks.named<JavaExec>("run") {
    standardOutput = System.out
    errorOutput = System.err
    isIgnoreExitValue = false
}

tasks.withType<JavaExec>().configureEach {
    // Show app stdout/stderr in Gradle console
    standardOutput = System.out
    errorOutput = System.err

    // Make sure Gradle doesn’t swallow logs
    isIgnoreExitValue = false

    // Optional but helpful: force UTF-8
    systemProperty("file.encoding", "UTF-8")
}
