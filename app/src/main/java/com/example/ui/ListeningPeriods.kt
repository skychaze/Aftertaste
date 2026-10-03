package com.example.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object ListeningPeriods {
    fun bounds(scope: GenreScope, today: String): DateBounds {
        if (scope == GenreScope.ALL_TIME) return DateBounds("0001-01-01", "9999-12-31")
        if (scope == GenreScope.YEAR)
            return DateBounds("${today.take(4)}-01-01", "${today.take(4)}-12-31")
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val start =
            Calendar.getInstance().apply {
                time = requireNotNull(formatter.parse(today))
                add(
                    Calendar.MONTH,
                    -when (scope) {
                        GenreScope.THREE_MONTHS -> 3
                        GenreScope.SIX_MONTHS -> 6
                        else -> 1
                    },
                )
                add(Calendar.DAY_OF_YEAR, 1)
            }
        return DateBounds(formatter.format(start.time), today)
    }
}
