package es.gdgaranjuez.handsup.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.gdgaranjuez.handsup.AppModel
import es.gdgaranjuez.handsup.i18n.I18n
import es.gdgaranjuez.handsup.i18n.Lang

@Composable
fun LangToggle(app: AppModel, modifier: Modifier = Modifier) {
    val other = if (I18n.lang == Lang.ES) Lang.EN else Lang.ES
    Row(
        modifier
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .border(1.dp, tokens.line, CircleShape)
            .clickable { app.setLang(other) }
            .padding(horizontal = 12.dp)
            .testTag("lang-toggle"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconView(AppIcon.Globe, size = 16.dp, tint = MaterialTheme.colorScheme.onSurface)
        Text(" " + other.code.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
