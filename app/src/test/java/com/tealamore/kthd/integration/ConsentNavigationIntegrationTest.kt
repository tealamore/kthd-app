package com.tealamore.kthd.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tealamore.kthd.ConsentManager
import com.tealamore.kthd.network.ApiClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConsentNavigationIntegrationTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ApiClient.init(context)
        ConsentManager.setConsented(context, false)
    }

    @Test
    fun testConsentFlowAndDeviceIdIntegration() {
        assertFalse(ConsentManager.hasConsented(context))

        val deviceId = ApiClient.getDeviceId(context)
        assertNotNull(deviceId)
        assertTrue(deviceId.isNotEmpty())

        ConsentManager.setConsented(context, true)
        assertTrue(ConsentManager.hasConsented(context))
    }
}
