package app.buildx.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.ui.theme.BuildXFontFamily
import app.buildx.chat.ui.theme.BuildXTheme
import java.util.Locale

data class CountryOption(val code: String, val name: String)

private fun buildCountryOptions(locale: Locale): List<CountryOption> =
    Locale.getISOCountries()
        .map { code ->
            CountryOption(
                code = code,
                name = Locale("", code).getDisplayCountry(locale)
            )
        }
        .filter { it.name.isNotBlank() }
        .distinctBy { it.code }
        .sortedBy { it.name.lowercase(locale) }

/**
 * Searchable country/region picker backed by the platform ISO-3166 list.
 * Selecting "Not shown" clears the value; the value is only stored when the
 * user explicitly chooses one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryPickerSheet(
    isOpen: Boolean,
    selectedCode: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return
    val colors = BuildXTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val locale = remember { Locale.getDefault() }
    val countries = remember(locale) { buildCountryOptions(locale) }
    val filtered = remember(query, countries) {
        val q = query.trim().lowercase(locale)
        if (q.isEmpty()) {
            countries
        } else {
            countries.filter {
                it.name.lowercase(locale).contains(q) || it.code.lowercase(locale).contains(q)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.cardBackground,
        scrimColor = colors.scrimColor,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.grabHandle)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.pv2_country_title),
                color = colors.primaryText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 22.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceMuted)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.pv2_country_search_hint),
                        color = colors.secondaryText,
                        fontSize = 14.5.sp
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = BuildXFontFamily,
                        fontSize = 14.5.sp,
                        color = colors.primaryText,
                        textAlign = TextAlign.Start
                    ),
                    cursorBrush = SolidColor(colors.primaryBlue),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    CountryRow(
                        label = stringResource(R.string.pv2_country_not_shown),
                        selected = selectedCode.isNullOrBlank(),
                        onClick = {
                            onSelect(null)
                            onDismiss()
                        }
                    )
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.pv2_country_empty),
                            color = colors.secondaryText,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp)
                        )
                    }
                }
                items(filtered, key = { it.code }) { country ->
                    CountryRow(
                        label = country.name,
                        selected = country.code.equals(selectedCode, ignoreCase = true),
                        onClick = {
                            onSelect(country.code)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CountryRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = BuildXTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = colors.primaryText,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start
        )
        if (selected) {
            Icon(
                painter = painterResource(id = R.drawable.ic_check),
                contentDescription = null,
                tint = colors.primaryBlue,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
