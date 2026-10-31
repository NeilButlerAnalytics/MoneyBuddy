package com.moneybuddy.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneybuddy.app.data.Category
import com.moneybuddy.app.data.MAX_CATEGORY_NAME_LENGTH
import com.moneybuddy.app.data.OTHER_CATEGORY
import com.moneybuddy.app.data.Transaction
import com.moneybuddy.app.data.categoryPalette

private val DeleteRed = Color(0xFFE56B6F)

/** Settings card for adding, editing and deleting spending categories. */
@Composable
internal fun CategoriesCard(
    categories: List<Category>,
    transactions: List<Transaction>,
    onSave: (original: String?, name: String, colorArgb: Long) -> String?,
    onDelete: (String) -> Unit,
) {
    // null means no dialog; an empty string is "add"; anything else is the category being edited.
    var editing by remember { mutableStateOf<Category?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    val counts = remember(transactions) { transactions.groupingBy { it.category }.eachCount() }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Add, rename, recolour or delete the categories used for your spending. Renaming updates every " +
                    "transaction in it. Deleting moves its transactions to \u201C$OTHER_CATEGORY\u201D.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            categories.forEach { category ->
                val count = counts[category.name] ?: 0
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).background(Color(category.colorArgb), CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(category.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "$count transaction${if (count == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { editing = category }) { Text("Edit") }
                    if (category.name != OTHER_CATEGORY) {
                        TextButton(onClick = { deleting = category }) { Text("Delete", color = DeleteRed) }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { adding = true }) { Text("Add category") }
        }
    }

    if (adding) {
        CategoryEditorDialog(
            original = null,
            onDismiss = { adding = false },
            onSave = onSave,
        )
    }
    editing?.let { category ->
        CategoryEditorDialog(original = category, onDismiss = { editing = null }, onSave = onSave)
    }
    deleting?.let { category ->
        val count = counts[category.name] ?: 0
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \u201C${category.name}\u201D?") },
            text = {
                Text(
                    if (count == 0) {
                        "No transactions use this category."
                    } else {
                        "$count transaction${if (count == 1) "" else "s"} will move to \u201C$OTHER_CATEGORY\u201D."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(category.name)
                    deleting = null
                }) { Text("Delete", color = DeleteRed) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorDialog(
    original: Category?,
    onDismiss: () -> Unit,
    onSave: (original: String?, name: String, colorArgb: Long) -> String?,
) {
    var name by remember(original) { mutableStateOf(original?.name ?: "") }
    var color by remember(original) { mutableStateOf(original?.colorArgb ?: categoryPalette.first()) }
    var error by remember { mutableStateOf<String?>(null) }
    val isOther = original?.name == OTHER_CATEGORY

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "Add category" else "Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(MAX_CATEGORY_NAME_LENGTH + 1)
                        error = null
                    },
                    label = { Text("Name") },
                    singleLine = true,
                    enabled = !isOther,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Colour", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    categoryPalette.forEach { option ->
                        val selected = option == color
                        Box(
                            Modifier
                                .size(36.dp)
                                .background(Color(option), CircleShape)
                                .then(
                                    if (selected) {
                                        Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape)
                                    } else {
                                        Modifier
                                    },
                                )
                                .semantics { contentDescription = if (selected) "Selected colour" else "Colour" }
                                .clickable { color = option },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                error = onSave(original?.name, name, color)
                if (error == null) onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
