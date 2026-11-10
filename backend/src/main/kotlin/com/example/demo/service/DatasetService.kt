package com.example.demo.service

import com.example.demo.persistence.DatasetRepository
import com.example.demo.persistence.StoredDataset
import com.example.demo.service.model.DatasetFrequency
import com.example.demo.service.model.DatasetImportResult
import com.example.demo.service.model.DatasetSummary
import com.example.demo.service.model.MarketDataBar
import com.example.demo.service.model.QualityIssue
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import org.apache.avro.generic.GenericRecord
import org.apache.parquet.avro.AvroParquetReader
import org.apache.parquet.conf.PlainParquetConfiguration
import org.apache.parquet.io.InputFile
import org.apache.parquet.io.SeekableInputStream
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class DatasetService(
    private val datasetRepository: DatasetRepository,
) {

    private companion object {
        val REQUIRED_FIELDS = listOf("contract", "timestamp", "open", "high", "low", "close", "volume")
        val HEADER_ALIASES = mapOf(
            "contract" to listOf("contract", "contractsymbol"),
            "timestamp" to listOf("timestamp", "timestampchicagowall", "timestampms", "t", "date"),
            "open" to listOf("open", "o"),
            "high" to listOf("high", "h"),
            "low" to listOf("low", "l"),
            "close" to listOf("close", "c"),
            "volume" to listOf("volume", "v"),
        )
    }

    fun importDataset(
        filename: String,
        extension: String,
        content: ByteArray,
        frequency: DatasetFrequency = DatasetFrequency.MINUTE,
    ): DatasetImportResult {

        val importedRows = when (extension) {
            "csv" -> processCsv(content)
            "parquet" -> processParquet(content)
            else -> throw badRequest("Only CSV and Parquet files are supported")
        }

        val rowsImported = importedRows.bars.size
        val gaps = findGaps(importedRows.bars, frequency)
        val qualityIssues = importedRows.issues + gaps
        val rowsRejected = importedRows.rowsRejected
        val status = if (qualityIssues.isEmpty()) "COMPLETED" else "COMPLETED_WITH_WARNINGS"
        val id = UUID.randomUUID()
        val uploadedAt = Instant.now()
        val summary = DatasetSummary(
            id = id,
            filename = filename,
            status = status,
            rowsImported = rowsImported,
            rowsRejected = rowsRejected,
            uploadedAt = uploadedAt,
            frequency = frequency,
        )
        datasetRepository.save(
            StoredDataset(
                summary = summary,
                bars = importedRows.bars,
                qualityIssues = qualityIssues,
            ),
        )

        return DatasetImportResult(
            id = id,
            filename = filename,
            status = status,
            rowsImported = rowsImported,
            rowsRejected = rowsRejected,
            frequency = frequency,
        )
    }

    fun listDatasets(): List<DatasetSummary> = datasetRepository.findAllDatasets()

    fun allMarketData(): List<MarketDataBar> = datasetRepository.findAllMarketData()

    fun findDataset(datasetId: UUID): DatasetSummary? = datasetRepository.findDataset(datasetId)

    fun qualityIssues(datasetId: UUID): List<QualityIssue> = datasetRepository.findQualityIssues(datasetId)

    private fun findGaps(bars: List<MarketDataBar>, frequency: DatasetFrequency): List<QualityIssue> =
        bars.groupBy(MarketDataBar::contract).values.flatMap { contractBars ->
            val orderedBars = contractBars.sortedBy(MarketDataBar::timestamp)
            orderedBars.zipWithNext().mapNotNull { (previous, next) ->
                val missingCount: Long
                val firstMissing: LocalDateTime
                when (frequency) {
                    DatasetFrequency.MINUTE -> {
                        val elapsedMinutes = ChronoUnit.MINUTES.between(previous.timestamp, next.timestamp)
                        if (elapsedMinutes <= 0) return@mapNotNull null
                        val lastExpected = previous.timestamp.plusMinutes(elapsedMinutes)
                        missingCount = if (lastExpected.isBefore(next.timestamp)) elapsedMinutes else elapsedMinutes - 1
                        firstMissing = previous.timestamp.plusMinutes(1)
                    }
                    DatasetFrequency.DAILY -> {
                        val previousDate = previous.timestamp.toLocalDate()
                        val nextDate = next.timestamp.toLocalDate()
                        val elapsedDays = ChronoUnit.DAYS.between(previousDate, nextDate)
                        if (elapsedDays <= 0) return@mapNotNull null
                        missingCount = elapsedDays - 1
                        firstMissing = previousDate.plusDays(1).atStartOfDay()
                    }
                }
                if (missingCount <= 0) return@mapNotNull null
                QualityIssue(
                    rowNumber = null,
                    contract = previous.contract,
                    timestamp = firstMissing,
                    code = "MISSING_TIMESTAMP",
                    message = "$missingCount missing ${frequency.name.lowercase(Locale.ROOT)} bar(s) between ${previous.timestamp} and ${next.timestamp}",
                )
            }
        }

    private fun processCsv(content: ByteArray): ImportedRows {
        val records = parseCsv(content.toString(Charsets.UTF_8)).iterator()
        val headers = records.asSequence().firstOrNull { row -> row.any(String::isNotBlank) }
            ?: throw badRequest("The CSV file does not contain a header")
        val columns = resolveColumns(headers)
        val seenBars = hashSetOf<BarKey>()
        val imported = mutableListOf<MarketDataBar>()
        val issues = mutableListOf<QualityIssue>()
        var rowsRejected = 0
        var rowNumber = 1L

        for (row in records.asSequence()) {
            if (row.all(String::isBlank)) continue
            rowNumber++
            if (row.size != headers.size) {
                rowsRejected++
                issues.add(
                    QualityIssue(
                        rowNumber = rowNumber,
                        contract = row.getOrNull(columns.getValue("contract"))?.trim()?.uppercase(Locale.ROOT),
                        timestamp = row.getOrNull(columns.getValue("timestamp"))?.let(::parseTimestamp),
                        code = "MALFORMED_RECORD",
                        message = "Record has ${row.size} fields; expected ${headers.size}",
                    ),
                )
                continue
            }
            val values = columns.mapValues { (_, index) -> row.getOrNull(index)?.trim().orEmpty() }
            when (val parsed = parseBar(values)) {
                is BarParseResult.Invalid -> {
                    rowsRejected++
                    issues.add(qualityIssue(rowNumber, values, parsed.code, parsed.message))
                }
                is BarParseResult.Valid -> {
                    if (!seenBars.add(BarKey(parsed.bar.contract, parsed.bar.timestamp))) {
                        rowsRejected++
                        issues.add(
                            qualityIssue(
                                rowNumber,
                                values,
                                "DUPLICATE_BAR",
                                "A bar already exists for this contract and timestamp",
                            ),
                        )
                    } else {
                        imported.add(parsed.bar)
                    }
                }
            }
        }
        return ImportedRows(imported, issues, rowsRejected)
    }

    private fun processParquet(content: ByteArray): ImportedRows {
        try {
            val reader = AvroParquetReader.builder<GenericRecord>(
                ByteArrayParquetInputFile(content),
                PlainParquetConfiguration(),
            ).build()
            reader.use { parquetReader ->
                val firstRecord = parquetReader.read() ?: return ImportedRows(emptyList(), emptyList(), 0)
                val headers = firstRecord.schema.fields.map { it.name() }
                val columns = resolveColumns(headers)
                val seenBars = hashSetOf<BarKey>()
                val imported = mutableListOf<MarketDataBar>()
                val issues = mutableListOf<QualityIssue>()
                var rowsRejected = 0
                var rowNumber = 0L

                fun inspect(record: GenericRecord) {
                    rowNumber++
                    val values = columns.mapValues { (_, index) ->
                        record.get(headers[index])?.toString()?.trim().orEmpty()
                    }
                    when (val parsed = parseBar(values)) {
                        is BarParseResult.Invalid -> {
                            rowsRejected++
                            issues.add(qualityIssue(rowNumber, values, parsed.code, parsed.message))
                        }
                        is BarParseResult.Valid -> {
                            if (!seenBars.add(BarKey(parsed.bar.contract, parsed.bar.timestamp))) {
                                rowsRejected++
                                issues.add(
                                    qualityIssue(
                                        rowNumber,
                                        values,
                                        "DUPLICATE_BAR",
                                        "A bar already exists for this contract and timestamp",
                                    ),
                                )
                            } else {
                                imported.add(parsed.bar)
                            }
                        }
                    }
                }

                inspect(firstRecord)
                while (true) {
                    val record = parquetReader.read() ?: break
                    inspect(record)
                }
                return ImportedRows(imported, issues, rowsRejected)
            }
        } catch (exception: ResponseStatusException) {
            throw exception
        } catch (_: IOException) {
            throw badRequest("The uploaded Parquet file could not be read")
        } catch (_: RuntimeException) {
            throw badRequest("The uploaded Parquet file could not be read")
        }
    }

    private fun resolveColumns(headers: List<String>): Map<String, Int> {
        val normalized = headers.mapIndexed { index, name -> normalizeHeader(name) to index }.toMap()
        val columns = linkedMapOf<String, Int>()
        REQUIRED_FIELDS.forEach { field ->
            val index = HEADER_ALIASES.getValue(field).firstNotNullOfOrNull(normalized::get)
                ?: throw badRequest("The uploaded file is missing the required '$field' column")
            columns[field] = index
        }
        return columns
    }

    private fun parseBar(values: Map<String, String>): BarParseResult {
        val contract = values["contract"]?.trim()?.takeIf(String::isNotEmpty)?.uppercase(Locale.ROOT)
            ?: return BarParseResult.Invalid("MALFORMED_RECORD", "Contract is missing")
        val timestamp = values["timestamp"]?.let(::parseTimestamp)
            ?: return BarParseResult.Invalid("MALFORMED_RECORD", "Timestamp is missing or invalid")
        val open = values["open"]?.toDoubleOrNull()?.takeIf(Double::isFinite)
            ?: return BarParseResult.Invalid("MALFORMED_RECORD", "Open price is missing or invalid")
        val high = values["high"]?.toDoubleOrNull()?.takeIf(Double::isFinite)
            ?: return BarParseResult.Invalid("MALFORMED_RECORD", "High price is missing or invalid")
        val low = values["low"]?.toDoubleOrNull()?.takeIf(Double::isFinite)
            ?: return BarParseResult.Invalid("MALFORMED_RECORD", "Low price is missing or invalid")
        val close = values["close"]?.toDoubleOrNull()?.takeIf(Double::isFinite)
            ?: return BarParseResult.Invalid("MALFORMED_RECORD", "Close price is missing or invalid")
        val volume = values["volume"]?.toLongOrNull()
            ?: return BarParseResult.Invalid("INVALID_VOLUME", "Volume is missing or is not an integer")
        if (volume < 0) return BarParseResult.Invalid("INVALID_VOLUME", "Volume must not be negative")
        if (high < maxOf(open, low, close) || low > minOf(open, high, close)) {
            return BarParseResult.Invalid("INVALID_OHLC", "Prices violate the high/low OHLC range")
        }
        return BarParseResult.Valid(MarketDataBar(contract, timestamp, open, high, low, close, volume))
    }

    private fun qualityIssue(
        rowNumber: Long,
        values: Map<String, String>,
        code: String,
        message: String,
    ): QualityIssue = QualityIssue(
        rowNumber = rowNumber,
        contract = values["contract"]?.trim()?.takeIf(String::isNotEmpty)?.uppercase(Locale.ROOT),
        timestamp = values["timestamp"]?.let(::parseTimestamp),
        code = code,
        message = message,
    )

    private fun parseTimestamp(value: String): LocalDateTime? {
        val normalized = value.trim()
        if (normalized.isEmpty()) return null
        normalized.toLongOrNull()?.let { epochMillis ->
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC)
        }
        return try {
            LocalDateTime.parse(normalized)
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(normalized).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()
            } catch (_: DateTimeParseException) {
                try {
                    LocalDateTime.parse(normalized.replaceFirst(' ', 'T'))
                } catch (_: DateTimeParseException) {
                    try {
                        LocalDate.parse(normalized).atStartOfDay()
                    } catch (_: DateTimeParseException) {
                        null
                    }
                }
            }
        }
    }

    private fun normalizeHeader(name: String): String = name.trim().lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun parseCsv(csv: String): Sequence<List<String>> = sequence {
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var insideQuotes = false
        var index = 0

        while (index < csv.length) {
            val character = csv[index]
            when {
                character == '"' && insideQuotes && index + 1 < csv.length && csv[index + 1] == '"' -> {
                    field.append('"')
                    index++
                }
                character == '"' -> insideQuotes = !insideQuotes
                character == ',' && !insideQuotes -> {
                    row.add(field.toString())
                    field.setLength(0)
                }
                (character == '\n' || character == '\r') && !insideQuotes -> {
                    row.add(field.toString())
                    field.setLength(0)
                    if (row.any(String::isNotBlank)) yield(row)
                    row = mutableListOf()
                    if (character == '\r' && index + 1 < csv.length && csv[index + 1] == '\n') index++
                }
                else -> field.append(character)
            }
            index++
        }

        if (insideQuotes) throw badRequest("The CSV file contains an unterminated quoted field")
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            if (row.any(String::isNotBlank)) yield(row)
        }
    }

    private fun badRequest(message: String): ResponseStatusException =
        ResponseStatusException(HttpStatus.BAD_REQUEST, message)

    private data class BarKey(val contract: String, val timestamp: LocalDateTime)

    private sealed interface BarParseResult {
        data class Valid(val bar: MarketDataBar) : BarParseResult
        data class Invalid(val code: String, val message: String) : BarParseResult
    }

    private data class ImportedRows(val bars: List<MarketDataBar>, val issues: List<QualityIssue>, val rowsRejected: Int)

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
