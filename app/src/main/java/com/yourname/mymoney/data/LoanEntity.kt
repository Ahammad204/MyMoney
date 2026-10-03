package com.yourname.mymoney.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val personName: String,
    val amount: Double,
    val type: String, // "LENT" (someone owes me) or "BORROWED" (I owe someone)
    val date: Long = System.currentTimeMillis(),
    val dueDate: Long = 0L,
    val note: String = "",
    val isSettled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
