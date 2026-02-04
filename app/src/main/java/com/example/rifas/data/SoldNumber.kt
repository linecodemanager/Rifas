package com.example.rifas.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sold_numbers",
    foreignKeys = [
        ForeignKey(
            entity = Raffle::class,
            parentColumns = ["id"],
            childColumns = ["raffleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("raffleId")]
)
data class SoldNumber(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val raffleId: Long,
    val number: String,
    val buyerName: String,
    val buyerPhone: String,
    val isPaid: Boolean = false
)
