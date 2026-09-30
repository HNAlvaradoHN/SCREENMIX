package com.screenmix.app.ui.prompt

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screenmix.app.R
import com.screenmix.app.handler.PromptPosition
import com.screenmix.app.ui.rememberOverlaySystemBarInsets
import com.screenmix.app.ui.theme.ScreenMixBodyFont
import com.screenmix.app.ui.theme.ScreenMixDisplayFont
import com.screenmix.app.ui.theme.ScreenMixThemeColors

@Composable
fun ScreenshotPromptContent(
    onCopyDelete: () -> Unit,
    onCopySave: () -> Unit,
    onDismiss: () -> Unit,
    overlayMode: Boolean = false,
    position: PromptPosition = PromptPosition.CENTER,
    tapOutsideToDismiss: Boolean = true,
) {
    val colors = ScreenMixThemeColors.current
    val (statusBarInset, navBarInset) = rememberOverlaySystemBarInsets()
    val scrimAlpha = if (overlayMode) 0f else 0.45f
    val effectivePosition = if (overlayMode) position else PromptPosition.BOTTOM
    val alignment = when (effectivePosition) {
        PromptPosition.TOP -> Alignment.TopCenter
        PromptPosition.CENTER -> Alignment.Center
        PromptPosition.BOTTOM -> Alignment.BottomCenter
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (overlayMode) {
                    Modifier.padding(top = statusBarInset, bottom = navBarInset)
                } else {
                    Modifier.statusBarsPadding()
                },
            )
            .then(
                if (tapOutsideToDismiss) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    )
                } else Modifier,
            ),
    ) {
        if (scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha)),
            )
        }

        Surface(
            modifier = Modifier
                .align(alignment)
                .fillMaxWidth()
                .then(
                    if (overlayMode) {
                        Modifier.padding(horizontal = 18.dp)
                    } else {
                        Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    },
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            color = colors.surface,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 14.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontFamily = ScreenMixDisplayFont,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent,
                    fontSize = 13.sp,
                )
                Text(
                    text = stringResource(R.string.prompt_title),
                    fontFamily = ScreenMixDisplayFont,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PromptButton(
                        label = stringResource(R.string.action_copy_delete),
                        onClick = onCopyDelete,
                        primary = false,
                        modifier = Modifier.weight(1f),
                    )
                    PromptButton(
                        label = stringResource(R.string.action_copy_save),
                        onClick = onCopySave,
                        primary = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                PromptButton(
                    label = stringResource(R.string.action_close),
                    onClick = onDismiss,
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!overlayMode) {
                    Text(
                        text = stringResource(R.string.tap_outside_title),
                        fontFamily = ScreenMixBodyFont,
                        color = colors.textMuted,
                        fontSize = 9.sp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 12.dp)
                            .clickable(onClick = onDismiss),
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = ScreenMixThemeColors.current
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 54.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) colors.accent else colors.surfaceAlt,
            contentColor = if (primary) colors.background else colors.textPrimary,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text = label,
            fontFamily = ScreenMixBodyFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
