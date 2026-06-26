package works.tether.cli

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

// Copied verbatim from dayfold (zero coupling). Renders a QR of the device
// verification URL into the terminal so the owner can scan it with the app
// instead of typing the user_code. The text user_code/URI is ALWAYS printed
// alongside so SSH/CI/non-UTF-8 terminals still work.
object Qr {
  private const val ESC = ""
  private const val UPPER_HALF = "▀" // ▀

  fun encode(text: String, margin: Int = 2): BitMatrix {
    val hints = mapOf(
      EncodeHintType.MARGIN to margin,
      EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
    )
    return QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
  }

  fun render(text: String): String {
    val m = encode(text)
    val sb = StringBuilder()
    var y = 0
    while (y < m.height) {
      for (x in 0 until m.width) {
        val top = m.get(x, y)
        val bottom = y + 1 < m.height && m.get(x, y + 1)
        val fg = if (top) "30" else "37"
        val bg = if (bottom) "40" else "47"
        sb.append(ESC).append("[").append(fg).append(';').append(bg).append('m').append(UPPER_HALF)
      }
      sb.append(ESC).append("[0m\n")
      y += 2
    }
    return sb.toString()
  }
}
