package com.xaeryx.translatorrep

import android.os.Bundle
import android.util.Log // SMOKE TEST — Story 1.6 AC-5: detekt SafeLog ForbiddenImport must fire on this line. REVERT before merging.
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xaeryx.translatorrep.ui.components.GlassIntensity
import com.xaeryx.translatorrep.ui.components.MonochromeGlassPanel
import com.xaeryx.translatorrep.ui.theme.TextPrimary
import com.xaeryx.translatorrep.ui.theme.TextSecondary
import com.xaeryx.translatorrep.ui.theme.TranslatorRepTheme

/**
 * Story 1.1 hello-world host. Renders a single `MonochromeGlassPanel` to
 * validate that the Theme A monochrome-glass primitive is wired
 * end-to-end. Story 1.2 (Android, but really Story 1.8) replaces this with
 * the actual Paired-Empty home + pairing flow.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge per UX spec — In-Call surfaces use the full screen.
        enableEdgeToEdge()
        setContent {
            TranslatorRepTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    HelloWorldGlassPanelScreen()
                }
            }
        }
    }
}

@Composable
private fun HelloWorldGlassPanelScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        MonochromeGlassPanel(
            intensity = GlassIntensity.Regular,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "TranslatorRep",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                Text(
                    text = "Scaffold ready — Story 1.1 done.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
private fun HelloWorldPreview() {
    TranslatorRepTheme {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            HelloWorldGlassPanelScreen()
        }
    }
}
