package id.andreasmbngaol.agallery.presentation.ai

import androidx.annotation.StringRes

/**
 * A one-shot, user-facing message emitted by the AI view models and resolved to
 * text by the screen (so the domain/presentation layers stay free of
 * [android.content.Context]).
 *
 * @property textRes the string resource to show.
 * @property formatArg an optional argument for a `%1$s` placeholder (e.g. a model
 *   or file name); null for messages without a placeholder.
 */
data class AiUiMessage(
    @StringRes val textRes: Int,
    val formatArg: String? = null,
)
