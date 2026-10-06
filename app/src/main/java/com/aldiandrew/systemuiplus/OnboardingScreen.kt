package com.aldiandrew.systemuiplus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    shizukuReady: Boolean,
    notificationAccess: Boolean,
    phoneStateGranted: Boolean,
    overlayPermissionGranted: Boolean,
    appLanguageMode: AppLanguageMode,
    onLanguageSelected: (AppLanguageMode) -> Unit,
    onShizukuAction: () -> Unit,
    onNotificationAccess: () -> Unit,
    onPhonePermission: () -> Unit,
    onOverlayPermission: () -> Unit,
    onComplete: () -> Unit
) {
    var page by remember {
        mutableStateOf(0)
    }

    val backgroundBrush =
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            )
        )

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(
                    horizontal = 18.dp,
                    vertical = 16.dp
                )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (page > 0) {
                    IconButton(
                        onClick = {
                            page = (page - 1).coerceAtLeast(0)
                        }
                    ) {
                        Text(
                            "‹",
                            style =
                                MaterialTheme.typography.headlineMedium,
                            color =
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }

                Spacer(Modifier.weight(1f))

                if (page > 0) {
                    TextButton(
                        onClick = onComplete
                    ) {
                        Text(stringResourceSafe("onboarding_skip"))
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (page) {
                    0 -> WelcomePage(
                        selectedLanguage = appLanguageMode,
                        onLanguageSelected = onLanguageSelected,
                        onContinue = {
                            page = 1
                        }
                    )

                    1 -> ShizukuPage(
                        shizukuReady = shizukuReady,
                        onAction = onShizukuAction,
                        onContinue = {
                            page = 2
                        }
                    )

                    2 -> PermissionsPage(
                        shizukuReady = shizukuReady,
                        notificationAccess = notificationAccess,
                        phoneStateGranted = phoneStateGranted,
                        overlayPermissionGranted = overlayPermissionGranted,
                        onShizukuAction = onShizukuAction,
                        onNotificationAccess = onNotificationAccess,
                        onPhonePermission = onPhonePermission,
                        onOverlayPermission = onOverlayPermission,
                        onContinue = {
                            page = 3
                        }
                    )

                    else -> ReadyPage(
                        onFinish = onComplete
                    )
                }
            }

            OnboardingProgress(
                page = page
            )
        }
    }
}

@Composable
private fun WelcomePage(
    selectedLanguage: AppLanguageMode,
    onLanguageSelected: (AppLanguageMode) -> Unit,
    onContinue: () -> Unit
) {
    OnboardingCard {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResourceSafe("onboarding_welcome_title"),
            style =
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResourceSafe("onboarding_welcome_message"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResourceSafe("onboarding_language_label"),
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        LanguageChoice(
            label = stringResourceSafe("language_device"),
            selected = selectedLanguage == AppLanguageMode.DEVICE,
            onClick = {
                onLanguageSelected(AppLanguageMode.DEVICE)
            }
        )

        LanguageChoice(
            label = stringResourceSafe("language_english"),
            selected = selectedLanguage == AppLanguageMode.ENGLISH,
            onClick = {
                onLanguageSelected(AppLanguageMode.ENGLISH)
            }
        )

        Spacer(Modifier.height(24.dp))

        OnboardingPreview()

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Text(stringResourceSafe("onboarding_continue"))
        }
    }
}

@Composable
private fun ShizukuPage(
    shizukuReady: Boolean,
    onAction: () -> Unit,
    onContinue: () -> Unit
) {
    OnboardingCard {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResourceSafe("onboarding_shizuku_title"),
            style =
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResourceSafe("onboarding_shizuku_message"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        StatusRow(
            title = stringResourceSafe("shizuku"),
            ready = shizukuReady
        )

        Spacer(Modifier.height(16.dp))

        if (!shizukuReady) {
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Text(stringResourceSafe("onboarding_setup_shizuku"))
            }

            Spacer(Modifier.height(10.dp))
        }

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Text(stringResourceSafe("onboarding_continue"))
        }
    }
}

@Composable
private fun PermissionsPage(
    shizukuReady: Boolean,
    notificationAccess: Boolean,
    phoneStateGranted: Boolean,
    overlayPermissionGranted: Boolean,
    onShizukuAction: () -> Unit,
    onNotificationAccess: () -> Unit,
    onPhonePermission: () -> Unit,
    onOverlayPermission: () -> Unit,
    onContinue: () -> Unit
) {
    OnboardingCard {
        Icon(
            imageVector = Icons.Default.Tune,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResourceSafe("onboarding_permissions_title"),
            style =
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResourceSafe("onboarding_permissions_message"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        PermissionActionRow(
            title = stringResourceSafe("shizuku"),
            ready = shizukuReady,
            action = onShizukuAction
        )

        HorizontalDivider()

        PermissionActionRow(
            title = stringResourceSafe("notification_access"),
            ready = notificationAccess,
            action = onNotificationAccess
        )

        HorizontalDivider()

        PermissionActionRow(
            title = stringResourceSafe("display_over_other_apps"),
            ready = overlayPermissionGranted,
            action = onOverlayPermission
        )

        HorizontalDivider()

        PermissionActionRow(
            title = stringResourceSafe("phone_state"),
            ready = phoneStateGranted,
            action = onPhonePermission
        )

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Text(stringResourceSafe("onboarding_continue"))
        }
    }
}

@Composable
private fun ReadyPage(
    onFinish: () -> Unit
) {
    OnboardingCard {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResourceSafe("onboarding_ready_title"),
            style =
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResourceSafe("onboarding_ready_message"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(22.dp))

        FeatureRow(
            icon = Icons.Default.AccessTime,
            title = stringResourceSafe("onboarding_feature_clock")
        )

        FeatureRow(
            icon = Icons.Default.Tune,
            title = stringResourceSafe("onboarding_feature_indicators")
        )

        FeatureRow(
            icon = Icons.Default.Notifications,
            title = stringResourceSafe("onboarding_feature_notifications")
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Text(stringResourceSafe("onboarding_finish"))
        }
    }
}

@Composable
private fun OnboardingCard(
    content: @Composable () -> Unit
) {
    val shape = MaterialTheme.shapes.large
    val brush =
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.colorScheme.surfaceContainer,
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
            )
        )

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(brush)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = shape
                )
                .padding(
                    horizontal = 20.dp,
                    vertical = 24.dp
                )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}

@Composable
private fun LanguageChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    }
            )
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 8.dp,
                        vertical = 4.dp
                    ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = null
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun StatusRow(
    title: String,
    ready: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color =
            MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = 14.dp,
                    vertical = 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector =
                    if (ready) {
                        Icons.Default.Check
                    } else {
                        Icons.Default.Settings
                    },
                contentDescription = null,
                tint =
                    if (ready) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
            )

            Spacer(Modifier.width(10.dp))

            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )

            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        stringResourceSafe(
                            if (ready) {
                                "onboarding_ready"
                            } else {
                                "onboarding_not_ready"
                            }
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun PermissionActionRow(
    title: String,
    ready: Boolean,
    action: () -> Unit
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector =
                if (ready) {
                    Icons.Default.Check
                } else {
                    Icons.Default.Settings
                },
            contentDescription = null,
            tint =
                if (ready) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
        )

        Spacer(Modifier.width(10.dp))

        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )

        TextButton(
            onClick = action,
            enabled = !ready
        ) {
            Text(
                stringResourceSafe(
                    if (ready) {
                        "onboarding_ready"
                    } else {
                        "onboarding_setup"
                    }
                )
            )
        }
    }
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Text(
            title,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun OnboardingPreview() {
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(
            modifier =
                Modifier.padding(
                    horizontal = 18.dp,
                    vertical = 18.dp
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "12:45",
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Medium
                        )
                )

                Spacer(Modifier.weight(1f))

                Text(
                    text = "●  ▪  85%",
                    style =
                        MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = stringResourceSafe("onboarding_preview_label"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OnboardingProgress(
    page: Int
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    top = 12.dp,
                    bottom = 4.dp
                ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(4) { index ->
            val selected = index == page
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(
                            width = if (selected) 22.dp else 7.dp,
                            height = 7.dp
                        )
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
            )
        }
    }
}

@Composable
private fun stringResourceSafe(
    name: String
): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    val id =
        context.resources.getIdentifier(
            name,
            "string",
            context.packageName
        )

    return if (id != 0) {
        context.getString(id)
    } else {
        name
    }
}
