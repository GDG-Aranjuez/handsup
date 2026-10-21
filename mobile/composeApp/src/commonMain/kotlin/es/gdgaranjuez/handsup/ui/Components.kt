@file:OptIn(ExperimentalMaterial3Api::class)

package es.gdgaranjuez.handsup.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.gdgaranjuez.handsup.currentTimeMillis
import es.gdgaranjuez.handsup.domain.Delivery
import es.gdgaranjuez.handsup.i18n.t
import qrcode.QRCode

/** A lazy list centered and width-capped like [ContentColumn]. */
@Composable
fun ContentLazyColumn(
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.widthIn(max = 640.dp).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/** Centers content and caps its width so tablets do not stretch the phone layout. */
@Composable
fun ContentColumn(
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            // scrollable so forms stay reachable with the keyboard open on small screens
            Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

enum class ButtonKind { Primary, Ghost, Danger }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    icon: AppIcon? = null,
    enabled: Boolean = true,
    tag: String? = null,
) {
    val tk = tokens
    val shape = RoundedCornerShape(12.dp)
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            AppIconView(icon, size = 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val base = modifier.heightIn(min = 44.dp).then(if (tag != null) Modifier.testTag(tag) else Modifier)
    when (kind) {
        ButtonKind.Primary -> Button(
            onClick, base, enabled, shape = shape,
            colors = ButtonDefaults.buttonColors(containerColor = tk.brand, contentColor = tk.onBrand),
            content = content,
        )
        ButtonKind.Ghost -> OutlinedButton(
            onClick, base, enabled, shape = shape,
            border = BorderStroke(1.dp, tk.line),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            content = content,
        )
        ButtonKind.Danger -> OutlinedButton(
            onClick, base, enabled, shape = shape,
            border = BorderStroke(1.dp, tk.line),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = tk.danger),
            content = content,
        )
    }
}

enum class PillTone { Neutral, Brand, Ok, Warn, Live }

@Composable
fun Pill(text: String, tone: PillTone = PillTone.Neutral, modifier: Modifier = Modifier) {
    val tk = tokens
    val (bg, fg) = when (tone) {
        PillTone.Neutral -> tk.surface2 to MaterialTheme.colorScheme.onSurface
        PillTone.Brand -> tk.brand to tk.onBrand
        PillTone.Ok -> tk.surface2 to tk.ok
        PillTone.Warn -> tk.surface2 to tk.warn
        PillTone.Live -> tk.live to Color.White
    }
    Box(modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Selectable pill used for tabs, filters and the delivery toggle. */
@Composable
fun ToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: AppIcon? = null,
    selectedColor: Color? = null,
    tag: String? = null,
) {
    val tk = tokens
    val bg = if (selected) (selectedColor ?: MaterialTheme.colorScheme.onSurface) else tk.surface2
    val fg = if (selected) {
        if (selectedColor == null) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) AppIconView(icon, size = 14.dp, tint = fg)
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    borderColor: Color = tokens.line,
    borderWidth: Dp = 1.dp,
    background: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun DeliveryBadge(delivery: Delivery, modifier: Modifier = Modifier) {
    val tk = tokens
    val speak = delivery == Delivery.SPEAK
    Row(
        modifier.clip(CircleShape).background(tk.surface2).padding(horizontal = 10.dp, vertical = 3.dp).testTag("delivery-${delivery.wire}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val color = if (speak) tk.accentText else tk.muted
        AppIconView(if (speak) AppIcon.Mic else AppIcon.Volume, size = 14.dp, tint = color)
        Text(t(if (speak) "delivery.speak.short" else "delivery.read.short"), color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ScreenTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    // same max width as the content below, so tablets line the bar up with the column
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    Box(Modifier.widthIn(max = 672.dp).fillMaxWidth()) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                Box(Modifier.size(48.dp).clickable(onClick = onBack).testTag("back"), contentAlignment = Alignment.Center) {
                    AppIconView(AppIcon.Back, size = 22.dp, tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
    }
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    tag: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier),
        singleLine = singleLine,
        minLines = minLines,
        enabled = enabled,
        isError = isError,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun MutedText(text: String, modifier: Modifier = Modifier, fontSize: Int = 14) {
    Text(text, modifier, color = tokens.muted, fontSize = fontSize.sp)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, color = tokens.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
}

/** QR code drawn from the raw module matrix; always dark-on-white so it scans in dark mode. */
@Composable
fun QrView(value: String, modifier: Modifier = Modifier, size: Dp = 200.dp) {
    val matrix = remember(value) { runCatching { QRCode(value).rawData.map { row -> row.map { it.dark } } }.getOrNull() }
    Box(modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp).testTag("qr")) {
        if (matrix != null && matrix.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                val n = matrix.size
                val cell = this.size.width / n
                for (r in 0 until n) for (c in 0 until matrix[r].size) {
                    if (matrix[r][c]) drawRect(Color(0xFF14141A), Offset(c * cell, r * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}

fun timeAgo(thenMs: Long, nowMs: Long = currentTimeMillis()): String {
    val s = (nowMs - thenMs) / 1000
    return when {
        s < 45 -> t("time.now")
        s < 3600 -> t("time.minutes", "n" to (s / 60).coerceAtLeast(1))
        s < 86400 -> t("time.hours", "n" to (s / 3600))
        else -> t("time.days", "n" to (s / 86400))
    }
}
