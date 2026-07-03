# ============================================================
# AGallery — R8 / ProGuard rules
# ============================================================

# Biar stacktrace crash tetap kebaca setelah kode di-obfuscate.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*, InnerClasses, Signature

# ------------------------------------------------------------
# kotlinx.serialization
# Runtime-nya sudah bawa consumer-rules sendiri; ini penguat khusus
# buat model @Serializable milik app (DataStore DTO, dsb) supaya
# serializer-nya nggak ke-strip R8.
# ------------------------------------------------------------
-keep @kotlinx.serialization.Serializable class id.andreasmbngaol.agallery.** { *; }
-keepclasseswithmembers class **$$serializer { *; }

# Enum yang di-serialize (GallerySortOrder, EdgeEffectMode) butuh values()/valueOf().
-keepclassmembers enum * {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ------------------------------------------------------------
# Library UI pihak ketiga (Coil, Kyant, Telephoto, Haze, Phosphor,
# Room, Koin) umumnya sudah bawa consumer-rules masing-masing.
# Kalau nanti build R8 ngeluh soal kelas tertentu, tambahin
# -keep / -dontwarn spesifik di bawah ini.
# ------------------------------------------------------------
