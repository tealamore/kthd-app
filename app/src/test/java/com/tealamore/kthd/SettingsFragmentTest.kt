package com.tealamore.kthd

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import com.tealamore.kthd.network.ApiClient
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsFragmentTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ApiClient.init(context)
    }

    @Test
    fun testDeviceIdProvider() {
        val deviceId = ApiClient.getDeviceId(context)
        assertNotNull(deviceId)
        assertTrue(deviceId.isNotEmpty())
    }

    @Test
    fun testSettingsFragmentViewCreation() {
        val activityController = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = activityController.get()
        val fragment = SettingsFragment()

        activity.supportFragmentManager.beginTransaction()
            .add(fragment, "SettingsFragment")
            .commitNow()

        assertTrue(fragment.isAdded)
        assertNotNull(fragment.view)
    }
}
