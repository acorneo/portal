import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

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
            macOS {
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSLocalNetworkUsageDescription</key>
                        <string>PORTAL hosts a local session so your iPhone can receive and return the sphere on the same Wi-Fi.</string>
                    """.trimIndent()
                }
            }
        }
    }
}
