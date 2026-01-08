package dev.gertjanassies.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Custom serializer for List<DayOfWeek> that encodes/decodes as comma-separated string.
 * Example: "MO,WE,FR" <-> listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
 */
object DayOfWeekListSerializer : KSerializer<List<DayOfWeek>> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("DayOfWeekList", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: List<DayOfWeek>) {
        val str = value.joinToString(",") { it.code }
        encoder.encodeString(str)
    }

    override fun deserialize(decoder: Decoder): List<DayOfWeek> {
        val str = decoder.decodeString()
        if (str.isBlank()) return emptyList()
        return str.split(",")
            .map { it.trim() }
            .mapNotNull { DayOfWeek.fromCode(it) }
    }
}
