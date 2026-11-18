package id.tukangkilat.shared

/**
 * Sole authority for order status transitions, per PRD section 4.
 * Any change to this table must keep backend, app and docs in sync.
 */
object OrderStateMachine {

    private val transitions: Map<OrderStatus, Map<OrderStatus, Set<Actor>>> = mapOf(
        OrderStatus.MENUNGGU_PEMBAYARAN to mapOf(
            OrderStatus.MENCARI_TEKNISI to setOf(Actor.KONSUMEN, Actor.SYSTEM, Actor.ADMIN),
            OrderStatus.PEMBAYARAN_GAGAL to setOf(Actor.SYSTEM, Actor.ADMIN),
            OrderStatus.DIBATALKAN to setOf(Actor.KONSUMEN, Actor.ADMIN)
        ),
        OrderStatus.PEMBAYARAN_GAGAL to mapOf(
            OrderStatus.MENUNGGU_PEMBAYARAN to setOf(Actor.KONSUMEN, Actor.ADMIN),
            OrderStatus.DIBATALKAN to setOf(Actor.KONSUMEN, Actor.ADMIN)
        ),
        OrderStatus.MENCARI_TEKNISI to mapOf(
            OrderStatus.TEKNISI_DITEMUKAN to setOf(Actor.TUKANG, Actor.SYSTEM, Actor.ADMIN),
            OrderStatus.DIBATALKAN to setOf(Actor.KONSUMEN, Actor.ADMIN, Actor.SYSTEM)
        ),
        OrderStatus.TEKNISI_DITEMUKAN to mapOf(
            OrderStatus.DALAM_PERJALANAN to setOf(Actor.TUKANG, Actor.ADMIN),
            OrderStatus.MENCARI_TEKNISI to setOf(Actor.TUKANG, Actor.SYSTEM, Actor.ADMIN)
        ),
        OrderStatus.DALAM_PERJALANAN to mapOf(
            OrderStatus.TIBA to setOf(Actor.TUKANG, Actor.ADMIN)
        ),
        OrderStatus.TIBA to mapOf(
            OrderStatus.SEDANG_BEKERJA to setOf(Actor.TUKANG, Actor.ADMIN)
        ),
        OrderStatus.SEDANG_BEKERJA to mapOf(
            OrderStatus.MENUNGGU_KONFIRMASI to setOf(Actor.TUKANG, Actor.ADMIN)
        ),
        OrderStatus.MENUNGGU_KONFIRMASI to mapOf(
            OrderStatus.SELESAI to setOf(Actor.KONSUMEN, Actor.SYSTEM, Actor.ADMIN),
            OrderStatus.DILAPORKAN to setOf(Actor.KONSUMEN, Actor.ADMIN)
        ),
        OrderStatus.DILAPORKAN to mapOf(
            OrderStatus.KUNJUNGAN_ULANG to setOf(Actor.ADMIN, Actor.SYSTEM)
        ),
        OrderStatus.KUNJUNGAN_ULANG to mapOf(
            OrderStatus.TEKNISI_DITEMUKAN to setOf(Actor.TUKANG, Actor.SYSTEM, Actor.ADMIN),
            OrderStatus.MENCARI_TEKNISI to setOf(Actor.SYSTEM, Actor.ADMIN),
            OrderStatus.DIBATALKAN to setOf(Actor.ADMIN)
        ),
        OrderStatus.SELESAI to emptyMap(),
        OrderStatus.DIBATALKAN to emptyMap()
    )

    enum class Actor {
        KONSUMEN,
        TUKANG,
        ADMIN,
        SYSTEM
    }

    fun canTransition(from: OrderStatus, to: OrderStatus, actor: Actor): Boolean =
        transitions[from]?.get(to)?.contains(actor) == true

    fun toActor(role: Role): Actor = when (role) {
        Role.KONSUMEN -> Actor.KONSUMEN
        Role.TUKANG -> Actor.TUKANG
        Role.ADMIN -> Actor.ADMIN
    }
}
