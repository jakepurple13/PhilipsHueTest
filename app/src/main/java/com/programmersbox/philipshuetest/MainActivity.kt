package com.programmersbox.philipshuetest

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.RecyclerView
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.scan.ScanSettings
import com.programmersbox.dragswipe.DragSwipeAdapter
import com.programmersbox.dragswipe.iterator
import com.programmersbox.flowutils.collectOnUi
import com.programmersbox.helpfulutils.animateChildren
import com.programmersbox.helpfulutils.layoutInflater
import com.programmersbox.helpfulutils.requestPermissions
import com.programmersbox.loggingutils.Loged
import com.programmersbox.philipshuetest.databinding.ActivityMainBinding
import com.programmersbox.philipshuetest.databinding.LightItemBinding
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

typealias Lights = Pair<String, Light>

class MainActivity : AppCompatActivity() {

    private val disposable = CompositeDisposable()

    private val hueService by lazy { HueFit.build() }

    private val adapter by lazy { LightAdapter(this, hueService) }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Loged.FILTER_BY_PACKAGE_NAME = "philipshuetest"

        binding.lightList.adapter = adapter

        binding.refreshLights.setOnRefreshListener { loadLights() }

        loadLights()

        binding.toggleAll.setOnCheckedChangeListener { _, isChecked ->
            for (l in adapter) {
                GlobalScope.launch {
                    hueService.turnOn(l.first.toInt(), BridgeLightRequestBody(isChecked))
                }
            }
        }

        requestPermissions(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) {
            if (it.isGranted) {
                //bluetoothSearch()
            }
        }

        /*ticker(500)
            .receiveAsFlow()
            .map { hueService.getLights().execute().body()?.entries?.all { it.value.state?.on ?: false } }
            .filterNotNull()
            .collectOnUi { binding.toggleAll.isChecked = it }*/

    }

    private fun bluetoothSearch() {
        val client = RxBleClient.create(this)
        client.scanBleDevices(ScanSettings.Builder().build())
            .filter { it.bleDevice.name.orEmpty().contains("Triones") }
            .subscribe { s ->
                println(s)
                /* client.getBleDevice(s.bleDevice.macAddress)
                 .establishConnection(false)
                 .flatMapSingle { it.writeCharacteristic(UUID.fromString(s.bleDevice.bluetoothDevice.alias), byteArrayOf(0xCC.toByte(), 0x23, 0x33)) }
                 .subscribe { println(it) }
                 .addTo(disposable)*/
            }
            .addTo(disposable)

        /*client.getBleDevice("XX:XX:XX:XX:XX:XX")
            .establishConnection(false)
            .flatMapSingle { it.writeCharacteristic(UUID.randomUUID(), byteArrayOf(0xCC.toByte(), 0x23, 0x33)) }
            .subscribe { println(it) }
            .addTo(disposable)*/

        GlobalScope.launch {
            delay(10000)
            disposable.dispose()
        }
    }

    private fun loadLights() {
        binding.refreshLights.isRefreshing = true
        GlobalScope.launch {
            hueService.getLights().execute().body()
                //.also { println(it?.entries?.joinToString("\n")) }
                ?.let { runOnUiThread { adapter.setListNotify(it.toList()) } }
                .also { runOnUiThread { binding.refreshLights.isRefreshing = false } }
        }
    }
}

class LightAdapter(private val context: Context, private val hueService: HueService) : DragSwipeAdapter<Lights, LightHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LightHolder =
        LightHolder(LightItemBinding.inflate(context.layoutInflater, parent, false))

    override fun LightHolder.onBind(item: Pair<String, Light>, position: Int) = loadLight(item, hueService)
}

class LightHolder(private val binding: LightItemBinding) : RecyclerView.ViewHolder(binding.root) {

    fun loadLight(light: Lights, hueService: HueService) {

        ticker(500)
            .receiveAsFlow()
            .map { hueService.getLight(light.first.toInt()).execute().body() }
            .filterNotNull()
            .collectOnUi { binding.light = it }

        binding.light = light.second

        val constraint = ConstraintSet()
        constraint.clone(binding.lightInfoCard)
        val constraintInfo = ConstraintSet()
        constraintInfo.clone(binding.root.context, R.layout.light_item_more_info)

        var info = false

        binding.toggleOnOff.setOnCheckedChangeListener { _, isChecked ->
            if(light.second.state?.on != isChecked) {
                GlobalScope.launch { hueService.turnOn(light.first.toInt(), BridgeLightRequestBody(isChecked)) }
            }
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
        //binding.toggleOnOff.isChecked = light.second.state?.on ?: false
        //binding.lightName.text = light.second.name
        //binding.lightBrightness.value = light.second.state?.bri?.toFloat()?.times(100)?.div(254) ?: 0f
        binding.lightBrightness.setLabelFormatter { "${it.roundToInt()}%" }
        //binding.lightModel.text = light.second.modelid

        binding.moreInfoCard.setOnClickListener {
            binding.lightInfoCard.animateChildren {
                if (info) {
                    constraint.applyTo(binding.lightInfoCard)
                } else {
                    constraintInfo.applyTo(binding.lightInfoCard)
                }
                info = !info
            }
        }
    }

}