import com.expediagroup.graphql.plugin.gradle.tasks.GraphQLIntrospectSchemaTask

val testcontainersVersion = "1.19.3"
val graphQLKotlinVersion = "7.0.2"
val ktorVersion = "2.3.7"

plugins {
    kotlin("plugin.serialization") version "1.9.0"
    id("com.expediagroup.graphql") version "7.0.0"
}

dependencies {
    api("com.nimbusds:nimbus-jose-jwt:9.37.3")
    api("io.ktor:ktor-server-double-receive:$ktorVersion")
    implementation(project(":spesialist-felles"))
    implementation("com.expediagroup:graphql-kotlin-server:$graphQLKotlinVersion")
    implementation("com.expediagroup:graphql-kotlin-gradle-plugin:$graphQLKotlinVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("io.ktor:ktor-server-netty:$ktorVersion")
}

val graphqlDir = "${project.projectDir}/src/main/resources/graphql"

graphql {
    client {
        // hvis endpoint settes her vil spleis introspectes hver gang man bygger
        schemaFile = file("$graphqlDir/schema.graphql")
        queryFileDirectory = graphqlDir
        packageName = "no.nav.helse.spleis.graphql"
        serializer = com.expediagroup.graphql.plugin.gradle.config.GraphQLSerializer.JACKSON
    }
}

val graphqlIntrospectSchema by tasks.getting(GraphQLIntrospectSchemaTask::class) {
    endpoint.set("https://spleis-api.intern.dev.nav.no/graphql/introspection")
}

val copySchemaFile by tasks.registering(Copy::class) {
    from(graphqlIntrospectSchema.outputFile)
    into(graphqlDir)
}

tasks.graphqlIntrospectSchema {
    finalizedBy(copySchemaFile)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

