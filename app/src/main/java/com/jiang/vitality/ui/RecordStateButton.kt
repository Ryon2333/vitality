package com.jiang.vitality.ui

import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** One shared material for every action that records the current vitality state. */
@Composable
fun RecordStateButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    GlassButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = RecoveryCoral.copy(alpha = .10f),
            disabledContentColor = RecoveryCoral.copy(alpha = .76f)
        ),
        modifier = modifier.height(52.dp)
    ) { Text(text) }
}
