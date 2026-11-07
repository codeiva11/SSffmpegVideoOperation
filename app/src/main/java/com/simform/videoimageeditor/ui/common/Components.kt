package com.simform.videoimageeditor.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** One screen = one top bar + scrollable content. Every operation screen uses this. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}

/**
 * As the original operation layouts: label on the left, filled teal
 * Upload button on the right, path/size text below.
 */
@Composable
fun PickFileRow(
    label: String,
    path: String?,
    buttonText: String,
    onPick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f)
        )
        Button(onClick = onPick) {
            Text(buttonText)
        }
    }
    Text(path ?: "", style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun TimeField(label: String, value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text("00:00:00") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun NumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = { if (it.all { c -> c.isDigit() }) onValueChange(it) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun TextField(label: String, value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun RunControls(
    running: Boolean,
    canRun: Boolean,
    runLabel: String,
    onRun: () -> Unit,
    onCancel: () -> Unit
) {
    // As the original screens: one full-width teal run button. Cancel only
    // shows while a run is in progress, so the idle screen matches the old one.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onRun,
            enabled = canRun && !running,
            modifier = Modifier.fillMaxWidth()
        ) { Text(runLabel) }
        if (running) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cancel") }
        }
    }
}

@Composable
fun RunStatus(log: String, output: String?, running: Boolean) {
    if (running) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    if (log.isNotEmpty()) {
        Text(log, style = MaterialTheme.typography.bodySmall)
    }
    output?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error)
}
