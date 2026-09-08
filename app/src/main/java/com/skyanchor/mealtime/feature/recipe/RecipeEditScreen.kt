package com.skyanchor.mealtime.feature.recipe

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.skyanchor.mealtime.app.rememberAppContainer
import com.skyanchor.mealtime.core.common.copyImageToPrivate
import com.skyanchor.mealtime.core.model.Difficulty
import com.skyanchor.mealtime.core.model.Tag
import com.skyanchor.mealtime.core.model.chineseLabel
import com.skyanchor.mealtime.core.ui.FoodTextField
import com.skyanchor.mealtime.core.ui.FoodTheme
import com.skyanchor.mealtime.core.ui.PrimaryButton
import com.skyanchor.mealtime.core.ui.SecondaryButton
import com.skyanchor.mealtime.core.ui.SectionTitle
import com.skyanchor.mealtime.core.ui.TagChip
import kotlinx.coroutines.launch

/**
 * 新增/编辑菜谱（规范文档：新增菜谱页面规范文档）。
 * 分层输入：基本信息（菜名/封面/分类，必填 3 项）→ 菜谱信息（难度/时间）→
 * 用料 → 做法 → 更多信息（简介/备注/标签，默认折叠）。
 * 保存按钮固定底部；已选标签 Chip 自带 × 可直接删除，自定义标签经底部弹层添加。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditScreen(
    recipeId: Long,
    onDone: () -> Unit,
    viewModel: RecipeEditViewModel = viewModel(
        key = "recipe_edit_${recipeId}",
        factory = RecipeEditViewModel.factory(
            container = rememberAppContainer(),
            recipeId = recipeId.takeIf { it > 0 },
        ),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showTagSheet by rememberSaveable { mutableStateOf(false) }
    var showLeaveDialog by rememberSaveable { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val stored = copyImageToPrivate(context, uri, "recipe_covers")
                if (stored != null) viewModel.setImageUri(stored)
            }
        }
    }

    fun attemptLeave() {
        if (viewModel.isDirty()) showLeaveDialog = true else onDone()
    }

    BackHandler(onBack = ::attemptLeave)

    Scaffold(
        containerColor = FoodTheme.colors.background,
        bottomBar = {
            if (!state.isLoading) {
                PrimaryButton(
                    text = if (state.isSaving) "保存中…" else "保存菜谱",
                    onClick = { viewModel.save(onSaved = { onDone() }) },
                    enabled = !state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = FoodTheme.dimens.pageHorizontalPadding,
                            vertical = FoodTheme.dimens.spaceMd,
                        ),
                )
            }
        },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = FoodTheme.colors.primary)
            }
            return@Scaffold
        }

        // 编辑已有简介/备注/标签时默认展开"更多信息"，新增时保持折叠
        val hasMoreInfo = state.description.isNotBlank() ||
            state.note.isNotBlank() ||
            state.selectedTagIds.isNotEmpty()
        var moreExpanded by rememberSaveable(state.isLoading) {
            mutableStateOf(!state.isLoading && hasMoreInfo)
        }
        val selectedTags = state.availableTags.filter { it.id in state.selectedTagIds }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = FoodTheme.dimens.pageHorizontalPadding,
                end = FoodTheme.dimens.pageHorizontalPadding,
                bottom = innerPadding.calculateBottomPadding(),
            ),
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth().offset(x = (-20).dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::attemptLeave) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = FoodTheme.colors.textPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.width(FoodTheme.dimens.spaceSm))
                    Text(
                        text = if (state.isNew) "新增菜谱" else "编辑菜谱",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = FoodTheme.colors.textPrimary,
                    )
                }
            }

            if (state.isNew) {
                item {
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
                    GuideBanner()
                }
            }

            // ① 基本信息：菜名 / 封面 / 分类（必填 3 项）
            item {
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
                SectionHeader(title = "基本信息", badge = "必填 3 项")
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
                FormLabel("菜名", true)
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                FoodTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    placeholder = "给这道菜起个名字吧",
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.nameError) ErrorText("请输入菜名")
            }
            item {
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
                FormLabel("封面", true)
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                CoverPicker(
                    imageUri = state.imageUri,
                    onPick = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onClear = { viewModel.setImageUri(null) },
                )
                if (state.imageError) ErrorText("请添加封面图")
            }
            if (state.categories.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
                    FormLabel("分类", true)
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm),
                    ) {
                        state.categories.forEach { category ->
                            TagChip(
                                text = category.name,
                                selected = state.selectedCategoryId == category.id,
                                onClick = { viewModel.selectCategory(category.id) },
                            )
                        }
                    }
                    if (state.categoryError) ErrorText("请选择一个分类")
                }
            }

            // ② 菜谱信息：难度 / 制作时间
            item {
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXxl))
                SectionHeader(title = "菜谱信息")
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
                FormLabel("难度")
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                Row(horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm)) {
                    Difficulty.entries.forEach { difficulty ->
                        TagChip(
                            text = difficulty.chineseLabel,
                            selected = state.difficulty == difficulty,
                            onClick = { viewModel.setDifficulty(difficulty) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
                FormLabel("制作时间（分钟）")
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                FoodTextField(
                    value = state.cookingTimeText,
                    onValueChange = viewModel::setCookingTime,
                    placeholder = "如：15",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = Icons.Outlined.AccessTime
                )
            }

            // ③ 用料：按食材种类分区（默认 食材/调料）
            item {
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXxl))
                SectionTitle(text = "用料")
            }
            state.sections.forEach { section ->
                item(key = "section_${section.typeKey}") {
                    IngredientSection(
                        title = section.typeLabel,
                        lines = section.lines,
                        onUpdate = { index, value -> viewModel.updateLine(section.typeKey, index, value) },
                        onAdd = { viewModel.addLine(section.typeKey) },
                        onRemove = { index -> viewModel.removeLine(section.typeKey, index) },
                    )
                }
            }

            // ④ 做法：步骤
            item {
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXxl))
                SectionTitle(text = "做法步骤")
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
            }
            itemsIndexed(state.steps) { index, step ->
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(FoodTheme.colors.primaryLight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = FoodTheme.colors.primaryDark,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    FoodTextField(
                        value = step,
                        onValueChange = { viewModel.updateStep(index, it) },
                        placeholder = "第 ${index + 1} 步做什么",
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.removeStep(index) }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "删除步骤",
                            tint = FoodTheme.colors.textTertiary,
                        )
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                SecondaryButton(
                    text = "＋ 添加步骤",
                    onClick = viewModel::addStep,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ⑤ 更多信息 · 可选（默认折叠）：简介 / 备注 / 标签
            item {
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXxl))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { moreExpanded = !moreExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "更多信息 · 可选",
                        style = MaterialTheme.typography.titleMedium,
                        color = FoodTheme.colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = if (moreExpanded) {
                            Icons.Outlined.KeyboardArrowUp
                        } else {
                            Icons.Outlined.KeyboardArrowDown
                        },
                        contentDescription = if (moreExpanded) "收起" else "展开",
                        tint = FoodTheme.colors.textTertiary,
                    )
                }
            }
            if (moreExpanded) {
                item {
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
                    FormLabel("简介")
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                    FoodTextField(
                        value = state.description,
                        onValueChange = viewModel::setDescription,
                        placeholder = "一句话介绍这道菜",
                        minLines = 2,
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
                    FormLabel("备注")
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                    FoodTextField(
                        value = state.note,
                        onValueChange = viewModel::setNote,
                        placeholder = "小贴士、失败经验等",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
                    FormLabel("标签")
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                    if (selectedTags.isEmpty()) {
                        Text(
                            text = "还没添加标签",
                            style = MaterialTheme.typography.bodySmall,
                            color = FoodTheme.colors.textTertiary,
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm),
                        ) {
                            selectedTags.forEach { tag ->
                                RemovableTagChip(
                                    text = tag.name,
                                    onRemove = { viewModel.toggleTag(tag) },
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                    SecondaryButton(
                        text = "＋ 添加标签",
                        onClick = { showTagSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                state.saveError?.let { error ->
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = FoodTheme.colors.danger,
                    )
                }
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXxl))
            }
        }
    }

    if (showTagSheet) {
        AddTagSheet(
            availableTags = state.availableTags,
            selectedTagIds = state.selectedTagIds,
            newTagText = state.newTagText,
            onToggleTag = viewModel::toggleTag,
            onNewTagTextChange = viewModel::setNewTagText,
            onAddNewTag = viewModel::addNewTag,
            onDismiss = { showTagSheet = false },
        )
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("确定离开？") },
            text = { Text("当前内容尚未保存。") },
            confirmButton = {
                TextButton(onClick = onDone) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text("继续编辑") }
            },
        )
    }
}

@Composable
private fun FormLabel(
    text: String,
    required: Boolean = false,
) {
    Text(
        text = buildAnnotatedString {
            append(text)

            if (required) {
                append(" ")
                withStyle(
                    SpanStyle(
                        color = FoodTheme.colors.danger
                    )
                ) {
                    append("*")
                }
            }
        },
        style = MaterialTheme.typography.titleMedium,
        color = FoodTheme.colors.textPrimary,
    )
}

@Composable
private fun ErrorText(text: String) {
    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXs))
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = FoodTheme.colors.danger,
    )
}

/** 分组标题，可带淡紫色小标签（如"必填 3 项"） */
@Composable
private fun SectionHeader(title: String, badge: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = FoodTheme.colors.textPrimary,
        )
        if (badge != null) {
            Spacer(modifier = Modifier.width(FoodTheme.dimens.spaceSm))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(FoodTheme.dimens.radiusPill))
                    .background(FoodTheme.colors.primaryLight)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = FoodTheme.colors.primaryDark,
                )
            }
        }
    }
}

/** 轻量引导 Banner：淡紫渐变，不加重插画（规范文档 §36） */
@Composable
private fun GuideBanner(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(
                RoundedCornerShape(
                    FoodTheme.dimens.radiusLg
                )
            )
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFE1D8FF),
                        Color(0xFFC3B0F8),
                    )
                )
            )
    ) {

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFF2EEFF),
                            Color(0xFFF2EEFF).copy(alpha = 0.95f),
                            Color(0xFFF2EEFF).copy(alpha = 0.25f),
                            Color.Transparent,
                        ),
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(
                    start = 16.dp,
                    end = 100.dp,
                )
        ) {
            Text(
                text = "记录每一道",
                style = MaterialTheme.typography.bodySmall,
                color = FoodTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = "让你心动的味道",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = FoodTheme.colors.primary,
            )
        }
    }
}

/** 已选标签：点击 Chip（含 ×）立即移除，无需进入独立管理页 */
@Composable
private fun RemovableTagChip(text: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(FoodTheme.dimens.radiusPill))
            .background(FoodTheme.colors.primaryLight)
            .clickable(onClick = onRemove)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = FoodTheme.colors.primaryDark,
        )
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = "删除标签",
            tint = FoodTheme.colors.primaryDark,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .size(14.dp),
        )
    }
}

/** 添加标签底部弹层：系统标签点选 + 自定义标签输入（规范文档 §34/§35） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTagSheet(
    availableTags: List<Tag>,
    selectedTagIds: Set<Long>,
    newTagText: String,
    onToggleTag: (Tag) -> Unit,
    onNewTagTextChange: (String) -> Unit,
    onAddNewTag: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FoodTheme.dimens.pageHorizontalPadding)
                .padding(bottom = FoodTheme.dimens.spaceXxl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "添加标签",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FoodTheme.colors.textPrimary,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("完成") }
            }
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
            Text(
                text = "系统标签",
                style = MaterialTheme.typography.bodyMedium,
                color = FoodTheme.colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm),
            ) {
                availableTags.forEach { tag ->
                    TagChip(
                        text = tag.name,
                        selected = tag.id in selectedTagIds,
                        onClick = { onToggleTag(tag) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
            Text(
                text = "自定义标签",
                style = MaterialTheme.typography.bodyMedium,
                color = FoodTheme.colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm),
            ) {
                FoodTextField(
                    value = newTagText,
                    onValueChange = onNewTagTextChange,
                    placeholder = "输入自定义标签",
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = "＋",
                    onClick = onAddNewTag,
                )
            }
        }
    }
}

@Composable
private fun CoverPicker(
    imageUri: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(FoodTheme.dimens.radiusXl))
            .background(FoodTheme.colors.surfaceSoft)
            .clickable(onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = "封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(FoodTheme.dimens.spaceSm)
                    .clip(CircleShape)
                    .background(FoodTheme.colors.surface),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "移除封面",
                    tint = FoodTheme.colors.textSecondary,
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.AddPhotoAlternate,
                    contentDescription = null,
                    tint = FoodTheme.colors.primary,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                Text(
                    text = "添加菜品图片",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FoodTheme.colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXs))
                Text(
                    text = "一张好看的图片更诱人",
                    style = MaterialTheme.typography.bodySmall,
                    color = FoodTheme.colors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun IngredientSection(
    title: String,
    lines: List<IngredientLineInput>,
    onUpdate: (Int, IngredientLineInput) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    Column {
        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = FoodTheme.colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
        lines.forEachIndexed { index, line ->
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm),
            ) {
                FoodTextField(
                    value = line.name,
                    onValueChange = { onUpdate(index, line.copy(name = it)) },
                    placeholder = "名称",
                    modifier = Modifier.weight(1f),
                )
                FoodTextField(
                    value = line.quantity,
                    onValueChange = { onUpdate(index, line.copy(quantity = it)) },
                    placeholder = "数量",
                    modifier = Modifier.width(72.dp),
                )
                FoodTextField(
                    value = line.unit,
                    onValueChange = { onUpdate(index, line.copy(unit = it)) },
                    placeholder = "单位",
                    modifier = Modifier.width(64.dp),
                )
                IconButton(onClick = { onRemove(index) }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "删除",
                        tint = FoodTheme.colors.textTertiary,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
        SecondaryButton(
            text = "＋ 添加$title",
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
