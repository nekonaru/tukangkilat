package id.tukangkilat.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PricingTest {

    @Test
    fun `payout is net service fee plus full visit fee`() {
        // 15% of 125.000 = 18.750 commission; net = 106.250; plus 25.000 visit fee
        assertEquals(131_250L, Pricing.technicianPayout(125_000L, 25_000L))
    }

    @Test
    fun `commission rounds to whole rupiah`() {
        assertEquals(18_750L, Pricing.commission(125_000L))
        assertEquals(0L, Pricing.commission(0L))
    }

    @Test
    fun `eta grows with distance`() {
        val near = Geo.estimateMinutes(1.0)
        val far = Geo.estimateMinutes(10.0)
        assertTrue(far > near)
        assertTrue(near >= 1)
    }

    @Test
    fun `haversine is symmetric and zero for same point`() {
        assertEquals(0.0, Geo.haversineKm(-6.2, 106.8, -6.2, 106.8), 0.0001)
        val d1 = Geo.haversineKm(-6.2, 106.8, -6.3, 106.9)
        val d2 = Geo.haversineKm(-6.3, 106.9, -6.2, 106.8)
        assertEquals(d1, d2, 0.0001)
    }

    @Test
    fun `tebet to mangga dua is roughly ten km`() {
        val km = Geo.haversineKm(-6.2297, 106.8294, -6.1385, 106.8170)
        assertTrue(km in 7.0..15.0, "expected roughly 7-15 km, was $km")
    }

    @Test
    fun `rupiah formatting groups thousands`() {
        assertEquals("Rp150.000", Format.rupiah(150_000L))
        assertEquals("Rp1.500.000", Format.rupiah(1_500_000L))
        assertEquals("Rp0", Format.rupiah(0L))
    }
}
