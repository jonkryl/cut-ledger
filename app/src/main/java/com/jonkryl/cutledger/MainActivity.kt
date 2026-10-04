package com.jonkryl.cutledger

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.content.FileProvider
import com.jonkryl.cutledger.ads.BannerController
import com.jonkryl.cutledger.core.*
import org.json.JSONArray
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("cut_ledger", MODE_PRIVATE) }
    private lateinit var ad: BannerController
    private lateinit var page: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var name: EditText
    private lateinit var stock: EditText
    private lateinit var parts: EditText
    private lateinit var kerf: EditText
    private lateinit var trim: EditText
    private lateinit var error: TextView
    private var draft = Draft()
    private var busy = false
    private var editor = true
    private var building = false
    private var generation = 0
    private val persist = Runnable { prefs.edit().putString("draft", DraftCodec.encode(draft)).apply() }
    private val blue = Color.rgb(25, 60, 112)
    private val ink = Color.rgb(30, 43, 62)
    private val paperColor = Color.rgb(243, 245, 250)
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = paperColor
        window.navigationBarColor = paperColor
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        draft = DraftCodec.decode(prefs.getString("draft", null)) ?: Draft()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(paperColor) }
        root.setOnApplyWindowInsetsListener { v, i ->
            v.setPadding(i.systemWindowInsetLeft, i.systemWindowInsetTop, i.systemWindowInsetRight, i.systemWindowInsetBottom); i
        }
        scroll = ScrollView(this).apply { isFillViewport = true }
        page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(20)) }
        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val host = FrameLayout(this).apply { minimumHeight = dp(58); setPadding(dp(8), dp(8), dp(8), 0) }
        root.addView(host, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        ad = BannerController(this)
        renderEditor()
        ad.attach(host)
    }

    private fun text(value: String, size: Float = 16f, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(ink); setPadding(0, dp(5), 0, dp(5))
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private fun label(resource: Int, size: Float = 16f, bold: Boolean = false) = text(getString(resource), size, bold)
    private fun button(resource: Int, identifier: Int, action: () -> Unit) = Button(this).apply {
        id = identifier; setText(resource); isAllCaps = false; textSize = 16f
        minimumHeight = dp(48); minHeight = dp(48); setTextColor(blue)
        setPadding(dp(10), dp(8), dp(10), dp(8)); setOnClickListener { if (!busy) action() }
    }
    private fun header() {
        page.removeAllViews()
        page.addView(label(R.string.app_name, 27f, true).apply { id = R.id.title; setTextColor(blue) })
        page.addView(label(R.string.subtitle, 14f))
        page.addView(button(R.string.menu, R.id.menu) { menu() })
    }
    private fun input(title: Int, identifier: Int, value: String, hint: Int? = null, multi: Boolean = false, max: Int = 20_000): EditText {
        val caption = label(title, 16f, true).apply { labelFor = identifier }
        page.addView(caption)
        val input = EditText(this).apply {
            id = identifier; setText(value); hint?.let { setHint(it) }; textSize = 17f; setTextColor(ink)
            filters = arrayOf(InputFilter.LengthFilter(max))
            inputType = InputType.TYPE_CLASS_TEXT or if (multi) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0
            minLines = if (multi) 2 else 1
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(9).toFloat(); setStroke(dp(1), Color.rgb(205, 214, 229)) }
        }
        page.addView(input, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { if (!building && editor) { draft = readDraft(); handler.removeCallbacks(persist); handler.postDelayed(persist, 400) } }
        })
        return input
    }
    private fun readDraft() = Draft(name.text.toString(), stock.text.toString(), parts.text.toString(), kerf.text.toString(), trim.text.toString())
    private fun persistNow() {
        handler.removeCallbacks(persist)
        if (!building && editor && ::trim.isInitialized) draft = readDraft()
        prefs.edit().putString("draft", DraftCodec.encode(draft)).commit()
    }
    private fun renderEditor() {
        building = true; editor = true; busy = false
        header()
        page.addView(label(R.string.editor_title, 21f, true))
        page.addView(button(R.string.example, R.id.example) { replaceDraft(true) })
        name = input(R.string.name, R.id.project_name, draft.name, R.string.name_hint, max = 60)
        stock = input(R.string.stock, R.id.stock_input, draft.stock, R.string.stock_hint, true)
        page.addView(label(R.string.stock_format, 13f))
        parts = input(R.string.parts, R.id.parts_input, draft.parts, R.string.parts_hint, true)
        page.addView(label(R.string.parts_format, 13f))
        kerf = input(R.string.kerf, R.id.kerf_input, draft.kerf, max = 20)
        trim = input(R.string.trim, R.id.trim_input, draft.trim, max = 20)
        page.addView(label(R.string.trim_detail, 13f))
        error = text("").apply { id = R.id.error; setTextColor(Color.rgb(152, 35, 35)); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        page.addView(error)
        page.addView(button(R.string.calculate, R.id.calculate) { calculate() })
        page.addView(button(R.string.save, R.id.save) { saveProject() })
        page.addView(button(R.string.open, R.id.open) { projects() })
        page.addView(button(R.string.help, R.id.help) { help() })
        page.addView(label(R.string.support, 12f))
        building = false
        scroll.post { scroll.scrollTo(0, 0) }
    }
    private fun calculate() {
        persistNow()
        val job = try { JobParser.parse(draft) } catch (e: InputIssue) {
            val input = when (e.field) { Field.STOCK -> stock; Field.PARTS -> parts; Field.KERF -> kerf; Field.TRIM -> trim }
            val message = when (e.field) {
                Field.STOCK -> getString(R.string.invalid_stock, e.line)
                Field.PARTS -> getString(R.string.invalid_parts, e.line)
                else -> getString(R.string.invalid_setting)
            }
            input.error = message; error.text = message; input.requestFocus()
            scroll.post { scroll.smoothScrollTo(0, input.top) }
            return
        }
        currentFocus?.let { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken, 0) }
        busy = true
        val token = ++generation
        error.setText(R.string.calculating)
        worker.execute {
            val result = runCatching { CuttingPlanner.solve(job) }
            runOnUiThread {
                if (isDestroyed || isFinishing || token != generation) return@runOnUiThread
                busy = false
                result.fold({ renderPlan(it) }, { error.setText(R.string.unexpected) })
            }
        }
    }
    private fun renderPlan(plan: Plan) {
        editor = false
        header()
        page.addView(label(R.string.plan_title, 21f, true))
        page.addView(text(plan.job.name.ifBlank { getString(R.string.untitled) }, 18f, true))
        page.addView(text(getString(R.string.result_summary, plan.placedCount, plan.job.pieces.size, plan.bars.size), 19f, true).apply { id = R.id.summary })
        page.addView(text(getString(R.string.result_lengths, Lengths.format(plan.placedLength), Lengths.format(plan.sawLoss), Lengths.format(plan.remainder))).apply { id = R.id.result })
        page.addView(label(if (plan.exhaustive) R.string.exact else R.string.heuristic, 14f, true).apply { id = R.id.method })
        page.addView(button(R.string.back, R.id.back) { renderEditor() })
        page.addView(button(R.string.share, R.id.share) { share(plan) })
        page.addView(button(R.string.save, R.id.save) { saveProject() })
        page.addView(label(R.string.legend, 13f))
        for ((i, bar) in plan.bars.withIndex()) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(12).toFloat() }
            }
            card.addView(text(getString(R.string.bar_title, i + 1, Lengths.format(bar.stock.length)), 18f, true))
            card.addView(BarDiagram(bar).apply { contentDescription = getString(R.string.bar_detail, bar.pieces.size, Lengths.format(bar.sawLoss), Lengths.format(bar.remainder)) }, LinearLayout.LayoutParams(-1, dp(42)))
            card.addView(text(getString(R.string.bar_detail, bar.pieces.size, Lengths.format(bar.sawLoss), Lengths.format(bar.remainder)), 14f))
            var position = bar.trim
            for (p in bar.pieces) {
                card.addView(text(getString(R.string.part_line, p.label, Lengths.format(p.length), Lengths.format(position), Lengths.format(position + p.length)), 15f))
                position += p.length + bar.kerf
            }
            page.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        }
        if (plan.unplaced.isNotEmpty()) {
            page.addView(text(getString(R.string.unplaced_title, plan.unplaced.size), 19f, true))
            page.addView(label(R.string.unplaced_detail, 14f))
            plan.unplaced.forEach { page.addView(text(getString(R.string.unplaced_part, it.label, Lengths.format(it.length)))) }
        }
        page.addView(text(getString(R.string.unused_title, plan.job.stocks.size - plan.bars.size), 15f))
        page.addView(label(R.string.method_details, 13f))
        page.addView(button(R.string.help, R.id.help) { help() })
        scroll.post { scroll.scrollTo(0, 0) }
    }
    private fun menu() {
        val items = intArrayOf(R.string.new_project, R.string.example, R.string.open, R.string.help, R.string.privacy)
        AlertDialog.Builder(this).setTitle(R.string.menu).setItems(items.map { getString(it) }.toTypedArray()) { dialog, i ->
            dialog.dismiss()
            page.post { when (i) { 0 -> replaceDraft(false); 1 -> replaceDraft(true); 2 -> projects(); 3 -> help(); 4 -> ad.showPrivacyChoice() } }
        }.show()
    }
    private fun replaceDraft(example: Boolean) {
        persistNow()
        fun replace() {
            draft = if (example) Draft(getString(R.string.example_name), getString(R.string.example_stock), getString(R.string.example_parts), "3", "0") else Draft()
            prefs.edit().putString("draft", DraftCodec.encode(draft)).commit()
            renderEditor()
        }
        if (draft.stock.isBlank() && draft.parts.isBlank() && draft.name.isBlank()) replace()
        else AlertDialog.Builder(this).setMessage(if (example) R.string.example_confirm else R.string.new_confirm)
            .setPositiveButton(R.string.replace) { _, _ -> replace() }.setNegativeButton(R.string.cancel, null).show()
    }
    private fun savedProjects(): MutableList<Draft> = try {
        val array = JSONArray(prefs.getString("projects", "[]"))
        (0 until minOf(array.length(), 20)).mapNotNull { DraftCodec.decode(array.getString(it)) }.toMutableList()
    } catch (_: Exception) { mutableListOf() }
    private fun writeProjects(projects: List<Draft>) = prefs.edit().putString("projects", JSONArray(projects.map { DraftCodec.encode(it) }).toString()).commit()
    private fun saveProject() {
        persistNow()
        val all = savedProjects().filter { it.name != draft.name }.toMutableList()
        all.add(0, draft)
        if (writeProjects(all.take(20))) Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
    }
    private fun projects() {
        val saved = savedProjects()
        if (saved.isEmpty()) { AlertDialog.Builder(this).setMessage(R.string.empty_saved).setPositiveButton(R.string.close, null).show(); return }
        AlertDialog.Builder(this).setTitle(R.string.open).setItems(saved.map { it.name.ifBlank { getString(R.string.untitled) } }.toTypedArray()) { dialog, index ->
            dialog.dismiss()
            page.post {
                val selected = saved[index]
                val title = selected.name.ifBlank { getString(R.string.untitled) }
                AlertDialog.Builder(this).setTitle(getString(R.string.saved_actions, title)).setPositiveButton(R.string.load) { _, _ ->
                    draft = selected; prefs.edit().putString("draft", DraftCodec.encode(draft)).commit(); renderEditor()
                }.setNeutralButton(R.string.delete) { _, _ ->
                    page.post { AlertDialog.Builder(this).setMessage(getString(R.string.delete_confirm, title)).setPositiveButton(R.string.delete) { _, _ ->
                        writeProjects(savedProjects().filter { it != selected })
                    }.setNegativeButton(R.string.cancel, null).show() }
                }.setNegativeButton(R.string.cancel, null).show()
            }
        }.show()
    }
    private fun help() {
        val content = ScrollView(this).apply { addView(label(R.string.help_text, 16f).apply { setPadding(dp(20), dp(10), dp(20), dp(10)) }) }
        AlertDialog.Builder(this).setTitle(R.string.help).setView(content).setPositiveButton(R.string.close, null).show()
    }
    private fun share(plan: Plan) {
        val folder = File(cacheDir, "exports").apply { mkdirs() }
        folder.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
        val file = File(folder, "cut-plan-${UUID.randomUUID()}.csv")
        try {
            file.writeText("\uFEFF" + PlanCsv.export(plan), Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(this, "$packageName.exports", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"; putExtra(Intent.EXTRA_SUBJECT, plan.job.name); putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(contentResolver, "Cut Ledger CSV", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_title)))
        }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, R.string.share_unavailable, Toast.LENGTH_LONG).show() }
        catch (_: java.io.IOException) { Toast.makeText(this, R.string.unexpected, Toast.LENGTH_LONG).show() }
    }
    private inner class BarDiagram(private val bar: Bar) : View(this@MainActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val top = dp(8).toFloat(); val bottom = height - dp(8).toFloat()
            fun rectangle(start: Long, length: Long, color: Int) {
                if (length <= 0) return
                paint.color = color
                val x1 = width * (start.toDouble() / bar.stock.length).toFloat()
                val x2 = width * ((start + length).toDouble() / bar.stock.length).toFloat()
                canvas.drawRect(x1, top, x2, bottom, paint)
            }
            rectangle(0, bar.stock.length, Color.rgb(216, 224, 236))
            var position = bar.trim
            for ((i, p) in bar.pieces.withIndex()) {
                rectangle(position, p.length, if (i % 2 == 0) Color.rgb(36, 86, 166) else Color.rgb(87, 128, 194))
                position += p.length
                if (i < bar.pieces.lastIndex || bar.finalCut) { rectangle(position, bar.kerf, Color.rgb(235, 158, 38)); position += bar.kerf }
            }
        }
    }
    override fun onStart() { super.onStart(); if (::ad.isInitialized) ad.onStart() }
    override fun onStop() { if (::ad.isInitialized) ad.onStop(); super.onStop() }
    override fun onPause() { persistNow(); super.onPause() }
    override fun onDestroy() { generation++; handler.removeCallbacks(persist); worker.shutdownNow(); if (::ad.isInitialized) ad.destroy(); super.onDestroy() }
}
