package id.andreasmbngaol.agallery.domain.model

data class Album(
    val id: Long,
    val name: String,
    val coverUri: String?,
    val itemCount: Int,
)
