package com.scrollmate.core.time

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Calendar-day arithmetic in a given time zone. DST-safe: a local day may be 23, 24 or 25 hours long. */
object DayMath {

    fun startOfDay(date: LocalDate, zone: ZoneId): Instant = date.atStartOfDay(zone).toInstant()

    fun endOfDay(date: LocalDate, zone: ZoneId): Instant = startOfDay(date.plusDays(1), zone)

    fun dayLength(date: LocalDate, zone: ZoneId): Duration =
        Duration.between(startOfDay(date, zone), endOfDay(date, zone))

    fun localDate(instant: Instant, zone: ZoneId): LocalDate = instant.atZone(zone).toLocalDate()

    /**
     * Splits the half-open interval [start, end) at every local midnight and returns the
     * duration that falls on each local calendar day, in chronological order.
     */
    fun splitByLocalDay(start: Instant, end: Instant, zone: ZoneId): Map<LocalDate, Duration> {
        val result = LinkedHashMap<LocalDate, Duration>()
        if (!end.isAfter(start)) return result
        var cursor = start
        var day = localDate(start, zone)
        while (cursor.isBefore(end)) {
            val dayEnd = endOfDay(day, zone)
            val segmentEnd = if (dayEnd.isBefore(end)) dayEnd else end
            val piece = Duration.between(cursor, segmentEnd)
            if (!piece.isZero) result[day] = (result[day] ?: Duration.ZERO).plus(piece)
            cursor = segmentEnd
            day = day.plusDays(1)
        }
        return result
    }
}
