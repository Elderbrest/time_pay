package com.example.timepay.repository

import com.example.timepay.models.CalendarDayInfo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class CalendarDayRepository {
    private val db = FirebaseFirestore.getInstance()
    private val userId: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    private fun collectionRef() = userId?.let {
        db.collection("users")
            .document(it)
            .collection("calendar")
    } ?: throw IllegalStateException("User is not authenticated")

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

    suspend fun updateDayInfo(date: String, updates: Map<String, Any?>) {
        collectionRef()
            .document(date)
            .set(updates, SetOptions.merge())
            .await()
    }

    suspend fun deleteDayInfo(date: String) {
        collectionRef().document(date).delete().await()
    }

    suspend fun getCurrentMonthDates(yearMonth: YearMonth): Map<String, CalendarDayInfo> {
        val firstDay = LocalDate.of(yearMonth.year, yearMonth.month, 1)
        val lastDay = firstDay.plusMonths(1).minusDays(1)

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        val snapshot = collectionRef()
            .whereGreaterThanOrEqualTo(FieldPath.documentId(), firstDay.format(formatter))
            .whereLessThanOrEqualTo(FieldPath.documentId(), lastDay.format(formatter))
            .get()
            .await()

        return snapshot.documents.associate { doc ->
            doc.id to (doc.toObject(CalendarDayInfo::class.java) ?: CalendarDayInfo())
        }
    }
}
