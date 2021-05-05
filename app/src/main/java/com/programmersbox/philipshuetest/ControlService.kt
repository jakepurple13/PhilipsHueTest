package com.programmersbox.philipshuetest

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.controls.Control
import android.service.controls.ControlsProviderService
import android.service.controls.DeviceTypes
import android.service.controls.actions.BooleanAction
import android.service.controls.actions.ControlAction
import android.service.controls.actions.FloatAction
import android.service.controls.templates.ControlButton
import android.service.controls.templates.RangeTemplate
import android.service.controls.templates.ToggleRangeTemplate
import android.util.Log
import androidx.annotation.RequiresApi
import com.programmersbox.loggingutils.Loged
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.Flowables
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.reactivestreams.FlowAdapters
import java.util.concurrent.Flow
import java.util.function.Consumer
import kotlin.random.Random

class ControlService : ControlsProviderService() {

    /*
    This won't do things in real time. If another phone controls the device,
    it will not update on the current phone until leaving the screen and returning
     */

    /*
    This is what comes up when press and holding the tile. Google recommends the following:
         Fill in details for the activity related to this device. On long press,
         this Intent will be launched in a bottomsheet. Please design the activity
         accordingly to fit a more limited space (about 2/3 screen height).
     */
    private val bottomSheet by lazy {
        PendingIntent.getActivity(
            baseContext,
            Random.nextInt(0, 100),
            Intent(baseContext, MainActivity::class.java),
            PendingIntent.FLAG_CANCEL_CURRENT
        )
    }

    private fun Map.Entry<String, Light>.toStateful(): Control {
        return Control.StatefulBuilder(
            key,
            bottomSheet
        )
            .setDeviceType(DeviceTypes.TYPE_LIGHT)
            .setTitle("${value.name}")
            .setSubtitle("Slider")
            .setStatus(Control.STATUS_OK)
            .setStructure("Living Room")
            //.setControlId(it.key)
            .setControlTemplate(
                ToggleRangeTemplate(
                    "brightness",
                    ControlButton(value.state?.on ?: false, "brightness-${key}"),
                    RangeTemplate("range", 0f, 255f, value.state?.bri?.toFloat() ?: 0f, 5f, null)
                )
            )
            .build()
    }

    private fun Map.Entry<String, Light>.toStateless(): Control {
        return Control.StatelessBuilder(
            key,
            bottomSheet
        )
            .setDeviceType(DeviceTypes.TYPE_LIGHT)
            .setTitle("${value.name}")
            .setSubtitle("Slider")
            .build()
    }

    private val fit = HueFit.build()

    override fun createPublisherForAllAvailable(): Flow.Publisher<Control> {
        //This is for lazy loading. Get all devices but don't allow control
        return FlowAdapters.toFlowPublisher(
            fit.getLightsRx()
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .map { it.entries.map { it.toStateless() } }
                .flattenAsFlowable { it }
        )
    }

    override fun createPublisherFor(controlIds: MutableList<String>): Flow.Publisher<Control> {
        //This is once everything is loaded and all information is obtained.
        return FlowAdapters.toFlowPublisher(
            fit.getLightsRx()
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .map { it.entries.map { it.toStateful() } }
                .flattenAsFlowable { it }
                .filter { controlIds.contains(it.controlId) }
        )
    }

    override fun performControlAction(controlId: String, action: ControlAction, consumer: Consumer<Int>) {
        Loged.a("$controlId - $action")
        //Here is where we check what the action is and do the action, update the tile, and send the request
        when (action) {
            is BooleanAction -> {
                GlobalScope.launch { fit.turnOn(controlId.toInt(), BridgeLightRequestBody(action.newState)) }
            }
        }
        consumer.accept(ControlAction.RESPONSE_OK)
    }

}