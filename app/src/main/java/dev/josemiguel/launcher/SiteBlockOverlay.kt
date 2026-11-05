package dev.josemiguel.launcher

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import dev.josemiguel.launcher.ui.Palette

/**
 * Covers the browser while the guard moves an over-budget site's tab to the
 * new-tab page: the site stays hidden and untouchable meanwhile, and the
 * browser stays usable once the tab has moved. If the move fails, [showChoices]
 * offers **new tab** (try again) and **home**.
 *
 * Plain Views rather than Compose: a service overlay has no lifecycle owner
 * for a ComposeView. Styled after BlockedScreen.
 */
class SiteBlockOverlay(
    private val service: AccessibilityService,
    private val onNewTab: () -> Unit,
    private val onHome: () -> Unit,
) {

    private var view: View? = null
    private var choices: View? = null

    fun show(groupName: String, limitMinutes: Int, withChoices: Boolean) {
        hide()
        val palette = Palette.forSystem(service)
        val pad = dp(28)
        val choiceRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(choice("new tab", palette.ink.toArgb(), onNewTab), weighted())
            addView(choice("home", palette.ink.toArgb(), onHome), weighted())
            visibility = if (withChoices) View.VISIBLE else View.INVISIBLE
        }
        val root = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, dp(24), pad, dp(24))
            setBackgroundColor(palette.background.toArgb())
            // Swallows every touch that misses the choices.
            isClickable = true
            addView(View(service), weighted(vertical = true))
            addView(text("that's your $limitMinutes minutes\nof ${groupName.lowercase()} today", 28f, palette.accent.toArgb()))
            addView(text("come back tomorrow.", 16f, palette.muted.toArgb()).apply { setPadding(0, dp(16), 0, 0) })
            addView(View(service), weighted(vertical = true))
            addView(choiceRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        ).apply {
            // As the guard's shield: reach under the status bar and cutout.
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
        }
        try {
            service.getSystemService(WindowManager::class.java).addView(root, params)
            view = root
            choices = choiceRow
        } catch (e: Exception) {
            Log.w(TAG, "overlay failed", e)
        }
    }

    fun showChoices() {
        choices?.visibility = View.VISIBLE
    }

    fun hideChoices() {
        choices?.visibility = View.INVISIBLE
    }

    fun hide() {
        view?.let { service.getSystemService(WindowManager::class.java).removeView(it) }
        view = null
        choices = null
    }

    private fun text(s: String, sp: Float, color: Int) = TextView(service).apply {
        text = s
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    }

    private fun choice(s: String, color: Int, onClick: () -> Unit) = text(s, 22f, color).apply {
        setPadding(0, dp(16), 0, dp(16))
        setOnClickListener { onClick() }
    }

    private fun weighted(vertical: Boolean = false) =
        if (vertical) LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        else LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

    private fun dp(v: Int) = (v * service.resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "SiteBlockOverlay"
    }
}
