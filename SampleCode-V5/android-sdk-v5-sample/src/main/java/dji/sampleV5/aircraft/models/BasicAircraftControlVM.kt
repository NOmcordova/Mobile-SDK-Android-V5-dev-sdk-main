package dji.sampleV5.aircraft.models

import androidx.lifecycle.MutableLiveData
import dji.sampleV5.aircraft.util.ToastUtils
import dji.sdk.keyvalue.key.CameraKey
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.GimbalKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.value.camera.CameraMode
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.sdk.keyvalue.value.common.EmptyMsg
import dji.sdk.keyvalue.value.flightcontroller.FlightControlAuthorityChangeReason
import dji.sdk.keyvalue.value.gimbal.GimbalAngleRotation
import dji.sdk.keyvalue.value.gimbal.GimbalAngleRotationMode
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.common.error.RxError
import dji.v5.common.utils.CallbackUtils
import dji.v5.common.utils.RxUtil
import dji.v5.et.action
import dji.v5.et.create
import dji.v5.manager.aircraft.virtualstick.VirtualStickManager
import dji.v5.manager.aircraft.virtualstick.VirtualStickState
import dji.v5.manager.aircraft.virtualstick.VirtualStickStateListener
import dji.v5.manager.datacenter.camera.CameraStreamManager
import dji.v5.manager.interfaces.ICameraStreamManager
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.http.ContentType
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.WebSockets

class BasicAircraftControlVM : DJIViewModel() {
    data class CommandCompleted(val completed: Boolean, val errorDescription: String?)
    data class DroneState<T>(val state:T, val width:Int, val height:Int)

    private fun myTakeOff(callback: CommonCallbacks.CompletionCallbackWithParam<EmptyMsg>) {
        FlightControllerKey.KeyStartTakeoff.create().action({
            callback.onSuccess(it)
        }, { e: IDJIError ->
            callback.onFailure(e)
        })
    }

    private fun myLand(callback: CommonCallbacks.CompletionCallbackWithParam<EmptyMsg>) {
        FlightControllerKey.KeyStartAutoLanding.create().action({
            callback.onSuccess(it)
        }, { e: IDJIError ->
            callback.onFailure(e)
        })
    }

    private fun myCameraDown(callback: CommonCallbacks.CompletionCallbackWithParam<EmptyMsg>) {
        val angle = GimbalAngleRotation(GimbalAngleRotationMode.ABSOLUTE_ANGLE,
            -90.0, 0.0, 0.0, false, false, false, 1.0, false, 3)
        GimbalKey.KeyRotateByAngle.create().action(angle, {
            callback.onSuccess(it)
        }, { e: IDJIError ->
            callback.onFailure(e)
        })
    }

    private fun myCameraUp(callback: CommonCallbacks.CompletionCallbackWithParam<EmptyMsg>) {
        val angle = GimbalAngleRotation(GimbalAngleRotationMode.ABSOLUTE_ANGLE,
            0.0, 0.0, 0.0, false, false, false, 1.0, false, 3)
        GimbalKey.KeyRotateByAngle.create().action(angle, {
            callback.onSuccess(it)
        }, { e: IDJIError ->
            callback.onFailure(e)
        })
    }

    private fun myMoveForward(param1: Int, param2 : Int, time: Long) {
        VirtualStickManager.getInstance().rightStick.verticalPosition = param1
        VirtualStickManager.getInstance().rightStick.horizontalPosition = param2
        Thread.sleep(time)
        VirtualStickManager.getInstance().rightStick.verticalPosition = 0
        VirtualStickManager.getInstance().rightStick.horizontalPosition = 0
    }

    private fun myRotate(param: Int, time: Long) {
        VirtualStickManager.getInstance().leftStick.horizontalPosition = param
        Thread.sleep(time)
        VirtualStickManager.getInstance().leftStick.horizontalPosition = 0
    }

    private fun myMoveUp(param: Int, time: Long) {
        VirtualStickManager.getInstance().leftStick.verticalPosition = param
        Thread.sleep(time)
        VirtualStickManager.getInstance().leftStick.verticalPosition = 0
    }

    private fun myMoveSideWays(param: Int, time: Long) {
        VirtualStickManager.getInstance().rightStick.horizontalPosition = param
        Thread.sleep(time)
        VirtualStickManager.getInstance().rightStick.horizontalPosition = 0
    }

    /*private fun myTakePicture(callback: CommonCallbacks.CompletionCallbackWithParam<EmptyMsg>) {
        var fileListState = MutableLiveData<MediaFileListState>()
        var mediaFileListData = MutableLiveData<MediaFileListData>()
        MediaDataCenter.getInstance().mediaManager.addMediaFileListStateListener(object :
            MediaFileListStateListener {
            override fun onUpdate(mediaFileListState: MediaFileListState) {
                fileListState.postValue(mediaFileListState)
            }

        })
        mediaFileListData.postValue(MediaDataCenter.getInstance().mediaManager.mediaFileListData)
        MediaDataCenter.getInstance().mediaManager.addMediaFileListStateListener { mediaFileListState ->
            if (mediaFileListState == MediaFileListState.UP_TO_DATE) {
                val data = MediaDataCenter.getInstance().mediaManager.mediaFileListData;
                mediaFileListData.postValue(data)
            }
        }
        MediaDataCenter.getInstance().mediaManager.enable(object :
            CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                LogUtils.e(logTag, "enable playback success")
            }

            override fun onFailure(error: IDJIError) {
                LogUtils.e(logTag, "error is ${error.description()}")
            }
        })
        var currentTime = System.currentTimeMillis()
        MediaDataCenter.getInstance().mediaManager.pullMediaFileListFromCamera(
            PullMediaFileListParam.Builder().mediaFileIndex(-1).count(1).build(),
            object :
                CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    ToastUtils.showToast("Spend time:${(System.currentTimeMillis() - currentTime) / 1000}s")
                    LogUtils.i(logTag, "fetch success")
                }

                override fun onFailure(error: IDJIError) {
                    LogUtils.e(logTag, "fetch failed$error")
                }
            })
        /*var mediaFile = MediaDataCenter.getInstance().mediaManager.mediaFileListData.data[0]*/
        var mediaFile = mediaFileListData.value!!.data[0]
        myCameraDown(callback)
        val dirs = File(DiskUtil.getExternalCacheDirPath(ContextUtil.getContext(),  "/mediafile"))
        if (!dirs.exists()) {
            dirs.mkdirs()
        }
        val filepath = DiskUtil.getExternalCacheDirPath(ContextUtil.getContext(),  "/mediafile/"  + mediaFile?.fileName)
        val file = File(filepath)
        var offset = 0L
        val outputStream = FileOutputStream(file, true)
        val bos = BufferedOutputStream(outputStream)
        mediaFile?.pullOriginalMediaFileFromCamera(offset, object : MediaFileDownloadListener {
            override fun onStart() {
                LogUtils.i("MediaFile" , "${mediaFile.fileIndex } start download"  )
            }

            override fun onProgress(total: Long, current: Long) {
                val fullSize = offset + total;
                val downloadedSize = offset + current
                val data: Double = StringUtils.formatDouble((downloadedSize.toDouble() / fullSize.toDouble()))
                val result: String = StringUtils.formatDouble(data * 100, "#0").toString() + "%"
                LogUtils.i("MediaFile"  , "${mediaFile.fileIndex}  progress $result")
            }

            override fun onRealtimeDataUpdate(data: ByteArray, position: Long) {
                try {
                    bos.write(data)
                    bos.flush()
                } catch (e: IOException) {
                    LogUtils.e("MediaFile", "write error" + e.message)
                }
            }

            override fun onFinish() {
                try {
                    outputStream.close()
                    bos.close()
                } catch (error: IOException) {
                    LogUtils.e("MediaFile", "close error$error")
                }
                LogUtils.i("MediaFile" , "${mediaFile.fileIndex }  download finish"  )
            }

            override fun onFailure(error: IDJIError?) {
                LogUtils.e("MediaFile", "download error$error")
            }

        })
        /*MediaDataCenter.getInstance().mediaManager.deleteMediaFiles(listOf(mediaFile), object :
            CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                ToastUtils.showToast("delete success ");
            }

            override fun onFailure(error: IDJIError) {
                ToastUtils.showToast("delete failed  " + error.description());
            }

        })*/
        MediaDataCenter.getInstance().mediaManager.disable(object :
            CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                LogUtils.e(logTag, "exit playback success")
            }

            override fun onFailure(error: IDJIError) {
                LogUtils.e(logTag, "error is ${error.description()}")
            }
        })
        MediaDataCenter.getInstance().mediaManager.removeAllMediaFileListStateListener()
    }*/

    private fun myEnableVirtualStick() {
        val currentVirtualStickStateInfo = MutableLiveData(VirtualStickVM.VirtualStickStateInfo())
        VirtualStickManager.getInstance().setVirtualStickStateListener(object :
            VirtualStickStateListener {
            override fun onVirtualStickStateUpdate(stickState: VirtualStickState) {
                currentVirtualStickStateInfo.postValue(currentVirtualStickStateInfo.value?.apply {
                    this.state = stickState
                })
            }

            override fun onChangeReasonUpdate(reason: FlightControlAuthorityChangeReason) {
                currentVirtualStickStateInfo.postValue(currentVirtualStickStateInfo.value?.apply {
                    this.reason = reason
                })
            }
        })
        VirtualStickManager.getInstance().enableVirtualStick(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                ToastUtils.showToast("enableVirtualStick success.")
            }

            override fun onFailure(error: IDJIError) {
                ToastUtils.showToast("enableVirtualStick error,$error")
            }
        })
    }

    private fun myDisableVirtualStick() {
        VirtualStickManager.getInstance().disableVirtualStick(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                ToastUtils.showToast("disableVirtualStick success.")
            }

            override fun onFailure(error: IDJIError) {
                ToastUtils.showToast("disableVirtualStick error,${error})")
            }
        })
        VirtualStickManager.getInstance().clearAllVirtualStickStateListener()
    }

    /*private fun myGetHeight() {

    }*/

    /*private fun displayIP() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipString = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        ipText.text = getString(R.string.ip_format, ipString, 8080)
    }*/

    private fun startServer(callback: CommonCallbacks.CompletionCallbackWithParam<EmptyMsg>) {
        embeddedServer(Netty, 8080) {
            install(WebSockets)
            install(CallLogging)
            install(ContentNegotiation) {
                gson {  }
            }

            routing {
                meta()
                control(callback)
            }
        }.start(wait = false)
    }

    private fun Route.meta() {
        get("/") {
            call.respondText ( text="Connected", contentType = ContentType.Text.Plain )
        }
    }
    private fun Route.control(callback: CommonCallbacks.CompletionCallbackWithParam<EmptyMsg>) {
        var globalData = byteArrayOf()
        var globalWidth = 0
        var globalHeight = 0

        get("/TakeOff") {
            myTakeOff(callback)
            call.respond(CommandCompleted(true, "nice take off"))
        }

        get("/Land") {
            myLand(callback)
            call.respond(CommandCompleted(true, "nice landing"))
        }

        get("/CameraDown") {
            myCameraDown(callback)
            call.respond(CommandCompleted(true, "nice Camera"))
        }

        get("/CameraUp") {
            myCameraUp(callback)
            call.respond(CommandCompleted(true, "nice Camera"))
        }

        get("/MoveUp/{param}/{time}") {
            myMoveUp(call.parameters["param"]!!.toInt(), call.parameters["time"]!!.toLong())
            call.respond(CommandCompleted(true, "nice Move"))
        }

        get("/MoveSideways/{param}/{time}") {
            myMoveSideWays(call.parameters["param"]!!.toInt(), call.parameters["time"]!!.toLong())
            call.respond(CommandCompleted(true, "nice Move"))
        }

        get("/Rotate/{param}/{time}") {
            myRotate(call.parameters["param"]!!.toInt(), call.parameters["time"]!!.toLong())
            call.respond(CommandCompleted(true, "nice Move"))
        }

        get("/MoveForward/{param1}/{param2}/{time}") {
            myMoveForward(call.parameters["param1"]!!.toInt(), call.parameters["param2"]!!.toInt(), call.parameters["time"]!!.toLong())
            call.respond(CommandCompleted(true, "nice Move"))
        }

        get("/Enable") {
            myEnableVirtualStick()
            call.respond(CommandCompleted(true, "nice Enabling"))
        }

        get("/Disable") {
            myDisableVirtualStick()
            call.respond(CommandCompleted(true, "nice Disabling"))
        }

        get("/TakePicture") {
            RxUtil.setValue(
                KeyTools.createKey<CameraMode>(
                    CameraKey.KeyCameraMode
                ), CameraMode.PHOTO_NORMAL)
                .andThen(RxUtil.performActionWithOutResult(KeyTools.createKey(CameraKey.KeyStartShootPhoto)))
                .subscribe({ CallbackUtils.onSuccess(object : CommonCallbacks.CompletionCallback {
                    override fun onSuccess() {
                        ToastUtils.showToast("Take photo success success.")
                    }

                    override fun onFailure(error: IDJIError) {
                        ToastUtils.showToast("Take photo error,${error})")
                    }
                }) }
                ) { throwable: Throwable ->
                    CallbackUtils.onFailure(
                        callback,
                        (throwable as RxError).djiError
                    )
                }
            /*Thread.sleep(3_000)
            myTakePicture(callback)*/
            call.respond(CommandCompleted(true, "nice Picture"))
        }

        get("/CameraStream") {
            call.respond(DroneState(globalData, globalWidth, globalHeight))
        }

        get("/StartCameraStream") {
            CameraStreamManager.getInstance().addFrameListener(ComponentIndexType.LEFT_OR_MAIN, ICameraStreamManager.FrameFormat. object :
                ICameraStreamManager.CameraFrameListener {
                override fun onFrame(
                    frameData: ByteArray,
                    offset: Int,
                    length: Int,
                    width: Int,
                    height: Int,
                    format: ICameraStreamManager.FrameFormat
                ) {
                    globalData = frameData
                    globalWidth = width
                    globalHeight = height
                }
            })
            call.respond(CommandCompleted(true, "nice Stream"))
        }

        /*get("/getHeight") {
            call.respond(DroneState(myGetHeight()))
        }*/
    }

    fun startTakeOff(callback: CommonCallbacks.CompletionCallbackWithParam<EmptyMsg>) {
        startServer(callback)
    }

    fun startLanding(callback: CommonCallbacks.CompletionCallbackWithParam<EmptyMsg>) {
        FlightControllerKey.KeyStartAutoLanding.create().action({
            callback.onSuccess(it)
        }, { e: IDJIError ->
            callback.onFailure(e)
        })
    }


}
