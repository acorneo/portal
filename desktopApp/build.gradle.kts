import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.ktor.serialization.json)
    implementation(libs.compose.material3)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)

    testImplementation(libs.kotlin.testJunit)
}

compose.desktop {
    application {
        mainClass = "de.acorn42.portal.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "de.acorn42.portal"
            packageVersion = "1.0.0"
            appResourcesRootDir.set(layout.buildDirectory.dir("nearby-resources"))
            macOS {
                minimumSystemVersion = "13.0"
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSBonjourServices</key>
                        <array><string>_portalnear._tcp</string></array>
                        <key>NSLocalNetworkUsageDescription</key>
                        <string>PORTAL discovers and connects to a nearby iPhone over peer-to-peer Wi-Fi for the connection POC and hosts the LAN portal demo.</string>
                    """.trimIndent()
                }
            }
        }
    }
}

// One local Swift package, no external native bridge dependency. Native resources
// are also used by Compose's development run, so both launch paths agree.
val nearbyNative = layout.buildDirectory.dir("nearby-native")
val buildNearbyHelper by tasks.registering(Exec::class) {
    onlyIf { System.getProperty("os.name").startsWith("Mac") }
    inputs.dir(rootProject.layout.projectDirectory.dir("native/nearby/Sources"))
    inputs.file(rootProject.layout.projectDirectory.file("native/nearby/Package.swift"))
    inputs.file(rootProject.layout.projectDirectory.file("native/nearby/HelperInfo.plist"))
    outputs.file(nearbyNative.map { it.file("release/PortalNearbyHelper") })
    commandLine("/usr/bin/xcrun", "swift", "build", "--package-path", rootProject.file("native/nearby").absolutePath,
        "--scratch-path", nearbyNative.get().asFile.absolutePath, "--configuration", "release", "--product", "PortalNearbyHelper")
}
val prepareNearbyHelper by tasks.registering(Copy::class) {
    dependsOn(buildNearbyHelper)
    onlyIf { System.getProperty("os.name").startsWith("Mac") }
    from(nearbyNative.map { it.file("release/PortalNearbyHelper") })
    into(layout.buildDirectory.dir("nearby-resources/macos/nearby"))
    filePermissions { unix("rwxr-xr-x") }
}
tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(prepareNearbyHelper) }
tasks.withType<Test>().configureEach {
    dependsOn(prepareNearbyHelper)
    systemProperty("portal.nearby.helper", layout.buildDirectory.file("nearby-resources/macos/nearby/PortalNearbyHelper").get().asFile.absolutePath)
}

// Compose resource copies normalize mode bits. Restore the helper at both the
// development-resource and final bundle stages; never chmod the installed app at runtime.
tasks.matching { it.name == "prepareAppResources" || it.name == "createDistributable" }.configureEach {
    doLast {
        outputs.files.asFileTree.matching { include("**/PortalNearbyHelper") }.files.forEach { helper ->
            check(helper.setExecutable(true, false) && helper.canExecute()) { "Nearby helper must be executable: $helper" }
        }
    }
}
