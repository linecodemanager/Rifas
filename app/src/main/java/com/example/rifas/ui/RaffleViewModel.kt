package com.example.rifas.ui

import android.app.Application
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
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
import java.io.File

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
    val isDownloading = mutableStateOf(false)
    private var currentDownloadId: Long? = null

    fun checkForUpdates() {
        if (isDownloading.value) return
        
        // Verificar si hay una descarga en curso en DownloadManager
        val downloadManager = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterByStatus(
            DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PENDING or DownloadManager.STATUS_PAUSED
        )
        val cursor = downloadManager.query(query)
        if (cursor != null && cursor.moveToFirst()) {
            val titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
            if (titleIndex != -1) {
                val title = cursor.getString(titleIndex)
                if (title == "Descargando actualización de Rifas") {
                    isDownloading.value = true
                    cursor.close()
                    return
                }
            }
            cursor.close()
        }

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
                    
                    android.util.Log.d("UpdateCheck", "Current Version: $currentVersionCode, Remote Version: ${updateInfo.versionCode}")
                    
                    if (updateInfo.versionCode > currentVersionCode) {
                        withContext(Dispatchers.Main) {
                            updateAvailable.value = updateInfo
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun downloadUpdate(context: Context, url: String) {
        if (isDownloading.value) return
        isDownloading.value = true

        try {
            // Limpiar descarga anterior si existe
            val oldFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "rifas_update.apk")
            if (oldFile.exists()) {
                oldFile.delete()
            }

            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Descargando actualización de Rifas")
                .setDescription("Preparando nueva versión...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "rifas_update.apk")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)
            currentDownloadId = downloadId

            // Escuchar cuando termine la descarga
            val onComplete = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        isDownloading.value = false
                        currentDownloadId = null
                        installApk(context)
                        try {
                            context.unregisterReceiver(this)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(onComplete, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(onComplete, filter)
            }
            
        } catch (e: Exception) {
            isDownloading.value = false
            currentDownloadId = null
            e.printStackTrace()
        }
    }

    private fun installApk(context: Context) {
        try {
            // 1. Verificar si tenemos permiso para instalar aplicaciones (Android 8.0+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                }
            }

            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "rifas_update.apk")
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    // Añadir flag para indicar que es una instalación de confianza si es posible
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                    putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.packageName)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val raffles: Flow<List<Raffle>> = repository.allRaffles
    val allSoldNumbers: Flow<List<SoldNumber>> = repository.allSoldNumbers

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
