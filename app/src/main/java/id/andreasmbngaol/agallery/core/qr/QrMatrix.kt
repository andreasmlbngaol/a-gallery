package id.andreasmbngaol.agallery.core.qr

/**
 * A QR code as a grid of *modules* (not pixels); each dark module is `true`.
 *
 * The quiet zone is intentionally excluded -- the renderer draws its own margin
 * -- so callers are free to style the dots (square, round, or rounded) and place
 * a center logo.
 *
 * @property size the side length of the matrix in modules.
 * @property cells the row-major module grid, `true` for dark modules.
 */
class QrMatrix(
    val size: Int,
    private val cells: BooleanArray,
) {
    /**
     * Returns whether the module at ([x], [y]) is dark, treating out-of-bounds
     * coordinates as light.
     *
     * @param x the column index.
     * @param y the row index.
     * @return `true` if the module is dark and within bounds.
     */
    fun isDark(x: Int, y: Int): Boolean {
        if (x < 0 || y < 0 || x >= size || y >= size) return false
        return cells[y * size + x]
    }
}
