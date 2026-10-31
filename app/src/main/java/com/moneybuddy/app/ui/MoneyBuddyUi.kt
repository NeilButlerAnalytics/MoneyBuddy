package com.moneybuddy.app.ui

import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moneybuddy.app.data.CsvFormatException
import com.moneybuddy.app.data.MonthSummary
import com.moneybuddy.app.data.ParsedStatement
import com.moneybuddy.app.data.Transaction
import com.moneybuddy.app.data.TransactionDao
import com.moneybuddy.app.data.demoTransactions
import com.moneybuddy.app.data.parseBankCsv
import com.moneybuddy.app.data.summariseLatestMonth
import java.text.NumberFormat
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Orange = Color(0xFFF28C28)
private val Green = Color(0xFF39B978)
private val Red = Color(0xFFE56B6F)

private val transactionCategories = listOf(
    "Groceries", "Shopping", "Fuel", "Transport", "Utilities", "Mortgage / Rent",
    "Insurance", "Eating out", "Entertainment", "Subscriptions", "Family", "Holidays", "Other",
)

class MoneyBuddyViewModel(
    private val dao: TransactionDao,
    private val prefs: SharedPreferences,
) : ViewModel() {
    val transactions: StateFlow<List<Transaction>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage

    private val _importedBalance = MutableStateFlow(readImportedBalance())
    val importedBalance: StateFlow<ImportedBalance?> = _importedBalance

    init {
        viewModelScope.launch {
            // Demo data is seeded once only, so deleting local data is not undone on the next launch.
            if (!prefs.getBoolean(KEY_DEMO_SEEDED, false)) {
                if (dao.count() == 0) dao.insertAll(demoTransactions)
                prefs.edit().putBoolean(KEY_DEMO_SEEDED, true).apply()
            }
        }
    }

    val summary: StateFlow<MonthSummary> = transactions.map { summariseLatestMonth(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthSummary("No data", 0, 0, emptyList()))

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch { dao.update(transaction) }
    }

    fun importCsv(text: String) {
        viewModelScope.launch {
            _importMessage.value = try {
                val statement = withContext(Dispatchers.Default) { parseBankCsv(text) }
                if (statement.transactions.isEmpty()) {
                    "No transactions were found in that file."
                } else {
                    val added = dao.insertIgnoring(statement.transactions).count { it != -1L }
                    dao.deleteDemo()
                    saveImportedBalance(statement)
                    val duplicates = statement.transactions.size - added
                    "Imported $added new transactions" +
                        (if (duplicates > 0) ", $duplicates already imported" else "") +
                        (if (statement.skippedRows > 0) ", ${statement.skippedRows} unreadable rows skipped" else "") +
                        "."
                }
            } catch (error: CsvFormatException) {
                error.message
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                "The file could not be imported."
            }
        }
    }

    fun reportImportProblem(message: String) {
        _importMessage.value = message
    }

    fun deleteAllLocalData() {
        viewModelScope.launch {
            dao.deleteAll()
            prefs.edit().remove(KEY_BALANCE).remove(KEY_BALANCE_DATE).apply()
            _importedBalance.value = null
            _importMessage.value = "All local transactions have been deleted."
        }
    }

    private fun readImportedBalance(): ImportedBalance? {
        val date = prefs.getString(KEY_BALANCE_DATE, null) ?: return null
        return if (prefs.contains(KEY_BALANCE)) ImportedBalance(prefs.getLong(KEY_BALANCE, 0), date) else null
    }

    private fun saveImportedBalance(statement: ParsedStatement) {
        val balance = statement.latestBalancePence ?: return
        val date = statement.latestBalanceDate ?: return
        // An older statement imported later must not replace a newer balance.
        val current = _importedBalance.value
        if (current != null && current.date > date) return
        prefs.edit().putLong(KEY_BALANCE, balance).putString(KEY_BALANCE_DATE, date).apply()
        _importedBalance.value = ImportedBalance(balance, date)
    }

    private companion object {
        const val KEY_DEMO_SEEDED = "demo_seeded"
        const val KEY_BALANCE = "imported_balance_pence"
        const val KEY_BALANCE_DATE = "imported_balance_date"
    }
}

data class ImportedBalance(val pence: Long, val date: String)

class MoneyBuddyViewModelFactory(
    private val dao: TransactionDao,
    private val prefs: SharedPreferences,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MoneyBuddyViewModel(dao, prefs) as T
}

@Composable
fun MoneyBuddyTheme(isDark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isDark) {
            androidx.compose.material3.darkColorScheme(
                primary = Orange,
                secondary = Green,
                background = Color(0xFF111827),
                surface = Color(0xFF1F2937),
                onBackground = Color(0xFFF9FAFB),
                onSurface = Color(0xFFF9FAFB),
            )
        } else {
            androidx.compose.material3.lightColorScheme(
                primary = Color(0xFFB85E00),
                secondary = Color(0xFF087A45),
                background = Color(0xFFF4F6F8),
                surface = Color.White,
                onBackground = Color(0xFF17202A),
                onSurface = Color(0xFF17202A),
                onSurfaceVariant = Color(0xFF53606D),
            )
        },
        content = content,
    )
}

@Composable
fun MoneyBuddyApp(
    viewModel: MoneyBuddyViewModel,
    isDarkTheme: Boolean,
    onThemeChanged: (Boolean) -> Unit,
) {
    val transactions by viewModel.transactions.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val importedBalance by viewModel.importedBalance.collectAsState()
    val importMessage by viewModel.importMessage.collectAsState()
    var selectedScreen by remember { mutableStateOf("Home") }
    val screenState = ScreenState(
        transactions = transactions,
        summary = summary,
        importedBalance = importedBalance,
        importMessage = importMessage,
        isDarkTheme = isDarkTheme,
        onThemeChanged = onThemeChanged,
        onTransactionUpdated = viewModel::updateTransaction,
        onImportCsv = viewModel::importCsv,
        onImportProblem = viewModel::reportImportProblem,
        onDeleteAll = viewModel::deleteAllLocalData,
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val compact = maxWidth < 600.dp
        if (compact) {
            Column(Modifier.fillMaxSize()) {
                ScreenContent(selectedScreen, screenState, Modifier.weight(1f))
                NavigationBar {
                    listOf("Home", "Transactions", "Settings").forEach { screen ->
                        NavigationBarItem(
                            selected = selectedScreen == screen,
                            onClick = { selectedScreen = screen },
                            icon = { Text(if (screen == "Home") "⌂" else if (screen == "Transactions") "≡" else "⚙") },
                            label = { Text(screen) },
                        )
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight().width(150.dp),
                    containerColor = Color(0xFF0B1220),
                    header = {
                        Column(
                            modifier = Modifier.padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("MB", color = Orange, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.headlineSmall)
                            Text("MoneyBuddy", color = Color.White,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    },
                ) {
                    listOf("Home", "Transactions", "Settings").forEach { screen ->
                        NavigationRailItem(
                            selected = selectedScreen == screen,
                            onClick = { selectedScreen = screen },
                            icon = { Text(if (screen == "Home") "⌂" else if (screen == "Transactions") "≡" else "⚙") },
                            label = { Text(screen) },
                        )
                    }
                }
                ScreenContent(selectedScreen, screenState)
            }
        }
    }
}

private class ScreenState(
    val transactions: List<Transaction>,
    val summary: MonthSummary,
    val importedBalance: ImportedBalance?,
    val importMessage: String?,
    val isDarkTheme: Boolean,
    val onThemeChanged: (Boolean) -> Unit,
    val onTransactionUpdated: (Transaction) -> Unit,
    val onImportCsv: (String) -> Unit,
    val onImportProblem: (String) -> Unit,
    val onDeleteAll: () -> Unit,
)

@Composable
private fun ScreenContent(
    selectedScreen: String,
    state: ScreenState,
    modifier: Modifier = Modifier,
) {
    when (selectedScreen) {
        "Home" -> DashboardScreen(state.transactions, state.summary, state.importedBalance, modifier)
        "Transactions" -> TransactionsScreen(state.transactions, state.onTransactionUpdated, modifier)
        else -> SettingsScreen(state, modifier)
    }
}

@Composable
private fun DashboardScreen(
    transactions: List<Transaction>,
    summary: MonthSummary,
    importedBalance: ImportedBalance?,
    modifier: Modifier = Modifier,
) {
    val hasDemo = transactions.any { it.isDemo }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(top = 26.dp)) {
                Text("Good afternoon", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Your financial overview", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        hasDemo -> "DEMO DATA — not connected to HSBC"
                        transactions.isEmpty() -> "No transactions yet — import a bank CSV in Settings"
                        else -> "Imported from a bank CSV — stored only on this tablet"
                    },
                    color = Orange,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (importedBalance != null) {
                    SummaryCard("Balance at last import", importedBalance.pence, "As of ${importedBalance.date}",
                        Modifier.weight(1.2f))
                } else {
                    SummaryCard("Month balance", summary.income - summary.spending, "Income less spending",
                        Modifier.weight(1.2f))
                }
                SummaryCard("Monthly income", summary.income, summary.label, Modifier.weight(1f), Green)
                SummaryCard("Monthly spending", summary.spending, summary.label, Modifier.weight(1f), Red)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SpendingCard(summary.transactions, Modifier.weight(1f))
                UpcomingPaymentsCard(Modifier.weight(1f))
            }
        }
        item { Text("Recent transactions", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold) }
        items(transactions.take(5), key = { it.id }) { TransactionRow(it) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    amount: Long,
    subtitle: String,
    modifier: Modifier,
    amountColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(10.dp))
            Text(formatPounds(amount), color = amountColor,
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SpendingCard(transactions: List<Transaction>, modifier: Modifier) {
    val categories = transactions.filter { it.amountPence < 0 }
        .groupBy { it.category }.mapValues { (_, values) -> values.sumOf { -it.amountPence } }
        .toList().sortedByDescending { it.second }.take(5)
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("Spending breakdown", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(128.dp).padding(10.dp)) {
                    val total = categories.sumOf { it.second }.toFloat().coerceAtLeast(1f)
                    var start = -90f
                    categories.forEachIndexed { index, item ->
                        val sweep = item.second / total * 360f
                        drawArc(
                            color = listOf(Orange, Green, Color(0xFF7C83FD), Color(0xFFE5989B), Color(0xFF6BCBCA))[index % 5],
                            startAngle = start, sweepAngle = sweep, useCenter = false,
                            style = Stroke(width = 22.dp.toPx()),
                        )
                        start += sweep
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { (category, amount) ->
                        Text("$category  ${formatPounds(amount)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingPaymentsCard(modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("Upcoming payments", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            listOf("Council tax" to "31 Oct · £142.00", "Electricity" to "05 Nov · £124.00",
                "Netflix" to "09 Nov · £15.99").forEach { (name, detail) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, color = MaterialTheme.colorScheme.onSurface)
                    Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Example payments — edit in a future stage", color = Orange,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TransactionsScreen(
    transactions: List<Transaction>,
    onTransactionUpdated: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchText by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("All") }
    var categoryFilter by remember { mutableStateOf("All") }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    val visibleTransactions = transactions.filter { transaction ->
        val matchesSearch = searchText.isBlank() ||
            transaction.merchant.contains(searchText, ignoreCase = true) ||
            transaction.category.contains(searchText, ignoreCase = true) ||
            transaction.note.contains(searchText, ignoreCase = true)
        val matchesType = typeFilter == "All" ||
            (typeFilter == "Income" && transaction.amountPence > 0) ||
            (typeFilter == "Spending" && transaction.amountPence < 0)
        val matchesCategory = categoryFilter == "All" || transaction.category == categoryFilter
        matchesSearch && matchesType && matchesCategory
    }

    Column(modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        Text("Transactions", Modifier.padding(top = 28.dp),
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("All local demonstration transactions", color = Orange,
            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 5.dp))
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            label = { Text("Search merchant, category, or note") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(listOf("All", "Income", "Spending")) { filter ->
                AssistChip(
                    onClick = { typeFilter = filter },
                    label = { Text(if (filter == typeFilter) "✓ $filter" else filter) },
                )
            }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(listOf("All") + transactionCategories) { category ->
                AssistChip(
                    onClick = { categoryFilter = category },
                    label = { Text(if (category == categoryFilter) "✓ $category" else category) },
                )
            }
        }
        Text("${visibleTransactions.size} transaction${if (visibleTransactions.size == 1) "" else "s"}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(visibleTransactions, key = { it.id }) { transaction ->
                TransactionRow(transaction, onClick = { selectedTransaction = transaction })
            }
        }
    }
    selectedTransaction?.let { transaction ->
        TransactionEditorDialog(
            transaction = transaction,
            onDismiss = { selectedTransaction = null },
            onSave = {
                onTransactionUpdated(it)
                selectedTransaction = null
            },
        )
    }
}

@Composable
private fun TransactionRow(transaction: Transaction, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable { onClick.invoke() })
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(transaction.merchant, fontWeight = FontWeight.SemiBold)
            Text("${transaction.date} · ${transaction.category}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
        Text(
            formatPounds(transaction.amountPence),
            color = if (transaction.amountPence >= 0) Green else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
    }
    HorizontalDivider(color = Color(0xFF374151))
}

@Composable
private fun TransactionEditorDialog(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit,
) {
    var category by remember(transaction.id) { mutableStateOf(transaction.category) }
    var note by remember(transaction.id) { mutableStateOf(transaction.note) }
    var categoryMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transaction details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(transaction.merchant, fontWeight = FontWeight.Bold)
                Text("${transaction.date} · ${formatPounds(transaction.amountPence)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    AssistChip(
                        onClick = { categoryMenuOpen = true },
                        label = { Text("Category: $category") },
                    )
                    DropdownMenu(
                        expanded = categoryMenuOpen,
                        onDismissRequest = { categoryMenuOpen = false },
                    ) {
                        transactionCategories.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    category = option
                                    categoryMenuOpen = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(transaction.copy(category = category, note = note))
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SettingsScreen(
    state: ScreenState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        val buffer = CharArray(MAX_IMPORT_CHARS + 1)
                        val read = reader.read(buffer)
                        if (read > MAX_IMPORT_CHARS) null else String(buffer, 0, maxOf(read, 0))
                    }
                } catch (_: Exception) {
                    null
                }
            }
            if (text == null) {
                state.onImportProblem("That file could not be read, or it is too large to be a bank statement.")
            } else {
                state.onImportCsv(text)
            }
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("Bank data", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Not connected — import a statement instead", color = Orange,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    "Download a CSV from HSBC online banking, then import it here. Re-importing an overlapping " +
                        "file never creates duplicates or overwrites categories and notes you have edited. " +
                        "The file is read on this tablet only and is never uploaded.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 5.dp),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Import bank CSV") }
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete all local transactions", color = Red)
                    }
                }
                state.importMessage?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(if (state.isDarkTheme) "Dark theme" else "Light theme")
                Text("Choose the appearance for this tablet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
            androidx.compose.material3.Switch(
                checked = state.isDarkTheme,
                onCheckedChange = state.onThemeChanged,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("MoneyBuddy 0.1.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Financial data is stored locally on this tablet only.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all local transactions?") },
            text = { Text("This removes every transaction, category and note stored on this tablet. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    state.onDeleteAll()
                }) { Text("Delete", color = Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private const val MAX_IMPORT_CHARS = 5_000_000

private fun formatPounds(pence: Long): String =
    NumberFormat.getCurrencyInstance(Locale.UK).format(pence / 100.0)
