package io.github.eelnicoo.pocketcode.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eelnicoo.pocketcode.data.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSheet(
    current: String,
    extendedThinking: Boolean,
    onSelect: (String) -> Unit,
    onThinking: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalClaude.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.composer) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Text("Modelo", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
            val list = if (Settings.MODELS.any { it.id == current }) Settings.MODELS
            else Settings.MODELS + Settings.modelInfo(current)
            list.forEach { m ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(m.id); onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Claude ${m.name}", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(m.description, color = c.textTertiary, fontSize = 13.sp)
                    }
                    if (m.id == current) Icon(Icons.Filled.Check, "Seleccionado", tint = c.accent)
                }
            }
            HorizontalDivider(color = c.border, modifier = Modifier.padding(vertical = 8.dp, horizontal = 24.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onThinking(!extendedThinking) }
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Pensamiento extendido", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (extendedThinking) "Piensa más a fondo: mejor para código y tareas largas"
                        else "Respuestas más rápidas y directas",
                        color = c.textTertiary, fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Switch(
                    checked = extendedThinking,
                    onCheckedChange = onThinking,
                    colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = Color.White),
                )
            }
        }
    }
}
