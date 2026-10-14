package com.fvstexpress.driver.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fvstexpress.driver.R
import com.fvstexpress.driver.data.model.Leg
import com.fvstexpress.driver.ui.theme.Fv
import java.time.LocalTime

// ---------------------------------------------------------------------------
// Primitives styled exactly like mockup-driver/assets/css/style.css
// ---------------------------------------------------------------------------

@Composable
fun FvIcon(@DrawableRes res: Int, size: Dp = 19.dp, tint: Color = Fv.Text, contentDescription: String? = null, modifier: Modifier = Modifier) {
    Icon(painterResource(res), contentDescription, modifier.size(size), tint = tint)
}

/** Plain text with the mockup's defaults (system font, --color-text). */
@Composable
fun T(
    text: String,
    size: TextUnit = 14.5.sp,
    color: Color = Fv.Text,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(text, modifier, color = color, fontSize = size, fontWeight = weight, textAlign = align ?: TextAlign.Unspecified, lineHeight = lineHeight, maxLines = maxLines)
}

@Composable
fun T(text: AnnotatedString, size: TextUnit = 14.5.sp, color: Color = Fv.Text, modifier: Modifier = Modifier, lineHeight: TextUnit = TextUnit.Unspecified) {
    Text(text, modifier, color = color, fontSize = size, lineHeight = lineHeight)
}

/** h1 = 1.2rem, h2 = 1.05rem, h3 = .95rem, weight 600. */
@Composable fun H1(text: String, modifier: Modifier = Modifier) = T(text, 19.2.sp, weight = FontWeight.SemiBold, modifier = modifier, lineHeight = 24.sp)
@Composable fun H2(text: String, modifier: Modifier = Modifier) = T(text, 16.8.sp, weight = FontWeight.SemiBold, modifier = modifier, lineHeight = 21.sp)
@Composable fun H3(text: String, modifier: Modifier = Modifier) = T(text, 15.2.sp, weight = FontWeight.SemiBold, modifier = modifier, lineHeight = 19.sp)

/** 1px --color-border rule. */
@Composable
fun Rule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Fv.Border))
}

enum class BtnKind { Primary, Secondary, Danger, Success }

/** .btn (+ .btn-primary / .btn-secondary / .btn-danger / .btn-success). Disabled = 45 % opacity. */
@Composable
fun FvButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.Secondary,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    small: Boolean = false,
    busy: Boolean = false,
    leadingText: String? = null,
) {
    val (bg, border, fg) = when (kind) {
        BtnKind.Primary -> Triple(Fv.Accent, Fv.Accent, Color.White)
        BtnKind.Secondary -> Triple(Fv.SurfaceAlt, Fv.BorderStrong, Fv.Text)
        BtnKind.Danger -> Triple(Fv.DangerSoft, Fv.Danger, Fv.Danger)
        BtnKind.Success -> Triple(Fv.Success, Fv.Success, Fv.OnSuccess)
    }
    val shape = RoundedCornerShape(Fv.RadiusMd)
    val active = enabled && !busy
    Row(
        modifier
            .alpha(if (enabled || kind == BtnKind.Success) 1f else 0.45f)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable(enabled = active, onClick = onClick)
            .padding(if (small) PaddingValues(horizontal = 10.dp, vertical = 5.dp) else PaddingValues(horizontal = 14.dp, vertical = 9.dp)),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(15.dp), color = fg, strokeWidth = 2.dp)
        } else {
            if (icon != null) FvIcon(icon, if (small) 13.dp else 15.dp, fg)
            if (leadingText != null) T(leadingText, if (small) 11.5.sp else 13.sp, fg)
            T(label, if (small) 11.5.sp else 13.sp, fg, FontWeight.SemiBold, align = TextAlign.Center, lineHeight = 18.sp)
        }
    }
}

/** .form-group label */
@Composable
fun FormLabel(text: String, modifier: Modifier = Modifier, color: Color = Fv.TextMuted, size: TextUnit = 11.5.sp) {
    T(text, size, color, FontWeight.SemiBold, modifier.padding(bottom = 4.dp))
}

/** .form-group input / textarea: surface-alt, 1px border-strong, radius 4, padding 8×10, 13px. */
@Composable
fun FvInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    locked: Boolean = false,
) {
    val shape = RoundedCornerShape(Fv.RadiusSm)
    val style = TextStyle(color = Fv.Text, fontSize = 13.sp, lineHeight = 18.sp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled && !locked,
        singleLine = minLines == 1,
        minLines = minLines,
        textStyle = style,
        cursorBrush = SolidColor(Fv.Accent),
        visualTransformation = if (password) PasswordVisualTransformation('•') else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                if (locked) Modifier.fillMaxWidth().padding(vertical = 2.dp)
                else Modifier.fillMaxWidth().clip(shape).background(Fv.SurfaceAlt).border(1.dp, Fv.BorderStrong, shape)
                    .heightIn(min = if (minLines > 1) 70.dp else 0.dp).padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                if (value.isEmpty() && !locked) T(placeholder, 13.sp, Fv.TextFaint.copy(alpha = 0.55f))
                inner()
            }
        },
    )
}

/** .form-group wrapper: label + field, 12 dp bottom margin. */
@Composable
fun FormGroup(label: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        if (label != null) FormLabel(label)
        content()
    }
}

/** .form-help */
@Composable
fun FormHelp(text: String, modifier: Modifier = Modifier, align: TextAlign? = null) {
    T(text, 11.sp, Fv.TextFaint, modifier = modifier.padding(top = 4.dp), align = align, lineHeight = 15.sp)
}

enum class Tone { Info, Warning, Danger, Success, Neutral, Accent }

private fun toneColors(t: Tone): Pair<Color, Color> = when (t) {
    Tone.Info, Tone.Accent -> Fv.AccentSoft to Fv.AccentHover
    Tone.Warning -> Fv.WarningSoft to Fv.Warning
    Tone.Danger -> Fv.DangerSoft to Fv.Danger
    Tone.Success -> Fv.SuccessSoft to Fv.Success
    Tone.Neutral -> Fv.NeutralSoft to Fv.TextMuted
}

/** .alert (.alert-info / -warning / -danger / -success): radius 8, padding 10×12, 12px, 1px border. */
@Composable
fun FvAlert(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val (bg, fg) = toneColors(tone)
    val shape = RoundedCornerShape(Fv.RadiusMd)
    Box(modifier.fillMaxWidth().padding(vertical = 10.dp).clip(shape).background(bg).border(1.dp, fg, shape).padding(horizontal = 12.dp, vertical = 10.dp)) {
        T(text, 12.sp, fg, lineHeight = 17.sp)
    }
}

/** .badge: 10.5px bold uppercase pill. */
@Composable
fun FvBadge(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val (bg, fg) = toneColors(tone)
    Box(modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
        T(text.uppercase(), 10.5.sp, fg, FontWeight.Bold)
    }
}

/** .kc-leg-tag: 8.5px weight 800 uppercase pill — amber for pickup, blue for drop-off. */
@Composable
fun LegTag(leg: String, label: String? = null, modifier: Modifier = Modifier) {
    val bg = if (leg == Leg.PICKUP) Fv.Warning else Fv.Accent
    Box(modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 5.dp, vertical = 1.dp)) {
        T(label ?: if (leg == Leg.PICKUP) "▲" else "▼", 8.5.sp, Color.White, FontWeight.ExtraBold)
    }
}

/** .urgency-flag */
@Composable
fun UrgencyFlag() = T("⚑", 13.sp, Fv.Danger)

/** .online-toggle: 34×19 switch (green when online) + "Online"/"Offline" 11.5px. */
@Composable
fun OnlineToggle(online: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.clickable(remember { MutableInteractionSource() }, null) { onToggle(!online) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(34.dp, 19.dp).clip(RoundedCornerShape(999.dp)).background(if (online) Fv.Success else Fv.BorderStrong)) {
            Box(Modifier.offset(x = if (online) 17.dp else 2.dp, y = 2.dp).size(15.dp).clip(CircleShape).background(Color.White))
        }
        T(if (online) "Online" else "Offline", 11.5.sp)
    }
}

/** .segmented: surface-alt strip, 12.5px semibold, active = accent with white text. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().background(Fv.SurfaceAlt)) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).background(if (i == selected) Fv.Accent else Color.Transparent)
                        .clickable { onSelect(i) }.padding(vertical = 3.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) { T(label, 12.5.sp, if (i == selected) Color.White else Fv.Text, FontWeight.SemiBold) }
            }
        }
        Rule()
    }
}

data class Step(val name: String, val time: String? = null)

/**
 * .status-stepper: 20 dp dots joined by a 2 dp line; done = green with ✓, current = accent with
 * an hourglass. [timeBelow] puts the time under the name (the "-done" pages) instead of beside it.
 */
@Composable
fun StatusStepper(steps: List<Step>, doneCount: Int, currentIndex: Int?, timeBelow: Boolean = false) {
    Column(Modifier.padding(top = 4.dp)) {
        steps.forEachIndexed { i, s ->
            val done = i < doneCount
            val current = i == currentIndex
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(Modifier.width(20.dp).fillMaxHeight()) {
                    if (i < steps.lastIndex) {
                        val lineColor = if (done) Fv.Success else Fv.BorderStrong
                        Canvas(Modifier.fillMaxHeight().width(20.dp)) {
                            drawRect(lineColor, topLeft = Offset(9.dp.toPx(), 20.dp.toPx()), size = Size(2.dp.toPx(), size.height - 20.dp.toPx() + 1f))
                        }
                    }
                    val (bg, bd) = when {
                        done -> Fv.Success to Fv.Success
                        current -> Fv.Accent to Fv.Accent
                        else -> Fv.SurfaceAlt to Fv.BorderStrong
                    }
                    Box(Modifier.size(20.dp).clip(CircleShape).background(bg).border(2.dp, bd, CircleShape), contentAlignment = Alignment.Center) {
                        when {
                            done -> FvIcon(R.drawable.ic_check, 11.dp, Fv.OnSuccess)
                            current -> FvIcon(R.drawable.ic_hourglass, 12.dp, Color.White)
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.padding(top = 1.dp, bottom = if (i < steps.lastIndex) 10.dp else 0.dp)) {
                    if (timeBelow) {
                        T(s.name, 12.5.sp, weight = FontWeight.SemiBold, lineHeight = 17.sp)
                        s.time?.let { T(it, 11.sp, Fv.TextFaint) }
                    } else {
                        Row {
                            T(s.name, 12.5.sp, weight = FontWeight.SemiBold, lineHeight = 17.sp)
                            s.time?.let { T(it, 12.5.sp, Fv.TextFaint, modifier = Modifier.padding(start = 6.dp), lineHeight = 17.sp) }
                        }
                    }
                }
            }
        }
    }
}

/** .info-row: faint label left, value right, 12.5px. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        T(label, 12.5.sp, Fv.TextFaint)
        Spacer(Modifier.weight(1f))
        T(value, 12.5.sp, Fv.Text, align = TextAlign.End)
    }
}

/** .toggle-group: equal buttons, active = accent. */
@Composable
fun ToggleGroup(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, locked: Boolean = false) {
    if (locked) {
        T(options.firstOrNull { it.first == selected }?.second ?: "", 12.sp, Fv.Text, FontWeight.SemiBold)
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            val active = value == selected
            val shape = RoundedCornerShape(Fv.RadiusSm)
            Box(
                Modifier.weight(1f).clip(shape).background(if (active) Fv.Accent else Fv.SurfaceAlt)
                    .border(1.dp, if (active) Fv.Accent else Fv.BorderStrong, shape).clickable { onSelect(value) }.padding(8.dp),
                contentAlignment = Alignment.Center,
            ) { T(label, 12.sp, if (active) Color.White else Fv.TextMuted, FontWeight.SemiBold) }
        }
    }
}

/** .stat-box */
@Composable
fun StatBox(value: String, label: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Fv.RadiusMd)
    Column(
        modifier.clip(shape).background(Fv.Surface).border(1.dp, Fv.Border, shape).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        T(value, 20.8.sp, weight = FontWeight.Bold)
        T(label.uppercase(), 10.5.sp, Fv.TextFaint, modifier = Modifier.padding(top = 2.dp), align = TextAlign.Center)
    }
}

/** .order-message: dispatch message banner — danger-soft, 3 dp danger left border, ✉. */
@Composable
fun OrderMessage(text: AnnotatedString, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(top = 8.dp).height(IntrinsicSize.Min).clip(RoundedCornerShape(Fv.RadiusSm)).background(Fv.DangerSoft),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(Fv.Danger))
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            T("✉", 12.5.sp, Fv.Danger)
            T(text, 12.5.sp, Fv.Text, lineHeight = 17.5.sp)
        }
    }
}

/** .offline-note */
@Composable
fun OfflineNote(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(Fv.RadiusSm)).background(Fv.WarningSoft).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        T("⚠", 11.sp, Fv.Warning)
        T(text, 11.sp, Fv.Warning, lineHeight = 15.sp)
    }
}

/**
 * .day-graph-bar: 24 one-hour cells across the day, the delivery window in grey and "now" as a
 * white tick. Window times are "HH:MM".
 */
@Composable
fun DayGraphBar(start: String?, end: String?, modifier: Modifier = Modifier, now: LocalTime = LocalTime.now()) {
    fun frac(t: String?): Float? = t?.let { runCatching { LocalTime.parse(it) }.getOrNull() }?.let { (it.toSecondOfDay() / 86400f) }
    val s = frac(start)
    val e = frac(end)
    val n = now.toSecondOfDay() / 86400f
    Canvas(modifier.height(6.dp).clip(RoundedCornerShape(2.dp))) {
        val cell = size.width / 24f
        for (i in 0 until 24) drawRect(Fv.GraphTick, Offset(i * cell, 0f), Size(cell - 1.dp.toPx(), size.height))
        if (s != null || e != null) {
            val a = (s ?: (e!! - 1f / 48f)).coerceIn(0f, 1f)
            val b = (e ?: (s!! + 1f / 48f)).coerceIn(0f, 1f)
            drawRect(Fv.GraphWindow, Offset(a * size.width, 0f), Size(((b - a) * size.width).coerceAtLeast(2f), size.height))
        }
        drawRect(Color.White, Offset(n * size.width - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
    }
}

/** .info-contact-btn: 24 dp round person button next to a name. */
@Composable
fun ContactDot(onClick: () -> Unit) {
    Box(Modifier.padding(start = 4.dp).size(24.dp).clip(CircleShape).background(Fv.SurfaceAlt).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        FvIcon(R.drawable.ic_user, 14.dp)
    }
}

/** .contact-call-btn: 30 dp round button with a phone icon. */
@Composable
fun RoundIconButton(@DrawableRes icon: Int, size: Dp = 30.dp, iconSize: Dp = 14.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Fv.SurfaceAlt).border(1.dp, Fv.BorderStrong, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { FvIcon(icon, iconSize) }
}

/**
 * .confirm-overlay + .confirm-modal: dim backdrop, surface card (radius 14, padding 18, 1px
 * border-strong) with the ✕ in the corner.
 */
@Composable
fun FvModal(onDismiss: () -> Unit, title: String? = null, scrollable: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(Fv.RadiusLg)
            Box(Modifier.widthIn(max = 360.dp).fillMaxWidth().clip(shape).background(Fv.Surface).border(1.dp, Fv.BorderStrong, shape)) {
                Column(
                    Modifier.fillMaxWidth().then(if (scrollable) Modifier.heightIn(max = 560.dp) else Modifier).padding(18.dp),
                ) {
                    if (title != null) T(title, 15.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(end = 24.dp, bottom = 8.dp))
                    content()
                }
                Box(Modifier.align(Alignment.TopEnd).padding(12.dp).size(24.dp).clip(CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    FvIcon(R.drawable.ic_close, 16.dp, Fv.TextFaint, "Close")
                }
            }
        }
    }
}

/** .confirm-modal p */
@Composable
fun ModalText(text: String) = T(text, 13.sp, Fv.TextMuted, modifier = Modifier.padding(bottom = 16.dp), lineHeight = 19.5.sp)

/** .confirm-modal-actions: buttons sharing the row. */
@Composable
fun ModalActions(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** "Confirm status change" — every status tap asks once more. */
@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String = "Confirm", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    FvModal(onDismiss, title) {
        ModalText(text)
        ModalActions {
            FvButton("Cancel", onDismiss, Modifier.weight(1f))
            FvButton(confirmLabel, onConfirm, Modifier.weight(1f), BtnKind.Primary)
        }
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Fv.Bg), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Fv.Accent, strokeWidth = 2.dp)
    }
}

/** .placeholder-block style empty / error state. */
@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 60.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        T("⚠", 34.sp, Fv.TextFaint, modifier = Modifier.padding(bottom = 10.dp))
        T(message, 12.5.sp, Fv.TextFaint, align = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(14.dp))
            FvButton("Try again", onRetry, kind = BtnKind.Primary)
        }
    }
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(top = 60.dp, start = 24.dp, end = 24.dp), contentAlignment = Alignment.Center) {
        T(text, 12.5.sp, Fv.TextFaint, align = TextAlign.Center)
    }
}

/** Kept for older call sites: a padded alert. */
@Composable
fun InfoBanner(text: String, color: Color = Fv.Accent) {
    val tone = when (color) { Fv.Warning -> Tone.Warning; Fv.Danger -> Tone.Danger; Fv.Success -> Tone.Success; else -> Tone.Info }
    FvAlert(text, tone)
}

/** Section heading inside .info-card (h3 with 8 dp bottom margin). */
@Composable
fun SectionLabel(text: String) = H3(text, Modifier.padding(top = 10.dp, bottom = 8.dp))
