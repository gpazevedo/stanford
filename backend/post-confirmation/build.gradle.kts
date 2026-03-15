plugins { java; alias(libs.plugins.shadow) }

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

dependencies {
    implementation(libs.lambda.java.core)
    implementation(libs.lambda.java.events)
    implementation(libs.aws.cognito)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation("org.assertj:assertj-core:3.26.0")
}

tasks.test {
    jvmArgs(
        "-XX:+EnableDynamicAgentLoading",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "-Dnet.bytebuddy.experimental=true"
    )
}

tasks.shadowJar {
    archiveClassifier = ""
    mergeServiceFiles()
}
