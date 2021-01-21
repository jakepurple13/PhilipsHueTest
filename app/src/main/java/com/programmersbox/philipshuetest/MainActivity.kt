package com.programmersbox.philipshuetest

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.programmersbox.dragswipe.DragSwipeAdapter
import com.programmersbox.helpfulutils.layoutInflater
import com.programmersbox.philipshuetest.databinding.ActivityMainBinding
import com.programmersbox.philipshuetest.databinding.LightItemBinding
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private val hueService by lazy { HueFit.build() }

    private val adapter by lazy { LightAdapter(this, hueService) }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.lightList.adapter = adapter

        binding.refreshLights.setOnRefreshListener { loadLights() }

        loadLights()
    }

    private fun loadLights() {
        binding.refreshLights.isRefreshing = true
        GlobalScope.launch {
            hueService.getLights().execute().body()
                .also { println(it?.entries?.joinToString("\n")) }
                ?.let { runOnUiThread { adapter.addItems(it.toList()) } }
                .also { runOnUiThread { binding.refreshLights.isRefreshing = false } }
        }
    }
}

class LightAdapter(private val context: Context, private val hueService: HueService) : DragSwipeAdapter<Pair<String, Light>, LightHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LightHolder =
        LightHolder(LightItemBinding.inflate(context.layoutInflater, parent, false))

    override fun LightHolder.onBind(item: Pair<String, Light>, position: Int) = loadLight(item, hueService)
}

class LightHolder(private val binding: LightItemBinding) : RecyclerView.ViewHolder(binding.root) {

    fun loadLight(light: Pair<String, Light>, hueService: HueService) {
        binding.toggleOnOff.setOnCheckedChangeListener { _, isChecked ->
            GlobalScope.launch { hueService.turnOn(light.first.toInt(), BridgeLightRequestBody(isChecked)) }
        }
        binding.lightBrightness.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                GlobalScope.launch {
                    hueService.turnOn(
                        light.first.toInt(),
                        BridgeLightRequestBody(binding.toggleOnOff.isChecked, bri = (value.toInt() * 254) / 100)
                    )
                }
            }
        }
        binding.toggleOnOff.isChecked = light.second.state?.on ?: false
        binding.lightName.text = light.second.name
        binding.lightBrightness.value = light.second.state?.bri?.toFloat()?.times(100)?.div(254) ?: 0f
        binding.lightBrightness.setLabelFormatter { "${it.roundToInt()}%" }
    }

}