package dev.inter129.mochikeys.mozc

import android.content.Context
import com.google.android.apps.inputmethod.libs.mozc.session.MozcJni
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidateWindow.CandidateList
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.*
import org.mozc.android.inputmethod.japanese.protobuf.ProtoConfig.Config
import java.io.File
import java.util.concurrent.Executors

/** Mozc 세션 한 개. evalCommand에 락이 없으므로 모든 호출을 전용 스레드 하나에서 직렬로 실행한다. */
class MozcEngine private constructor(private val sessionId: Long) {

    data class State(
        val committed: String,
        val preedit: String,
        val candidates: List<Pair<Int, String>>,
    )

    fun sendKeyCode(code: Int) = eval(input(Input.CommandType.SEND_KEY).setKey(KeyEvent.newBuilder().setKeyCode(code)))
    fun sendSpecial(k: KeyEvent.SpecialKey) = eval(input(Input.CommandType.SEND_KEY).setKey(KeyEvent.newBuilder().setSpecialKey(k)))
    fun command(t: SessionCommand.CommandType, id: Int? = null) = eval(
        input(Input.CommandType.SEND_COMMAND).setCommand(SessionCommand.newBuilder().setType(t).apply { id?.let { setId(it) } })
    )
    /** 후보를 골라 그 부분만 확정(나머지는 조합 유지). */
    fun submitCandidate(id: Int) = command(SessionCommand.CommandType.SUBMIT_CANDIDATE, id)
    fun reset() = command(SessionCommand.CommandType.RESET_CONTEXT)

    private fun input(t: Input.CommandType) = Input.newBuilder().setType(t).setId(sessionId)

    private fun eval(b: Input.Builder): State {
        val out = Command.parseFrom(run { MozcJni.evalCommand(Command.newBuilder().setInput(b).build().toByteArray()) }).output
        val list: CandidateList? = if (out.hasAllCandidateWords()) out.allCandidateWords else null
        return State(
            committed = if (out.hasResult()) out.result.value else "",
            preedit = out.preedit.segmentList.joinToString("") { it.value },
            candidates = list?.candidatesList?.map { it.id to it.value } ?: emptyList(),
        )
    }

    companion object {
        private val thread = Executors.newSingleThreadExecutor { Thread(it, "mozc") }
        @Volatile private var loaded = false
        var dataVersion: String = ""; private set

        fun <T> run(block: () -> T): T = if (Thread.currentThread().name == "mozc") block() else thread.submit(block).get()

        /** assets/mozc.data를 내부 저장소로 복사하고 엔진을 연다. 데이터 로드 실패면 예외(조용한 폴백 방지). */
        fun open(ctx: Context): MozcEngine = run {
            if (!loaded) {
                val data = File(ctx.filesDir, "mozc.data")
                if (!data.exists() || data.length() == 0L) ctx.assets.open("mozc.data").use { i -> data.outputStream().use { i.copyTo(it) } }
                val profile = File(ctx.filesDir, "mozc_profile").apply { mkdirs() }
                check(MozcJni.initialize()) { "MozcJni.initialize failed" }
                check(MozcJni.onPostLoad(profile.absolutePath, data.absolutePath)) { "Mozc onPostLoad failed" }
                dataVersion = MozcJni.getDataVersion() ?: ""
                check(dataVersion.isNotEmpty()) { "mozc.data not loaded" }
                loaded = true
            }
            val created = evalRaw(Input.newBuilder().setType(Input.CommandType.CREATE_SESSION))
            val id = created.output.id
            evalRaw(Input.newBuilder().setType(Input.CommandType.SET_CONFIG).setConfig(
                Config.newBuilder().setSessionKeymap(Config.SessionKeymap.MOBILE).setUseKanaModifierInsensitiveConversion(true)))
            evalRaw(Input.newBuilder().setType(Input.CommandType.SET_REQUEST).setId(id).setRequest(
                Request.newBuilder()
                    .setZeroQuerySuggestion(true).setMixedConversion(true).setAutoPartialSuggestion(true)
                    .setKanaModifierInsensitiveConversion(true).setUpdateInputModeFromSurroundingText(false)
                    .setSpecialRomanjiTable(Request.SpecialRomanjiTable.TOGGLE_FLICK_TO_HIRAGANA)))
            MozcEngine(id)
        }

        private fun evalRaw(b: Input.Builder): Command =
            Command.parseFrom(MozcJni.evalCommand(Command.newBuilder().setInput(b).build().toByteArray()))
    }
}
