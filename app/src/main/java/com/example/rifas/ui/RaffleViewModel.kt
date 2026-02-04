package com.example.rifas.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.rifas.data.Raffle
import com.example.rifas.data.RaffleDatabase
import com.example.rifas.data.RaffleRepository
import com.example.rifas.data.SoldNumber
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val updateUrl: String,
    val description: String
)

class RaffleViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RaffleRepository(
        RaffleDatabase.getInstance(application).raffleDao()
    )

    val updateAvailable = mutableStateOf<AppUpdate?>(null)

    fun checkForUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = "https://raw.githubusercontent.com/linecodemanager/Rifas/main/update.json"
                
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@launch
                    
                    val json = response.body?.string() ?: return@launch
                    val updateInfo = Gson().fromJson(json, AppUpdate::class.java)
                    
                    val packageInfo = getApplication<Application>().packageManager
                        .getPackageInfo(getApplication<Application>().packageName, 0)
                    
                    val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode.toInt()
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode
                    }
                    
                    if (updateInfo.versionCode > currentVersionCode) {
                        withContext(Dispatchers.Main) {
                            updateAvailable.value = updateInfo
                        }
                    }
                }
            } catch (e: Exception) {
                // Silenciosamente fallar si no hay internet o error en la URL
                e.printStackTrace()
            }
        }
    }

    fun downloadUpdate(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    val raffles: Flow<List<Raffle>> = repository.allRaffles

    fun addRaffle(name: String, rangeStart: Int, rangeEnd: Int, digits: Int, drawDate: String, lotteryName: String, prize: String, price: String) {
        viewModelScope.launch {
            repository.addRaffle(
                Raffle(
                    name = name,
                    rangeStart = rangeStart,
                    rangeEnd = rangeEnd,
                    digits = digits,
                    drawDate = drawDate,
                    lotteryName = lotteryName,
                    prize = prize,
                    price = price
                )
            )
        }
    }

    suspend fun getRaffleById(id: Long): Raffle? {
        return repository.getRaffleById(id)
    }

    fun getSoldNumbers(raffleId: Long): Flow<List<SoldNumber>> {
        return repository.getSoldNumbers(raffleId)
    }

    fun sellNumbers(raffleId: Long, numbers: List<String>, buyerName: String, buyerPhone: String) {
        viewModelScope.launch {
            val soldList = numbers.map {
                SoldNumber(
                    raffleId = raffleId,
                    number = it,
                    buyerName = buyerName,
                    buyerPhone = buyerPhone
                )
            }
            repository.sellNumbers(soldList)
        }
    }

    fun deleteRaffle(raffle: Raffle) {
        viewModelScope.launch {
            repository.deleteRaffle(raffle)
        }
    }

    fun updateRaffle(raffle: Raffle) {
        viewModelScope.launch {
            repository.updateRaffle(raffle)
        }
    }

    fun updateSoldNumber(soldNumber: SoldNumber) {
        viewModelScope.launch {
            repository.updateSoldNumber(soldNumber)
        }
    }

    fun deleteSoldNumber(soldNumber: SoldNumber) {
        viewModelScope.launch {
            repository.deleteSoldNumber(soldNumber)
        }
    }
}

class RaffleViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RaffleViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RaffleViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
