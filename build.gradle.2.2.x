import java.util.concurrent.TimeUnit

buildscript {
    dependencies {
        classpath "org.springframework.boot:spring-boot-gradle-plugin:2.2.13.RELEASE"
    }
}

plugins {
    id 'idea'
    // com.vanniktech.maven.publish 0.30.0 requires Gradle 8.5+, disabled for 2.3.12 test
    // id 'com.vanniktech.maven.publish' version '0.30.0' apply false
}

// 全局版本控制配置
gradle.ext {
    dependencies = [
            groupConstraints   : [
                    [group: 'com.google.zxing', version: '3.4.1'],
                    [group: 'org.springframework.security', version: "5.4.6"]
            ] as ArrayList<Properties>,
            artifactConstraints: [
                    [group: 'org.springframework.security', name: 'spring-security-oauth2-authorization-server', version: "0.4.1"],
                    [group: 'org.springframework.security', name: 'spring-security-rsa', version: '1.0.11.RELEASE'],
                    [group: 'org.reflections', name: 'reflections', version: '0.9.12'],
                    [group: 'org.apache.commons', name: 'commons-text', version: '1.9'],
                    [group: 'com.google.guava', name: 'guava', version: '30.1.1-jre'],
                    [group: 'mysql', name: 'mysql-connector-java', version: '8.0.32'],
                    [group: 'io.swagger', name: 'swagger-annotations', version: '1.6.12'],
                    [group: "commons-beanutils", name: "commons-beanutils", version: "1.9.4"],
                    [group: 'com.google.protobuf', name: 'protobuf-java', version: '3.25.1'],
                    [group: 'com.google.protobuf', name: 'protobuf-java-util', version: '3.25.1'],
                    [group: 'org.xerial.snappy', name: 'snappy-java', version: '1.1.10.5'],
                    [group: 'org.apache.httpcomponents', name: 'httpclient', version: '4.5.13'],
                    [group: 'org.apache.skywalking', name: 'apm-toolkit-log4j-2.x', version: '8.16.0'],
                    [group: 'org.apache.skywalking', name: 'apm-toolkit-trace', version: '8.16.0'],
                    [group: 'com.fasterxml.jackson.core', name: 'jackson-databind', version: '2.15.2'],
                    [group: 'com.fasterxml.jackson.core', name: 'jackson-core', version: '2.15.2'],
                    [group: 'com.fasterxml.jackson.core', name: 'jackson-annotations', version: '2.15.2'],
                    [group: 'com.fasterxml.jackson.datatype', name: 'jackson-datatype-jsr310', version: '2.15.2']
            ] as ArrayList<Properties>
    ]

    memberMavenGroups = ['io.github.sure-zzzzzz']
}

idea {
    module {
        downloadSources = true
    }
}

subprojects {
    def hasSrcDir = file("${projectDir}/src").exists()

    if (!hasSrcDir) {
        logger.lifecycle("⊗ 跳过中间目录项目 ${project.path}")
        return
    }

    apply plugin: 'java-library'
    apply plugin: 'org.springframework.boot'
    apply plugin: 'io.spring.dependency-management'
    // publish plugin disabled for 2.3.12 test

    group = 'io.github.sure-zzzzzz'

    def versionFile = file("${projectDir}/version.properties")
    if (!versionFile.exists()) {
        throw new GradleException("未找到模块 '${project.path}' 的版本文件：${versionFile.absolutePath}")
    }

    def props = new Properties()
    versionFile.withInputStream { props.load(it) }
    def moduleVersion = props.getProperty('version')

    if (!moduleVersion) {
        throw new GradleException("模块 '${project.path}' 的 version.properties 中缺少 version 属性")
    }

    project.ext.moduleVersion = moduleVersion
    logger.lifecycle("✓ 模块 ${project.path} 版本: ${moduleVersion}")

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    configurations.all {
        exclude module: 'spring-boot-starter-logging'
        resolutionStrategy.cacheChangingModulesFor 0, TimeUnit.SECONDS

        resolutionStrategy {
            eachDependency { DependencyResolveDetails details ->
                def springBootGroups = ['org.springframework.boot', 'org.springframework', 'org.springframework.cloud']
                def isSpringBootRelated = springBootGroups.any { group ->
                    details.requested.group.startsWith(group)
                }

                if (!isSpringBootRelated) {
                    for (Properties item : (gradle.ext.dependencies.groupConstraints as List<Properties>)) {
                        if (details.requested.group == item.group) {
                            details.useVersion item.version.toString()
                            if (item.because) {
                                details.because item.because.toString()
                            }
                        }
                    }

                    for (Properties item : (gradle.ext.dependencies.artifactConstraints as List<Properties>)) {
                        if (details.requested.group == item.group && details.requested.name == item.name) {
                            details.useVersion item.version.toString()
                            if (item.because) {
                                details.because item.because.toString()
                            }
                        }
                    }
                }
            }
        }
    }

    dependencyManagement {
        dependencies {
            gradle.ext.dependencies.artifactConstraints.each { constraint ->
                dependency "${constraint.group}:${constraint.name}:${constraint.version}"
            }
        }
    }

    dependencies {
        compileOnly group: "org.springframework.boot", name: "spring-boot-starter-log4j2"
        compileOnly group: 'org.projectlombok', name: 'lombok'
        annotationProcessor group: 'org.projectlombok', name: 'lombok'
        annotationProcessor group: 'org.springframework.boot', name: 'spring-boot-configuration-processor'

        testImplementation 'org.springframework.boot:spring-boot-starter-web'
        testImplementation 'org.springframework.boot:spring-boot-starter-test'
        testImplementation 'org.springframework.boot:spring-boot-starter-log4j2'
        testImplementation group: 'org.projectlombok', name: 'lombok'
        testAnnotationProcessor group: 'org.projectlombok', name: 'lombok'
    }

    tasks.bootJar {
        enabled = false
    }

    tasks.jar {
        enabled = true
        archiveClassifier.set('')
    }

    tasks.javadoc {
        options.encoding = 'UTF-8'
        options.charSet = 'UTF-8'
        options.addStringOption('Xdoclint:none', '-quiet')
        failOnError = false
    }

    tasks.test {
        useJUnitPlatform()
    }
}