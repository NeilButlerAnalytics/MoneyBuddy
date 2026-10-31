package com.moneybuddy.app

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moneybuddy.app.data.MoneyDatabase
import com.moneybuddy.app.ui.MoneyBuddyApp
import com.moneybuddy.app.ui.MoneyBuddyViewModel
import com.moneybuddy.app.ui.MoneyBuddyViewModelFactory
import com.moneybuddy.app.ui.MoneyBuddyTheme

class MainActivity : ComponentActivity() {
    private var isDarkTheme by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isDarkTheme = getPreferences(MODE_PRIVATE).getBoolean("dark_theme", true)
        val database = MoneyDatabase.create(applicationContext)
        setContent {
            MoneyBuddyTheme(isDark = isDarkTheme) {
                Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val financeViewModel: MoneyBuddyViewModel = viewModel(
                        factory = MoneyBuddyViewModelFactory(
                            database.transactionDao(),
                            database.categoryDao(),
                            getPreferences(MODE_PRIVATE),
                        ),
                    )
                    MoneyBuddyApp(
                        viewModel = financeViewModel,
                        isDarkTheme = isDarkTheme,
                        onThemeChanged = { dark ->
                            isDarkTheme = dark
                            getPreferences(MODE_PRIVATE).edit()
                                .putBoolean("dark_theme", dark)
                                .apply()
                        },
                    )
                }
            }
        }
    }
}
