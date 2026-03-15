plugins {
    alias(libs.plugins.spring.boot)           apply false
    alias(libs.plugins.spring.dep.management) apply false
    alias(libs.plugins.shadow)                apply false
}

subprojects {
    repositories { mavenCentral() }
    tasks.withType<JavaCompile> { options.release = 25 }
    tasks.withType<Test> { useJUnitPlatform() }
}
