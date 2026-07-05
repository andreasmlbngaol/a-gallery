package id.andreasmbngaol.agallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import id.andreasmbngaol.agallery.core.navigation.AGalleryNavDisplay
import id.andreasmbngaol.agallery.core.permission.MediaPermissionGate
import id.andreasmbngaol.agallery.presentation.theme.AGalleryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AGalleryTheme {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    MediaPermissionGate {
                        AGalleryNavDisplay()
                    }
                }
            }
        }
    }
}
