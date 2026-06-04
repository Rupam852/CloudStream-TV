package com.cloudstream.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.text.font.FontFamily
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cloudstream.tv.ui.theme.CloudStreamTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TVFocusableItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    glowColor: Color = CloudStreamTheme.extraColors.focusGlow,
    borderColor: Color = CloudStreamTheme.extraColors.focusBorder,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    scaleOnFocus: Float = 1.07f,
    content: @Composable (isFocused: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) scaleOnFocus else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "scale"
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isFocused) borderColor else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocus()
        }
    }

    Surface(
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(focusedScale = scaleOnFocus),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        border = ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(border = BorderStroke(1.5.dp, Color.Transparent)),
            focusedBorder = androidx.tv.material3.Border(border = BorderStroke(2.dp, borderColor))
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = androidx.tv.material3.Glow(
                elevationColor = glowColor,
                elevation = 12.dp
            )
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        interactionSource = interactionSource,
        modifier = modifier
    ) {
        content(isFocused)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun TVCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    iconTint: Color = MaterialTheme.colorScheme.primary,
    badgeText: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "scale"
    )
    
    val focusColor = CloudStreamTheme.extraColors.focusBorder

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocus()
        }
    }

    val cardModifier = if (onLongClick != null) {
        modifier
            .scale(scale)
            .focusGroup()
            .onFocusChanged { }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    } else {
        modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    }

    Box(
        modifier = cardModifier
            .width(160.dp)
            .height(130.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface
            )
            .drawBehind {
                if (isFocused) {
                    drawRoundRect(
                        color = focusColor,
                        style = Stroke(width = 2.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
                    )
                }
            }
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .align(Alignment.Start),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isFocused) MaterialTheme.colorScheme.primary else iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelLarge,
                color = CloudStreamTheme.extraColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (badgeText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeText,
                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TVSidebarItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val backgroundBrush = when {
        isFocused -> Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
            )
        )
        isSelected -> Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                Color.Transparent
            )
        )
        else -> Brush.horizontalGradient(colors = listOf(Color.Transparent, Color.Transparent))
    }

    val animatedBorderColor by animateColorAsState(
        targetValue = if (isFocused) CloudStreamTheme.extraColors.focusBorder else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "sidebarBorder"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundBrush)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect
            )
            .drawBehind {
                if (isFocused) {
                    drawRoundRect(
                        color = animatedBorderColor,
                        style = Stroke(width = 1.5.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                    )
                }
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = when {
                isFocused -> MaterialTheme.colorScheme.primary
                isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            },
            modifier = Modifier.size(22.dp)
        )
        
        if (isExpanded) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal
                ),
                color = when {
                    isFocused -> MaterialTheme.colorScheme.onSurface
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TVSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onSearchAction: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocusedBySystem by interactionSource.collectIsFocusedAsState()

    var isEditing by remember { mutableStateOf(false) }
    val textFocusRequester = remember { FocusRequester() }

    val textInteractionSource = remember { MutableInteractionSource() }
    val isTextFocused by textInteractionSource.collectIsFocusedAsState()

    val isVisualFocused = isFocusedBySystem || isTextFocused
    val focusBorderColor = CloudStreamTheme.extraColors.focusBorder

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .clickable {
                isEditing = true
            }
            .clip(RoundedCornerShape(23.dp))
            .background(
                if (isVisualFocused) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface
            )
            .drawBehind {
                if (isVisualFocused) {
                    drawRoundRect(
                        color = focusBorderColor,
                        style = Stroke(width = 2.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(23.dp.toPx())
                    )
                }
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        LaunchedEffect(isEditing) {
            if (isEditing) {
                textFocusRequester.requestFocus()
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Search,
                contentDescription = null,
                tint = if (isVisualFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                enabled = isEditing,
                interactionSource = textInteractionSource,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.SansSerif
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { 
                        isEditing = false
                        onSearchAction()
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(textFocusRequester)
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused) {
                            isEditing = false
                        }
                    },
                decorationBox = { innerTextField ->
                    innerTextField()
                }
            )
        }
    }
}
