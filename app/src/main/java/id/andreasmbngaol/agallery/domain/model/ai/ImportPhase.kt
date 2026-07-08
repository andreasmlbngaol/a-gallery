package id.andreasmbngaol.agallery.domain.model.ai

/**
 * Progress stage reported while importing a model, so the UI can show what is
 * happening during a potentially long copy of a large `.onnx` file.
 *
 * - [COPYING]: streaming the picked file into app storage.
 * - [VERIFYING]: checking size / checksum and running a one-shot inference probe
 *   to confirm the file is a usable model.
 */
enum class ImportPhase {
    COPYING,
    VERIFYING,
}
