package com.programmersbox.philipshuetest

import android.app.PendingIntent
import android.content.Intent
import android.service.controls.Control
import android.service.controls.ControlsProviderService
import android.service.controls.DeviceTypes
import android.service.controls.actions.BooleanAction
import android.service.controls.actions.ControlAction
import android.service.controls.actions.FloatAction
import android.service.controls.templates.ControlButton
import android.service.controls.templates.RangeTemplate
import android.service.controls.templates.ToggleRangeTemplate
import com.programmersbox.loggingutils.Loged
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.processors.ReplayProcessor
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.reactivestreams.FlowAdapters
import java.util.concurrent.Flow
import java.util.function.Consumer
import kotlin.random.Random

class ControlService : ControlsProviderService() {

    private val disposable = CompositeDisposable()

    private lateinit var updatePublisher: ReplayProcessor<Control>

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
            //put any data into here OR! If using navigation component, we can use the pending intent for that
            Intent(baseContext, MainActivity::class.java),
            PendingIntent.FLAG_CANCEL_CURRENT
        )
    }

    private fun Map.Entry<String, Light>.toStateful(): Control {
        return Control.StatefulBuilder(
            key, //unique id
            bottomSheet //what opens when press and held
        )
            .setDeviceType(DeviceTypes.TYPE_LIGHT) //required
            .setTitle("${value.name}") //required
            .setSubtitle("Slider") //required
            .setStatus(Control.STATUS_OK) //required
            .setStructure("Living Room") //optional - most used for room location
            //.setCustomColor() //optional
            //.setCustomIcon()  //optional
            //.setStatusText()  //optional
            //.setZone() //optional
            .setControlTemplate(
                ToggleRangeTemplate(
                    "brightness",
                    ControlButton(value.state?.on ?: false, "brightness-${key}"),
                    RangeTemplate(
                        "range",
                        0f,
                        100f,
                        value.state?.bri?.toFloat()?.times(100f)?.div(254f) ?: 0f,
                        5f,
                        "• %.0f%%"
                    )
                )
            )
            .build()
    }

    private fun Map.Entry<String, Light>.toStateless(): Control {
        return Control.StatelessBuilder(
            key, //unique id
            bottomSheet //what opens when press and held
        )
            .setDeviceType(DeviceTypes.TYPE_LIGHT) //required
            .setTitle("${value.name}") //required
            .setSubtitle("Slider") //required
            .build()
    }

    private val fit = HueFit.build()

    override fun createPublisherForAllAvailable(): Flow.Publisher<Control> {
        //This is for lazy loading. Get all devices but don't allow control
        //this is just for choosing what to be able to control
        return FlowAdapters.toFlowPublisher(
            fit.getLightsRx()
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .map { it.entries.map { it.toStateless() } }
                .flattenAsFlowable { it }
        )
    }

    private fun Control.string(): String {
        val hc = String.format("0x%08x", hashCode())
        return ("Control($hc id=${controlId}, type=${deviceType}, " +
                "title=${title}, template=${controlTemplate})")
    }

    override fun createPublisherFor(controlIds: MutableList<String>): Flow.Publisher<Control> {
        ///the controlIds are what the user has chosen to be viewed

        updatePublisher = ReplayProcessor.create()

        //This is once everything is loaded and all information is obtained.
        disposable.add(
            fit.getLightsRx()
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .map { it.entries.map { it.toStateful() } }
                .flattenAsFlowable { it }
                .filter { controlIds.contains(it.controlId) }
                .subscribe { updatePublisher.onNext(it) }
        )

        //return the updatePublisher that will listen to any updates
        return FlowAdapters.toFlowPublisher(updatePublisher)
    }

    override fun performControlAction(controlId: String, action: ControlAction, consumer: Consumer<Int>) {
        Loged.a("$controlId - $action")
        //Here is where we check what the action is and do the action, update the tile, and send the request
        when (action) {
            is BooleanAction -> {
                GlobalScope.launch { fit.turnOn(controlId.toInt(), BridgeLightRequestBody(action.newState)) }
                updatePublisher.onNext(
                    Control.StatefulBuilder(
                        controlId, //unique id
                        bottomSheet //what opens when press and held
                    )
                        .setDeviceType(DeviceTypes.TYPE_LIGHT) //required
                        .setTitle("${updatePublisher.filter { it.controlId == controlId }.blockingFirst().title}") //required
                        .setSubtitle("Slider") //required
                        .setStatus(Control.STATUS_OK) //required
                        .setStructure("Living Room") //optional
                        .setControlTemplate(
                            ToggleRangeTemplate(
                                "brightness",
                                ControlButton(action.newState, "brightness-${controlId}"),
                                RangeTemplate(
                                    "range",
                                    0f,
                                    100f,
                                    50f, //This can either be pre-saved or set to min or max when its turned off or on
                                    5f,
                                    "• %.0f%%"
                                )
                            )
                        )
                        .build()
                )
            }
            is FloatAction -> {
                GlobalScope.launch {
                    fit.turnOn(controlId.toInt(), BridgeLightRequestBody(true, bri = (action.newValue.toInt() * 254) / 100))
                }
                updatePublisher.onNext(
                    Control.StatefulBuilder(
                        controlId, //unique id
                        bottomSheet //what opens when press and held
                    )
                        .setDeviceType(DeviceTypes.TYPE_LIGHT) //required
                        .setTitle("${updatePublisher.filter { it.controlId == controlId }.blockingFirst().title}") //required
                        .setSubtitle("Slider") //required
                        .setStatus(Control.STATUS_OK) //required
                        .setStructure("Living Room") //optional
                        .setControlTemplate(
                            ToggleRangeTemplate(
                                "brightness",
                                ControlButton(true, "brightness-${controlId}"),
                                RangeTemplate(
                                    "range",
                                    0f,
                                    100f,
                                    action.newValue,
                                    5f,
                                    "• %.0f%%"
                                )
                            )
                        )
                        .build()
                )
            }
        }
        //only accept ok if an action went through
        consumer.accept(ControlAction.RESPONSE_OK)
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.dispose()
    }

}