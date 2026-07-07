package id.andreasmbngaol.agallery.data.local.mediastore

import androidx.exifinterface.media.ExifInterface

/**
 * Konstanta tag EXIF yang dipakai BARENG oleh [MetadataRemover] (strip metadata)
 * dan [ImageFormatConverter] (salin metadata saat konversi). Dikumpulkan di satu
 * tempat supaya tak duplikat & tetap konsisten.
 */
internal object MediaExifTags {
    /** Format yang bisa di-strip lossless oleh ExifInterface. */
    val STRIP_SUPPORTED_MIME = setOf(
        "image/jpeg", "image/jpg", "image/png", "image/webp",
    )

    /** Semua tag GPS/lokasi. */
    val LOCATION_EXIF_TAGS = listOf(
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_AREA_INFORMATION,
        ExifInterface.TAG_GPS_DOP, ExifInterface.TAG_GPS_MAP_DATUM,
        ExifInterface.TAG_GPS_SPEED, ExifInterface.TAG_GPS_SPEED_REF,
        ExifInterface.TAG_GPS_TRACK, ExifInterface.TAG_GPS_TRACK_REF,
        ExifInterface.TAG_GPS_IMG_DIRECTION, ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
        ExifInterface.TAG_GPS_DEST_LATITUDE, ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
        ExifInterface.TAG_GPS_DEST_LONGITUDE, ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
        ExifInterface.TAG_GPS_DEST_BEARING, ExifInterface.TAG_GPS_DEST_BEARING_REF,
        ExifInterface.TAG_GPS_DEST_DISTANCE, ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
        ExifInterface.TAG_GPS_VERSION_ID, ExifInterface.TAG_GPS_SATELLITES,
        ExifInterface.TAG_GPS_STATUS, ExifInterface.TAG_GPS_MEASURE_MODE,
        ExifInterface.TAG_GPS_DIFFERENTIAL,
    )

    /** Tag perangkat + pengaturan pemotretan (termasuk kapan diambil). */
    val CAMERA_EXIF_TAGS = listOf(
        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_APERTURE_VALUE,
        ExifInterface.TAG_MAX_APERTURE_VALUE, ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_SHUTTER_SPEED_VALUE, ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_EXPOSURE_MODE, ExifInterface.TAG_EXPOSURE_PROGRAM,
        ExifInterface.TAG_EXPOSURE_INDEX,
        // Catatan: TAG_ISO_SPEED_RATINGS deprecated & 1:1 dgn tag di bawah
        // (EXIF 0x8827), jadi cukup pakai TAG_PHOTOGRAPHIC_SENSITIVITY.
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_FLASH,
        ExifInterface.TAG_FLASH_ENERGY, ExifInterface.TAG_METERING_MODE,
        ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_LIGHT_SOURCE,
        ExifInterface.TAG_DIGITAL_ZOOM_RATIO, ExifInterface.TAG_SCENE_CAPTURE_TYPE,
        ExifInterface.TAG_SCENE_TYPE, ExifInterface.TAG_SENSING_METHOD,
        ExifInterface.TAG_CONTRAST, ExifInterface.TAG_SATURATION,
        ExifInterface.TAG_SHARPNESS, ExifInterface.TAG_BRIGHTNESS_VALUE,
        ExifInterface.TAG_SUBJECT_DISTANCE, ExifInterface.TAG_SUBJECT_DISTANCE_RANGE,
        ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
    )

    /**
     * Info personal lain (hanya dipakai saat "Semua"). Orientasi SENGAJA
     * tak dimasukkan supaya foto tak jadi miring setelah di-strip.
     */
    val MISC_EXIF_TAGS = listOf(
        ExifInterface.TAG_ARTIST, ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_IMAGE_DESCRIPTION, ExifInterface.TAG_USER_COMMENT,
        ExifInterface.TAG_MAKER_NOTE, ExifInterface.TAG_IMAGE_UNIQUE_ID,
        ExifInterface.TAG_DATETIME, ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_SUBSEC_TIME,
    )

    /** Semua tag yg disalin saat konversi (orientasi ditangani terpisah). */
    val COPYABLE_EXIF_TAGS: List<String> =
        (LOCATION_EXIF_TAGS + CAMERA_EXIF_TAGS + MISC_EXIF_TAGS).distinct()
}
