package com.moneybuddy.app.ui

import androidx.compose.foundation.Canvas
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
import com.moneybuddy.app.data.Transaction
import com.moneybuddy.app.data.TransactionDao
import com.moneybuddy.app.data.demoTransactions
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Orange = Color(0xFFF28C28)
private val Green = Color(0xFF39B978)
private val Red = Color(0xFFE56B6F)

private val transactionCategories = listOf(
    "Groceries", "Shopping", "Fuel", "Transport", "Utilities", "Mortgage / Rent",
    "Insurance", "Eating out", "Entertainment", "Subscriptions", "Family", "Holidays", "Other",
)

class MoneyBuddyViewModel(private val dao: TransactionDao) : ViewModel() {
    val transactions: StateFlow<List<Transaction>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            if (dao.count() == 0) dao.insertAll(demoTransactions)
        }
    }

    val monthlyIncome: StateFlow<Long> = transactions.map { list ->
        list.filter { it.amountPence > 0 }.sumOf { it.amountPence }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val monthlySpending: StateFlow<Long> = transactions.map { list ->
        list.filter { it.amountPence < 0 }.sumOf { -it.amountPence }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch { dao.update(transaction) }
    }
}

class MoneyBuddyViewModelFactory(private val dao: TransactionDao) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MoneyBuddyViewModel(dao) as T
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
    val income by viewModel.monthlyIncome.collectAsState()
    val spending by viewModel.monthlySpending.collectAsState()
    var selectedScreen by remember { mutableStateOf("Home") }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val compact = maxWidth < 600.dp
        if (compact) {
            Column(Modifier.fillMaxSize()) {
                ScreenContent(selectedScreen, transactions, income, spending, isDarkTheme, onThemeChanged,
                    viewModel::updateTransaction,
                    Modifier.weight(1f))
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
                ScreenContent(selectedScreen, transactions, income, spending, isDarkTheme, onThemeChanged,
                    viewModel::updateTransaction)
            }
        }
    }
}

@Composable
private fun ScreenContent(
    selectedScreen: String,
    transactions: List<Transaction>,
    income: Long,
    spending: Long,
    isDarkTheme: Boolean,
    onThemeChanged: (Boolean) -> Unit,
    onTransactionUpdated: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (selectedScreen) {
        "Home" -> DashboardScreen(transactions, income, spending, modifier)
        "Transactions" -> TransactionsScreen(transactions, onTransactionUpdated, modifier)
        else -> SettingsScreen(isDarkTheme, onThemeChanged, modifier)
    }
}

@Composable
private fun DashboardScreen(
    transactions: List<Transaction>,
    income: Long,
    spending: Long,
    modifier: Modifier = Modifier,
) {
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
                Text("DEMO DATA — not connected to HSBC", color = Orange,
                    style = MaterialTheme.typography.labelMedium)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SummaryCard("Available balance", income - spending, "HSBC Current Account", Modifier.weight(1.2f))
                SummaryCard("Monthly income", income, "October 2026", Modifier.weight(1f), Green)
                SummaryCard("Monthly spending", spending, "October 2026", Modifier.weight(1f), Red)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SpendingCard(transactions, Modifier.weight(1f))
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
    amountColor: Color = Color.White,
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
            color = if (transaction.amountPence >= 0) Green else Color.White,
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
    isDarkTheme: Boolean,
    onThemeChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(28.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("Account connection", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Not connected", color = Orange, fontWeight = FontWeight.SemiBold)
                Text("HSBC Open Banking connection will be added after Stage 1 is reviewed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 5.dp))
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
                Text(if (isDarkTheme) "Dark theme" else "Light theme")
                Text("Choose the appearance for this tablet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
            androidx.compose.material3.Switch(
                checked = isDarkTheme,
                onCheckedChange = onThemeChanged,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("MoneyBuddy 0.1.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("All current data is synthetic demonstration data stored locally on this tablet.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatPounds(pence: Long): String =
    NumberFormat.getCurrencyInstance(Locale.UK).format(pence / 100.0)
