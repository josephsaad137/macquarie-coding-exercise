package com.example.demo.service

import com.example.demo.service.model.TabularData
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.apache.avro.generic.GenericRecord
import org.apache.parquet.avro.AvroParquetReader
import org.apache.parquet.conf.PlainParquetConfiguration
import org.apache.parquet.hadoop.ParquetReader
import org.apache.parquet.io.InputFile
import org.apache.parquet.io.SeekableInputStream

class ParquetFileReader {
    @Throws(IOException::class)
    fun <T> read(content: ByteArray, process: (TabularData) -> T): T {
        val reader = try {
            AvroParquetReader.builder<GenericRecord>(
                ByteArrayParquetInputFile(content),
                PlainParquetConfiguration(),
            ).build()
        } catch (exception: RuntimeException) {
            throw IOException("The Parquet file could not be opened", exception)
        }

        return reader.use { parquetReader ->
            val firstRecord = readRecordSafely(parquetReader)
                ?: return@use process(TabularData(headers = emptyList(), rows = emptySequence()))
            val headers = firstRecord.schema.fields.map { it.name() }
            val rows = sequence {
                yield(readRecord(firstRecord, headers))
                while (true) {
                    val record = readRecordSafely(parquetReader) ?: break
                    yield(readRecord(record, headers))
                }
            }
            process(TabularData(headers, rows))
        }
    }

    private fun readRecordSafely(reader: ParquetReader<GenericRecord>): GenericRecord? =
        try {
            reader.read()
        } catch (exception: RuntimeException) {
            throw IOException("The Parquet file contains invalid data", exception)
        }

    private fun readRecord(record: GenericRecord, headers: List<String>): List<String> =
        headers.map { fieldName ->
            val value = record.get(fieldName) ?: return@map ""
            val logicalType = record.schema.getField(fieldName).schema().logicalType?.name
            val nanosPerUnit = when {
                logicalType == "timestamp-micros" || logicalType == "local-timestamp-micros" -> 1_000L
                logicalType == "timestamp-nanos" || logicalType == "local-timestamp-nanos" -> 1L
                // This naive Chicago wall-clock field is stored as epoch nanoseconds.
                normalizeHeader(fieldName) == "timestampchicagowall" -> 1L
                else -> null
            }
            if (nanosPerUnit == null) {
                value.toString().trim()
            } else {
                value.toString().toLongOrNull()?.let { timestampUnits ->
                    val unitsPerSecond = 1_000_000_000L / nanosPerUnit
                    LocalDateTime.ofEpochSecond(
                        Math.floorDiv(timestampUnits, unitsPerSecond),
                        (Math.floorMod(timestampUnits, unitsPerSecond) * nanosPerUnit).toInt(),
                        ZoneOffset.UTC,
                    ).toString()
                } ?: value.toString().trim()
            }
        }

    private fun normalizeHeader(name: String): String =
        name.trim().lowercase().replace(Regex("[^a-z0-9]"), "")

    private class ByteArrayParquetInputFile(private val content: ByteArray) : InputFile {
        override fun getLength(): Long = content.size.toLong()

        override fun newStream(): SeekableInputStream = object : SeekableInputStream() {
            private var position = 0

            override fun getPos(): Long = position.toLong()

            override fun seek(newPosition: Long) {
                if (newPosition < 0 || newPosition > content.size) {
                    throw IOException("Invalid Parquet stream position: $newPosition")
                }
                position = newPosition.toInt()
            }

            override fun read(): Int {
                if (position >= content.size) return -1
                return content[position++].toInt() and 0xff
            }

            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (offset < 0 || length < 0 || length > bytes.size - offset) {
                    throw IndexOutOfBoundsException()
                }
                if (length == 0) return 0
                if (position >= content.size) return -1
                val count = minOf(length, content.size - position)
                content.copyInto(bytes, offset, position, position + count)
                position += count
                return count
            }

            override fun readFully(bytes: ByteArray) = readFully(bytes, 0, bytes.size)

            override fun readFully(bytes: ByteArray, offset: Int, length: Int) {
                var copied = 0
                while (copied < length) {
                    val count = read(bytes, offset + copied, length - copied)
                    if (count < 0) throw EOFException()
                    copied += count
                }
            }

            override fun read(buffer: ByteBuffer): Int {
                if (!buffer.hasRemaining()) return 0
                if (position >= content.size) return -1
                val count = minOf(buffer.remaining(), content.size - position)
                buffer.put(content, position, count)
                position += count
                return count
            }

            override fun readFully(buffer: ByteBuffer) {
                while (buffer.hasRemaining()) {
                    if (read(buffer) < 0) throw EOFException()
                }
            }
        }
    }
}
