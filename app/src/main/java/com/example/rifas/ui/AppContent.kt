@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.example.rifas.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.example.rifas.data.Raffle
import com.example.rifas.data.SoldNumber
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import kotlin.math.max
import kotlin.math.min

val LogoBlue = Color(0xFF2196F3)
val LogoCyan = Color(0xFF00BCD4)
val BackgroundLight = Color(0xFFF5F7FA)

@Composable
fun RifasApp(viewModel: RaffleViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val updateInfo = viewModel.updateAvailable.value
    val isDownloading = viewModel.isDownloading.value
    var showInstallDownloaded by remember { mutableStateOf(false) }
    var pendingUpdateUrl by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val url = pendingUpdateUrl
        pendingUpdateUrl = null
        if (granted && url != null) {
            viewModel.downloadUpdate(context, url)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkForUpdates()
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "rifas_update.apk")
        if (file.exists()) showInstallDownloaded = true
    }

    NavHost(navController = navController, startDestination = "intro") {
        composable("intro") {
            IntroScreen(onFinished = {
                navController.navigate("home") { popUpTo("intro") { inclusive = true } }
            })
        }
        composable("home") {
            MainPanelScreen(
                viewModel = viewModel,
                onOpenRaffles = { navController.navigate("list") },
                onOpenClients = { navController.navigate("clients") },
                onCreateRaffle = { navController.navigate("create") },
                onRaffleClick = { raffle -> navController.navigate("detail/${raffle.id}") },
                onEditRaffle = { raffle -> navController.navigate("edit/${raffle.id}") }
            )
        }
        composable("list") {
            RaffleListScreen(
                viewModel = viewModel,
                onHome = { navController.navigate("home") { launchSingleTop = true } },
                onCreateRaffle = { navController.navigate("create") },
                onRaffleClick = { raffle -> navController.navigate("detail/${raffle.id}") },
                onEditRaffle = { raffle -> navController.navigate("edit/${raffle.id}") }
            )
        }
        composable(
            "clients?raffleId={raffleId}",
            arguments = listOf(navArgument("raffleId") { type = NavType.LongType; defaultValue = -1L })
        ) {
            val raffleId = it.arguments?.getLong("raffleId") ?: -1L
            ClientsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenRaffle = { id -> navController.navigate("detail/$id") },
                raffleIdFilter = raffleId.takeIf { id -> id > 0 }
            )
        }
        composable("create") {
            CreateRaffleScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("edit/{raffleId}", arguments = listOf(navArgument("raffleId") { type = NavType.LongType })) {
            val raffleId = it.arguments?.getLong("raffleId") ?: 0L
            EditRaffleScreen(raffleId = raffleId, viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("detail/{raffleId}", arguments = listOf(navArgument("raffleId") { type = NavType.LongType })) {
            val raffleId = it.arguments?.getLong("raffleId") ?: 0L
            RaffleDetailScreen(
                raffleId = raffleId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onViewBuyers = { navController.navigate("clients?raffleId=$raffleId") }
            )
        }
    }

    if (updateInfo != null) {
        AlertDialog(
            onDismissRequest = { viewModel.updateAvailable.value = null },
            title = { Text("Actualización disponible (${updateInfo.versionName})") },
            text = { Text(updateInfo.description) },
            confirmButton = {
                Button(
                    onClick = {
                        val url = updateInfo.updateUrl
                        if (Build.VERSION.SDK_INT >= 33) {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                            if (!granted) {
                                pendingUpdateUrl = url
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.downloadUpdate(context, url)
                            }
                        } else {
                            viewModel.downloadUpdate(context, url)
                        }
                    },
                    enabled = !isDownloading
                ) { Text(if (isDownloading) "Descargando..." else "Actualizar") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.updateAvailable.value = null }, enabled = !isDownloading) { Text("Más tarde") }
            }
        )
    }

    if (showInstallDownloaded && !isDownloading) {
        AlertDialog(
            onDismissRequest = { showInstallDownloaded = false },
            title = { Text("Actualización descargada") },
            text = { Text("Ya hay una actualización descargada. ¿Quieres instalarla ahora?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.installDownloadedUpdate(context)
                    showInstallDownloaded = false
                }) { Text("Instalar") }
            },
            dismissButton = { TextButton(onClick = { showInstallDownloaded = false }) { Text("Después") } }
        )
    }
}

@Composable
fun IntroScreen(onFinished: () -> Unit) {
    var startAnim by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (startAnim) 1f else 0f, tween(1000))
    LaunchedEffect(Unit) { startAnim = true; delay(2000); onFinished() }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(LogoBlue, LogoCyan))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(alpha)) {
            Icon(Icons.Default.Casino, null, Modifier.size(100.dp), Color.White)
            Text("¡BIENVENIDO!", style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.Bold)
            Text("Gestor de Rifas", color = Color.White.copy(0.8f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainPanelScreen(
    viewModel: RaffleViewModel,
    onOpenRaffles: () -> Unit,
    onOpenClients: () -> Unit,
    onCreateRaffle: () -> Unit,
    onRaffleClick: (Raffle) -> Unit,
    onEditRaffle: (Raffle) -> Unit
) {
    val raffles by viewModel.raffles.collectAsState(initial = emptyList())
    val allSold by viewModel.allSoldNumbers.collectAsState(initial = emptyList())

    val soldByRaffle = remember(allSold) { allSold.groupBy { it.raffleId } }
    val raffleStats = remember(raffles, soldByRaffle) {
        raffles.map { raffle ->
            val sold = soldByRaffle[raffle.id].orEmpty()
            val totalTickets = max(0, raffle.rangeEnd - raffle.rangeStart + 1)
            val price = parsePriceToDouble(raffle.price)
            val soldCount = sold.size
            val paidCount = sold.count { it.isPaid }
            val pendingCount = soldCount - paidCount
            val remainingCount = max(0, totalTickets - soldCount)

            val totalPotential = totalTickets * price
            val soldTotal = soldCount * price
            val paidTotal = paidCount * price
            val pendingTotal = pendingCount * price
            val remainingTotal = remainingCount * price

            PanelRaffleStat(
                raffle = raffle,
                totalTickets = totalTickets,
                soldCount = soldCount,
                paidCount = paidCount,
                pendingCount = pendingCount,
                remainingCount = remainingCount,
                totalPotential = totalPotential,
                soldTotal = soldTotal,
                paidTotal = paidTotal,
                pendingTotal = pendingTotal,
                remainingTotal = remainingTotal
            )
        }
    }

    val totals = remember(raffleStats) {
        PanelTotals(
            totalRaffles = raffleStats.size,
            totalTickets = raffleStats.sumOf { it.totalTickets },
            soldCount = raffleStats.sumOf { it.soldCount },
            paidCount = raffleStats.sumOf { it.paidCount },
            pendingCount = raffleStats.sumOf { it.pendingCount },
            remainingCount = raffleStats.sumOf { it.remainingCount },
            totalPotential = raffleStats.sumOf { it.totalPotential },
            soldTotal = raffleStats.sumOf { it.soldTotal },
            paidTotal = raffleStats.sumOf { it.paidTotal },
            pendingTotal = raffleStats.sumOf { it.pendingTotal },
            remainingTotal = raffleStats.sumOf { it.remainingTotal }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Panel Principal", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LogoBlue, titleContentColor = Color.White),
                actions = {
                    IconButton(onClick = onOpenClients) { Icon(Icons.Default.Group, null, tint = Color.White) }
                    IconButton(onClick = onOpenRaffles) { Icon(Icons.AutoMirrored.Filled.List, null, tint = Color.White) }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(BackgroundLight),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Resumen General", fontWeight = FontWeight.Bold)

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricCard(
                                title = "Recaudo Pagado",
                                value = formatMoney(totals.paidTotal),
                                icon = Icons.Default.CheckCircle,
                                accent = Color(0xFF2E7D32)
                            )
                            MetricCard(
                                title = "Por Cobrar",
                                value = formatMoney(totals.pendingTotal),
                                icon = Icons.Default.Schedule,
                                accent = Color(0xFFF57C00)
                            )
                            MetricCard(
                                title = "Vendido (Total)",
                                value = formatMoney(totals.soldTotal),
                                icon = Icons.Default.Sell,
                                accent = LogoBlue
                            )
                            MetricCard(
                                title = "Si se venden todos",
                                value = formatMoney(totals.totalPotential),
                                icon = Icons.Default.EmojiEvents,
                                accent = Color(0xFF6A1B9A)
                            )
                        }

                        DonutRevenueChart(
                            paid = totals.paidTotal,
                            pending = totals.pendingTotal,
                            remaining = totals.remainingTotal
                        )

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Boletas: ${totals.totalTickets}", color = Color.Gray)
                            Text("Vendidas: ${totals.soldCount} · Pendientes: ${totals.pendingCount}", color = Color.Gray)
                        }
                    }
                }
            }

            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(
                        onClick = onOpenRaffles,
                        modifier = Modifier.height(52.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.List, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Mis Rifas")
                    }
                    Button(onClick = onCreateRaffle, modifier = Modifier.height(52.dp)) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Crear Rifa")
                    }
                    OutlinedButton(
                        onClick = onOpenClients,
                        modifier = Modifier.height(52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Group, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Clientes")
                    }
                }
            }

            item {
                Text("Rifas", fontWeight = FontWeight.Bold)
            }

            items(raffleStats.sortedByDescending { it.soldTotal }.take(12), key = { it.raffle.id }) { stat ->
                Card(Modifier.fillMaxWidth().combinedClickable(onClick = { onRaffleClick(stat.raffle) }), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Casino, null, tint = LogoBlue)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stat.raffle.name, fontWeight = FontWeight.Bold)
                                Text("Precio: ${stat.raffle.price.ifBlank { "0" }} · Rango: ${stat.raffle.rangeStart}-${stat.raffle.rangeEnd}", color = Color.Gray, fontSize = 12.sp)
                            }
                            IconButton(onClick = { onEditRaffle(stat.raffle) }) { Icon(Icons.Default.Edit, null, tint = LogoBlue) }
                        }

                        RevenueProgressBar(
                            paid = stat.paidTotal,
                            pending = stat.pendingTotal,
                            remaining = stat.remainingTotal
                        )

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Pagado: ${formatMoney(stat.paidTotal)}", color = Color(0xFF2E7D32), fontSize = 12.sp)
                            Text("Pendiente: ${formatMoney(stat.pendingTotal)}", color = Color(0xFFF57C00), fontSize = 12.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Vendido: ${stat.soldCount}/${stat.totalTickets}", color = Color.Gray, fontSize = 12.sp)
                            Text("Total: ${formatMoney(stat.totalPotential)}", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private data class PanelRaffleStat(
    val raffle: Raffle,
    val totalTickets: Int,
    val soldCount: Int,
    val paidCount: Int,
    val pendingCount: Int,
    val remainingCount: Int,
    val totalPotential: Double,
    val soldTotal: Double,
    val paidTotal: Double,
    val pendingTotal: Double,
    val remainingTotal: Double
)

private data class PanelTotals(
    val totalRaffles: Int,
    val totalTickets: Int,
    val soldCount: Int,
    val paidCount: Int,
    val pendingCount: Int,
    val remainingCount: Int,
    val totalPotential: Double,
    val soldTotal: Double,
    val paidTotal: Double,
    val pendingTotal: Double,
    val remainingTotal: Double
)

@Composable
private fun MetricCard(title: String, value: String, icon: ImageVector, accent: Color) {
    ElevatedCard(
        Modifier.widthIn(min = 160.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = accent)
                Text(title, color = Color.Gray, fontSize = 12.sp)
            }
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = Color.Black)
        }
    }
}

@Composable
private fun DonutRevenueChart(paid: Double, pending: Double, remaining: Double) {
    val total = max(0.0, paid + pending + remaining)
    val paidSweep = if (total <= 0.0) 0f else (paid / total * 360f).toFloat()
    val pendingSweep = if (total <= 0.0) 0f else (pending / total * 360f).toFloat()
    val remainingSweep = if (total <= 0.0) 0f else (remaining / total * 360f).toFloat()

    val paidColor = Color(0xFF2E7D32)
    val pendingColor = Color(0xFFF57C00)
    val remainingColor = Color(0xFF90A4AE)

    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 18f)
                    var start = -90f
                    if (total <= 0.0) {
                        drawArc(color = remainingColor.copy(alpha = 0.35f), startAngle = 0f, sweepAngle = 360f, useCenter = false, style = stroke)
                    } else {
                        drawArc(color = paidColor, startAngle = start, sweepAngle = paidSweep, useCenter = false, style = stroke)
                        start += paidSweep
                        drawArc(color = pendingColor, startAngle = start, sweepAngle = pendingSweep, useCenter = false, style = stroke)
                        start += pendingSweep
                        drawArc(color = remainingColor, startAngle = start, sweepAngle = remainingSweep, useCenter = false, style = stroke)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total", color = Color.Gray, fontSize = 12.sp)
                    Text(formatMoney(total), fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center, color = Color.Black)
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendRow(color = paidColor, label = "Pagado", value = formatMoney(paid))
                LegendRow(color = pendingColor, label = "Pendiente", value = formatMoney(pending))
                LegendRow(color = remainingColor, label = "Restante", value = formatMoney(remaining))
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(MaterialTheme.shapes.small).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, Modifier.weight(1f), color = Color.Gray, fontSize = 12.sp)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.Black)
    }
}

@Composable
private fun RevenueProgressBar(paid: Double, pending: Double, remaining: Double) {
    val total = max(0.0, paid + pending + remaining)
    val paidFrac = if (total <= 0.0) 0f else (paid / total).toFloat()
    val pendingFrac = if (total <= 0.0) 0f else (pending / total).toFloat()
    val remainingFrac = if (total <= 0.0) 1f else (remaining / total).toFloat()

    val paidColor = Color(0xFF2E7D32)
    val pendingColor = Color(0xFFF57C00)
    val remainingColor = Color(0xFFCFD8DC)

    Canvas(Modifier.fillMaxWidth().height(14.dp).clip(MaterialTheme.shapes.small)) {
        val w = size.width
        val h = size.height
        var x = 0f
        val paidW = w * paidFrac
        val pendingW = w * pendingFrac
        val remainingW = max(0f, w - paidW - pendingW)

        drawRoundRect(color = paidColor, topLeft = androidx.compose.ui.geometry.Offset(x, 0f), size = androidx.compose.ui.geometry.Size(paidW, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f))
        x += paidW
        drawRoundRect(color = pendingColor, topLeft = androidx.compose.ui.geometry.Offset(x, 0f), size = androidx.compose.ui.geometry.Size(pendingW, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f))
        x += pendingW
        drawRoundRect(color = remainingColor, topLeft = androidx.compose.ui.geometry.Offset(x, 0f), size = androidx.compose.ui.geometry.Size(remainingW, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f))
    }
}

private fun parsePriceToDouble(raw: String): Double {
    val text = raw.trim()
    if (text.isBlank()) return 0.0
    val normalized = buildString {
        for (c in text) {
            if (c.isDigit() || c == '.' || c == ',') append(c)
        }
    }
    if (normalized.isBlank()) return 0.0
    val hasComma = normalized.contains(',')
    val hasDot = normalized.contains('.')
    return when {
        hasComma && hasDot -> {
            val lastComma = normalized.lastIndexOf(',')
            val lastDot = normalized.lastIndexOf('.')
            if (lastComma > lastDot) normalized.replace(".", "").replace(',', '.').toDoubleOrNull() ?: 0.0
            else normalized.replace(",", "").toDoubleOrNull() ?: 0.0
        }
        hasComma -> normalized.replace(".", "").replace(',', '.').toDoubleOrNull() ?: 0.0
        else -> normalized.replace(",", "").toDoubleOrNull() ?: 0.0
    }
}

private fun formatMoney(amount: Double): String {
    val nf = NumberFormat.getCurrencyInstance()
    return nf.format(amount)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RaffleListScreen(
    viewModel: RaffleViewModel,
    onHome: () -> Unit,
    onCreateRaffle: () -> Unit,
    onRaffleClick: (Raffle) -> Unit,
    onEditRaffle: (Raffle) -> Unit
) {
    val raffles by viewModel.raffles.collectAsState(initial = emptyList())
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Mis Rifas", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LogoBlue, titleContentColor = Color.White),
                navigationIcon = { IconButton(onClick = onHome) { Icon(Icons.Default.Home, null, tint = Color.White) } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateRaffle,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) { Icon(Icons.Default.Add, null) }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).background(BackgroundLight), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(raffles) { raffle ->
                RaffleItem(raffle, onClick = { onRaffleClick(raffle) }, onEdit = { onEditRaffle(raffle) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RaffleItem(raffle: Raffle, onClick: () -> Unit, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth().combinedClickable(onClick = onClick), elevation = CardDefaults.cardElevation(4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Casino, null, Modifier.size(40.dp), LogoBlue)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(raffle.name, fontWeight = FontWeight.Bold)
                Text("Rango: ${raffle.rangeStart}-${raffle.rangeEnd}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, tint = LogoBlue) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateRaffleScreen(viewModel: RaffleViewModel, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var prize by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var lottery by remember { mutableStateOf("") }
    
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Crear Rifa") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = prize, onValueChange = { prize = it }, label = { Text("Premio") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Precio") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Fecha Sorteo") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = lottery, onValueChange = { lottery = it }, label = { Text("Lotería") }, modifier = Modifier.fillMaxWidth())
            
            Button(onClick = {
                viewModel.addRaffle(
                    name,
                    0,
                    99,
                    2,
                    date,
                    lottery,
                    prize,
                    price
                )
                onBack()
            }, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = name.isNotBlank()) { Text("Guardar Rifa") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRaffleScreen(raffleId: Long, viewModel: RaffleViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var raffle by remember { mutableStateOf<Raffle?>(null) }
    var name by remember { mutableStateOf("") }
    var prize by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var lottery by remember { mutableStateOf("") }
    var img by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableStateOf(1f) }
    var tx by remember { mutableStateOf(0f) }
    var ty by remember { mutableStateOf(0f) }

    LaunchedEffect(raffleId) {
        viewModel.getRaffleById(raffleId)?.let {
            raffle = it; name = it.name; prize = it.prize; price = it.price; date = it.drawDate; lottery = it.lotteryName
            img = it.backgroundImagePath; scale = it.imageScale; tx = it.imageOffsetX; ty = it.imageOffsetY
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        img = uri.toString()
    }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Editar Rifa") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }, actions = {
        IconButton(onClick = {
            raffle?.let { viewModel.updateRaffle(it.copy(name=name, prize=prize, price=price, drawDate=date, lotteryName=lottery, backgroundImagePath=img, imageScale=scale, imageOffsetX=tx, imageOffsetY=ty)) }
            onBack()
        }) { Icon(Icons.Default.Save, null) }
    }) }) { padding ->
        if (raffle == null) Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        else {
            Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = prize, onValueChange = { prize = it }, label = { Text("Premio") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Precio") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Fecha Sorteo") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = lottery, onValueChange = { lottery = it }, label = { Text("Lotería") }, modifier = Modifier.fillMaxWidth())
                
                Text("Imagen de Fondo", fontWeight = FontWeight.Bold)
                Button(onClick = { launcher.launch(arrayOf("image/*")) }) { Text(if(img==null)"Seleccionar Imagen" else "Cambiar Imagen") }
                
                if (img != null) {
                    Box(Modifier.fillMaxWidth().height(250.dp).clip(MaterialTheme.shapes.medium).background(Color.Black).pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ -> scale *= zoom; tx += pan.x; ty += pan.y }
                    }) {
                        AsyncImage(model = img, contentDescription = null, modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = tx, translationY = ty), contentScale = ContentScale.Fit)
                    }
                    TextButton(onClick = { scale = 1f; tx = 0f; ty = 0f }) { Text("Resetear Posición") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
fun RaffleDetailScreen(raffleId: Long, viewModel: RaffleViewModel, onBack: () -> Unit, onViewBuyers: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var raffle by remember { mutableStateOf<Raffle?>(null) }
    val soldNumbers by viewModel.getSoldNumbers(raffleId).collectAsState(initial = emptyList())
    var selected by remember { mutableStateOf(setOf<String>()) }
    var showSell by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var bgScale by remember { mutableStateOf(1f) }
    var bgTx by remember { mutableStateOf(0f) }
    var bgTy by remember { mutableStateOf(0f) }

    val shareLayer = rememberGraphicsLayer()
    val raffleState by rememberUpdatedState(raffle)

    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val current = raffle ?: return@rememberLauncherForActivityResult
        val updated = current.copy(
            backgroundImagePath = uri.toString(),
            imageScale = 1f,
            imageOffsetX = 0f,
            imageOffsetY = 0f
        )
        raffle = updated
        bgScale = 1f
        bgTx = 0f
        bgTy = 0f
        viewModel.updateRaffle(updated)
    }

    LaunchedEffect(raffleId) { raffle = viewModel.getRaffleById(raffleId) }
    LaunchedEffect(raffle?.backgroundImagePath) {
        val r = raffle ?: return@LaunchedEffect
        bgScale = r.imageScale
        bgTx = r.imageOffsetX
        bgTy = r.imageOffsetY
    }
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(bgScale, bgTx, bgTy) }
            .debounce(300)
            .collectLatest { (s, x, y) ->
                val r = raffleState ?: return@collectLatest
                if (r.imageScale == s && r.imageOffsetX == x && r.imageOffsetY == y) return@collectLatest
                val updated = r.copy(imageScale = s, imageOffsetX = x, imageOffsetY = y)
                raffle = updated
                viewModel.updateRaffle(updated)
            }
    }

    val numbers = remember(raffle) {
        raffle?.let { r -> (r.rangeStart..r.rangeEnd).map { it.toString().padStart(r.digits, '0') } } ?: emptyList()
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(raffle?.name ?: "Detalle") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }, actions = {
            IconButton(onClick = onViewBuyers) { Icon(Icons.Default.Group, null) }
            IconButton(onClick = { backgroundPicker.launch(arrayOf("image/*")) }) { Icon(Icons.Default.Image, null) }
            if (raffle?.backgroundImagePath != null) {
                IconButton(onClick = {
                    val current = raffle ?: return@IconButton
                    bgScale = 1f
                    bgTx = 0f
                    bgTy = 0f
                    val updated = current.copy(imageScale = 1f, imageOffsetX = 0f, imageOffsetY = 0f)
                    raffle = updated
                    viewModel.updateRaffle(updated)
                    Toast.makeText(context, "Fondo reseteado", Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Default.CenterFocusStrong, null) }
            }
            IconButton(onClick = { showShare = true }) { Icon(Icons.Default.Share, null) }
        }) },
        floatingActionButton = { if(selected.isNotEmpty()) ExtendedFloatingActionButton(onClick = { showSell = true }, icon = { Icon(Icons.Default.Check, null) }, text = { Text("Vender ${selected.size}") }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(12.dp)) {
                    Text(raffle?.name ?: "", fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (raffle?.drawDate?.isNotBlank() == true) Text("Fecha: ${raffle?.drawDate}", fontSize = 12.sp, color = Color.Gray)
                        if (raffle?.lotteryName?.isNotBlank() == true) Text("Lotería: ${raffle?.lotteryName}", fontSize = 12.sp, color = Color.Gray)
                    }
                    if (raffle?.prize?.isNotBlank() == true) Text("Premio: ${raffle?.prize}", color = LogoBlue, fontSize = 12.sp)
                }
            }

            Text("Toca números para vender:", Modifier.padding(horizontal = 16.dp), color = Color.Gray)

            Box(Modifier.fillMaxSize().padding(12.dp)) {
                if (raffle?.backgroundImagePath != null) {
                    AsyncImage(
                        model = raffle?.backgroundImagePath,
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    bgScale *= zoom
                                    bgTx += pan.x
                                    bgTy += pan.y
                                }
                            }
                            .graphicsLayer(
                                scaleX = bgScale,
                                scaleY = bgScale,
                                translationX = bgTx,
                                translationY = bgTy
                            ),
                        contentScale = ContentScale.Crop,
                        alpha = 0.45f
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(numbers) { num ->
                        val isSold = soldNumbers.any { it.number == num }
                        val isSel = selected.contains(num)
                        val hasBg = raffle?.backgroundImagePath != null
                        Card(
                            modifier = Modifier.aspectRatio(1f).combinedClickable {
                                if (!isSold) selected = if (isSel) selected - num else selected + num
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isSel -> LogoBlue
                                    isSold -> Color.LightGray.copy(alpha = if (hasBg) 0.85f else 1f)
                                    else -> Color.White.copy(alpha = if (hasBg) 0.85f else 1f)
                                }
                            ),
                            border = BorderStroke(1.dp, Color.LightGray)
                        ) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Text(num, color = if (isSel) Color.White else Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSell) {
        var bName by remember { mutableStateOf("") }
        var bPhone by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSell = false },
            title = { Text("Vender") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = bName, onValueChange = { bName = it }, label = { Text("Nombre Comprador") })
                    OutlinedTextField(
                        value = bPhone,
                        onValueChange = { bPhone = it },
                        label = { Text("Teléfono (opcional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.sellNumbers(raffleId, selected.toList(), bName, bPhone)
                    selected = emptySet()
                    showSell = false
                }) { Text("Vender") }
            },
            dismissButton = { TextButton(onClick = { showSell = false }) { Text("Cancelar") } }
        )
    }

    if (showShare) {
        AlertDialog(
            onDismissRequest = { showShare = false },
            title = { Text("Compartir") },
            text = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .drawWithContent { shareLayer.record { this@drawWithContent.drawContent() } }
                ) {
                    RaffleShareContent(raffle, numbers, soldNumbers)
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val bitmap = shareLayer.toImageBitmap().asAndroidBitmap()
                        shareBitmap(context, bitmap, raffle?.name ?: "Rifa")
                        showShare = false
                    }
                }) { Text("Compartir") }
            },
            dismissButton = { TextButton(onClick = { showShare = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun RaffleShareContent(raffle: Raffle?, numbers: List<String>, sold: List<SoldNumber>) {
    Box(Modifier.fillMaxSize().background(Color.White)) {
        if (raffle?.backgroundImagePath != null) {
            AsyncImage(
                model = raffle.backgroundImagePath,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer(
                        scaleX = raffle.imageScale,
                        scaleY = raffle.imageScale,
                        translationX = raffle.imageOffsetX,
                        translationY = raffle.imageOffsetY
                    ),
                contentScale = ContentScale.Crop,
                alpha = 0.45f
            )
        }
        Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.78f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        raffle?.name ?: "",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    if (raffle?.prize?.isNotBlank() == true) {
                        Text("Premio: ${raffle.prize}", color = LogoBlue)
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                        if (raffle?.drawDate?.isNotBlank() == true) {
                            Text("Fecha: ${raffle.drawDate}", fontSize = 12.sp, color = Color.DarkGray)
                        }
                        if (raffle?.lotteryName?.isNotBlank() == true) {
                            Text("Lotería: ${raffle.lotteryName}", fontSize = 12.sp, color = Color.DarkGray)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            // Cuadrícula compacta de números
            Column(Modifier.fillMaxWidth()) {
                numbers.chunked(10).forEach { row ->
                    Row(Modifier.fillMaxWidth(), Arrangement.Center) {
                        row.forEach { num ->
                            val isSold = sold.any { it.number == num }
                            Text(num, Modifier.padding(2.dp).background(if(isSold) Color.Red.copy(0.2f) else Color.Transparent), fontSize = 10.sp, fontWeight = if(isSold) FontWeight.Bold else FontWeight.Normal, color = if(isSold) Color.Red else Color.Black)
                        }
                    }
                }
            }
        }
    }
}

private fun shareBitmap(context: Context, bitmap: Bitmap, name: String) {
    val file = File(context.cacheDir, "rifa.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "com.example.rifas.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    context.startActivity(Intent.createChooser(intent, "Compartir $name"))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClientsScreen(
    viewModel: RaffleViewModel,
    onBack: () -> Unit,
    onOpenRaffle: (Long) -> Unit,
    raffleIdFilter: Long?
) {
    val raffles by viewModel.raffles.collectAsState(initial = emptyList())
    val sold by viewModel.allSoldNumbers.collectAsState(initial = emptyList())

    val raffleById = remember(raffles) { raffles.associateBy { it.id } }
    val soldFiltered = remember(sold, raffleIdFilter) {
        if (raffleIdFilter == null) sold else sold.filter { it.raffleId == raffleIdFilter }
    }
    val grouped = remember(soldFiltered) {
        soldFiltered.groupBy { it.buyerName to it.buyerPhone }
            .toList()
            .sortedBy { (key, _) -> key.first.lowercase() }
    }

    var pendingPay by remember { mutableStateOf<Triple<Long, Pair<String, String>, Boolean>?>(null) }
    var pendingDelete by remember { mutableStateOf<Pair<Long, Pair<String, String>>?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (raffleIdFilter == null) "Clientes" else "Ventas", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(grouped, key = { it.first.first + "|" + it.first.second }) { (key, items) ->
                val buyerName = key.first
                val buyerPhone = key.second

                val byRaffle = remember(items) { items.groupBy { it.raffleId } }
                val paidTotal = remember(items, raffleById) {
                    byRaffle.entries.sumOf { (raffleId, soldList) ->
                        val price = parsePriceToDouble(raffleById[raffleId]?.price.orEmpty())
                        soldList.count { it.isPaid } * price
                    }
                }
                val pendingTotal = remember(items, raffleById) {
                    byRaffle.entries.sumOf { (raffleId, soldList) ->
                        val price = parsePriceToDouble(raffleById[raffleId]?.price.orEmpty())
                        soldList.count { !it.isPaid } * price
                    }
                }

                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(buyerName, fontWeight = FontWeight.Bold)
                                if (buyerPhone.isNotBlank()) Text(buyerPhone, color = Color.Gray, fontSize = 12.sp)
                            }
                            Text(formatMoney(paidTotal), fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Por cobrar: ${formatMoney(pendingTotal)}", color = Color(0xFFF57C00), fontSize = 12.sp)
                            Text("Números: ${items.size}", color = Color.Gray, fontSize = 12.sp)
                        }

                        byRaffle.toList()
                            .sortedBy { (raffleId, _) -> raffleById[raffleId]?.name.orEmpty().lowercase() }
                            .forEach { (raffleId, soldList) ->
                                val raffleName = raffleById[raffleId]?.name ?: "Rifa #$raffleId"
                                val numbers = remember(soldList) { soldList.map { it.number }.sorted() }
                                val isPaid = remember(soldList) { soldList.all { it.isPaid } }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(raffleName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    IconButton(onClick = { pendingPay = Triple(raffleId, buyerName to buyerPhone, !isPaid) }) {
                                        Icon(
                                            if (isPaid) Icons.Default.Check else Icons.Default.AttachMoney,
                                            null,
                                            tint = if (isPaid) Color(0xFF2E7D32) else Color(0xFFF57C00)
                                        )
                                    }
                                    IconButton(onClick = { pendingDelete = raffleId to (buyerName to buyerPhone) }) {
                                        Icon(Icons.Default.Delete, null, tint = Color.Red)
                                    }
                                    IconButton(onClick = { onOpenRaffle(raffleId) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) }
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    numbers.forEach { num -> AssistChip(onClick = {}, label = { Text(num) }) }
                                }
                            }
                    }
                }
            }
        }
    }

    pendingPay?.let { (raffleId, key, newPaid) ->
        val name = key.first
        val phone = key.second
        val raffleName = raffleById[raffleId]?.name ?: "Rifa #$raffleId"
        AlertDialog(
            onDismissRequest = { pendingPay = null },
            title = { Text(if (newPaid) "Marcar pago" else "Quitar pago") },
            text = { Text("¿Seguro que quieres ${if (newPaid) "marcar como pagado" else "marcar como pendiente"} a $name en $raffleName?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.setPaidForBuyer(raffleId, name, phone, newPaid)
                    pendingPay = null
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { pendingPay = null }) { Text("Cancelar") } }
        )
    }

    pendingDelete?.let { (raffleId, key) ->
        val name = key.first
        val phone = key.second
        val raffleName = raffleById[raffleId]?.name ?: "Rifa #$raffleId"
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar") },
            text = { Text("¿Seguro que quieres eliminar las ventas de $name en $raffleName?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteSoldNumbersForBuyer(raffleId, name, phone)
                    pendingDelete = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } }
        )
    }
}
