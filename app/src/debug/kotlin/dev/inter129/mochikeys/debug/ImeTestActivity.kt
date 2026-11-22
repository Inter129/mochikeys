package dev.inter129.mochikeys.debug

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

/** 입력 필드 종류별 테스트 화면. 각 필드의 내용이 바뀔 때마다 logcat(IMETEST)으로 출력한다. */
class ImeTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 96, 32, 32) }
        val fields = listOf(
            "text" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) to EditorInfo.IME_ACTION_NONE,
            "search" to InputType.TYPE_CLASS_TEXT to EditorInfo.IME_ACTION_SEARCH,
            "url" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI) to EditorInfo.IME_ACTION_GO,
            "email" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) to EditorInfo.IME_ACTION_NEXT,
            "number" to InputType.TYPE_CLASS_NUMBER to EditorInfo.IME_ACTION_DONE,
            "password" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD) to EditorInfo.IME_ACTION_DONE,
            "multiline" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) to EditorInfo.IME_ACTION_NONE,
        )
        for ((pair, action) in fields) {
            val (name, type) = pair
            col.addView(EditText(this).apply {
                hint = name; inputType = type; imeOptions = action; tag = name
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: Editable?) { Log.i("IMETEST", "$name=[$s]") }
                })
            })
        }
        setContentView(ScrollView(this).apply { addView(col) })
        col.getChildAt(0).requestFocus()
    }
}
