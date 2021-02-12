package com.adamratzman

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CORS
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.serialization.SerializationConverter
import kotlinx.serialization.json.Json

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(CORS) {
        allowCredentials = true
        allowNonSimpleContentTypes = true
        method(HttpMethod.Get)
        method(HttpMethod.Post)
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        anyHost()
    }

    install(ContentNegotiation) {
        register(
            ContentType.Any,
            SerializationConverter(Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true })
        )
    }

    routing {
        get("/list-configuration-parameters") {
            call.respond(ConfigurationParameters)
        }
        post("/translate") {
            try {
                val configuredValues = call.receive<Map<String, ParameterValue?>>().map { (jsonKey, value) ->
                    val parameter = ConfigurationParameters.find { it.jsonKey == jsonKey }
                        ?: throw IllegalArgumentException("Json key $jsonKey not found")
                    if (value != null && parameter.acceptType.parameterInputType != value.parameterInputType) {
                        throw IllegalArgumentException("Parameter ${parameter.jsonKey} only accepts values of type ${parameter.acceptType.parameterInputType}")
                    }
                    parameter to value
                }

                val translatedProgram = configuredValues.map { (parameter, value) ->
                    "${parameter.macroType}(['${parameter.verilogName}'], ${
                        (parameter.outputMapper?.mapper?.invoke(
                            value ?: parameter.defaultValue
                        ) ?: (value ?: parameter.defaultValue)).toVerilogString()
                    })"
                }.let { TLVerilogProgram(it) }
                call.respond(translatedProgram)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, Error(e.message ?: e.localizedMessage))
            }
        }
    }
}

