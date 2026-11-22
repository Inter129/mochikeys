package dev.inter129.mochikeys.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import com.google.android.material.R as M
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDivider
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** 설정 화면(Material 3). 구획마다 카드 하나, 항목은 제목·설명 + 스위치/슬라이더. */
class SettingsActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var statusTitle: TextView
    private lateinit var statusBody: TextView
    private lateinit var statusCard: MaterialCardView
    private val dp get() = resources.displayMetrics.density
    private fun px(v: Int) = (v * dp).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(16), px(4), px(16), px(32)) }

        // ── 키보드 켜기 ──
        statusCard = card(col, filled = true)
        val sc = cardColumn(statusCard, pad = 20)
        statusTitle = text(sc, "", M.style.TextAppearance_Material3_TitleMedium)
        statusBody = text(sc, "", M.style.TextAppearance_Material3_BodyMedium).apply { setPadding(0, px(4), 0, px(12)) }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(MaterialButton(this, null, M.attr.materialButtonOutlinedStyle).apply {
            text = "키보드 켜기"; setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = px(8) })
        row.addView(MaterialButton(this).apply {
            text = "기본으로 선택"; setOnClickListener { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        sc.addView(row)

        col.addView(TextInputLayout(this, null, M.attr.textInputOutlinedStyle).apply {
            hint = "여기에 입력해 보세요"
            addView(TextInputEditText(context).apply { minLines = 2; gravity = Gravity.TOP })
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = px(16) })

        // ── 언어 ──
        header(col, "언어")
        val lc = cardColumn(card(col))
        val langSwitches = LinkedHashMap<String, MaterialSwitch>()
        val langs = listOf(Triple("en", "English", "QWERTY"), Triple("ko", "한국어", "두벌식"), Triple("ja", "日本語", "かな 플릭"))
        langs.forEachIndexed { i, (code, name, sub) ->
            langSwitches[code] = switchRow(lc, name, sub, code in prefs.languages) { on ->
                val enabled = langSwitches.filter { it.value.isChecked }.keys.toList()
                if (enabled.isEmpty()) langSwitches[code]!!.isChecked = true   // 마지막 하나는 끌 수 없다
                else prefs.languages = enabled
            }
            if (i < langs.lastIndex) divider(lc)
        }
        note(col, "지구본 키로 전환할 언어입니다.")

        // ── 소리와 진동 ──
        header(col, "소리와 진동")
        val fc = cardColumn(card(col))
        val volume = sliderRow("음량", 10, 100, 5, prefs.soundVolume, "%") { prefs.soundVolume = it }
        switchRow(fc, "키 누르는 소리", "무음·진동 모드에서는 나지 않습니다", prefs.sound) { prefs.sound = it; volume.visibility = if (it) View.VISIBLE else View.GONE }
        fc.addView(volume); volume.visibility = if (prefs.sound) View.VISIBLE else View.GONE
        divider(fc)
        switchRow(fc, "키 누르는 진동", null, prefs.haptic) { prefs.haptic = it }

        // ── 입력 ──
        header(col, "입력")
        val ic = cardColumn(card(col))
        switchRow(ic, "자동 대문자", "영어 문장의 첫 글자를 대문자로", prefs.autoCap) { prefs.autoCap = it }
        divider(ic)
        switchRow(ic, "스페이스 두 번으로 마침표", null, prefs.doubleSpace) { prefs.doubleSpace = it }

        // ── 모양 ──
        header(col, "모양")
        val mc = cardColumn(card(col))
        mc.addView(sliderRow("키 높이", 50, 64, 1, prefs.rowHeight, "") { prefs.rowHeight = it })
        note(col, "바꾼 설정은 키보드를 다시 열면 적용됩니다.")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MaterialColors.getColor(this, M.attr.colorSurface))
            addView(MaterialToolbar(context).apply { title = "Mochi Keys"; subtitle = "키보드 설정" })
            addView(NestedScrollView(context).apply { addView(col); clipToPadding = false }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        // target 35+는 화면이 시스템 바 뒤까지 그려지므로 그만큼 안쪽으로 민다
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.updatePadding(top = b.top, bottom = b.bottom); insets
        }
    }

    override fun onResume() { super.onResume(); refreshStatus() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) refreshStatus() }

    private fun refreshStatus() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val selected = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith("$packageName/") == true
        val (title, body, container) = when {
            selected -> Triple("사용 중", "Mochi Keys가 기본 키보드입니다.", M.attr.colorPrimaryContainer)
            enabled -> Triple("거의 다 됐습니다", "켜져 있지만 기본 키보드는 아닙니다. \"기본으로 선택\"을 눌러 주세요.", M.attr.colorTertiaryContainer)
            else -> Triple("아직 꺼져 있습니다", "\"키보드 켜기\"에서 Mochi Keys를 켠 뒤 \"기본으로 선택\"을 눌러 주세요.", M.attr.colorErrorContainer)
        }
        statusTitle.text = title; statusBody.text = body
        statusCard.setCardBackgroundColor(MaterialColors.getColor(statusCard, container))
    }

    // ── 구성 요소 ──
    private fun card(col: LinearLayout, filled: Boolean = false): MaterialCardView =
        MaterialCardView(this, null, M.attr.materialCardViewFilledStyle).apply {
            radius = 24 * dp
            if (!filled) setCardBackgroundColor(MaterialColors.getColor(this, M.attr.colorSurfaceContainerHigh))
            col.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
    private fun cardColumn(card: MaterialCardView, pad: Int = 0) =
        LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(pad), px(pad.coerceAtLeast(4)), px(pad), px(pad.coerceAtLeast(4))); card.addView(this) }
    private fun text(parent: LinearLayout, s: String, style: Int) = TextView(this).apply { text = s; setTextAppearance(style); parent.addView(this) }
    private fun header(col: LinearLayout, s: String) = TextView(this).apply {
        text = s; setTextAppearance(M.style.TextAppearance_Material3_TitleSmall)
        setTextColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)); setPadding(px(8), px(28), 0, px(10)); col.addView(this)
    }
    private fun note(col: LinearLayout, s: String) = TextView(this).apply {
        text = s; setTextAppearance(M.style.TextAppearance_Material3_BodySmall)
        setTextColor(MaterialColors.getColor(this, M.attr.colorOnSurfaceVariant)); setPadding(px(8), px(8), px(8), 0); col.addView(this)
    }
    private fun divider(parent: LinearLayout) = parent.addView(MaterialDivider(this).apply { dividerInsetStart = px(20); dividerInsetEnd = px(20) })
    private fun switchRow(parent: LinearLayout, title: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit): MaterialSwitch {
        val sw = MaterialSwitch(this).apply { isChecked = value; setOnCheckedChangeListener { _, b -> onChange(b) } }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        text(texts, title, M.style.TextAppearance_Material3_BodyLarge)
        if (sub != null) text(texts, sub, M.style.TextAppearance_Material3_BodyMedium).setTextColor(MaterialColors.getColor(texts, M.attr.colorOnSurfaceVariant))
        parent.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(px(20), px(14), px(16), px(14)); minimumHeight = px(64)
            addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)); addView(sw)
            setOnClickListener { sw.toggle() }
        })
        return sw
    }
    private fun sliderRow(title: String, min: Int, max: Int, step: Int, value: Int, unit: String, onChange: (Int) -> Unit): LinearLayout {
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val label = TextView(this).apply { text = title; setTextAppearance(M.style.TextAppearance_Material3_BodyLarge) }
        val num = TextView(this).apply { text = "$value$unit"; setTextAppearance(M.style.TextAppearance_Material3_LabelLarge); setTextColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)) }
        head.addView(label, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)); head.addView(num)
        val slider = Slider(this).apply {
            valueFrom = min.toFloat(); valueTo = max.toFloat(); stepSize = step.toFloat()
            this.value = (((value - min) / step) * step + min).coerceIn(min, max).toFloat()
            addOnChangeListener { _, v, _ -> num.text = "${v.toInt()}$unit"; onChange(v.toInt()) }
        }
        return LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(20), px(14), px(12), px(6)); addView(head); addView(slider) }
    }
}
