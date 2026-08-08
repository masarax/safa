package com.safa.account.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safa.account.ui.viewmodel.HundiViewModel
import com.safa.account.ui.viewmodel.AppScreen
import com.safa.account.ui.screens.CalculatorDialog
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(
    viewModel: HundiViewModel,
    modifier: Modifier = Modifier,
    isAddingEntryView: Boolean = false
) {
    val items by viewModel.expensesIncomes.collectAsStateWithLifecycle()
    val stats by viewModel.financialStats.collectAsStateWithLifecycle()
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val foreignCurrency by viewModel.selectedForeignCurrency.collectAsStateWithLifecycle()
    val localCurrency by viewModel.selectedLocalCurrency.collectAsStateWithLifecycle()

    var showAddChoiceDialog by remember { mutableStateOf(false) }

    if (isAddingEntryView) {
        androidx.activity.compose.BackHandler {
            viewModel.navigateBack()
        }
    }

    var itemToDelete by remember { mutableStateOf<com.safa.account.data.model.ExpenseIncome?>(null) }
    
    val defaultCategories = listOf("Rent", "Salary", "Utilities", "Food", "Other")
    var dynamicCategories by remember { mutableStateOf(defaultCategories) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryInput by remember { mutableStateOf("") }

    // Forms
    var titleInput by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var currencyInput by remember(localCurrency) { mutableStateOf(localCurrency) } // dynamic
    var isExpenseInput by remember { mutableStateOf(true) }
    var categoryInput by remember { mutableStateOf("Food") }
    var searchQuery by remember { mutableStateOf("") }
    var isCustomizerExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Expense, 1 = Income
    var selectedSortOption by remember { mutableStateOf("Newest") } // Newest, Oldest, Max, Min
    var selectedFilterCurrency by remember { mutableStateOf("All") } // All, SAR, BDT

    var showAmountCalc by remember { mutableStateOf(false) }

    val currencyFormatter = remember { DecimalFormat("#,##0") }

    val typeFilteredItems = items.filter { item -> item.isExpense == (selectedTab == 0) }
    val filteredItems = remember(typeFilteredItems, searchQuery, selectedSortOption, selectedFilterCurrency) {
        var list = typeFilteredItems.filter { item ->
            item.title.contains(searchQuery, ignoreCase = true) || item.category.contains(searchQuery, ignoreCase = true)
        }

        if (selectedFilterCurrency != "All") {
            list = list.filter { it.currency == selectedFilterCurrency }
        }

        list = when (selectedSortOption) {
            "Oldest" -> list.sortedBy { it.timestamp }
            "Max" -> list.sortedByDescending { it.amount }
            "Min" -> list.sortedBy { it.amount }
            else -> list.sortedByDescending { it.timestamp } // Newest first
        }

        list
    }

    AnimatedContent(
        targetState = isAddingEntryView,
        transitionSpec = {
            if (!targetState) {
                // Slide back (left to right)
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(160, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(110)) togetherWith
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(160, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(110))
            } else {
                // Slide forward (right to left)
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(160, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(110)) togetherWith
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(160, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(110))
            }
        },
        label = "ExpenseSubPageTransition",
        modifier = Modifier.fillMaxSize()
    ) { adding ->
        if (adding) {
            Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.navigateBack() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalCafe,
                            contentDescription = "",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (isExpenseInput) (if (lang == "BN") "নতুন খরচ এন্ট্রি" else "New Expense Entry") else (if (lang == "BN") "নতুন আয় এন্ট্রি" else "New Income Entry"),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                letterSpacing = (-0.3).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (lang == "BN") "পিছনে ফিরে যেতে এখানে চাপুন" else "Tap here to go back",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text(if (lang == "BN") "বিবরণ" else "Details / Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = amountInput,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(viewModel.t("amount")) },
                            trailingIcon = {
                                Icon(Icons.Default.Calculate, contentDescription = "Calculate", tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Box(modifier = Modifier.matchParentSize().clickable { showAmountCalc = true })
                    }

                    // Currency Selector Segment
                    Text(text = if (lang == "BN") "কারেন্সি" else "Currency", style = MaterialTheme.typography.labelSmall)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(localCurrency, foreignCurrency).forEach { curr ->
                            FilterChip(
                                selected = currencyInput == curr,
                                onClick = { currencyInput = curr },
                                label = { Text(curr) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Categories selectors
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = if (lang == "BN") "ক্যাটাগরি" else "Category", style = MaterialTheme.typography.labelSmall)
                        TextButton(onClick = { showAddCategoryDialog = true }, contentPadding = PaddingValues(0.dp)) {
                            Text(text = if (lang == "BN") "+ যুক্ত করুন" else "+ Add New", fontSize = 12.sp)
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        dynamicCategories.forEach { c ->
                            FilterChip(
                                selected = categoryInput == c,
                                onClick = { categoryInput = c },
                                label = { Text(c, fontSize = 13.sp) }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val amt = amountInput.toDoubleOrNull() ?: 0.0
                            if (titleInput.isNotBlank() && amt > 0) {
                                viewModel.addExpenseIncome(titleInput, amt, currencyInput, isExpenseInput, categoryInput) {
                                    titleInput = ""
                                    amountInput = ""
                                    viewModel.navigateBack()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (lang == "BN") "সেভ করুন" else "Save", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        if (showAddCategoryDialog) {
            AlertDialog(
                onDismissRequest = { showAddCategoryDialog = false },
                title = { Text(if (lang == "BN") "ক্যাটাগরি যুক্ত করুন" else "Add Category") },
                text = {
                    OutlinedTextField(
                        value = newCategoryInput,
                        onValueChange = { newCategoryInput = it },
                        label = { Text("Category Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newCategoryInput.isNotBlank() && !dynamicCategories.contains(newCategoryInput)) {
                            dynamicCategories = dynamicCategories + newCategoryInput
                            categoryInput = newCategoryInput
                        }
                        newCategoryInput = ""
                        showAddCategoryDialog = false
                    }) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddCategoryDialog = false }) { Text("Cancel") }
                }
            )
        }
        
        if (showAmountCalc) {
            CalculatorDialog(
                initialValue = amountInput,
                title = viewModel.t("amount"),
                lang = lang,
                onDismiss = { showAmountCalc = false },
                onConfirm = { result ->
                    amountInput = result
                    showAmountCalc = false
                }
            )
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Header (Full width)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalCafe,
                                contentDescription = "",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = if (lang == "BN") "আয় / ব্যয়" else "Income & Expenses",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                letterSpacing = (-0.3).sp
                            ),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Button(
                        onClick = { showAddChoiceDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (lang == "BN") "নতুন এন্ট্রি" else "New Entry",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                
                // Tab Switcher for Expense vs Income
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectedTab == 0) {
                            Button(
                                onClick = { selectedTab = 0 },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFC62828),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = if (lang == "BN") "খরচ" else "Expenses",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { selectedTab = 0 },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = if (lang == "BN") "খরচ" else "Expenses",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        if (selectedTab == 1) {
                            Button(
                                onClick = { selectedTab = 1 },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2E7D32),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = if (lang == "BN") "আয়" else "Incomes",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { selectedTab = 1 },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = if (lang == "BN") "আয়" else "Incomes",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }

                // Quick Stats summary based on Tab
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (selectedTab == 0) {
                            Card(
                                modifier = Modifier.weight(1f),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = if (lang == "BN") "মোট খরচ" else "Total Expenses", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828))
                                    Text(
                                        text = "৳ ${currencyFormatter.format(stats.totalExpensesBdt)}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFC62828)
                                    )
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier.weight(1f),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = if (lang == "BN") "মোট আয়" else "Total Incomes", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                                    Text(
                                        text = "৳ ${currencyFormatter.format(stats.totalOtherIncomeBdt)}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }
                    }
                }

                // Category Breakdowns
                item {
                    Text(
                        text = if (lang == "BN") "রিপোর্ট (সব মিলে)" else "Overall Reports",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                item {
                    val grouped = typeFilteredItems.groupBy { it.category }
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        grouped.forEach { (cat, catItems) ->
                            val totalCatAmount = catItems.sumOf { if (it.currency == "SAR") it.amount * 32.5 else it.amount }
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                                    Text(text = "$cat: ", style = MaterialTheme.typography.labelSmall)
                                    Text(text = "৳${currencyFormatter.format(totalCatAmount)}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = if (selectedTab == 0) Color(0xFFC62828) else Color(0xFF2E7D32))
                                }
                            }
                        }
                    }
                }

                // Tools/Customization Bar
                item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(if (lang == "BN") "খুঁজুন..." else "Search...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "", modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    )

                    // Advanced settings dropdown trigger
                    FilledIconButton(
                        onClick = { isCustomizerExpanded = !isCustomizerExpanded },
                        shape = RoundedCornerShape(12.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isCustomizerExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "",
                            tint = if (isCustomizerExpanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                AnimatedVisibility(visible = isCustomizerExpanded) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = if (lang == "BN") "⚙️ ফিল্টার ও সেটিংস" else "⚙️ Filter & Settings",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Sort Option Dropdown
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (lang == "BN") "বাছাই করুন" else "Sort By",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                    var sortMenuExpanded by remember { mutableStateOf(false) }
                                    Box {
                                        OutlinedButton(
                                            onClick = { sortMenuExpanded = true },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = when (selectedSortOption) {
                                                    "Oldest" -> if (lang == "BN") "পুরাতন প্রথম" else "Oldest First"
                                                    "Max" -> if (lang == "BN") "সর্বোচ্চ পরিমাণ" else "Max Amount"
                                                    "Min" -> if (lang == "BN") "সর্বনিম্ন পরিমাণ" else "Min Amount"
                                                    else -> if (lang == "BN") "নতুন প্রথম" else "Newest First"
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = sortMenuExpanded,
                                            onDismissRequest = { sortMenuExpanded = false }
                                        ) {
                                            listOf("Newest", "Oldest", "Max", "Min").forEach { option ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            when (option) {
                                                                "Oldest" -> if (lang == "BN") "পুরাতন প্রথম" else "Oldest First"
                                                                "Max" -> if (lang == "BN") "সর্বোচ্চ পরিমাণ" else "Max Amount"
                                                                "Min" -> if (lang == "BN") "সর্বনিম্ন পরিমাণ" else "Min Amount"
                                                                else -> if (lang == "BN") "নতুন প্রথম" else "Newest First"
                                                            }
                                                        )
                                                    },
                                                    onClick = {
                                                        selectedSortOption = option
                                                        sortMenuExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Filter Status Option Dropdown
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (lang == "BN") "মুদ্রা ফিল্টার" else "Filter Currency",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                    var filterMenuExpanded by remember { mutableStateOf(false) }
                                    Box {
                                        OutlinedButton(
                                            onClick = { filterMenuExpanded = true },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = when (selectedFilterCurrency) {
                                                    foreignCurrency -> if (lang == "BN") "শুধু রিয়াল ($foreignCurrency)" else "Only Riyal ($foreignCurrency)"
                                                    localCurrency -> if (lang == "BN") "শুধু টাকা ($localCurrency)" else "Only Taka ($localCurrency)"
                                                    else -> if (lang == "BN") "সকল মুদ্রা" else "All Currencies"
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = filterMenuExpanded,
                                            onDismissRequest = { filterMenuExpanded = false }
                                        ) {
                                            listOf("All", foreignCurrency, localCurrency).forEach { option ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            when (option) {
                                                                foreignCurrency -> if (lang == "BN") "শুধু রিয়াল ($foreignCurrency)" else "Only Riyal ($foreignCurrency)"
                                                                localCurrency -> if (lang == "BN") "শুধু টাকা ($localCurrency)" else "Only Taka ($localCurrency)"
                                                                else -> if (lang == "BN") "সকল মুদ্রা" else "All Currencies"
                                                            }
                                                        )
                                                    },
                                                    onClick = {
                                                        selectedFilterCurrency = option
                                                        filterMenuExpanded = false
                                                    }
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

            // Expense List Logs
            if (filteredItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.LocalCafe, contentDescription = "", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                            Text(
                                text = if (lang == "BN") "কোনো খরচ বা আয়ের রেকর্ড নেই" else "No expense or income records logged.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(filteredItems, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("expense_card_${item.id}"),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.isExpense) Color.White else Color(0xFFF1F8E9)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (item.isExpense) MaterialTheme.colorScheme.onSurface else Color(0xFF2E7D32)
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(item.category) },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                    Text(
                                        text = SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date(item.timestamp)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "${if (item.isExpense) "-" else "+"} ${item.currency} ${currencyFormatter.format(item.amount)}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (item.isExpense) Color(0xFFC62828) else Color(0xFF2E7D32)
                                )

                                IconButton(
                                    onClick = { itemToDelete = item },
                                    modifier = Modifier.testTag("delete_expense_${item.id}")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        } // End of LazyColumn
    } // End of Column

        // Delete confirmation warning alert dialog
        if (itemToDelete != null) {
            val deleteTitle = if (lang == "BN") "সতর্কতা!" else "Check Warning!"
            val deleteDesc = if (lang == "BN") "আপনি কি নিশ্চিত যে এই রেকর্ডটি মুছে ফেলতে চান? এটি স্থায়ীভাবে বাতিল হয়ে যাবে।" else "Are you sure you want to permanently delete this record? This action cannot be revoked."
            val confirmLabel = if (lang == "BN") "মুছে ফেলুন" else "Delete"
            val cancelLabel = if (lang == "BN") "বাতিল" else "Cancel"
            
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                title = { Text(text = deleteTitle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
                text = { Text(deleteDesc) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.removeExpenseIncome(itemToDelete!!.id)
                            itemToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(confirmLabel)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToDelete = null }) {
                        Text(cancelLabel)
                    }
                }
            )
        }
        
        if (showAddChoiceDialog) {
            AlertDialog(
                onDismissRequest = { showAddChoiceDialog = false },
                title = {
                    Text(
                        text = if (lang == "BN") "লেনদেনের ধরণ নির্বাচন করুন" else "Select Transaction Type",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Option 1: New Income (নতুন আয় এন্ট্রি)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    isExpenseInput = false
                                    showAddChoiceDialog = false 
                                    viewModel.navigateTo(AppScreen.EXPENSE_ADD)
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TrendingUp,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (lang == "BN") "নতুন আয়" else "New Income",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = if (lang == "BN") "নতুন আয়ের হিসাব যুক্ত করুন।" else "Add a new income entry.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }

                        // Option 2: New Expense (নতুন ব্যয় এন্ট্রি)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    isExpenseInput = true
                                    showAddChoiceDialog = false 
                                    viewModel.navigateTo(AppScreen.EXPENSE_ADD)
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFECEB)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TrendingDown,
                                        contentDescription = null,
                                        tint = Color(0xFFC62828),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (lang == "BN") "নতুন ব্যয়" else "New Expense",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFC62828)
                                    )
                                    Text(
                                        text = if (lang == "BN") "নতুন ব্যয়ের হিসাব যুক্ত করুন।" else "Add a new expense entry.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAddChoiceDialog = false }) {
                        Text(if (lang == "BN") "বন্ধ করুন" else "Cancel")
                    }
                }
            )
        }
    }
}
    }
}


