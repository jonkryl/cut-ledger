package com.jonkryl.cutledger

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.jonkryl.cutledger.core.DraftCodec
import org.junit.Assert.*
import org.junit.Test

class CutJourneyTest {
    private fun awaitPlan(scenario: ActivityScenario<MainActivity>) {
        repeat(100) {
            var ready = false
            scenario.onActivity { ready = it.findViewById<android.view.View>(R.id.summary) != null }
            if (ready) return
            Thread.sleep(100)
        }
        error("Actual result screen never appeared")
    }
    private fun edit(id: Int, value: String) { onView(withId(id)).perform(scrollTo(), replaceText(value), closeSoftKeyboard()) }
    @Test fun actualInputsProduceFinitePlanReadableCsvAndSavedDraft() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.example)).perform(scrollTo(), click())
            edit(R.id.project_name, "Bench")
            edit(R.id.stock_input, "1000;1")
            edit(R.id.parts_input, "Leg;400;2")
            edit(R.id.kerf_input, "3")
            edit(R.id.trim_input, "10")
            onView(withId(R.id.calculate)).perform(scrollTo(), click()); awaitPlan(scenario)
            onView(withId(R.id.summary)).check(matches(withText(context.getString(R.string.result_summary, 2, 2, 1))))
            onView(withId(R.id.result)).check(matches(withText(context.getString(R.string.result_lengths, "800", "6", "174"))))
            Intents.init()
            try {
                Intents.intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
                onView(withId(R.id.share)).perform(scrollTo(), click())
                val chooser = Intents.getIntents().last { it.action == Intent.ACTION_CHOOSER }
                @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                assertEquals(Intent.ACTION_SEND, send.action); assertEquals("text/csv", send.type)
                @Suppress("DEPRECATION") val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
                assertEquals(context.packageName + ".exports", uri.authority)
                assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                val csv = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                assertTrue(csv.contains("part;1;1000;\"Leg\";400;10;410;"))
                assertTrue(csv.contains("part;1;1000;\"Leg\";400;413;813;"))
                assertTrue(csv.contains("remainder;1;1000;;;;;174"))
            } finally { Intents.release() }
            onView(withId(R.id.back)).perform(scrollTo(), click())
            onView(withId(R.id.share)).check(doesNotExist())
            edit(R.id.parts_input, "Leg;400;3")
            onView(withId(R.id.calculate)).perform(scrollTo(), click()); awaitPlan(scenario)
            onView(withId(R.id.summary)).check(matches(withText(context.getString(R.string.result_summary, 2, 3, 1))))
            onView(withId(R.id.save)).perform(scrollTo(), click())
            val stored = context.getSharedPreferences("cut_ledger", 0).getString("draft", null)!!
            assertEquals("Leg;400;3", DraftCodec.decode(stored)!!.parts)
            context.getSharedPreferences("journey_receipt", 0).edit().putString("saved", stored).commit()
            onView(withId(R.id.help)).perform(scrollTo(), click())
            onView(withText(R.string.close)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.menu)).perform(scrollTo(), click())
            onView(withText(R.string.privacy)).inRoot(isDialog()).perform(click())
            onView(withText(R.string.ad_contextual)).inRoot(isDialog()).perform(click())
            assertFalse(context.getSharedPreferences("ad_privacy", 0).getBoolean("personalized", true))
        }
    }
}
