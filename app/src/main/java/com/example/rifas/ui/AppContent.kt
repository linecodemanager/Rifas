package com.example.rifas.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.rifas.data.Raffle
import com.example.rifas.data.SoldNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.Locale

import androidx.compose.ui.graphics.Brush
import com.example.rifas.ui.theme.*

@Composable
fun RifasApp(viewModel: RaffleViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "intro") {
        composable("intro") {
            IntroScreen(onFinished = {
                navController.navigate("dashboard") {
                    popUpTo("intro") { inclusive = true }
                }
            })
        }
        composable("dashboard") {
            DashboardScreen(
                viewModel = viewModel,
                onNavigateToRaffles = { navController.navigate("list") },
                onNavigateToBuyers = { navController.navigate("global_buyers") }
            )
        }
        composable("global_buyers") {
            GlobalBuyersScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("list") {
            RaffleListScreen(
                viewModel = viewModel,
                onCreateRaffle = { navController.navigate("create") },
                onRaffleClick = { raffle -> navController.navigate("detail/${raffle.id}") },
                onBack = { navController.popBackStack() }
            )
        }
        composable("create") {
            CreateRaffleScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            "detail/{raffleId}",
            arguments = listOf(navArgument("raffleId") { type = NavType.LongType })
        ) { backStackEntry ->
            val raffleId = backStackEntry.arguments?.getLong("raffleId") ?: 0L
            RaffleDetailScreen(
                raffleId = raffleId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onViewBuyers = { navController.navigate("buyers/$raffleId") }
            )
        }
        composable(
            "buyers/{raffleId}",
            arguments = listOf(navArgument("raffleId") { type = NavType.LongType })
        ) { backStackEntry ->
            val raffleId = backStackEntry.arguments?.getLong("raffleId") ?: 0L
            BuyersListScreen(
                raffleId = raffleId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GlobalBuyersScreen(
    viewModel: RaffleViewModel,
    onBack: () -> Unit
) {
    val allSoldNumbers by viewModel.allSoldNumbers.collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("Todos") } // "Todos", "Pagos", "No Pagos"

    // Estados para los diálogos
    var selectedBuyerForNumbers by remember { mutableStateOf<BuyerSummary?>(null) }
    var selectedBuyerForPayment by remember { mutableStateOf<BuyerSummary?>(null) }
    var showConfirmPaymentDialog by remember { mutableStateOf<BuyerSummary?>(null) }

    // Agrupar por comprador globalmente (nombre + teléfono)
    val buyerGroups = remember(allSoldNumbers) {
        allSoldNumbers.groupBy { it.buyerName.lowercase().trim() + it.buyerPhone.trim() }
            .map { (_, numbers) ->
                BuyerSummary(
                    name = numbers.first().buyerName,
                    phone = numbers.first().buyerPhone,
                    soldNumbers = numbers
                )
            }
            .sortedBy { it.name }
    }

    val filteredList = buyerGroups.filter { buyer ->
        val matchesSearch = buyer.name.contains(searchQuery, ignoreCase = true) || 
                          buyer.soldNumbers.any { it.number.contains(searchQuery) }
        val matchesFilter = when (filterType) {
            "Pagos" -> buyer.isFullyPaid
            "No Pagos" -> !buyer.isFullyPaid
            else -> true
        }
        matchesSearch && matchesFilter
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text(text = "Lista Global de Clientes") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                        }
                    }
                )
                // Barra de búsqueda y filtros
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar cliente o número") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Todos", "Pagos", "No Pagos").forEach { type ->
                            FilterChip(
                                selected = filterType == type,
                                onClick = { filterType = type },
                                label = { Text(type) }
                            )
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    ) { innerPadding ->
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotBlank() || filterType != "Todos") 
                        "No se encontraron clientes" else "Aún no hay ventas registradas",
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredList) { buyer ->
                    BuyerListItem(
                        buyer = buyer,
                        onViewNumbers = { selectedBuyerForNumbers = buyer },
                        onClick = { 
                            if (!buyer.isFullyPaid) {
                                selectedBuyerForPayment = buyer 
                            }
                        },
                        onDelete = { 
                            buyer.soldNumbers.forEach { viewModel.deleteSoldNumber(it) }
                        }
                    )
                }
            }
        }
    }

    // Modal para ver números comprados
    selectedBuyerForNumbers?.let { buyer ->
        AlertDialog(
            onDismissRequest = { selectedBuyerForNumbers = null },
            title = { Text("Números de ${buyer.name}") },
            text = {
                Column {
                    Text("Este cliente tiene los siguientes números:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        buyer.soldNumbers.forEach { sold ->
                            SuggestionChip(
                                onClick = { },
                                label = { Text(sold.number) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (sold.isPaid) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                    labelColor = if (sold.isPaid) Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedBuyerForNumbers = null }) {
                    Text("Cerrar")
                }
            }
        )
    }

    // Modal para elegir pago
    selectedBuyerForPayment?.let { buyer ->
        AlertDialog(
            onDismissRequest = { selectedBuyerForPayment = null },
            title = { Text("Actualizar pago") },
            text = {
                Column {
                    Text("Cliente: ${buyer.name}")
                    Text("Números: ${buyer.numbersListText}")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            selectedBuyerForPayment = null
                            showConfirmPaymentDialog = buyer
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("Marcar como PAGADO")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedBuyerForPayment = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Confirmación irreversible
    showConfirmPaymentDialog?.let { buyer ->
        AlertDialog(
            onDismissRequest = { showConfirmPaymentDialog = null },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(48.dp)) },
            title = { Text("¿Confirmar pago?") },
            text = {
                Text(
                    "¿Estás seguro de marcar todos los números de ${buyer.name} como PAGADOS? Esta acción no se puede deshacer.",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        buyer.soldNumbers.forEach { sold ->
                            if (!sold.isPaid) {
                                viewModel.updateSoldNumber(sold.copy(isPaid = true))
                            }
                        }
                        showConfirmPaymentDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("SÍ, CONFIRMAR")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmPaymentDialog = null }) {
                    Text("CANCELAR")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BuyersListScreen(raffleId: Long, viewModel: RaffleViewModel, onBack: () -> Unit) {
    val soldNumbers by viewModel.getSoldNumbers(raffleId).collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("Todos") } // "Todos", "Pagos", "No Pagos"
    var raffleName by remember { mutableStateOf("") }
    
    // Estados para los diálogos
    var selectedBuyerForNumbers by remember { mutableStateOf<BuyerSummary?>(null) }
    var selectedBuyerForPayment by remember { mutableStateOf<BuyerSummary?>(null) }
    var showConfirmPaymentDialog by remember { mutableStateOf<BuyerSummary?>(null) }

    LaunchedEffect(raffleId) {
        raffleName = viewModel.getRaffleById(raffleId)?.name ?: "Compradores"
    }

    // Agrupar por comprador
    val buyerGroups = remember(soldNumbers) {
        soldNumbers.groupBy { it.buyerName + it.buyerPhone }
            .map { (_, numbers) ->
                BuyerSummary(
                    name = numbers.first().buyerName,
                    phone = numbers.first().buyerPhone,
                    soldNumbers = numbers
                )
            }
    }

    val filteredList = buyerGroups.filter { buyer ->
        val matchesSearch = buyer.name.contains(searchQuery, ignoreCase = true) || 
                          buyer.soldNumbers.any { it.number.contains(searchQuery) }
        val matchesFilter = when (filterType) {
            "Pagos" -> buyer.isFullyPaid
            "No Pagos" -> !buyer.isFullyPaid
            else -> true
        }
        matchesSearch && matchesFilter
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text(text = raffleName) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                        }
                    }
                )
                // Search and Filter Bar
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar por nombre o número") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Todos", "Pagos", "No Pagos").forEach { type ->
                            FilterChip(
                                selected = filterType == type,
                                onClick = { filterType = type },
                                label = { Text(type) }
                            )
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    ) { innerPadding ->
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotBlank() || filterType != "Todos") 
                        "No se encontraron resultados" else "Aún no hay ventas",
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredList) { buyer ->
                    BuyerListItem(
                        buyer = buyer,
                        onViewNumbers = { selectedBuyerForNumbers = buyer },
                        onClick = { 
                            if (!buyer.isFullyPaid) {
                                selectedBuyerForPayment = buyer 
                            }
                        },
                        onDelete = { 
                            buyer.soldNumbers.forEach { viewModel.deleteSoldNumber(it) }
                        }
                    )
                }
            }
        }
    }

    // Modal para ver números comprados
    selectedBuyerForNumbers?.let { buyer ->
        AlertDialog(
            onDismissRequest = { selectedBuyerForNumbers = null },
            title = { Text("Números de ${buyer.name}") },
            text = {
                Column {
                    Text("Este comprador adquirió los siguientes números:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        buyer.soldNumbers.forEach { sold ->
                            SuggestionChip(
                                onClick = { },
                                label = { Text(sold.number) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (sold.isPaid) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                    labelColor = if (sold.isPaid) Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedBuyerForNumbers = null }) {
                    Text("Cerrar")
                }
            }
        )
    }

    // Modal para elegir pago
    selectedBuyerForPayment?.let { buyer ->
        AlertDialog(
            onDismissRequest = { selectedBuyerForPayment = null },
            title = { Text("Actualizar pago") },
            text = {
                Column {
                    Text("Comprador: ${buyer.name}")
                    Text("Números: ${buyer.numbersListText}")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            selectedBuyerForPayment = null
                            showConfirmPaymentDialog = buyer
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("Marcar como PAGADO")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedBuyerForPayment = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Confirmación irreversible (Swal style)
    showConfirmPaymentDialog?.let { buyer ->
        AlertDialog(
            onDismissRequest = { showConfirmPaymentDialog = null },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(48.dp)) },
            title = { Text("¿Confirmar pago?") },
            text = {
                Text(
                    "¿Estás seguro de marcar todos los números de ${buyer.name} como PAGADOS? Esta acción no se puede deshacer.",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        buyer.soldNumbers.forEach { sold ->
                            if (!sold.isPaid) {
                                viewModel.updateSoldNumber(sold.copy(isPaid = true))
                            }
                        }
                        showConfirmPaymentDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("SÍ, CONFIRMAR")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmPaymentDialog = null }) {
                    Text("CANCELAR")
                }
            }
        )
    }
}

data class BuyerSummary(
    val name: String,
    val phone: String,
    val soldNumbers: List<SoldNumber>
) {
    val isFullyPaid: Boolean get() = soldNumbers.all { it.isPaid }
    val numbersListText: String get() = soldNumbers.joinToString(", ") { it.number }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BuyerListItem(
    buyer: BuyerSummary,
    onViewNumbers: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (buyer.isFullyPaid) 
                MaterialTheme.colorScheme.surface 
            else 
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (buyer.isFullyPaid) Color(0xFF4CAF50) else Color(0xFFF44336),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = if (buyer.soldNumbers.size == 1) 
                                buyer.soldNumbers.first().number 
                            else 
                                "${buyer.soldNumbers.size} Núm",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = buyer.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = buyer.phone.ifBlank { "Sin teléfono" },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Row {
                IconButton(onClick = onViewNumbers) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Ver números",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (!buyer.isFullyPaid) {
                    IconButton(onClick = onClick) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = "Pendiente de pago",
                            tint = Color(0xFFF44336)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Pagado",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.padding(12.dp)
                    )
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar comprador",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar comprador") },
            text = { Text("¿Estás seguro de que deseas eliminar a ${buyer.name} y todos sus números comprados (${buyer.numbersListText})?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

fun shareRaffleImages(context: Context, bitmaps: List<Bitmap>, raffleName: String) {
    try {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        
        val uris = ArrayList<Uri>()
        
        bitmaps.forEachIndexed { index, bitmap ->
            val fileName = "raffle_share_$index.png"
            val file = File(cachePath, fileName)
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()
            
            val contentUri = FileProvider.getUriForFile(context, "com.example.rifas.fileprovider", file)
            if (contentUri != null) {
                uris.add(contentUri)
            }
        }

        if (uris.isNotEmpty()) {
            val shareIntent = if (uris.size == 1) {
                Intent().apply {
                    action = Intent.ACTION_SEND
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                    type = "image/png"
                }
            } else {
                Intent().apply {
                    action = Intent.ACTION_SEND_MULTIPLE
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    type = "image/png"
                }
            }
            context.startActivity(Intent.createChooser(shareIntent, "Compartir Rifa: $raffleName"))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun RaffleShareContent(
    raffle: Raffle,
    numberItems: List<String>,
    soldNumbers: List<SoldNumber>,
    columnsCount: Int,
    pageInfo: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White) // Usar fondo blanco para la imagen compartida
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (pageInfo != null) {
            Text(
                text = pageInfo,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.End)
            )
        }
        Text(
            text = raffle.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center
        )
        if (raffle.prize.isNotBlank()) {
            Text(
                text = "Premio: ${raffle.prize}",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF1976D2), // Un azul estándar
                textAlign = TextAlign.Center
            )
        }
        if (raffle.price.isNotBlank()) {
            Text(
                text = "Valor número: ${raffle.price}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (raffle.drawDate.isNotBlank()) {
                Text(
                    text = "Fecha: ${raffle.drawDate}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.DarkGray
                )
            }
            if (raffle.lotteryName.isNotBlank()) {
                Text(
                    text = "Lotería: ${raffle.lotteryName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.DarkGray
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = Color.LightGray)
        Spacer(modifier = Modifier.height(16.dp))

        // Grid de números no perezoso (Lazy) para capturar todo
        val rows = numberItems.chunked(columnsCount)
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            rows.forEach { rowItems ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowItems.forEach { number ->
                        val isSold = soldNumbers.any { it.number == number }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(
                                    if (isSold) Color(0xFFFFCDD2) else Color(0xFFF5F5F5),
                                    shape = MaterialTheme.shapes.small
                                )
                                .border(
                                    1.dp,
                                    if (isSold) Color(0xFFEF5350) else Color(0xFFE0E0E0),
                                    shape = MaterialTheme.shapes.small
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = number,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (columnsCount > 6) 10.sp else 12.sp,
                                    color = if (isSold) Color(0xFFC62828) else Color.Black
                                )
                                if (isSold) {
                                    Text(
                                        text = "Vendido",
                                        fontSize = 6.sp,
                                        color = Color(0xFFC62828)
                                    )
                                }
                            }
                        }
                    }
                    // Rellenar espacios vacíos en la última fila si es necesario
                    repeat(columnsCount - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Generado por Rifas App",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
    }
}

@Composable
fun PageCapturer(
    pageNumbers: List<String>,
    raffle: Raffle,
    soldNumbers: List<SoldNumber>,
    columnsCount: Int,
    pageInfo: String?,
    onLayerCreated: (GraphicsLayer) -> Unit
) {
    val layer = rememberGraphicsLayer()
    LaunchedEffect(layer) {
        onLayerCreated(layer)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .drawWithContent {
                layer.record {
                    this@drawWithContent.drawContent()
                }
            }
    ) {
        RaffleShareContent(
            raffle = raffle,
            numberItems = pageNumbers,
            soldNumbers = soldNumbers,
            columnsCount = columnsCount,
            pageInfo = pageInfo
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RaffleDetailScreen(raffleId: Long, viewModel: RaffleViewModel, onBack: () -> Unit, onViewBuyers: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var raffle by remember { mutableStateOf<Raffle?>(null) }
    val soldNumbers by viewModel.getSoldNumbers(raffleId).collectAsState(initial = emptyList())
    var selectedNumbers by remember { mutableStateOf(setOf<String>()) }
    var isMultiSelectActive by remember { mutableStateOf(false) }
    var showSellDialog by remember { mutableStateOf(false) }
    var showBuyerInfoDialog by remember { mutableStateOf<SoldNumber?>(null) }

    val buyerName = remember { mutableStateOf("") }
    val buyerPhone = remember { mutableStateOf("") }

    LaunchedEffect(raffleId) {
        raffle = viewModel.getRaffleById(raffleId)
    }

    val numberItems = remember(raffle) {
        val r = raffle
        if (r != null) {
            (r.rangeStart..r.rangeEnd).map { value ->
                if (r.digits > 0) value.toString().padStart(r.digits, '0') else value.toString()
            }
        } else {
            emptyList<String>()
        }
    }

    val columnsCount = when {
        numberItems.size <= 20 -> 4
        numberItems.size <= 50 -> 6
        else -> 8
    }

    val maxNumbersPerPage = 100
    val numberPages = remember(numberItems) { numberItems.chunked(maxNumbersPerPage) }
    val graphicsLayers = remember { mutableStateMapOf<Int, GraphicsLayer>() }

    // Este Box contiene el contenido que se compartirá. 
    // Se renderiza fuera de la vista del usuario pero permite ser capturado.
    if (raffle != null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .alpha(0f) // Invisible para el usuario
        ) {
            numberPages.forEachIndexed { index, pageNumbers ->
                PageCapturer(
                    pageNumbers = pageNumbers,
                    raffle = raffle!!,
                    soldNumbers = soldNumbers,
                    columnsCount = columnsCount,
                    pageInfo = if (numberPages.size > 1) "Página ${index + 1}/${numberPages.size}" else null,
                    onLayerCreated = { layer -> graphicsLayers[index] = layer }
                )
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = raffle?.name ?: "Detalle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    if (raffle != null) {
                        IconButton(onClick = onViewBuyers) {
                            Icon(Icons.Default.Group, contentDescription = "Ver Compradores")
                        }
                        IconButton(onClick = {
                            coroutineScope.launch {
                                try {
                                    val bitmaps = mutableListOf<Bitmap>()
                                    graphicsLayers.keys.sorted().forEach { key ->
                                        graphicsLayers[key]?.let { layer ->
                                            bitmaps.add(layer.toImageBitmap().asAndroidBitmap())
                                        }
                                    }
                                    if (bitmaps.isNotEmpty()) {
                                        shareRaffleImages(context, bitmaps, raffle!!.name)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Compartir")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (isMultiSelectActive && selectedNumbers.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showSellDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(text = "Vender (${selectedNumbers.size})") }
                )
            }
        }
    ) { innerPadding ->
        if (raffle == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Info header for sharing
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = raffle!!.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    if (raffle!!.prize.isNotBlank()) {
                        Text(
                            text = "Premio: ${raffle!!.prize}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (raffle!!.drawDate.isNotBlank()) {
                            Text(
                                text = "Fecha: ${raffle!!.drawDate}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (raffle!!.lotteryName.isNotBlank()) {
                            Text(
                                text = "Lotería: ${raffle!!.lotteryName}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = if (isMultiSelectActive) "Seleccionando números..." else "Toca un número para vender o mantén presionado para selección múltiple",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isMultiSelectActive) MaterialTheme.colorScheme.primary else Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columnsCount),
                        contentPadding = PaddingValues(4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(numberItems) { number ->
                            val soldInfo = soldNumbers.find { it.number == number }
                            val isSold = soldInfo != null
                            val isSelected = selectedNumbers.contains(number)

                            Card(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .combinedClickable(
                                        onClick = {
                                            if (isSold) {
                                                showBuyerInfoDialog = soldInfo
                                            } else if (isMultiSelectActive) {
                                                selectedNumbers = if (isSelected) {
                                                    selectedNumbers - number
                                                } else {
                                                    selectedNumbers + number
                                                }
                                            } else {
                                                selectedNumbers = setOf(number)
                                                showSellDialog = true
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSold) {
                                                isMultiSelectActive = true
                                                selectedNumbers = selectedNumbers + number
                                            }
                                        }
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        isSold -> MaterialTheme.colorScheme.errorContainer
                                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = number,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = if (columnsCount > 6) 12.sp else 16.sp
                                        )
                                        if (isSold) {
                                            Text(
                                                text = "Vendido",
                                                fontSize = 8.sp,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSellDialog) {
        AlertDialog(
            onDismissRequest = {
                showSellDialog = false
                if (!isMultiSelectActive) selectedNumbers = emptySet()
            },
            title = { Text(text = "Vender número(s)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Números: ${selectedNumbers.joinToString(", ")}")
                    OutlinedTextField(
                        value = buyerName.value,
                        onValueChange = { buyerName.value = it },
                        label = { Text("Nombre del comprador") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = buyerPhone.value,
                        onValueChange = { buyerPhone.value = it },
                        label = { Text("Teléfono") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.sellNumbers(
                            raffleId = raffleId,
                            numbers = selectedNumbers.toList(),
                            buyerName = buyerName.value,
                            buyerPhone = buyerPhone.value
                        )
                        showSellDialog = false
                        isMultiSelectActive = false
                        selectedNumbers = emptySet()
                        buyerName.value = ""
                        buyerPhone.value = ""
                    },
                    enabled = buyerName.value.isNotBlank()
                ) {
                    Text("Vender")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSellDialog = false
                    if (!isMultiSelectActive) selectedNumbers = emptySet()
                }) {
                    Text("Cancelar")
                }
            }
        )
    }

    showBuyerInfoDialog?.let { sold ->
        AlertDialog(
            onDismissRequest = { showBuyerInfoDialog = null },
            title = { Text(text = "Información del comprador") },
            text = {
                Column {
                    Text(text = "Número: ${sold.number}", fontWeight = FontWeight.Bold)
                    Text(text = "Comprador: ${sold.buyerName}")
                    Text(text = "Teléfono: ${sold.buyerPhone}")
                }
            },
            confirmButton = {
                TextButton(onClick = { showBuyerInfoDialog = null }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: RaffleViewModel,
    onNavigateToRaffles: () -> Unit,
    onNavigateToBuyers: () -> Unit
) {
    val context = LocalContext.current
    val raffles by viewModel.raffles.collectAsState(initial = emptyList())
    val allSoldNumbers by viewModel.allSoldNumbers.collectAsState(initial = emptyList())
    val updateAvailable by viewModel.updateAvailable
    val isDownloading by viewModel.isDownloading
    
    // Verificar actualizaciones al iniciar el panel
    LaunchedEffect(Unit) {
        viewModel.checkForUpdates()
    }

    // Diálogo de descarga en curso
    if (isDownloading) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Descargando...", fontWeight = FontWeight.Bold) },
            text = { 
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Por favor espera mientras se descarga la nueva versión.")
                }
            },
            confirmButton = { }
        )
    }

    // Diálogo de actualización
    if (updateAvailable != null && !isDownloading) {
        AlertDialog(
            onDismissRequest = { viewModel.updateAvailable.value = null },
            title = { Text("Actualización Disponible", fontWeight = FontWeight.Bold) },
            text = { 
                Column {
                    Text("Nueva versión: ${updateAvailable?.versionName}", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(updateAvailable?.description ?: "Hay mejoras y correcciones disponibles.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.downloadUpdate(context, updateAvailable?.updateUrl ?: "")
                        viewModel.updateAvailable.value = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Actualizar Ahora")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.updateAvailable.value = null }) {
                    Text("Más Tarde")
                }
            }
        )
    }

    // Cálculos de estadísticas
    val activeRaffles = raffles.filter { it.isActive }
    
    // Ganancia Bruta: Total de números vendidos * precio (de todas las rifas activas)
    val totalGrossRevenue = activeRaffles.sumOf { raffle ->
        val pricePerNum = raffle.price.toDoubleOrNull() ?: 0.0
        val soldCount = allSoldNumbers.count { it.raffleId == raffle.id }
        soldCount * pricePerNum
    }

    // Ganancia Neta Actual: Solo números PAGADOS de rifas activas
    val totalNetRevenue = activeRaffles.sumOf { raffle ->
        val pricePerNum = raffle.price.toDoubleOrNull() ?: 0.0
        val paidCount = allSoldNumbers.count { it.raffleId == raffle.id && it.isPaid }
        paidCount * pricePerNum
    }

    // Ganancia Potencial Total: Todos los números generados * precio (de todas las rifas activas)
    val totalPotentialRevenue = activeRaffles.sumOf { raffle ->
        val pricePerNum = raffle.price.toDoubleOrNull() ?: 0.0
        val totalNumbers = (raffle.rangeEnd - raffle.rangeStart + 1)
        totalNumbers * pricePerNum
    }

    // Clientes más fieles (Top 10)
    val loyalClients = remember(allSoldNumbers) {
        allSoldNumbers.groupBy { it.buyerPhone.trim() }
            .map { (phone, numbers) ->
                val name = numbers.first().buyerName
                name to numbers.size
            }
            .sortedByDescending { it.second }
            .take(10)
    }

    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("es", "CO"))

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { 
                    Text(
                        "Panel Principal", 
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.headlineMedium
                    ) 
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = LogoBlue,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(LogoBlue.copy(alpha = 0.05f), BackgroundLight)
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tarjeta de Ganancias
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(LogoBlue, LogoCyan)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Text(
                        "Resumen de Ganancias", 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Ganancia Bruta", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                            Text(currencyFormatter.format(totalGrossRevenue), style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.ExtraBold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Ganancia Neta", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                            Text(currencyFormatter.format(totalNetRevenue), style = MaterialTheme.typography.headlineMedium, color = LogoYellow, fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Ganancia Potencial (Total Generado)
                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Meta Total (Números Generados):",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                currencyFormatter.format(totalPotentialRevenue),
                                style = MaterialTheme.typography.bodyLarge,
                                color = LogoYellow,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // "Gráfica" mejorada
                    val progress = if (totalGrossRevenue > 0) (totalNetRevenue / totalGrossRevenue).toFloat() else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .background(Color.White.copy(alpha = 0.3f), MaterialTheme.shapes.extraLarge)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(listOf(LogoYellow, LogoOrange)),
                                    MaterialTheme.shapes.extraLarge
                                )
                        )
                    }
                    Text(
                        text = "${(progress * 100).toInt()}% recaudado",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Accesos Rápidos
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DashboardActionCard(
                    title = "Mis Rifas",
                    subtitle = "${raffles.size} totales",
                    icon = Icons.Default.Casino,
                    modifier = Modifier.weight(1f),
                    color = LogoPink,
                    onClick = onNavigateToRaffles
                )
                DashboardActionCard(
                    title = "Clientes",
                    subtitle = "Ver lista",
                    icon = Icons.Default.Group,
                    modifier = Modifier.weight(1f),
                    color = LogoPurple,
                    onClick = onNavigateToBuyers
                )
            }

            // Gráfica de Clientes más fieles
            if (loyalClients.isNotEmpty()) {
                Text("Top 10 Clientes más Fieles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        loyalClients.forEachIndexed { index, client ->
                            val maxPurchases = loyalClients.first().second.toFloat()
                            val clientProgress = client.second / maxPurchases
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = LogoBlue,
                                    modifier = Modifier.width(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(client.first, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Text("${client.second} núm.", style = MaterialTheme.typography.bodySmall, color = LogoPink, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .background(BackgroundLight, MaterialTheme.shapes.extraLarge)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(clientProgress)
                                                .fillMaxHeight()
                                                .background(
                                                    Brush.horizontalGradient(
                                                        colors = listOf(LogoBlue, LogoCyan)
                                                    ),
                                                    MaterialTheme.shapes.extraLarge
                                                )
                                        )
                                    }
                                }
                            }
                            if (index < loyalClients.size - 1) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }

            // Lista de Rifas Activas y sus Pagos
            Text("Estado de Pagos por Rifa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            if (activeRaffles.isEmpty()) {
                Text("No hay rifas activas actualmente", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            } else {
                activeRaffles.forEach { raffle ->
                    val raffleSold = allSoldNumbers.filter { it.raffleId == raffle.id }
                    val rafflePaid = raffleSold.count { it.isPaid }
                    val rafflePrice = raffle.price.toDoubleOrNull() ?: 0.0
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(raffle.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("${raffleSold.size} números vendidos", style = MaterialTheme.typography.bodySmall)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(currencyFormatter.format(rafflePaid * rafflePrice), color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                Text("de ${currencyFormatter.format(raffleSold.size * rafflePrice)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    color: Color = LogoBlue,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.height(120.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(color, color.copy(alpha = 0.7f))
                    )
                )
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
        }
    }
}

@Composable
fun IntroScreen(onFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(targetValue = if (startAnimation) 1f else 0f)
    val scale by animateFloatAsState(targetValue = if (startAnimation) 1f else 0.9f)

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(1800)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(LogoBlue, LogoCyan)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(alpha)
                .scale(scale)
        ) {
            // Un círculo blanco suave detrás del texto o logo
            Surface(
                modifier = Modifier.size(120.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = Color.White.copy(alpha = 0.2f)
            ) {
                Icon(
                    Icons.Default.Casino,
                    contentDescription = null,
                    modifier = Modifier.padding(24.dp).size(64.dp),
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Rifas",
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tu suerte en tus manos",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RaffleListScreen(
    viewModel: RaffleViewModel,
    onCreateRaffle: () -> Unit,
    onRaffleClick: (Raffle) -> Unit,
    onBack: () -> Unit
) {
    val raffles by viewModel.raffles.collectAsState(initial = emptyList())
    var raffleToDelete by remember { mutableStateOf<Raffle?>(null) }
    var raffleToEditStatus by remember { mutableStateOf<Raffle?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Mis Rifas", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = LogoBlue,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateRaffle,
                containerColor = LogoYellow,
                contentColor = TextDark
            ) {
                Icon(Icons.Default.Add, contentDescription = "Crear rifa")
            }
        }
    ) { innerPadding ->
        if (raffles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(BackgroundLight),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "No tienes rifas creadas", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(BackgroundLight),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(raffles) { raffle ->
                    RaffleItem(
                        raffle = raffle,
                        onClick = { onRaffleClick(raffle) },
                        onLongClick = { raffleToDelete = raffle },
                        onEditStatus = { raffleToEditStatus = raffle }
                    )
                }
            }
        }
    }

    raffleToEditStatus?.let { raffle ->
        AlertDialog(
            onDismissRequest = { raffleToEditStatus = null },
            title = { Text("Estado de la rifa") },
            text = { Text("¿Deseas cambiar el estado de '${raffle.name}' a ${if (raffle.isActive) "INACTIVA" else "ACTIVA"}?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateRaffle(raffle.copy(isActive = !raffle.isActive))
                        raffleToEditStatus = null
                    }
                ) {
                    Text("Cambiar a ${if (raffle.isActive) "Inactiva" else "Activa"}")
                }
            },
            dismissButton = {
                TextButton(onClick = { raffleToEditStatus = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    raffleToDelete?.let { raffle ->
        AlertDialog(
            onDismissRequest = { raffleToDelete = null },
            title = { Text("Eliminar rifa") },
            text = { Text("¿Estás seguro de que deseas eliminar la rifa '${raffle.name}'? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRaffle(raffle)
                        raffleToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { raffleToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RaffleItem(raffle: Raffle, onClick: () -> Unit, onLongClick: () -> Unit, onEditStatus: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (raffle.isActive) LogoBlue.copy(alpha = 0.2f) else Color.LightGray)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono representativo
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.small,
                color = if (raffle.isActive) LogoBlue.copy(alpha = 0.1f) else Color.LightGray.copy(alpha = 0.1f)
            ) {
                Icon(
                    Icons.Default.Casino,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = if (raffle.isActive) LogoBlue else Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = raffle.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = if (raffle.isActive) LogoCyan else Color.Gray,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = if (raffle.isActive) "ACTIVA" else "INACTIVA",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = if (raffle.isActive) LogoPurple else Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Rango: ${raffle.rangeStart} - ${raffle.rangeEnd}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Text(
                    text = "Precio: ${raffle.price}",
                    style = MaterialTheme.typography.bodySmall,
                    color = LogoPink,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onEditStatus) {
                Icon(Icons.Default.Edit, contentDescription = "Editar estado", tint = LogoBlue)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRaffleScreen(viewModel: RaffleViewModel, onBack: () -> Unit) {
    val name = remember { mutableStateOf("") }
    val rangeStart = remember { mutableStateOf("") }
    val rangeEnd = remember { mutableStateOf("") }
    val drawDate = remember { mutableStateOf("") }
    val lotteryName = remember { mutableStateOf("") }
    val prize = remember { mutableStateOf("") }
    val price = remember { mutableStateOf("") }

    val parsedStart = rangeStart.value.toIntOrNull()
    val parsedEnd = rangeEnd.value.toIntOrNull()
    
    // Calcular cifras automáticamente basado en la longitud de lo ingresado
    val parsedDigits = remember(rangeStart.value, rangeEnd.value) {
        maxOf(rangeStart.value.length, rangeEnd.value.length)
    }

    val numberItems = remember(parsedStart, parsedEnd, parsedDigits) {
        if (parsedStart != null && parsedEnd != null && parsedEnd >= parsedStart) {
            (parsedStart..parsedEnd).map { value ->
                value.toString().padStart(parsedDigits, '0')
            }
        } else {
            emptyList<String>()
        }
    }

    val canSave = name.value.isNotBlank() && parsedStart != null && parsedEnd != null && parsedEnd >= parsedStart

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Crear rifa") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = name.value,
                    onValueChange = { name.value = it },
                    label = { Text("Nombre de la rifa") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rangeStart.value,
                        onValueChange = { rangeStart.value = it },
                        label = { Text("Inicio (ej: 00)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = rangeEnd.value,
                        onValueChange = { rangeEnd.value = it },
                        label = { Text("Fin (ej: 99)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = if (parsedDigits > 0) parsedDigits.toString() else "Automático",
                    onValueChange = { },
                    label = { Text("Cifras") },
                    readOnly = true,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = drawDate.value,
                    onValueChange = { drawDate.value = it },
                    label = { Text("Fecha del sorteo (ej: 24 Dic)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = lotteryName.value,
                    onValueChange = { lotteryName.value = it },
                    label = { Text("Lotería") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = prize.value,
                    onValueChange = { prize.value = it },
                    label = { Text("Premio(s)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = price.value,
                    onValueChange = { price.value = it },
                    label = { Text("Precio por número") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (numberItems.isNotEmpty()) {
                item {
                    Text(text = "Vista previa de números:", style = MaterialTheme.typography.titleSmall)
                }
                
                val columnsCount = when {
                    numberItems.size <= 20 -> 4
                    numberItems.size <= 50 -> 6
                    else -> 8
                }

                // Instead of a nested grid, we can use chunks of the list in items
                val chunks = numberItems.chunked(columnsCount)
                items(chunks) { rowItems ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        rowItems.forEach { number ->
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = number,
                                        fontSize = if (columnsCount > 6) 10.sp else 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        // Add empty boxes if the row is not full
                        repeat(columnsCount - rowItems.size) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        viewModel.addRaffle(
                            name.value,
                            parsedStart!!,
                            parsedEnd!!,
                            parsedDigits,
                            drawDate.value,
                            lotteryName.value,
                            prize.value,
                            price.value
                        )
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canSave
                ) {
                    Text("Guardar Rifa")
                }
            }
        }
    }
}
