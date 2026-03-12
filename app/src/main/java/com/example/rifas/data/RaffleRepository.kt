package com.example.rifas.data

import kotlinx.coroutines.flow.Flow

class RaffleRepository(private val raffleDao: RaffleDao) {
    val allRaffles: Flow<List<Raffle>> = raffleDao.getAllRaffles()

    suspend fun getRaffleById(id: Long): Raffle? = raffleDao.getRaffleById(id)

    suspend fun addRaffle(raffle: Raffle) = raffleDao.insertRaffle(raffle)

    suspend fun sellNumbers(soldNumbers: List<SoldNumber>) = raffleDao.insertSoldNumbers(soldNumbers)

    fun getSoldNumbers(raffleId: Long): Flow<List<SoldNumber>> = raffleDao.getSoldNumbersByRaffleId(raffleId)

    val allSoldNumbers: Flow<List<SoldNumber>> = raffleDao.getAllSoldNumbers()

    suspend fun updateSoldNumber(soldNumber: SoldNumber) = raffleDao.updateSoldNumber(soldNumber)

    suspend fun updateRaffle(raffle: Raffle) = raffleDao.updateRaffle(raffle)

    suspend fun deleteSoldNumber(soldNumber: SoldNumber) = raffleDao.deleteSoldNumber(soldNumber)

    suspend fun setPaidForBuyer(raffleId: Long, buyerName: String, buyerPhone: String, isPaid: Boolean) =
        raffleDao.setPaidForBuyer(raffleId, buyerName, buyerPhone, isPaid)

    suspend fun deleteSoldNumbersForBuyer(raffleId: Long, buyerName: String, buyerPhone: String) =
        raffleDao.deleteSoldNumbersForBuyer(raffleId, buyerName, buyerPhone)

    suspend fun deleteRaffle(raffle: Raffle) {
        raffleDao.deleteSoldNumbersByRaffleId(raffle.id)
        raffleDao.deleteRaffle(raffle)
    }
}
