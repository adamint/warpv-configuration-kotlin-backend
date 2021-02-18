package com.adamratzman

import com.adamratzman.MacroType.M4_DEFINE
import com.adamratzman.MacroType.M4_DEFINE_HIER
import com.adamratzman.ParameterInputType.BINARY
import com.adamratzman.ParameterInputType.BOOLEAN
import com.adamratzman.ParameterInputType.DOUBLE
import com.adamratzman.ParameterInputType.INTEGER
import com.adamratzman.ParameterInputType.STRING
import com.adamratzman.ParameterValue.AbstractParameterValueSerializer
import com.adamratzman.ParameterValue.ParameterBinaryValue
import com.adamratzman.ParameterValue.ParameterBinaryValue.ParameterBinaryValueSerializer
import com.adamratzman.ParameterValue.ParameterBooleanValue
import com.adamratzman.ParameterValue.ParameterBooleanValue.ParameterBooleanValueSerializer
import com.adamratzman.ParameterValue.ParameterDoubleValue.ParameterDoubleValueSerializer
import com.adamratzman.ParameterValue.ParameterIntegerValue
import com.adamratzman.ParameterValue.ParameterIntegerValue.ParameterIntegerValueSerializer
import com.adamratzman.ParameterValue.ParameterStringValue
import com.adamratzman.ParameterValue.ParameterStringValue.ParameterStringValueSerializer
import com.adamratzman.ValidationType.GREATER_THAN
import com.adamratzman.ValidationType.IN
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class ConfigurationParameter(
    val macroType: MacroType,
    val readableName: String,
    val verilogName: String,
    val defaultValue: ParameterValue,
    val jsonKey: String,
    val acceptType: AcceptsType,
    val configurationCategory: ConfigurationCategory,
    val description: String? = null,
    @Transient val outputMapper: OutputMapper? = null // if no change is required
)

val ConfigurationParameters = listOf(
    ConfigurationParameter(
        M4_DEFINE_HIER,
        "CPU cores",
        "M4_CORE",
        ParameterIntegerValue(2),
        "cores",
        AcceptsType(
            INTEGER,
            Validation(GREATER_THAN, number = -1)
        ) { value -> value.toIntOrNull() != null && value.toInt() >= 0 },
        ConfigurationCategory.CPU,
        description = "Number of cores"
    ),
    ConfigurationParameter(
        M4_DEFINE_HIER,
        "VCs",
        "M4_VC",
        ParameterIntegerValue(2),
        "vcs",
        AcceptsType(
            INTEGER,
            Validation(GREATER_THAN, number = -1)
        ) { value -> value.toIntOrNull() != null && value.toInt() >= 0 },
        ConfigurationCategory.CPU,
        description = "VCs (meaningful if > 1 core)"
    ),
    ConfigurationParameter(
        M4_DEFINE_HIER,
        "Priority levels",
        "M4_PRIO",
        ParameterIntegerValue(2),
        "prios",
        AcceptsType(
            INTEGER,
            Validation(GREATER_THAN, number = -1)
        ) { value -> value.toIntOrNull() != null && value.toInt() >= 0 },
        ConfigurationCategory.CPU,
        description = "Number of priority levels in the NoC"
    ),
    ConfigurationParameter(
        M4_DEFINE,
        "Max Packet Size",
        "M4_MAX_PACKET_SIZE",
        ParameterIntegerValue(3),
        "max_packet_size",
        AcceptsType(
            INTEGER,
            Validation(GREATER_THAN, number = -1)
        ) { value -> value.toIntOrNull() != null && value.toInt() >= 0 },
        ConfigurationCategory.CPU,
        description = "Max number of payload flits in a packet"
    ),
    ConfigurationParameter(
        M4_DEFINE,
        "Implementation vs Simulation - true for implementation",
        "M4_IMPL",
        ParameterBooleanValue(true),
        "impl",
        AcceptsType(BOOLEAN, Validation.isTrueOrFalse) { value -> value.toLowerCase() in listOf("true", "false") },
        ConfigurationCategory.CPU,
        description = "For implementation (vs. simulation)",
    ),
    ConfigurationParameter(
        M4_DEFINE,
        "Soft reset",
        "m4_soft_reset",
        ParameterBooleanValue(true),
        "soft_reset",
        AcceptsType(BOOLEAN, Validation.isTrueOrFalse) { value -> value.toLowerCase() in listOf("true", "false") },
        ConfigurationCategory.CPU,
        description = "A hook for a software-controlled reset. None by default",
        outputMapper = OutputMapper { parameterValue ->
            parameterValue as ParameterBooleanValue
            if (parameterValue.boolean) ParameterBinaryValue(1, parameterValue.value)
            else ParameterBinaryValue(1, parameterValue.value)
        }
    ),
    ConfigurationParameter(
        M4_DEFINE,
        "Alignment of load return pseudo-instruction into |mem pipeline",
        "M4_LD_RETURN_ALIGN",
        ParameterBooleanValue(true),
        "ld_return_align",
        AcceptsType(BOOLEAN, Validation.isTrueOrFalse) { value -> value.toLowerCase() in listOf("true", "false") },
        ConfigurationCategory.CPU,
        description = "If |mem stages reflect nominal alignment w/ load instruction, this is the nominal load latency.",
        outputMapper = OutputMapper { parameterValue ->
            parameterValue as ParameterBooleanValue
            if (parameterValue.boolean) ParameterBinaryValue(1, parameterValue.value)
            else ParameterBinaryValue(1, parameterValue.value)
        }
    ),
    ConfigurationParameter(
        M4_DEFINE,
        "CPU Blocked?",
        "m4_cpu_blocked",
        ParameterBooleanValue(false),
        "cpu_blocked",
        AcceptsType(BOOLEAN, Validation.isTrueOrFalse) { value -> value.toLowerCase() in listOf("true", "false") },
        ConfigurationCategory.CPU,
        description = "A hook for CPU back-pressure in M4_REG_RD_STAGE. Various sources of back-pressure can add to this expression.",
        outputMapper = OutputMapper { parameterValue ->
            parameterValue as ParameterBooleanValue
            if (parameterValue.boolean) ParameterBinaryValue(1, parameterValue.value)
            else ParameterBinaryValue(1, parameterValue.value)
        }
    ),
    ConfigurationParameter(
        M4_DEFINE,
        "Branch prediction?",
        "M4_BRANCH_PRED",
        ParameterStringValue("fallthrough"),
        "branch_pred",
        AcceptsType(STRING, Validation(IN, listOf("fallthrough", "two_bit"))) { value ->
            value in listOf(
                "fallthrough",
                "two_bit"
            )
        },
        ConfigurationCategory.CPU,
        description = "A hook for CPU back-pressure in M4_REG_RD_STAGE. Various sources of back-pressure can add to this expression. two_bit or fallthrough",
    ),

    // stages
    ConfigurationParameter(
        M4_DEFINE,
        "Instruction fetch stage",
        "M4_FETCH_STAGE",
        ParameterBooleanValue(false),
        "fetch_stage_inc",
        AcceptsType(BOOLEAN, Validation.isTrueOrFalse) { value -> value.toLowerCase() in listOf("true", "false") },
        ConfigurationCategory.STAGE
    ),
    ConfigurationParameter(
        M4_DEFINE,
        "Instruction decode stage",
        "M4_DECODE_STAGE",
        ParameterBooleanValue(false),
        "decode_stage_inc",
        AcceptsType(BOOLEAN, Validation.isTrueOrFalse) { value -> value.toLowerCase() in listOf("true", "false") },
        ConfigurationCategory.STAGE
    ),
    ConfigurationParameter(
        M4_DEFINE,
        "Branch predict",
        "M4_BRANCH_PRED_STAGE",
        ParameterBooleanValue(false),
        "branch_pred_stage_inc",
        AcceptsType(BOOLEAN, Validation.isTrueOrFalse) { value -> value.toLowerCase() in listOf("true", "false") },
        ConfigurationCategory.STAGE,
        description = "Currently, we mispredict to a known branch target, so branch prediction is only relevant if target is computed before taken/not-taken is known. For other ISAs prediction is forced to fallthrough, and there is no pred-taken redirect"
    ),
    ConfigurationParameter(
        M4_DEFINE,
        "Register file read stage",
        "M4_REG_RD_STAGE",
        ParameterBooleanValue(false),
        "reg_rd_stage_inc",
        AcceptsType(BOOLEAN, Validation.isTrueOrFalse) { value -> value.toLowerCase() in listOf("true", "false") },
        ConfigurationCategory.STAGE
    ),
    ConfigurationParameter(
        M4_DEFINE,
        "Operation execution",
        "M4_EXECUTE_STAGE",
        ParameterBooleanValue(false),
        "execute_stage_inc",
        AcceptsType(BOOLEAN, Validation.isTrueOrFalse) { value -> value.toLowerCase() in listOf("true", "false") },
        ConfigurationCategory.STAGE

    ),
    ConfigurationParameter(
        M4_DEFINE,
        "Select execution result",
        "M4_RESULT_STAGE",
        ParameterBooleanValue(false),
        "result_stage",
        AcceptsType(BOOLEAN, Validation.isTrueOrFalse) { value -> value.toLowerCase() in listOf("true", "false") },
        ConfigurationCategory.STAGE
    ),
    ConfigurationParameter(
        M4_DEFINE,
        "Register file write",
        "M4_REG_WR_STAGE",
        ParameterBooleanValue(false),
        "reg_wr_stage",
        AcceptsType(BOOLEAN, Validation.isTrueOrFalse) { value -> value.toLowerCase() in listOf("true", "false") },
        ConfigurationCategory.STAGE
    ),
    ConfigurationParameter(
        M4_DEFINE,
        "Memory write",
        "M4_MEM_WR_STAGE",
        ParameterBooleanValue(false),
        "mem_wr_stage",
        AcceptsType(BOOLEAN, Validation.isTrueOrFalse) { value -> value.toLowerCase() in listOf("true", "false") },
        ConfigurationCategory.STAGE
    )
)

enum class ParameterInputType {
    INTEGER,
    DOUBLE,
    STRING,
    BOOLEAN,
    BINARY
}

@Serializable
data class AcceptsType(
    val parameterInputType: ParameterInputType,
    val validationType: Validation,
    @Transient val validationFunction: ((String) -> Boolean)? = null
)

@Serializable
data class Validation(val type: ValidationType, val list: List<String>? = null, val number: Int? = null) {
    companion object {
        val isTrueOrFalse = Validation(IN, listOf("true", "false"))
    }
}

enum class ValidationType {
    IN,
    GREATER_THAN
}


enum class MacroType {
    M4_DEFINE,
    M4_DEFAULT,
    M4_DEFINE_HIER;

    override fun toString(): String = name.toLowerCase()
}

@Serializable(with = AbstractParameterValueSerializer::class)
sealed class ParameterValue(val parameterInputType: ParameterInputType) {
    abstract override fun toString(): String
    open fun toVerilogString(): String = toString()

    @Serializable(with = ParameterStringValueSerializer::class)
    class ParameterStringValue(val value: String) : ParameterValue(STRING) {
        override fun toString(): String = value
        override fun toVerilogString(): String = "\"$value\""

        object ParameterStringValueSerializer : KSerializer<ParameterStringValue> {
            override fun deserialize(decoder: Decoder) = ParameterStringValue(decoder.decodeString())
            override val descriptor: SerialDescriptor =
                PrimitiveSerialDescriptor("ParameterStringValue", PrimitiveKind.STRING)

            override fun serialize(encoder: Encoder, value: ParameterStringValue) = encoder.encodeString(value.value)
        }
    }

    @Serializable(with = ParameterIntegerValueSerializer::class)
    open class ParameterIntegerValue(val value: Int) : ParameterValue(INTEGER) {
        override fun toString(): String = value.toString()

        object ParameterIntegerValueSerializer : KSerializer<ParameterIntegerValue> {
            override fun deserialize(decoder: Decoder) = ParameterIntegerValue(decoder.decodeInt())
            override val descriptor: SerialDescriptor =
                PrimitiveSerialDescriptor("ParameterIntegerValue", PrimitiveKind.INT)

            override fun serialize(encoder: Encoder, value: ParameterIntegerValue) = encoder.encodeInt(value.value)
        }
    }

    @Serializable(with = ParameterBooleanValueSerializer::class)
    class ParameterBooleanValue(val boolean: Boolean) : ParameterValue(BOOLEAN) {
        val value = if (boolean) 1 else 0

        override fun toString(): String = value.toString()

        object ParameterBooleanValueSerializer : KSerializer<ParameterBooleanValue> {
            override fun deserialize(decoder: Decoder) = ParameterBooleanValue(decoder.decodeBoolean())
            override val descriptor: SerialDescriptor =
                PrimitiveSerialDescriptor("ParameterBooleanValue", PrimitiveKind.INT)

            override fun serialize(encoder: Encoder, value: ParameterBooleanValue) =
                encoder.encodeBoolean(value.value == 1)
        }
    }

    @Serializable(with = ParameterDoubleValueSerializer::class)
    class ParameterDoubleValue(val value: Double) : ParameterValue(DOUBLE) {
        override fun toString(): String = value.toString()

        object ParameterDoubleValueSerializer : KSerializer<ParameterDoubleValue> {
            override fun deserialize(decoder: Decoder) = ParameterDoubleValue(decoder.decodeDouble())
            override val descriptor: SerialDescriptor =
                PrimitiveSerialDescriptor("ParameterDoubleValue", PrimitiveKind.DOUBLE)

            override fun serialize(encoder: Encoder, value: ParameterDoubleValue) = encoder.encodeDouble(value.value)
        }
    }

    @Serializable(with = ParameterBinaryValueSerializer::class)
    class ParameterBinaryValue(val numberOfBits: Int, val number: Int) : ParameterValue(BINARY) {
        override fun toString(): String = "$numberOfBits'b$number"

        object ParameterBinaryValueSerializer : KSerializer<ParameterBinaryValue> {
            override fun deserialize(decoder: Decoder) = createBinaryValue(decoder.decodeString())
            override val descriptor: SerialDescriptor =
                PrimitiveSerialDescriptor("ParameterBinaryValue", PrimitiveKind.DOUBLE)

            override fun serialize(encoder: Encoder, value: ParameterBinaryValue) =
                encoder.encodeString(value.toString())
        }

        companion object {
            internal val regex = "\\d+'b\\d+".toRegex()
            fun createBinaryValue(input: String) = regex.matchEntire(input)?.groupValues
                ?.subList(1, 3)
                ?.map { it.toIntOrNull() }
                ?.let { if (it.filterNotNull().size != it.size) null else it.filterNotNull() }
                ?.let { ParameterBinaryValue(it[0], it[1]) }
                ?: throw IllegalArgumentException("Input not binary value")
        }
    }

    object AbstractParameterValueSerializer : KSerializer<ParameterValue> by object :
        JsonContentPolymorphicSerializer<ParameterValue>(ParameterValue::class) {
        override fun selectDeserializer(element: JsonElement): KSerializer<out ParameterValue> {
            val primitive = element.jsonPrimitive
            return when {
                primitive.intOrNull != null -> ParameterIntegerValue.serializer()
                primitive.doubleOrNull != null -> ParameterDoubleValue.serializer()
                primitive.booleanOrNull != null -> ParameterBooleanValue.serializer()
                primitive.isString && ParameterBinaryValue.regex.matches(primitive.content) -> ParameterBinaryValue.serializer()
                primitive.isString -> ParameterStringValue.serializer()
                else -> throw IllegalStateException("Couldn't find a serializer for element $element")
            }
        }

    }
}

enum class ConfigurationCategory {
    CPU,
    STAGE
}

@Serializable
data class OutputMapper(
    val mapper: (ParameterValue) -> ParameterValue
)

@Serializable
data class TLVerilogProgram(
    val lines: List<String>
)

@Serializable
data class Error(val message: String? = null)