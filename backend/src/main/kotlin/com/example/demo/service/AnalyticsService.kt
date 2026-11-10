package com.example.demo.service

import com.example.demo.persistence.DatasetRepository
import com.example.demo.service.model.DailyBar
import com.example.demo.service.model.MarketDataBar
import com.example.demo.service.model.RollingVwapPoint
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import org.springframework.stereotype.Service

@Service
class AnalyticsService(
    private val datasetRepository: DatasetRepository,
) {
    fun dailyBars(contract: String?, from: LocalDate, to: LocalDate): List<DailyBar> {
        if (to.isBefore(from)) return emptyList()

        return filteredBars(contract)
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
            .sortedWith(compareBy(DailyBar::contract, DailyBar::date))
    }

    fun rollingVwap(contract: String?, from: LocalDate, to: LocalDate): List<RollingVwapPoint> {
        if (to.isBefore(from)) return emptyList()
        val firstWindowStart = from.atStartOfDay().minusMinutes(VWAP_WINDOW_MINUTES)
        val lastTimestamp = to.atTime(23, 59, 59, 999_999_999)
        val barsByContract = filteredBars(contract)
            .asSequence()
            .filter { it.timestamp.isAfter(firstWindowStart) && !it.timestamp.isAfter(lastTimestamp) }
            .groupBy(MarketDataBar::contract)

        return barsByContract.entries
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
    }

    private fun filteredBars(contract: String?): List<MarketDataBar> {
        val normalizedContract = contract?.trim()?.uppercase(Locale.ROOT)
        return datasetRepository.findAllMarketData().filter { bar ->
            normalizedContract == null || bar.contract == normalizedContract
        }
    }

    private fun typicalPrice(bar: MarketDataBar): Double = (bar.high + bar.low + bar.close) / 3.0

    private companion object {
        const val VWAP_WINDOW_MINUTES = 15L
    }
}
