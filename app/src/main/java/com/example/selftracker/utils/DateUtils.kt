package com.example.selftracker.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object DateUtils {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun getCurrentDate(): String {
        return LocalDate.now().format(formatter)
    }

    // Add this missing function
    fun getPreviousDate(currentDate: String): String {
        return try {
            val date = LocalDate.parse(currentDate, formatter)
            date.minusDays(1).format(formatter)
        } catch (e: Exception) {
            // Fallback: return yesterday's date
            LocalDate.now().minusDays(1).format(formatter)
        }
    }

    fun getDateFromString(dateString: String): LocalDate {
        return LocalDate.parse(dateString, formatter)
    }

    fun formatDateForDisplay(dateString: String): String {
        return try {
            val date = LocalDate.parse(dateString, formatter)
            date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
        } catch (e: Exception) {
            dateString
        }
    }

    fun getDayOfWeek(dateString: String): String {
        return try {
            val date = LocalDate.parse(dateString, formatter)
            date.dayOfWeek.toString()
        } catch (e: Exception) {
            ""
        }
    }

    fun isToday(dateString: String): Boolean {
        return dateString == getCurrentDate()
    }

    fun daysUntilDate(dateString: String): Long {
        return try {
            val targetDate = LocalDate.parse(dateString, formatter)
            val today = LocalDate.now()
            java.time.temporal.ChronoUnit.DAYS.between(today, targetDate)
        } catch (e: Exception) {
            -1
        }
    }
}