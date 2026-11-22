package dev.inter129.mochikeys.layout

object Layouts {
    private fun chars(s: String, variants: Map<Char, String> = emptyMap()) =
        s.map { c -> Key(c.toString(), KeyAction.Text(c.toString()), variants = variants[c]?.map { it.toString() } ?: emptyList()) }

    private val latinVariants = mapOf(
        'e' to "èéêëēė", 'a' to "àáâäæãåā", 'o' to "ôöòóœøōõ", 'u' to "ûüùúū",
        'i' to "îïíīįì", 'n' to "ñń", 'c' to "çćč", 's' to "ßśš", 'y' to "ÿ", 'z' to "žźż", 'l' to "ł",
    )

    private fun bottomRow(page: String, pageLabel: String, ret: String = "return") = Row(
        listOf(
            Key(pageLabel, KeyAction.SwitchPage(page), 1.25f, KeyStyle.FUNCTION),
            Key("☺", KeyAction.SwitchPage("emoji"), 1.25f, KeyStyle.FUNCTION),
            Key("", KeyAction.Space, 4.98f, KeyStyle.SPACE),
            Key(ret, KeyAction.Return, 2.52f, KeyStyle.RETURN),
        )
    )

    val qwerty = KeyboardLayout(
        "en", listOf(
            Row(chars("qwertyuiop", latinVariants)),
            Row(chars("asdfghjkl", latinVariants), inset = 0.5f),
            Row(listOf(Key("⇧", KeyAction.Shift, 1.5f, KeyStyle.FUNCTION, visW = 1.29f)) + chars("zxcvbnm", latinVariants) +
                Key("⌫", KeyAction.Backspace, 1.5f, KeyStyle.FUNCTION, visW = 1.29f)),
            bottomRow("num", "123"),
        )
    )

    val numbers = KeyboardLayout(
        "num", listOf(
            Row(chars("1234567890")),
            Row(chars("-/:;()$&@\"")),
            Row(listOf(Key("#+=", KeyAction.SwitchPage("sym"), 1.5f, KeyStyle.FUNCTION, visW = 1.29f)) +
                chars(".,?!'").map { it.copy(width = 1.4f) } + Key("⌫", KeyAction.Backspace, 1.5f, KeyStyle.FUNCTION, visW = 1.29f)),
            bottomRow("abc", "ABC"),
        )
    )

    val symbols = KeyboardLayout(
        "sym", listOf(
            Row(chars("[]{}#%^*+=")),
            Row(chars("_\\|~<>€£¥•")),
            Row(listOf(Key("123", KeyAction.SwitchPage("num"), 1.5f, KeyStyle.FUNCTION, visW = 1.29f)) +
                chars(".,?!'").map { it.copy(width = 1.4f) } + Key("⌫", KeyAction.Backspace, 1.5f, KeyStyle.FUNCTION, visW = 1.29f)),
            bottomRow("abc", "ABC"),
        )
    )

    private val koVariants = mapOf('ㅂ' to "ㅃ", 'ㅈ' to "ㅉ", 'ㄷ' to "ㄸ", 'ㄱ' to "ㄲ", 'ㅅ' to "ㅆ", 'ㅐ' to "ㅒ", 'ㅔ' to "ㅖ")

    val dubeolsik = KeyboardLayout(
        "ko", listOf(
            Row(chars("ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔ", koVariants)),
            Row(chars("ㅁㄴㅇㄹㅎㅗㅓㅏㅣ"), inset = 0.5f),
            Row(listOf(Key("⇧", KeyAction.Shift, 1.5f, KeyStyle.FUNCTION, visW = 1.29f)) + chars("ㅋㅌㅊㅍㅠㅜㅡ") +
                Key("⌫", KeyAction.Backspace, 1.5f, KeyStyle.FUNCTION, visW = 1.29f)),
            bottomRow("num", "123"),
        )
    )

    private fun kana(label: String, codes: String, labels: String) =
        Key(label, KeyAction.Kana(codes, labels.split(" ").map { if (it == "_") "" else it }), 1f, KeyStyle.FLICK)
    private fun side(label: String, a: KeyAction, style: KeyStyle = KeyStyle.FUNCTION, span: Int = 1) = Key(label, a, 1f, style, rowSpan = span)

    /** 일본어 かな 12키. 키 코드는 Mozc toggle_flick-hiragana 테이블 규약. */
    val kana12 = KeyboardLayout(
        "ja", listOf(
            Row(listOf(side("→", KeyAction.NextChar), kana("あ", "1_;:@", "あ い う え お"), kana("か", "2abc|", "か き く け こ"), kana("さ", "3def~", "さ し す せ そ"), side("⌫", KeyAction.Backspace))),
            Row(listOf(side("↺", KeyAction.Rewind), kana("た", "4ghi$", "た ち つ て と"), kana("な", "5jkl%", "な に ぬ ね の"), kana("は", "6mno&", "は ひ ふ へ ほ"), side("空白", KeyAction.Space, KeyStyle.SPACE))),
            Row(listOf(side("ABC", KeyAction.SwitchPage("latin")), kana("ま", "7pqrs", "ま み む め も"), kana("や", "8tuv^", "や （ ゆ ） よ"), kana("ら", "9wxyz", "ら り る れ ろ"), side("改行", KeyAction.Return, KeyStyle.RETURN, span = 2))),
            Row(listOf(side("☺", KeyAction.SwitchPage("emoji")), kana("゛゜小", "*[`]", "_ ゛ 小 ゜"), kana("わ", "0+/-<", "わ を ん ー 〜"), kana("、。?!", "#,?!>", "、 。 ？ ！ …"), side("", KeyAction.None))),
        )
    )

    /** Shift 상태에서 키에 보이고 입력되는 글자. */
    fun shifted(label: String): String = if (label.isNotEmpty() && label[0].code > 0x2000 && label[0] !in 'ㄱ'..'ㅣ') label else dev.inter129.mochikeys.engine.HangulAutomaton.SHIFT[label] ?: label.uppercase()

    /** 이모지 판은 키 격자가 아니라 app의 EmojiPanel이 그린다. */
    val emoji = KeyboardLayout("emoji", emptyList())

    /** 한국어용 기호판: 아이폰처럼 숫자판의 $ 자리에 ₩, 기호판의 € 자리에 $. */
    private fun swap(kb: KeyboardLayout, from: String, to: String) = kb.copy(rows = kb.rows.map { r ->
        r.copy(keys = r.keys.map { k -> if (k.label == from) k.copy(label = to, action = KeyAction.Text(to)) else k })
    })
    val numbersKo = swap(numbers, "$", "₩")
    val symbolsKo = swap(symbols, "€", "$")

    fun byPage(page: String, lang: String = "en") = when (page) { "num" -> if (lang == "ko") numbersKo else numbers; "sym" -> if (lang == "ko") symbolsKo else symbols; "emoji" -> emoji; "latin" -> qwerty; else -> when (lang) { "ko" -> dubeolsik; "ja" -> kana12; else -> qwerty } }
}
