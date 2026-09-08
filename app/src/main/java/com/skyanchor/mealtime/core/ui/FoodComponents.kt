package com.skyanchor.mealtime.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** 主操作按钮：紫渐变底、白字、高 48dp、圆角 16dp（UI_DESIGN.md §7）。 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = FoodTheme.colors
    val dimens = FoodTheme.dimens
    Box(
        modifier = modifier
            .heightIn(min = dimens.buttonHeight)
            .clip(RoundedCornerShape(dimens.radiusLg))
            .background(
                if (enabled) colors.primaryGradient else Brush.horizontalGradient(
                    listOf(colors.primaryLight, colors.primaryLight)
                )
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = dimens.spaceXxl, vertical = dimens.spaceMd),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = colors.surface,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 次操作按钮：浅紫底、深紫字、高 48dp。 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = FoodTheme.colors
    val dimens = FoodTheme.dimens
    Box(
        modifier = modifier
            .heightIn(min = dimens.buttonHeight)
            .clip(RoundedCornerShape(dimens.radiusLg))
            .background(colors.primarySoft)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceMd),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = colors.primaryDark,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** 模块标题（18sp SemiBold），可在右侧附加操作（如“更多 ›”）。 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = FoodTheme.colors.textPrimary,
        )
        Spacer(modifier = Modifier.weight(1f))
        trailing()
    }
}

/** 标签胶囊：浅紫底 + 深紫字（UI_DESIGN.md §9）。 */
@Composable
fun TagChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = FoodTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) colors.primary else colors.primarySoft)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) colors.surface else colors.primaryDark,
        )
    }
}

/** 通用空状态：图标 + 标题 + 辅助说明 + 可选行动按钮。 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = FoodTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FoodTheme.dimens.spaceXxl, vertical = FoodTheme.dimens.spaceXxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(colors.primarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        if (hint != null) {
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
            )
        }
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
            SecondaryButton(text = actionText, onClick = onAction)
        }
    }
}

/** 页面级浮动操作按钮：紫渐变圆底（UI_DESIGN.md §2.2 渐变允许位置）。 */
@Composable
fun FoodFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String,
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(brush = FoodTheme.colors.primaryGradient)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = contentDescription,
            tint = FoodTheme.colors.surface,
        )
    }
}

/** 统一卡片容器：白底、圆角 16dp、轻阴影（UI_DESIGN.md §6）。 */
@Composable
fun FoodCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = FoodTheme.colors.surface,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FoodTheme.dimens.radiusLg))
            .background(backgroundColor),
    ) {
        content()
    }
}

/** 搜索框：白底、圆角 12dp、高 52dp（UI_DESIGN.md §8）。 */
@Composable
fun FoodSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索",
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = FoodTheme.colors.textTertiary,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = FoodTheme.colors.textTertiary,
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "清空",
                    tint = FoodTheme.colors.textTertiary,
                    modifier = Modifier
                        .clickable { onValueChange("") }
                        .padding(FoodTheme.dimens.spaceSm),
                )
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(FoodTheme.dimens.radiusMd),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = FoodTheme.colors.surface,
            unfocusedContainerColor = FoodTheme.colors.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = FoodTheme.colors.primary,
        ),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = FoodTheme.colors.textPrimary),
    )
}

/** 表单文本框：白底、圆角 12dp、高 52dp（UI_DESIGN.md §8）。 */
@Composable
fun FoodTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    leadingIcon: ImageVector? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = {
            if (placeholder.isNotEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FoodTheme.colors.textTertiary,
                )
            }
        },

        leadingIcon = leadingIcon?.let { icon ->
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = FoodTheme.colors.primary.copy(
                        alpha = 0.75f
                    ),
                    modifier = Modifier.size(20.dp),
                )
            }
        },

        singleLine = singleLine,
        minLines = minLines,
        shape = RoundedCornerShape(FoodTheme.dimens.radiusMd),
        keyboardOptions = keyboardOptions,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = FoodTheme.colors.surface,
            unfocusedContainerColor = FoodTheme.colors.surface,
            focusedIndicatorColor = FoodTheme.colors.primaryLight,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = FoodTheme.colors.primary,
        ),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = FoodTheme.colors.textPrimary),
    )
}
