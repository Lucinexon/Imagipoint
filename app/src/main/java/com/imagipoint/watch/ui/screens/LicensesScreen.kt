package com.imagipoint.watch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imagipoint.watch.ui.ErrorBanner
import com.imagipoint.watch.ui.LoadingRing
import com.imagipoint.watch.ui.ScreenScaffold
import com.imagipoint.watch.ui.SectionLabel
import com.imagipoint.watch.ui.theme.IpColors
import com.imagipoint.watch.ui.theme.IpTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val notices by produceState<Result<String>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() }
            }
        }
    }

    ScreenScaffold(title = "Licenses", onBack = onBack) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (val result = notices) {
                null -> LoadingRing(
                    modifier = Modifier.align(Alignment.Center),
                    color = IpColors.Accent,
                )

                else -> {
                    val text = result.getOrNull()
                    if (text == null) {
                        ErrorBanner(
                            message = "The bundled notices could not be read: " +
                                (result.exceptionOrNull()?.message ?: "unknown error"),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(18.dp),
                            warning = true,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 64.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item { SectionLabel("Bundled third-party notices") }
                            item {
                                Text(
                                    text = text,
                                    style = IpTypography.bodySmall.copy(
                                        fontSize = 8.5.sp,
                                        lineHeight = 12.sp,
                                    ),
                                    color = IpColors.TextSecondary,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
