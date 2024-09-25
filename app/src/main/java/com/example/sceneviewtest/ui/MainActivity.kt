package com.example.sceneviewtest.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.lifecycle.lifecycleScope
import com.example.sceneviewtest.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.InstantPlacementPoint
import com.google.ar.core.Plane
import com.google.ar.core.TrackingFailureReason
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.getUpdatedPlanes
import io.github.sceneview.ar.arcore.position
import io.github.sceneview.ar.arcore.viewTransform
import io.github.sceneview.ar.getDescription
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity(R.layout.activity_main), UIEducacionalPermissao.NoticeDialogListener {

    lateinit var sceneView: ARSceneView
    lateinit var placeButton: ExtendedFloatingActionButton
    lateinit var clearButton: ExtendedFloatingActionButton

    lateinit var modelNode: ModelNode
    lateinit var loadingView: View
    lateinit var instructionText: TextView

    var modelCounter = 0
    var place = false
    var modelString = "file:///android_asset/Ferret.glb"
    var modelArray = arrayOf("file:///android_asset/Ferret.glb", "file:///android_asset/Fox.glb", "file:///android_asset/MinecraftAxolotl.glb")


    val smdCoords = arrayOf(-3.7489343191276374, -38.579288490805006)
    var posAtual: Location? = null

    lateinit var requestPermissionLauncher:androidx.activity.result.ActivityResultLauncher<String>
    lateinit var requestLocalPermissionLauncher:androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    var isLoading = false
        set(value) {
            field = value
            loadingView.isGone = !value
        }

    var anchorNode: AnchorNode? = null
        set(value) {
            if (field != value) {
                field = value
                updateInstructions()
            }
        }

    var trackingFailureReason: TrackingFailureReason? = null
        set(value) {
            if (field != value) {
                field = value
                updateInstructions()
            }
        }

    fun updateInstructions() {
        instructionText.text = trackingFailureReason?.let {
            it.getDescription(this)
        } ?: if (anchorNode == null) {
            getString(R.string.point_your_phone_down)
        } else {
            null
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        Log.v("PDM", filesDir.toString())

        sceneView = findViewById(R.id.sceneView)
        placeButton = findViewById(R.id.place)
        clearButton = findViewById(R.id.clear)

        clearButton.setOnClickListener(View.OnClickListener{clearAnchors()})

        instructionText = findViewById(R.id.instructionText)
        loadingView = findViewById(R.id.loadingView)

        sceneView = findViewById<ARSceneView?>(R.id.sceneView).apply {
            lifecycle = this@MainActivity.lifecycle
            planeRenderer.isEnabled = true
            configureSession { session, config ->
                config.depthMode = when (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    true -> Config.DepthMode.AUTOMATIC
                    else -> Config.DepthMode.DISABLED
                }
                config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            }
            onSessionUpdated = { _, frame ->
                if (anchorNode == null) {
                    frame.getUpdatedPlanes()
                        .firstOrNull { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
                        ?.let { plane ->
                            if (place) { // Apertou o botão de Place
                                addAnchorNode(plane.createAnchor(plane.centerPose), modelString)
                                place = false
                            }
                        }


                }
                //Log.v("PDM", "Automatico: " + frame.toString())
            }
            onTrackingFailureChanged = { reason ->
                this@MainActivity.trackingFailureReason = reason
            }
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        requestLocalPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                //Localização
                Log.v("PDM","Acesso à localização concedido")
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location : Location? ->
                        Log.v("PDM",location.toString())
                        if (location != null) {
                            posAtual = location
                        }
                    }
            } else {
                Log.v("PDM", "Sem permissão")
                Snackbar.make(
                    findViewById(R.id.mainLayout),
                    R.string.semPerLoca,
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }

        getLocal()



    }
    fun addAnchorNode(anchor: Anchor, fileModel: String) {
        sceneView.addChildNode(
            AnchorNode(sceneView.engine, anchor)
                .apply {
                    isEditable = true
                    lifecycleScope.launch {
                        isLoading = true
                        sceneView.modelLoader.loadModelInstance(
                            fileModel
                        )?.let { modelInstance ->
                            addChildNode(
                                ModelNode(
                                    modelInstance = modelInstance,
                                    // Scale to fit in a 0.5 meters cube
                                    scaleToUnits = 0.5f,
                                    // Bottom origin instead of center so the model base is on floor
                                    centerOrigin = Position(y = -0.5f)
                                ).apply {
                                    isEditable = true
                                }
                            )
                        }
                        isLoading = false
                    }
                    anchorNode = this
                }
        )
    }

    fun chooseModel(): String{
        if (modelCounter == 0){
            modelString = "file:///android_asset/Ferret.glb"
            return "file:///android_asset/Ferret.glb"
        }else if(modelCounter == 1){
            modelString = "file:///android_asset/Fox.glb"
            return "file:///android_asset/Fox.glb"
        }else if(modelCounter == 2){
            modelString = "https://sceneview.github.io/assets/models/DamagedHelmet.glb"
            return "https://sceneview.github.io/assets/models/DamagedHelmet.glb"
        }else{
            return ""
        }
    }

    fun changeModel(v: View){
        modelCounter = nextModel()
    }

    fun nextModel(): Int {
        return (modelCounter + 1) % modelArray.size
    }

    fun clearAnchors(){
        sceneView.clearChildNodes()
        anchorNode = null
        place = false

    }

    fun placeButt(v: View){
        getLocal()
        if (posAtual != null){
            var distance = calculateDistance(smdCoords[0], smdCoords[1], posAtual!!.latitude, posAtual!!.longitude)
            //var distance = 50
            Log.v("PDM", "Distância: " + distance)
            if (distance < 100){
                modelString = modelArray[modelCounter]//"file:///android_asset/Ferret.glb"
            }else{
                modelString = modelArray[nextModel()]
            }
            Log.v("PDM", nextModel().toString())

        }else{
            Log.v("PDM", "posAtual nulo: " + posAtual)
        }

        place = true
        var worldPosition = anchorNode?.worldPosition
        val screenPoint = sceneView.session
        //Log.v("PDM","Button: " + sceneView.frame.toString())
        val frame = sceneView.session?.update()
        val cameraPosition = frame?.camera?.pose?.position

        if (anchorNode != null && cameraPosition != null){
            anchorNode?.worldPosition = cameraPosition

        }
    }


    fun getLocal(){
        when{
            //Primeiro Caso do When - A permissão já foi concedida
            ContextCompat.checkSelfPermission(
                this.applicationContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ->{
                Log.v("PDM","Tem permissão de localização")
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location : Location? ->
                        if(location!=null){
                            Log.v("PDM","Lat: "+location?.latitude + " Lon: " + location?.longitude)
                            posAtual = location
                        }
                    }
            }
            //Permissão foi negada, mas não para sempre
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) ->{
                // Chamar a UI Educacional
                val mensagem =
                    "Nossa aplicação precisa acessar  a localização"
                val titulo = "Permissão de acesso a localização"
                val codigo = 2//Código da requisição
                val mensagemPermissao = UIEducacionalPermissao(mensagem, titulo, codigo)
                mensagemPermissao.onAttach(this as Context)
                mensagemPermissao.show(supportFragmentManager, "primeiravez")
            }
            // Permissão negada ou não foi pedida
            else ->{
                requestLocalPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    }

    override fun onDialogPositiveClick(codigo: Int) {
        //Método chamado pela caixa de diálogo
        Log.v("PDM","Apertou OK")
        if(codigo==1){
            requestLocalPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    fun calculateDistance(lat1: Double, long1: Double, lat2: Double, long2: Double): Double {
        val R = 6371 // Radius of the Earth in kilometers
        val latDelta = Math.toRadians(lat2 - lat1)
        val longDelta = Math.toRadians(long2 - long1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)

        val a = Math.sin(latDelta / 2) * Math.sin(latDelta / 2) +
                Math.sin(longDelta / 2) * Math.sin(longDelta / 2) * Math.cos(lat1Rad) * Math.cos(lat2Rad)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return (R * c)*1000
    }



}