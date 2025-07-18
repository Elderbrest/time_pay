package com.example.timepay.models

data class CalendarDayInfo(
    val status: String = "working",
    val note: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val hoursWorked: Double? = null
)
