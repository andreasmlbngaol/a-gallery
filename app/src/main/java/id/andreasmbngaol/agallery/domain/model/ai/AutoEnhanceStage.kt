package id.andreasmbngaol.agallery.domain.model.ai

/**
 * The ordered stages of the one-tap Auto Enhance pipeline. They always run in
 * this declared order (clean up the whole frame, then enlarge, then restore
 * faces) so each stage feeds the next its cleaned-up output. Face Restore is
 * last on purpose: restoring a face and THEN upscaling it re-processed the face
 * and looked over-done, so faces are restored on the already-enlarged frame and
 * become the final word.
 */
enum class AutoEnhanceStage {
    /** Denoise / restore detail across the whole photo (SCUNet). */
    ENHANCE,

    /** Enlarge and sharpen the result (Real-ESRGAN). */
    UPSCALE,

    /** Sharpen and restore every detected face (GPEN); applied last so the
     *  restored face is never re-upscaled. */
    FACE_RESTORE,
}
