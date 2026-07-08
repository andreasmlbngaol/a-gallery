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

# ------------------------------------------------------------
# ML Kit Barcode Scanning (bundled) — QR Detection 1.7.0
# ------------------------------------------------------------
# ML Kit barcode-scanning (bundled) SUDAH membawa consumer R8 rules sendiri di
# dalam AAR-nya, jadi TIDAK butuh -keep manual. Blanket keep seperti
# `-keep class com.google.mlkit.** { *; }` justru anti-pattern (mematikan
# shrink/optimize R8 utk seluruh paket) dan itulah yang memicu warning
# "Overly broad keep rule affecting more than 100 classes".
# (Rule `native <methods>` juga sudah ada di proguard-android-optimize.txt.)
#
# Penyebab bug "scan QR gagal HANYA di release": R8 full mode (default AGP 8/9)
# kadang tetap men-strip kelas internal ML Kit yang diakses via refleksi/JNI
# walau consumer rules ada. Mitigasinya BUKAN blanket keep, melainkan
# `android.enableR8.fullMode=false` di gradle.properties.
# Kalau kelak mau balik ke full mode: jalankan build release, baca nama kelas
# persis dari crash logcat, lalu tambahkan SATU keep sempit mengikuti pola
# ML Kit Known Issues, contoh:
#   -keep class com.google.mlkit.<...>.internal.<KelasDariLogcat> { *; }
-dontwarn com.google.mlkit.**

# ------------------------------------------------------------
# ZXing core — QR Generator 1.6.0 (pure-Java, offline)
# ------------------------------------------------------------
# Hanya modul `core` (encoder) yang dipakai; ZXing core aman untuk R8.
# Cukup redam warning opsional bila muncul; jangan blanket-keep supaya
# kelas yang tak terpakai tetap bisa di-shrink.
-dontwarn com.google.zxing.**

# ------------------------------------------------------------
# ONNX Runtime (Android) — AI framework / Background Remover 2.0.0
# ------------------------------------------------------------
# ONNX Runtime itu JNI-heavy: layer native (libonnxruntime.so &
# libonnxruntime4j_jni.so) mengakses kelas, field, & method Java di paket
# `ai.onnxruntime.**` LEWAT NAMA saat runtime (JNI FindClass/GetMethodID).
# Kalau R8 nge-rename atau nge-strip mereka, inference bakal gagal HANYA di
# build release (mis. UnsatisfiedLinkError / NoSuchMethodError) padahal debug
# aman. AAR-nya TIDAK membawa consumer-rules yang lengkap untuk ini, jadi keep
# manual berikut WAJIB supaya fungsi AI & ONNX Runtime tetap hidup setelah
# shrink/obfuscate. Ini beda kasus dg ML Kit (yg consumer-rules-nya lengkap),
# makanya di sini blanket keep paket ONNX memang diperlukan & benar.
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** {
    native <methods>;
    <fields>;
    <init>(...);
}
-dontwarn ai.onnxruntime.**

# Kode AI milik app sendiri (core/ai, data/ai, domain/**/ai, presentation/ai)
# dipanggil langsung (bukan via refleksi) sehingga reachable & aman dari shrink;
# binding Koin & DTO @Serializable sudah ditangani aturan di atas. Tidak perlu
# keep tambahan untuk logika AI internal — cukup surface JNI ONNX Runtime di atas.
