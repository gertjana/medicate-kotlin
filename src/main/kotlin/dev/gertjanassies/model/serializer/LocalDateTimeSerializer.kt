package dev.gertjanassies.model.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Custom [KSerializer] for [LocalDateTime] that encodes and decodes values
 * as ISO-8601 local date-time strings using [DateTimeFormatter.ISO_LOCAL_DATE_TIME].
 *
 * This serializer represents a [LocalDateTime] as a primitive `STRING` in the
 * serialization format. It can be used either by registering it in a
 * `SerializersModule` or by annotating properties explicitly, for example:
 *
 * `@Serializable data class Example(@Serializable(with = LocalDateTimeSerializer::class) val createdAt: LocalDateTime)`
 *
 * The serialized form will look like `"2024-01-31T13:45:30"`, i.e. without a time zone
 * or offset component, matching [DateTimeFormatter.ISO_LOCAL_DATE_TIME].
 */
object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeString(value.format(formatter))
    }

    override fun deserialize(decoder: Decoder): LocalDateTime {
        return LocalDateTime.parse(decoder.decodeString(), formatter)
    }
}
