package com.programmersbox.philipshuetest

import com.programmersbox.gsonutils.getApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() = runBlocking {
        val f = HueFit.build()
        val f1 = f.getLights().execute().body().orEmpty()
        println(f1.entries.joinToString("\n"))
        val arin = f1.entries.find { it.value.name == "Arin living room" }
        f.turnOn(
            arin!!.key.toInt(),
            BridgeLightRequestBody(
                true,
                254,
                254,
                65535
            )
        )

        Unit
    }
}