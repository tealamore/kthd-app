package com.tealamore.kthd

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConsentFragmentTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ConsentManager.setConsented(context, false)
    }

    @Test
    fun testConsentManagerInitialState() {
        assertFalse(ConsentManager.hasConsented(context))
    }

    @Test
    fun testConsentManagerSetConsented() {
        ConsentManager.setConsented(context, true)
        assertTrue(ConsentManager.hasConsented(context))
    }

    @Test
    fun testConsentFragmentLifecycleAndViews() {
        val activityController = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = activityController.get()
        val fragment = ConsentFragment()

        activity.supportFragmentManager.beginTransaction()
            .add(fragment, "ConsentFragment")
            .commitNow()

        assertTrue(fragment.isAdded)
        assertNotNull(fragment.view)
    }
}
