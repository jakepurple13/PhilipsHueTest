package com.programmersbox.philipshuetest

import androidx.annotation.IntRange
import com.programmersbox.thirdpartyutils.gsonConverter
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.create
import retrofit2.http.*

object HueFit {
    private const val baseUrl = "http://192.168.1.78/api//"

    private val client = OkHttpClient.Builder()
        .addInterceptor {
            it.proceed(
                it.request()
                    .newBuilder()
                    .build()
            )
        }
        .build()

    fun build(): HueService = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .gsonConverter()
        .build()
        .create()
}

interface HueService {

    @GET("lights")
    fun getLights(): Call<Map<String, Light>>

    @PUT("lights/{lightId}/state")
    suspend fun turnOn(@Path("lightId") lightId: Int, @Body bridgeLightRequestBody: BridgeLightRequestBody)

}

data class Light(
    val state: State?,
    val swupdate: Swupdate?,
    val type: String?,
    val name: String?,
    val modelid: String?,
    val manufacturername: String?,
    val productname: String?,
    val capabilities: Capabilities?,
    val config: Config?,
    val uniqueid: String?,
    val swversion: String?,
    val swconfigid: String?,
    val productid: String?
)

data class Capabilities(val certified: Boolean?, val control: Control?, val streaming: Streaming?)

data class Config(val archetype: String?, val function: String?, val direction: String?, val startup: Startup?)

data class Control(val mindimlevel: Number?, val maxlumen: Number?)

data class Startup(val mode: String?, val configured: Boolean?)

data class State(val on: Boolean?, val bri: Number?, val alert: String?, val mode: String?, val reachable: Boolean?)

data class Streaming(val renderer: Boolean?, val proxy: Boolean?)

data class Swupdate(val state: String?, val lastinstall: String?)

data class BridgeLightRequestBody(
    private val on: Boolean,

    @IntRange(from = 0, to = 254)
    private val sat: Int? = null,

    @IntRange(from = 0, to = 254)
    private val bri: Int? = null,

    //Maximum value = 65535 = 182.04 * 360
    @IntRange(from = 0, to = 65535)
    private val hue: Int? = null
)