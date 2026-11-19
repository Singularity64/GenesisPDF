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
import android.widget.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File

class MainActivity : Activity() {
    private var document: PDDocument? = null
    private var sourceFile: File? = null
    private var pageIndex = 0
    private lateinit var preview: ImageView
    private lateinit var status: TextView
    private lateinit var choices: LinearLayout
    private val entries = mutableListOf<TextEntry>()
    private data class TextEntry(val text: String, val x: Float, val top: Float, val width: Float, val height: Float)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 17, 23))
            setPadding(16, 28, 16, 16)
        }
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        toolbar.addView(button("Otevřít") { pickPdf() }, LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(button("Uložit jako") { saveAs() }, LinearLayout.LayoutParams(0, -2, 1f))
        status = TextView(this).apply {
            text = "Otevři PDF a vyber text k přepsání"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 12)
        }
        preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        choices = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroller = ScrollView(this).apply { addView(choices) }
        val navigation = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        navigation.addView(button("◀") { showPage(pageIndex - 1) }, LinearLayout.LayoutParams(0, -2, 1f))
        navigation.addView(button("▶") { showPage(pageIndex + 1) }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(toolbar)
        root.addView(status)
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 3f))
        root.addView(navigation)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 2f))
        setContentView(root)
    }

    private fun button(label: String, click: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(Color.WHITE)
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(27, 30, 161)))
        setOnClickListener { click() }
    }

    private fun pickPdf() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }, 100)
    }

    private fun saveAs() {
        if (document == null) return
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, "GenesisPDF-upraveno.pdf")
        }, 101)
    }

    @Deprecated("Activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            when (requestCode) {
                100 -> {
                    document?.close()
                    val file = File(cacheDir, "working.pdf")
                    contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
                    sourceFile = file
                    document = PDDocument.load(file)
                    showPage(0)
                }
                101 -> {
                    contentResolver.openOutputStream(uri, "w")!!.use { document!!.save(it) }
                    Toast.makeText(this, "PDF uloženo", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            status.text = "Chyba: ${e.localizedMessage}"
        }
    }

    private fun showPage(index: Int) {
        val pdf = document ?: return
        if (index !in 0 until pdf.numberOfPages) return
        pageIndex = index
        entries.clear()
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                if (text.isBlank() || textPositions.isEmpty()) return
                val first = textPositions.first()
                val last = textPositions.last()
                val width = (last.xDirAdj + last.widthDirAdj - first.xDirAdj).coerceAtLeast(1f)
                entries.add(TextEntry(text.trim(), first.xDirAdj, first.yDirAdj - first.heightDir, width, first.heightDir.coerceAtLeast(8f)))
            }
        }
        stripper.startPage = index + 1
        stripper.endPage = index + 1
        stripper.getText(pdf)
        render()
        choices.removeAllViews()
        entries.take(80).forEach { entry ->
            choices.addView(button(entry.text.take(65)) { edit(entry) })
        }
        status.text = "Stránka ${index + 1}/${pdf.numberOfPages} • ${entries.size} textových úseků"
    }

    private fun edit(entry: TextEntry) {
        val field = EditText(this).apply {
            setText(entry.text)
            setSelection(text.length)
            setTextColor(Color.BLACK)
            setSingleLine(false)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Přepsat text")
            .setView(field)
            .setNegativeButton("Zrušit", null)
            .setPositiveButton("Použít") { _, _ ->
                try {
                    val pdf = document ?: return@setPositiveButton
                    val page = pdf.getPage(pageIndex)
                    val h = page.mediaBox.height
                    val size = (entry.height * 0.85f).coerceIn(7f, 30f)
                    val replacement = field.text.toString()
                    PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                        stream.setNonStrokingColor(255, 255, 255)
                        stream.addRect(entry.x - 1, h - entry.top - entry.height - 2, entry.width + 6, entry.height + 5)
                        stream.fill()
                        stream.setNonStrokingColor(0, 0, 0)
                        stream.beginText()
                        stream.setFont(PDType1Font.HELVETICA, size)
                        stream.newLineAtOffset(entry.x, h - entry.top - entry.height + 1)
                        // Standard PDF font supports Latin-1 only; unsupported glyphs need embedded Unicode font.
                        stream.showText(replacement.replace(Regex("[^\\u0020-\\u007E\\u00A0-\\u00FF]"), "?").replace("\n", " "))
                        stream.endText()
                    }
                    showPage(pageIndex)
                } catch (e: Exception) {
                    Toast.makeText(this, "Úprava selhala: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    private fun render() {
        val pdf = document ?: return
        val tmp = File(cacheDir, "preview.pdf")
        pdf.save(tmp)
        ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                renderer.openPage(pageIndex).use { page ->
                    val width = (page.width * 2).coerceAtMost(2000)
                    val height = (page.height * width.toFloat() / page.width).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    preview.setImageBitmap(bitmap)
                }
            }
        }
    }

    override fun onDestroy() {
        document?.close()
        super.onDestroy()
    }
}
