plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "GatoRelay"

include(
    ":Protocol:bedrock-codec",
    ":Protocol:bedrock-connection",
    ":Protocol:common",
    ":Network:codec-query",
    ":Network:codec-rcon",
    ":Network:transport-raknet",
)