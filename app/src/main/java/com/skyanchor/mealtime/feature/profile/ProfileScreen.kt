package com.skyanchor.mealtime.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skyanchor.mealtime.core.ui.FoodCard
import com.skyanchor.mealtime.core.ui.FoodTheme

/** 我的：本地身份 + 历史 / 设置 / 关于（PAGES.md §10）。V1 不做账号。 */
@Composable
fun ProfileScreen(
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }

    if (showAbout) {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("关于 饭点") },
            text = {
                Text(
                    "饭点 v$version\n\n帮助决定\"今天吃什么\"，并优先把家里的食材吃掉。\n\n" +
                        "V1 为本地应用，所有数据仅保存在本机，可在\"设置 → 数据管理\"中导出备份。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("知道了") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = FoodTheme.dimens.pageHorizontalPadding),
    ) {
        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
        Text(
            text = "我的",
            style = MaterialTheme.typography.titleLarge,
            color = FoodTheme.colors.textPrimary,
        )

        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
        FoodCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(FoodTheme.dimens.spaceLg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(FoodTheme.colors.primarySoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = FoodTheme.colors.primary,
                    )
                }
                Spacer(modifier = Modifier.size(FoodTheme.dimens.spaceMd))
                Column {
                    Text(
                        text = "本地用户",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = FoodTheme.colors.textPrimary,
                    )
                    Text(
                        text = "数据仅保存在本机，可导出备份",
                        style = MaterialTheme.typography.bodySmall,
                        color = FoodTheme.colors.textTertiary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
        FoodCard {
            Column(
                modifier = Modifier.padding(horizontal = FoodTheme.dimens.spaceLg, vertical = FoodTheme.dimens.spaceSm),
            ) {
                EntryRow(icon = Icons.Outlined.History, title = "历史记录", subtitle = "吃过什么，一目了然") {
                    onOpenHistory()
                }
                EntryRow(icon = Icons.Outlined.Settings, title = "设置", subtitle = "通知、默认值与数据管理") {
                    onOpenSettings()
                }
                EntryRow(icon = Icons.Outlined.Info, title = "关于", subtitle = "版本与说明") {
                    showAbout = true
                }
            }
        }
        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXxl))
    }
}

@Composable
private fun EntryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = FoodTheme.dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = FoodTheme.colors.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.size(FoodTheme.dimens.spaceMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = FoodTheme.colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = FoodTheme.colors.textTertiary,
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.titleMedium,
            color = FoodTheme.colors.textTertiary,
        )
    }
}
