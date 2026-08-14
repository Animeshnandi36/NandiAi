package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.foundation.Image

@Composable
fun NandiHeader(
    selectedModel: String,
    hasApiKey: Boolean,
    isDarkMode: Boolean,
    onMenuClick: () -> Unit,
    onModelSelected: (String) -> Unit,
    onApiKeyClick: () -> Unit,
    onThemeToggle: () -> Unit
) {
    var showModelDropdown by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Menu button & Brand Logo
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.testTag("menu_drawer_button")
                ) {
                    Icon(imageVector = Icons.Default.Menu, contentDescription = "Open Drawer")
                }

                Spacer(modifier = Modifier.width(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onMenuClick() }
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_nandi_logo),
                        contentDescription = "NandiAI Logo",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "NandiAI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 18.sp
                            )
                        }
                        Text(
                            text = "by Animesh Nandi",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Right: Model Selector + API Badge + Theme Toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Model Dropdown Pill
                Box {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                            .clickable { showModelDropdown = true }
                            .testTag("model_selector_button"),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (selectedModel) {
                                    "gemini-3.1-pro-preview" -> "Gemini Pro"
                                    "gemini-3.1-flash-lite-preview" -> "Flash Lite"
                                    else -> "Gemini Flash"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showModelDropdown,
                        onDismissRequest = { showModelDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Gemini Flash (Default)", fontWeight = FontWeight.SemiBold) },
                            onClick = {
                                onModelSelected("gemini-3.5-flash")
                                showModelDropdown = false
                            },
                            leadingIcon = {
                                if (selectedModel == "gemini-3.5-flash") {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Gemini Pro (Advanced Reasoning)", fontWeight = FontWeight.SemiBold) },
                            onClick = {
                                onModelSelected("gemini-3.1-pro-preview")
                                showModelDropdown = false
                            },
                            leadingIcon = {
                                if (selectedModel == "gemini-3.1-pro-preview") {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Gemini Flash Lite (Ultra Fast)", fontWeight = FontWeight.SemiBold) },
                            onClick = {
                                onModelSelected("gemini-3.1-flash-lite-preview")
                                showModelDropdown = false
                            },
                            leadingIcon = {
                                if (selectedModel == "gemini-3.1-flash-lite-preview") {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // API Key Badge
                IconButton(
                    onClick = onApiKeyClick,
                    modifier = Modifier.testTag("api_key_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "API Settings",
                        tint = if (hasApiKey) MaterialTheme.colorScheme.primary else Color(0xFFF87171)
                    )
                }

                // Theme Toggle
                IconButton(
                    onClick = onThemeToggle,
                    modifier = Modifier.testTag("theme_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle Theme"
                    )
                }
            }
        }
    }
}
