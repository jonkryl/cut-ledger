package com.jonkryl.cutledger

import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.jonkryl.cutledger.core.DraftCodec
import org.junit.Assert.*
import org.junit.Test

class RestartTest {
    @Test fun newProcessRestoresActualUserQuantities() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val raw = context.getSharedPreferences("journey_receipt", 0).getString("saved", null) ?: error("Previous actual journey receipt absent")
        val expected = DraftCodec.decode(raw)!!
        ActivityScenario.launch(MainActivity::class.java).use { scenario -> scenario.onActivity {
            assertEquals(expected.name, it.findViewById<EditText>(R.id.project_name).text.toString())
            assertEquals(expected.stock, it.findViewById<EditText>(R.id.stock_input).text.toString())
            assertEquals(expected.parts, it.findViewById<EditText>(R.id.parts_input).text.toString())
            assertEquals(expected.kerf, it.findViewById<EditText>(R.id.kerf_input).text.toString())
            assertEquals(expected.trim, it.findViewById<EditText>(R.id.trim_input).text.toString())
            assertTrue(context.getSharedPreferences("cut_ledger", 0).getString("projects", "[]")!!.contains("Bench"))
        } }
    }
}
