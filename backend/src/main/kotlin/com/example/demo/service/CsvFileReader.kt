package com.example.demo.service

import com.example.demo.service.model.TabularData
import java.io.IOException
import java.io.UncheckedIOException
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser

class CsvFileReader {
    @Throws(IOException::class)
    fun <T> read(content: ByteArray, process: (TabularData) -> T): T {
        try {
            return CSVParser.parse(content.toString(Charsets.UTF_8), CSVFormat.DEFAULT).use { parser ->
                val records = parser.iterator()
                val headers = records.asSequence()
                    .map { record -> record.toList() }
                    .firstOrNull { row -> row.any(String::isNotBlank) }
                    .orEmpty()
                val rows = sequence {
                    while (records.hasNext()) {
                        val row = records.next().toList()
                        if (row.any(String::isNotBlank)) yield(row)
                    }
                }
                process(TabularData(headers, rows))
            }
        } catch (exception: UncheckedIOException) {
            throw exception.cause ?: exception
        }
    }
}
