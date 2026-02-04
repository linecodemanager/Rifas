package com.example.rifas.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "raffles")
data class Raffle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val rangeStart: Int,
    val rangeEnd: Int,
    val digits: Int,
    val drawDate: String = "",
    val lotteryName: String = "",
    val prize: String = "",
    val price: String = "",
    val isActive: Boolean = true
)
