package com.example.timepay.repository

import com.example.timepay.models.CalendarDayInfo
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class CalendarDayRepository {
    private val db = FirebaseFirestore.getInstance()
    private val userId = "demoUser"

    private fun collectionRef() = db
        .collection("users")
        .document(userId)
        .collection("calendar")

    suspend fun getDayInfo(date: String): CalendarDayInfo? {
        val snapshot = collectionRef().document(date).get().await()
        return if (snapshot.exists()) {
            snapshot.toObject(CalendarDayInfo::class.java)
        } else {
            null
        }
    }

    suspend fun saveDayInfo(date: String, info: CalendarDayInfo) {
        collectionRef().document(date).set(info).await()
    }

    suspend fun getAllDays(): Map<String, CalendarDayInfo> {
        val snapshot = collectionRef().get().await()
        return snapshot.documents.associate { doc ->
            doc.id to (doc.toObject(CalendarDayInfo::class.java) ?: CalendarDayInfo())
        }
    }
}
