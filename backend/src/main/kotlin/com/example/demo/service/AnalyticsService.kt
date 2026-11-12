package com.example.demo.service

import com.example.demo.persistence.DatasetRepository
import com.example.demo.service.model.AnalyticsPage
import com.example.demo.service.model.DailyBar
import com.example.demo.service.model.MarketDataBar
import com.example.demo.service.model.RollingVwapPoint
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class AnalyticsService(
    private val datasetRepository: DatasetRepository,
) {
    fun dailyBars(
        datasetId: UUID,
        contract: String?,
        from: LocalDate,
        to: LocalDate,
        page: Int,
        size: Int,
    ): AnalyticsPage<DailyBar> {
        requireDataset(datasetId)
        validatePagination(page, size)
        if (to.isBefore(from)) return paginate(emptyList(), page, size)

        val bars = filteredBars(datasetId, contract)
            .asSequence()
            .filter { bar ->
                val date = bar.timestamp.toLocalDate()
                !date.isBefore(from) && !date.isAfter(to)
            }
            .groupBy { it.contract to it.timestamp.toLocalDate() }
            .map { (key, bars) ->
                val orderedBars = bars.sortedBy(MarketDataBar::timestamp)
                DailyBar(
                    contract = key.first,
                    date = key.second,
                    open = orderedBars.first().open,
                    high = orderedBars.maxOf(MarketDataBar::high),
                    low = orderedBars.minOf(MarketDataBar::low),
                    close = orderedBars.last().close,
                    volume = orderedBars.fold(0L) { total, bar -> Math.addExact(total, bar.volume) },
                )
            }
            .sortedWith(compareBy(DailyBar::date, DailyBar::contract))
        return paginate(
            bars,
            page,
            size,
            rangeStart = bars.minOfOrNull(DailyBar::date)?.toString(),
            rangeEnd = bars.maxOfOrNull(DailyBar::date)?.toString(),
        )
    }

    fun rollingVwap(
        datasetId: UUID,
        contract: String?,
        from: LocalDate,
        to: LocalDate,
        page: Int,
        size: Int,
    ): AnalyticsPage<RollingVwapPoint> {
        requireDataset(datasetId)
        validatePagination(page, size)
        if (to.isBefore(from)) return paginate(emptyList(), page, size)
        val firstWindowStart = from.atStartOfDay().minusMinutes(VWAP_WINDOW_MINUTES)
        val lastTimestamp = to.atTime(23, 59, 59, 999_999_999)
        val barsByContract = filteredBars(datasetId, contract)
            .asSequence()
            .filter { it.timestamp.isAfter(firstWindowStart) && !it.timestamp.isAfter(lastTimestamp) }
            .groupBy(MarketDataBar::contract)

        val points = barsByContract.entries
            .sortedBy { it.key }
            .flatMap { (contractSymbol, unsortedBars) ->
                val bars = unsortedBars.sortedBy(MarketDataBar::timestamp)
                val result = mutableListOf<RollingVwapPoint>()
                var windowStart = 0
                var totalVolume = 0L
                var weightedPriceSum = 0.0

                bars.forEachIndexed { index, bar ->
                    totalVolume = Math.addExact(totalVolume, bar.volume)
                    weightedPriceSum += typicalPrice(bar) * bar.volume

                    val exclusiveBoundary = bar.timestamp.minusMinutes(VWAP_WINDOW_MINUTES)
                    while (windowStart <= index && !bars[windowStart].timestamp.isAfter(exclusiveBoundary)) {
                        val expiredBar = bars[windowStart]
                        totalVolume = Math.subtractExact(totalVolume, expiredBar.volume)
                        weightedPriceSum -= typicalPrice(expiredBar) * expiredBar.volume
                        windowStart++
                    }

                    val date = bar.timestamp.toLocalDate()
                    if (date in from..to && totalVolume > 0) {
                        result.add(
                            RollingVwapPoint(
                                contract = contractSymbol,
                                timestamp = bar.timestamp,
                                value = weightedPriceSum / totalVolume,
                            ),
                        )
                    }
                }
                result
            }
            .sortedWith(compareBy(RollingVwapPoint::timestamp, RollingVwapPoint::contract))
        return paginate(
            points,
            page,
            size,
            rangeStart = points.minOfOrNull(RollingVwapPoint::timestamp)?.toString(),
            rangeEnd = points.maxOfOrNull(RollingVwapPoint::timestamp)?.toString(),
        )
    }

    private fun filteredBars(datasetId: UUID, contract: String?): List<MarketDataBar> {
        val normalizedContract = contract?.trim()?.uppercase(Locale.ROOT)
        return datasetRepository.findMarketData(datasetId).filter { bar ->
            normalizedContract == null || bar.contract == normalizedContract
        }
    }

    private fun requireDataset(datasetId: UUID) {
        if (datasetRepository.findDataset(datasetId) == null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Dataset $datasetId was not found")
        }
    }

    private fun validatePagination(page: Int, size: Int) {
        if (page < 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be zero or greater")
        }
        if (size !in 1..MAX_PAGE_SIZE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Size must be between 1 and $MAX_PAGE_SIZE")
        }
    }

    private fun <T> paginate(
        items: List<T>,
        page: Int,
        size: Int,
        rangeStart: String? = null,
        rangeEnd: String? = null,
    ): AnalyticsPage<T> {
        val offset = page.toLong() * size
        val pageItems = if (offset >= items.size) emptyList() else items.drop(offset.toInt()).take(size)
        return AnalyticsPage(
            totalElements = items.size.toLong(),
            page = page,
            size = size,
            items = pageItems,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
        )
    }

    private fun typicalPrice(bar: MarketDataBar): Double = (bar.high + bar.low + bar.close) / 3.0

    private companion object {
        const val VWAP_WINDOW_MINUTES = 15L
        const val MAX_PAGE_SIZE = 500
    }
}
