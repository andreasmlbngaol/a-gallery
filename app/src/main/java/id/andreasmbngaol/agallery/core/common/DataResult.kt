package id.andreasmbngaol.agallery.core.common

sealed interface DataResult<out T> {
    data class Success<T>(val data: T) : DataResult<T>
    data class Error(val throwable: Throwable) : DataResult<Nothing>
    data object Loading : DataResult<Nothing>
}
