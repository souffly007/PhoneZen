// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bonobo.phonezen.R
import fr.bonobo.phonezen.data.local.BlockedCall
import fr.bonobo.phonezen.data.model.RiskLevel
import fr.bonobo.phonezen.ui.theme.PhoneZenTheme
import fr.bonobo.phonezen.utils.PhoneUtils
import fr.bonobo.phonezen.viewmodel.BlockedCallsViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Écran d'historique des appels bloqués.
 * 
 * Affiche la liste des appels bloqués avec possibilité de suppression.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BlockedCallsHistoryScreen(
    viewModel: BlockedCallsViewModel,
    onBack: () -> Unit,
    onCall: (String) -> Unit
) {
    val context = LocalContext.current
    val blockedCalls by viewModel.blockedCalls.collectAsState()
    
    // Format de date
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    
    // État pour le dialogue de confirmation
    var showDeleteDialog by remember { mutableStateOf(false) }
    var callToDelete by remember { mutableStateOf<BlockedCall?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    
    // État pour la recherche
    var searchQuery by remember { mutableStateOf("") }
    
    // Filtrer les appels bloqués
    val filteredCalls = remember(blockedCalls, searchQuery) {
        if (searchQuery.isBlank()) {
            blockedCalls
        } else {
            blockedCalls.filter {
                it.number.contains(searchQuery, ignoreCase = true) ||
                it.reason.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    // Dialogue de confirmation de suppression
    if (showDeleteDialog && callToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = stringResource(R.string.delete_call_title)) },
            text = { 
                Text(text = stringResource(R.string.delete_call_confirm, callToDelete!!.number))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBlockedCall(callToDelete!!.id)
                    showDeleteDialog = false
                    callToDelete = null
                }) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteDialog = false
                    callToDelete = null
                }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            }
        )
    }
    
    // Dialogue de confirmation de suppression de tout
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text(text = stringResource(R.string.clear_all_title)) },
            text = { 
                Text(text = stringResource(R.string.clear_all_confirm))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllBlockedCalls()
                    showClearAllDialog = false
                }) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.blocked_calls_history)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showClearAllDialog = true },
                        enabled = blockedCalls.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = stringResource(R.string.clear_all),
                            tint = if (blockedCalls.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Barre de recherche
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                label = { Text(text = stringResource(R.string.search)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                singleLine = true
            )
            
            // Statistiques
            if (blockedCalls.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.total_blocked_calls, blockedCalls.size),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        // Répartition par niveau de risque
                        val riskCounts = remember(blockedCalls) {
                            val counts = mutableMapOf<String, Int>()
                            blockedCalls.forEach { call ->
                                counts[call.riskLevel] = (counts[call.riskLevel] ?: 0) + 1
                            }
                            counts
                        }
                        
                        if (riskCounts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.by_risk_level),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                riskCounts.forEach { (riskLevel, count) ->
                                    val color = when (riskLevel) {
                                        RiskLevel.CRITICAL.name -> Color.Red
                                        RiskLevel.HIGH.name -> Color(0xFFFF5722) // Orange foncé
                                        RiskLevel.MEDIUM.name -> Color(0xFFFFA726) // Orange
                                        RiskLevel.LOW.name -> Color(0xFFFFC107) // Jaune
                                        else -> Color.Gray
                                    }
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .background(color, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "$riskLevel: $count",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Liste des appels bloqués
            if (filteredCalls.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isBlank()) 
                            stringResource(R.string.no_blocked_calls)
                        else 
                            stringResource(R.string.no_blocked_calls_matching, searchQuery),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredCalls, key = { it.id }) { call ->
                        BlockedCallItem(
                            call = call,
                            dateFormat = dateFormat,
                            onClick = { onCall(call.number) },
                            onLongClick = {
                                callToDelete = call
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Item de la liste des appels bloqués.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BlockedCallItem(
    call: BlockedCall,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val riskColor = when (call.riskLevel) {
        RiskLevel.CRITICAL.name -> Color.Red
        RiskLevel.HIGH.name -> Color(0xFFFF5722)
        RiskLevel.MEDIUM.name -> Color(0xFFFFA726)
        RiskLevel.LOW.name -> Color(0xFFFFC107)
        else -> Color.Gray
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Numéro de téléphone
                Text(
                    text = PhoneUtils.formatNumber(call.number),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                
                // Indicateur de risque
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(riskColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = call.riskLevel,
                        style = MaterialTheme.typography.labelSmall,
                        color = riskColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Raison et date
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = call.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = dateFormat.format(Date(call.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}
