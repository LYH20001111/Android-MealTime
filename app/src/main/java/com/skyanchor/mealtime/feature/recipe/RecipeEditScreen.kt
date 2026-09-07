package com.skyanchor.mealtime.feature.recipe

import android.net.Uri
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.skyanchor.mealtime.app.rememberAppContainer
import com.skyanchor.mealtime.core.common.copyImageToPrivate
import com.skyanchor.mealtime.core.model.Difficulty
import com.skyanchor.mealtime.core.model.chineseLabel
import com.skyanchor.mealtime.core.ui.FoodCard
import com.skyanchor.mealtime.core.ui.FoodTextField
import com.skyanchor.mealtime.core.ui.FoodTheme
import com.skyanchor.mealtime.core.ui.PrimaryButton
import com.skyanchor.mealtime.core.ui.SectionTitle
import com.skyanchor.mealtime.core.ui.SecondaryButton
import com.skyanchor.mealtime.core.ui.TagChip
import kotlinx.coroutines.launch


/**
 * 新增/编辑菜谱：基本信息、封面、分类/标签、食材/调料、步骤、备注（PAGES.md §5）。
 * 配料食材在保存时通过字典"选择或创建"，保证与库存可关联。
 */
@OptIn(ExperimentalLayoutApi::class)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FoodTheme.dimens.pageHorizontalPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDone) {
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
                color = FoodTheme.colors.textPrimary,
            )
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = FoodTheme.colors.primary)
            }
            return@Column
        }

        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
        FormLabel("菜名 *")
        FoodTextField(
            value = state.name,
            onValueChange = viewModel::setName,
            placeholder = "例如：番茄炒蛋",
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.nameError) {
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXs))
            Text(
                text = "请填写菜名",
                style = MaterialTheme.typography.bodySmall,
                color = FoodTheme.colors.danger,
            )
        }

        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
        FormLabel("封面")
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

        if (state.categories.isNotEmpty()) {
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
            FormLabel("分类")
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
        }

        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
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

        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
        FormLabel("制作时间（分钟）")
        FoodTextField(
            value = state.cookingTimeText,
            onValueChange = viewModel::setCookingTime,
            placeholder = "如：15",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        state.sections.forEach { section ->
            IngredientSection(
                title = section.typeLabel,
                lines = section.lines,
                onUpdate = { index, value -> viewModel.updateLine(section.typeKey, index, value) },
                onAdd = { viewModel.addLine(section.typeKey) },
                onRemove = { index -> viewModel.removeLine(section.typeKey, index) },
            )
        }

        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
        SectionTitle(text = "做法步骤")
        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
        state.steps.forEachIndexed { index, step ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm),
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(FoodTheme.colors.primarySoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
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
        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
        SecondaryButton(
            text = "＋ 添加步骤",
            onClick = viewModel::addStep,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
        FormLabel("简介")
        FoodTextField(
            value = state.description,
            onValueChange = viewModel::setDescription,
            placeholder = "一句话介绍这道菜",
            minLines = 2,
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
        FormLabel("备注")
        FoodTextField(
            value = state.note,
            onValueChange = viewModel::setNote,
            placeholder = "小贴士、失败经验等",
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.availableTags.isNotEmpty() || state.newTagText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
            FormLabel("标签")
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm),
            ) {
                state.availableTags.forEach { tag ->
                    TagChip(
                        text = tag.name,
                        selected = tag.id in state.selectedTagIds,
                        onClick = { viewModel.toggleTag(tag) },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm),
        ) {
            FoodTextField(
                value = state.newTagText,
                onValueChange = viewModel::setNewTagText,
                placeholder = "添加自定义标签",
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = "添加",
                onClick = viewModel::addNewTag,
            )
        }

        state.saveError?.let { error ->
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = FoodTheme.colors.danger,
            )
        }

        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXxl))
        PrimaryButton(
            text = if (state.isSaving) "保存中…" else "保存",
            onClick = { viewModel.save(onSaved = { onDone() }) },
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXxl))
    }
}

@Composable
private fun FormLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = FoodTheme.colors.textPrimary,
    )
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
            .clip(RoundedCornerShape(FoodTheme.dimens.radiusLg))
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
                    text = "添加封面图",
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
    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
    SectionTitle(text = title)
    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
    lines.forEachIndexed { index, line ->
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
