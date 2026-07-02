package id.andreasmbngaol.agallery.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.andreasmbngaol.agallery.domain.model.AppSettings
import id.andreasmbngaol.agallery.domain.model.EdgeEffectMode
import id.andreasmbngaol.agallery.domain.usecase.GetSettingsUseCase
import id.andreasmbngaol.agallery.domain.usecase.SetEdgeEffectModeUseCase
import id.andreasmbngaol.agallery.domain.usecase.SetGridColumnsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    getSettings: GetSettingsUseCase,
    private val setEdgeEffectMode: SetEdgeEffectModeUseCase,
    private val setGridColumns: SetGridColumnsUseCase,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = getSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppSettings(),
        )

    fun onSelectEdgeEffect(mode: EdgeEffectMode) {
        viewModelScope.launch {
            setEdgeEffectMode(mode)
        }
    }

    fun onSelectGridColumns(columns: Int) {
        viewModelScope.launch {
            setGridColumns(columns)
        }
    }
}
