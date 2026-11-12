package com.example.demo.service

import com.example.demo.persistence.DatasetRepository
import com.example.demo.persistence.StoredDataset
import com.example.demo.service.model.BarKey
import com.example.demo.service.model.BarParseResult
import com.example.demo.service.model.DatasetFrequency
import com.example.demo.service.model.DatasetImportResult
import com.example.demo.service.model.DatasetSummary
import com.example.demo.service.model.ImportedRows
import com.example.demo.service.model.MarketDataBar
import com.example.demo.service.model.QualityIssue
import com.example.demo.service.model.TabularData
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
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
        val csvFileReader = CsvFileReader()
        val parquetFileReader = ParquetFileReader()
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
        return try {
            csvFileReader.read(content) { table ->
                if (table.headers.isEmpty()) throw badRequest("The CSV file does not contain a header")
                processRows(table, initialRowNumber = 1L)
            }
        } catch (exception: ResponseStatusException) {
            throw exception
        } catch (_: IOException) {
            throw badRequest("The CSV file could not be read")
        } catch (_: RuntimeException) {
            throw badRequest("The CSV file could not be read")
        }
    }

    private fun processParquet(content: ByteArray): ImportedRows {
        return try {
            parquetFileReader.read(content) { table ->
                if (table.headers.isEmpty()) {
                    throw badRequest("The Parquet file contains no records or schema")
                } else {
                    processRows(table, initialRowNumber = 0L)
                }
            }
        } catch (exception: ResponseStatusException) {
            throw exception
        } catch (_: IOException) {
            throw badRequest("The uploaded Parquet file could not be read")
        } catch (_: RuntimeException) {
            throw badRequest("The uploaded Parquet file could not be read")
        }
    }

    private fun processRows(table: TabularData, initialRowNumber: Long): ImportedRows {
        val columns = resolveColumns(table.headers)
        val seenBars = linkedMapOf<BarKey, MarketDataBar>()
        val imported = mutableListOf<MarketDataBar>()
        val issues = mutableListOf<QualityIssue>()
        var rowsRejected = 0
        var rowNumber = initialRowNumber

        for (row in table.rows) {
            if (row.all(String::isBlank)) continue
            rowNumber++
            if (row.size != table.headers.size) {
                rowsRejected++
                issues.add(
                    QualityIssue(
                        rowNumber = rowNumber,
                        contract = row.getOrNull(columns.getValue("contract"))?.trim()?.uppercase(Locale.ROOT),
                        timestamp = row.getOrNull(columns.getValue("timestamp"))?.let(::parseTimestamp),
                        code = "MALFORMED_RECORD",
                        message = "Record has ${row.size} fields; expected ${table.headers.size}",
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
                    val key = BarKey(parsed.bar.contract, parsed.bar.timestamp)
                    val existingBar = seenBars[key]
                    if (existingBar != null) {
                        rowsRejected++
                        issues.add(duplicateIssue(rowNumber, values, existingBar, parsed.bar))
                    } else {
                        seenBars[key] = parsed.bar
                        imported.add(parsed.bar)
                    }
                }
            }
        }
        return ImportedRows(imported, issues, rowsRejected)
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

    private fun duplicateIssue(
        rowNumber: Long,
        values: Map<String, String>,
        existing: MarketDataBar,
        incoming: MarketDataBar,
    ): QualityIssue {
        val isExactDuplicate = existing == incoming
        return qualityIssue(
            rowNumber = rowNumber,
            values = values,
            code = if (isExactDuplicate) "DUPLICATE_BAR" else "CONFLICTING_BAR",
            message = if (isExactDuplicate) {
                "An identical bar already exists for this contract and timestamp"
            } else {
                "A different bar already exists for this contract and timestamp"
            },
        )
    }

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

    private fun badRequest(message: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, message)
}
