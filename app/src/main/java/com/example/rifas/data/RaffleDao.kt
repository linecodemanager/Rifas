package com.example.rifas.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Delete
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RaffleDao {
    @Query("SELECT * FROM raffles ORDER BY id DESC")
    fun getAllRaffles(): Flow<List<Raffle>>

    @Query("SELECT * FROM raffles WHERE id = :id")
    suspend fun getRaffleById(id: Long): Raffle?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRaffle(raffle: Raffle): Long

    @Update
    suspend fun updateRaffle(raffle: Raffle)

    @Delete
    suspend fun deleteRaffle(raffle: Raffle)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSoldNumbers(soldNumbers: List<SoldNumber>)

    @Update
    suspend fun updateSoldNumber(soldNumber: SoldNumber)

    @Delete
    suspend fun deleteSoldNumber(soldNumber: SoldNumber)

    @Query("SELECT * FROM sold_numbers WHERE raffleId = :raffleId")
    fun getSoldNumbersByRaffleId(raffleId: Long): Flow<List<SoldNumber>>

    @Query("DELETE FROM sold_numbers WHERE raffleId = :raffleId")
    suspend fun deleteSoldNumbersByRaffleId(raffleId: Long)
}
