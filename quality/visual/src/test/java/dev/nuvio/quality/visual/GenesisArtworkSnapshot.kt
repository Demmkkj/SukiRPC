package dev.nuvio.quality.visual

import android.graphics.Color
import android.widget.FrameLayout
import android.widget.ImageView
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class GenesisArtworkSnapshot {
    @get:Rule val paparazzi = Paparazzi(theme = "android:Theme.Material.NoActionBar")

    @Test
    fun notificationGlyph() {
        val image = ImageView(paparazzi.context).apply {
            setBackgroundColor(Color.rgb(16, 14, 23))
            setImageResource(R.drawable.suki_eclipse_notification)
            scaleType = ImageView.ScaleType.CENTER
            layoutParams = FrameLayout.LayoutParams(150, 150)
        }
        paparazzi.snapshot(image, "notification_glyph")
    }
}
