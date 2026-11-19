package cz.genesis.pdf

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.IOException

class MainActivity : Activity() {
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var pageIndex = 0
    private lateinit var image: ImageView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 17, 23))
            setPadding(16, 24, 16, 16)
        }
        val open = button("Otevřít PDF") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
            }, 100)
        }
        status = TextView(this).apply {
            text = "Vyber PDF dokument"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 12)
        }
        image = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val navigation = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        navigation.addView(button("Předchozí") { showPage(pageIndex - 1) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        navigation.addView(button("Další") { showPage(pageIndex + 1) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(open)
        root.addView(status)
        root.addView(image, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(navigation)
        setContentView(root)
    }

    private fun button(title: String, action: () -> Unit) = Button(this).apply {
        text = title
        setTextColor(Color.WHITE)
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(27, 30, 161)))
        setOnClickListener { action() }
    }

    @Deprecated("Uses the platform document picker result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            data?.data?.let { openPdf(it) }
        }
    }

    private fun openPdf(uri: Uri) {
        closePdf()
        try {
            descriptor = contentResolver.openFileDescriptor(uri, "r")
            renderer = PdfRenderer(descriptor ?: throw IOException("Dokument nelze otevřít"))
            showPage(0)
        } catch (e: Exception) {
            closePdf()
            status.text = "Nelze otevřít PDF: ${e.localizedMessage}"
        }
    }

    private fun showPage(index: Int) {
        val pdf = renderer ?: return
        if (index !in 0 until pdf.pageCount) return
        pageIndex = index
        pdf.openPage(index).use { page ->
            val width = (page.width * 2).coerceAtMost(2400)
            val height = (page.height * width.toFloat() / page.width).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            image.setImageBitmap(bitmap)
        }
        status.text = "Stránka ${index + 1} / ${pdf.pageCount}"
    }

    private fun closePdf() {
        imageOrNull()?.setImageDrawable(null)
        renderer?.close()
        renderer = null
        descriptor?.close()
        descriptor = null
    }

    private fun imageOrNull(): ImageView? = if (::image.isInitialized) image else null

    override fun onDestroy() {
        closePdf()
        super.onDestroy()
    }
}
