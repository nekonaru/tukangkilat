package id.tukangkilat.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrderStateMachineTest {

    @Test
    fun `valid happy path transitions are allowed`() {
        assertTrue(OrderStateMachine.canTransition(OrderStatus.MENUNGGU_PEMBAYARAN, OrderStatus.MENCARI_TEKNISI, OrderStateMachine.Actor.SYSTEM))
        assertTrue(OrderStateMachine.canTransition(OrderStatus.MENCARI_TEKNISI, OrderStatus.TEKNISI_DITEMUKAN, OrderStateMachine.Actor.TUKANG))
        assertTrue(OrderStateMachine.canTransition(OrderStatus.TEKNISI_DITEMUKAN, OrderStatus.DALAM_PERJALANAN, OrderStateMachine.Actor.TUKANG))
        assertTrue(OrderStateMachine.canTransition(OrderStatus.DALAM_PERJALANAN, OrderStatus.TIBA, OrderStateMachine.Actor.TUKANG))
        assertTrue(OrderStateMachine.canTransition(OrderStatus.TIBA, OrderStatus.SEDANG_BEKERJA, OrderStateMachine.Actor.TUKANG))
        assertTrue(OrderStateMachine.canTransition(OrderStatus.SEDANG_BEKERJA, OrderStatus.MENUNGGU_KONFIRMASI, OrderStateMachine.Actor.TUKANG))
        assertTrue(OrderStateMachine.canTransition(OrderStatus.MENUNGGU_KONFIRMASI, OrderStatus.SELESAI, OrderStateMachine.Actor.KONSUMEN))
    }

    @Test
    fun `auto complete after 24h is a system transition`() {
        assertTrue(OrderStateMachine.canTransition(OrderStatus.MENUNGGU_KONFIRMASI, OrderStatus.SELESAI, OrderStateMachine.Actor.SYSTEM))
    }

    @Test
    fun `payment failure and retry path`() {
        assertTrue(OrderStateMachine.canTransition(OrderStatus.MENUNGGU_PEMBAYARAN, OrderStatus.PEMBAYARAN_GAGAL, OrderStateMachine.Actor.SYSTEM))
        assertTrue(OrderStateMachine.canTransition(OrderStatus.PEMBAYARAN_GAGAL, OrderStatus.MENUNGGU_PEMBAYARAN, OrderStateMachine.Actor.KONSUMEN))
    }

    @Test
    fun `technician rejection returns to searching`() {
        assertTrue(OrderStateMachine.canTransition(OrderStatus.TEKNISI_DITEMUKAN, OrderStatus.MENCARI_TEKNISI, OrderStateMachine.Actor.TUKANG))
        assertTrue(OrderStateMachine.canTransition(OrderStatus.TEKNISI_DITEMUKAN, OrderStatus.MENCARI_TEKNISI, OrderStateMachine.Actor.SYSTEM))
    }

    @Test
    fun `issue report path`() {
        assertTrue(OrderStateMachine.canTransition(OrderStatus.MENUNGGU_KONFIRMASI, OrderStatus.DILAPORKAN, OrderStateMachine.Actor.KONSUMEN))
        assertTrue(OrderStateMachine.canTransition(OrderStatus.DILAPORKAN, OrderStatus.KUNJUNGAN_ULANG, OrderStateMachine.Actor.ADMIN))
        assertTrue(OrderStateMachine.canTransition(OrderStatus.KUNJUNGAN_ULANG, OrderStatus.TEKNISI_DITEMUKAN, OrderStateMachine.Actor.TUKANG))
    }

    @Test
    fun `cancellation rules`() {
        assertTrue(OrderStateMachine.canTransition(OrderStatus.MENUNGGU_PEMBAYARAN, OrderStatus.DIBATALKAN, OrderStateMachine.Actor.KONSUMEN))
        assertTrue(OrderStateMachine.canTransition(OrderStatus.PEMBAYARAN_GAGAL, OrderStatus.DIBATALKAN, OrderStateMachine.Actor.KONSUMEN))
        assertTrue(OrderStateMachine.canTransition(OrderStatus.MENCARI_TEKNISI, OrderStatus.DIBATALKAN, OrderStateMachine.Actor.KONSUMEN))
        // After DALAM_PERJALANAN the consumer can no longer cancel.
        assertFalse(OrderStateMachine.canTransition(OrderStatus.DALAM_PERJALANAN, OrderStatus.DIBATALKAN, OrderStateMachine.Actor.KONSUMEN))
        assertFalse(OrderStateMachine.canTransition(OrderStatus.TIBA, OrderStatus.DIBATALKAN, OrderStateMachine.Actor.KONSUMEN))
        // After TIBA the order cannot be cancelled at all by the consumer.
        assertFalse(OrderStateMachine.canTransition(OrderStatus.SEDANG_BEKERJA, OrderStatus.DIBATALKAN, OrderStateMachine.Actor.KONSUMEN))
    }

    @Test
    fun `illegal transitions are rejected`() {
        assertFalse(OrderStateMachine.canTransition(OrderStatus.MENUNGGU_PEMBAYARAN, OrderStatus.TEKNISI_DITEMUKAN, OrderStateMachine.Actor.SYSTEM))
        assertFalse(OrderStateMachine.canTransition(OrderStatus.SELESAI, OrderStatus.DIBATALKAN, OrderStateMachine.Actor.ADMIN))
        assertFalse(OrderStateMachine.canTransition(OrderStatus.DIBATALKAN, OrderStatus.SELESAI, OrderStateMachine.Actor.KONSUMEN))
        assertFalse(OrderStateMachine.canTransition(OrderStatus.DALAM_PERJALANAN, OrderStatus.SELESAI, OrderStateMachine.Actor.TUKANG))
        assertFalse(OrderStateMachine.canTransition(OrderStatus.SEDANG_BEKERJA, OrderStatus.SELESAI, OrderStateMachine.Actor.TUKANG))
        assertFalse(OrderStateMachine.canTransition(OrderStatus.MENUNGGU_KONFIRMASI, OrderStatus.DILAPORKAN, OrderStateMachine.Actor.TUKANG))
        // Consumer cannot mark work as started or finished.
        assertFalse(OrderStateMachine.canTransition(OrderStatus.TIBA, OrderStatus.SEDANG_BEKERJA, OrderStateMachine.Actor.KONSUMEN))
        assertFalse(OrderStateMachine.canTransition(OrderStatus.DALAM_PERJALANAN, OrderStatus.TIBA, OrderStateMachine.Actor.KONSUMEN))
        // Technician cannot confirm completion or report issues for the consumer.
        assertFalse(OrderStateMachine.canTransition(OrderStatus.MENUNGGU_KONFIRMASI, OrderStatus.SELESAI, OrderStateMachine.Actor.TUKANG))
        assertFalse(OrderStateMachine.canTransition(OrderStatus.MENUNGGU_KONFIRMASI, OrderStatus.DILAPORKAN, OrderStateMachine.Actor.TUKANG))
    }

    @Test
    fun `every status is reachable in the graph`() {
        val reachable = mutableSetOf(OrderStatus.MENUNGGU_PEMBAYARAN)
        val queue = ArrayDeque<OrderStatus>()
        queue.add(OrderStatus.MENUNGGU_PEMBAYARAN)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val nexts = OrderStatus.entries.filter { next ->
                OrderStateMachine.Actor.entries.any { actor ->
                    OrderStateMachine.canTransition(current, next, actor)
                }
            }
            for (n in nexts) {
                if (reachable.add(n)) queue.add(n)
            }
        }
        assertTrue(reachable.containsAll(OrderStatus.entries.toSet()))
    }
}
