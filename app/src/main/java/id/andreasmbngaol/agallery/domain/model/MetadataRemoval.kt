package id.andreasmbngaol.agallery.domain.model

import android.content.IntentSender

/**
 * Kategori metadata yang bisa dibuang dari sebuah foto (fitur 1.4.0).
 *
 * - [LOCATION]  : semua tag GPS (koordinat, arah, kecepatan, dst).
 * - [CAMERA]    : perangkat + pengaturan pemotretan (merek/model, aperture,
 *                 shutter, ISO, focal length, flash, tanggal ambil, dst).
 * - [ALL]       : gabungan semuanya + info personal lain (artist, copyright,
 *                 komentar, maker note, dll). Orientasi SENGAJA dipertahankan
 *                 supaya foto tidak jadi miring setelah di-strip.
 */
enum class MetadataCategory { LOCATION, CAMERA, ALL }

/**
 * Hasil percobaan hapus metadata. Mirror pola rename/delete yang memakai
 * IntentSender untuk consent tulis file non-owned (API 30+ / RecoverableSecurityException).
 */
sealed interface MetadataRemovalOutcome {
    /** Berhasil. [savedAsCopy] true kalau hasil disimpan sebagai salinan bersih. */
    data class Success(val savedAsCopy: Boolean) : MetadataRemovalOutcome

    /** Butuh persetujuan user dulu (file bukan milik app). Setelah disetujui, ulangi. */
    data class NeedsConsent(val intentSender: IntentSender) : MetadataRemovalOutcome

    /** Format tidak didukung untuk strip lossless (mis. HEIC/HEIF, video). */
    data object UnsupportedFormat : MetadataRemovalOutcome

    /** Gagal karena error I/O atau lainnya. */
    data object Failed : MetadataRemovalOutcome
}
