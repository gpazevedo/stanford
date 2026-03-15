plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dep.management)
}

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation(libs.aws.dynamodb)
    implementation(libs.aws.s3vectors)
    implementation(libs.aws.bedrock.runtime)
    implementation(libs.aws.appconfig.data)
    implementation(libs.jackson.databind)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation("org.assertj:assertj-core")
}
