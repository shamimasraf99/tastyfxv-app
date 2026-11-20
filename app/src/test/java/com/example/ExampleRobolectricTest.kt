package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun appName_isTastyFx() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Tasty FX", appName)
    }

    @Test
    fun bengaliCredits_matchExpectedText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("ডেভেলপার বাই শামীম", context.getString(R.string.developer_credit_line_1))
        assertEquals("টেলিগ্রাম - @shamimasraf99", context.getString(R.string.developer_credit_line_2))
    }

    @Test
    fun externalUrlDetection_routesExternalAppsAndKeepsTastyFxInternal() {
        assertFalse(MainActivity.shouldOpenExternally("https://tastyfx.pro/"))
        assertFalse(MainActivity.shouldOpenExternally("https://tastyfx.pro/trade/btcusd"))
        assertTrue(MainActivity.shouldOpenExternally("https://t.me/shamimasraf99"))
        assertTrue(MainActivity.shouldOpenExternally("tg://resolve?domain=shamimasraf99"))
        assertTrue(MainActivity.shouldOpenExternally("https://wa.me/8801700000000"))
        assertTrue(MainActivity.shouldOpenExternally("whatsapp://send?text=hello"))
        assertTrue(MainActivity.shouldOpenExternally("mailto:support@tastyfx.pro"))
        assertTrue(MainActivity.shouldOpenExternally("tel:+8801700000000"))
    }
}
