package id.tukangkilat.shared

/** Payment providers available in the app. Codes are stable and safe to persist. */
object PaymentProviders {
    data class Provider(val code: String, val name: String)

    val banks = listOf(
        Provider("BCA", "BCA"),
        Provider("MANDIRI", "Mandiri"),
        Provider("BNI", "BNI"),
        Provider("BRI", "BRI"),
        Provider("CIMB", "CIMB Niaga"),
        Provider("PERMATA", "Permata"),
        Provider("DANAMON", "Danamon"),
        Provider("OCBC", "OCBC"),
        Provider("PANIN", "Panin"),
        Provider("BTN", "BTN")
    )

    val wallets = listOf(
        Provider("GOPAY", "GoPay"),
        Provider("OVO", "OVO"),
        Provider("DANA", "DANA"),
        Provider("SHOPEEPAY", "ShopeePay"),
        Provider("LINKAJA", "LinkAja")
    )

    fun nameOf(code: String?): String =
        (banks + wallets).firstOrNull { it.code == code }?.name ?: code ?: "-"
}
