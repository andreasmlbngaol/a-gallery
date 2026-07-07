package id.andreasmbngaol.agallery.core.permission

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.FolderSimple
import com.adamglin.phosphoricons.bold.Image
import com.adamglin.phosphoricons.bold.WarningCircle
import id.andreasmbngaol.agallery.R

/**
 * Mandatory permission gate that wraps the entire app (installed in
 * `MainActivity`) and runs two steps in order each time the app opens.
 *
 * 1. **Media access (photos & videos).** Instead of showing the system dialog
 *    immediately, an explanation screen with an "Allow access" button is shown
 *    first so the user understands the context and is less likely to decline.
 *    Pressing it triggers the standard permission dialog ("Allow all / Select
 *    photos / Don't allow"). If full access is not granted, the app is not
 *    closed — the user is routed to App Settings to choose "Allow all", because
 *    Android will not re-show the dialog after a denial. The app only closes if
 *    the user taps "Exit".
 * 2. **All-files access (`MANAGE_EXTERNAL_STORAGE`).** An explanation screen
 *    with a "Grant access" button that opens system Settings. Required so that
 *    delete/rename/move happen directly without a per-action system dialog, plus
 *    the 30-day background Trash auto-purge.
 *
 * [content] is shown only once both permissions are granted.
 *
 * @param modifier the modifier applied to each step and to [content].
 * @param content the app UI, displayed after both permissions are satisfied.
 */
@Composable
fun MediaPermissionGate(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    var mediaGranted by remember { mutableStateOf(MediaPermissions.hasEssential(context)) }
    var filesGranted by remember { mutableStateOf(AllFilesAccess.isGranted()) }

    when {
        !mediaGranted -> MediaStep(
            modifier = modifier,
            onGranted = {
                mediaGranted = true
                filesGranted = AllFilesAccess.isGranted()
            },
            onExit = { activity?.finishAndRemoveTask() },
        )

        !filesGranted -> AllFilesStep(
            modifier = modifier,
            onGranted = { filesGranted = true },
            onExit = { activity?.finishAndRemoveTask() },
        )

        else -> content()
    }
}

/**
 * First gate step: requests full media access.
 *
 * A limited, selected, or denied result does not close the app; instead the
 * screen switches to a denied state that explains the situation and routes the
 * user to App Settings, the reliable path to choose "Allow all".
 *
 * @param modifier the modifier applied to the step.
 * @param onGranted invoked once full media access is granted.
 * @param onExit invoked when the user chooses to exit the app.
 */
@Composable
private fun MediaStep(
    modifier: Modifier,
    onGranted: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    var denied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (MediaPermissions.essentialGranted(result)) onGranted() else denied = true
    }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (MediaPermissions.hasEssential(context)) onGranted() else denied = true
    }

    PermissionBlockingScreen(
        modifier = modifier,
        icon = if (denied) PhosphorIcons.Bold.WarningCircle else PhosphorIcons.Bold.Image,
        title = if (denied) stringResource(R.string.permission_media_title_denied) else stringResource(R.string.permission_media_title),
        message = if (denied) {
            stringResource(R.string.permission_media_message_denied)
        } else {
            stringResource(R.string.permission_media_message)
        },
        primaryLabel = if (denied) stringResource(R.string.permission_open_settings) else stringResource(R.string.permission_allow_access),
        onPrimary = {
            if (denied) {
                settingsLauncher.launch(appSettingsIntent(context))
            } else {
                permissionLauncher.launch(MediaPermissions.required().toTypedArray())
            }
        },
        onExit = onExit,
    )
}

/**
 * Second gate step: requests All-files access.
 *
 * On returning from system Settings the grant is re-checked; if it is still not
 * granted the step stays in place rather than closing the app.
 *
 * @param modifier the modifier applied to the step.
 * @param onGranted invoked once All-files access is granted (or not required).
 * @param onExit invoked when the user chooses to exit the app.
 */
@Composable
private fun AllFilesStep(
    modifier: Modifier,
    onGranted: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (AllFilesAccess.isGranted()) onGranted()
    }
    PermissionBlockingScreen(
        modifier = modifier,
        icon = PhosphorIcons.Bold.FolderSimple,
        title = stringResource(R.string.permission_all_files_title),
        message = stringResource(R.string.permission_all_files_message),
        primaryLabel = stringResource(R.string.permission_grant_access),
        onPrimary = {
            if (AllFilesAccess.isSupported()) {
                launcher.launch(AllFilesAccess.settingsIntent(context))
            } else {
                onGranted()
            }
        },
        onExit = onExit,
    )
}

/**
 * Themed blocking screen (Material You): a circular primary-container icon, a
 * title, an explanatory message, a filled primary button, and a subtle "Exit".
 *
 * @param modifier the modifier applied to the root column.
 * @param icon the icon shown in the circular badge.
 * @param title the screen title.
 * @param message the explanatory body text.
 * @param primaryLabel the label of the filled primary button.
 * @param onPrimary invoked when the primary button is tapped.
 * @param onExit invoked when the "Exit" button is tapped.
 */
@Composable
private fun PermissionBlockingScreen(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp),
            )
        }

        Spacer(Modifier.height(28.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp),
        )

        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onPrimary,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                text = primaryLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onExit) {
            Text(
                text = stringResource(R.string.action_exit),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Builds an intent to this app's system settings details page.
 *
 * @param context the context whose package name targets the page.
 * @return the intent to launch.
 */
private fun appSettingsIntent(context: Context): Intent =
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )

/**
 * Finds the nearest [Activity] by unwrapping [ContextWrapper]s, or `null` if
 * none is found.
 *
 * @return the enclosing activity, or `null`.
 */
private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
