package com.jonkryl.cutledger

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import org.junit.Assert.*
import org.junit.Test

class LargeFontTest {
    @Test fun doubleFontScalePreservesInputLabelsAndLowerActions() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                assertTrue(it.resources.configuration.fontScale >= 1.9f)
                for (id in listOf(R.id.stock_input, R.id.parts_input, R.id.kerf_input, R.id.trim_input)) assertNotNull(it.findViewById<android.view.View>(id))
            }
            onView(withId(R.id.save)).perform(scrollTo(), click())
            onView(withId(R.id.help)).perform(scrollTo(), click())
            onView(withText(R.string.close)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.calculate)).perform(scrollTo(), click())
            repeat(100) {
                var ready = false
                scenario.onActivity { ready = it.findViewById<android.view.View>(R.id.summary) != null }
                if (ready) return@use
                Thread.sleep(100)
            }
            fail("Large-font input controls did not produce a plan")
        }
    }
}
