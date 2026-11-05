package dev.niltsiar.tvhome.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassKindTest {
    @Test
    fun refractive_glass_draws_flat_below_api_33() {
        assertEquals(GlassKind.Flat, glassKindOn(GlassKind.Refractive, sdk = 31))
        assertEquals(GlassKind.Flat, glassKindOn(GlassKind.Refractive, sdk = 32))
    }

    @Test
    fun refractive_glass_refracts_from_api_33() {
        assertEquals(GlassKind.Refractive, glassKindOn(GlassKind.Refractive, sdk = 33))
        assertEquals(GlassKind.Refractive, glassKindOn(GlassKind.Refractive, sdk = 35))
    }

    @Test
    fun flat_glass_stays_flat_on_every_api() {
        assertEquals(GlassKind.Flat, glassKindOn(GlassKind.Flat, sdk = 31))
        assertEquals(GlassKind.Flat, glassKindOn(GlassKind.Flat, sdk = 35))
    }
}
