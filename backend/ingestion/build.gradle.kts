plugins { java; alias(libs.plugins.shadow) }

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

dependencies {
    implementation(libs.lambda.java.core)
    implementation(libs.lambda.java.events)
    implementation(libs.aws.dynamodb)
    implementation(libs.aws.bedrock.runtime)
    implementation(libs.aws.s3vectors)
    implementation(libs.aws.appconfig.data)
    implementation(libs.jsoup)
    implementation(libs.jackson.databind)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation("org.assertj:assertj-core:3.26.0")
}

tasks.shadowJar { archiveClassifier = ""; mergeServiceFiles() }
