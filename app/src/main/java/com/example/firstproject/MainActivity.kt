package com.example.firstproject

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Button
import com.google.zxing.integration.android.IntentIntegrator
import android.provider.MediaStore
import android.widget.Toast
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.getSystemService


class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_IMAGE_PICKER = 100
    }
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        val scanBtn = findViewById<Button>(R.id.scanBtn)
        scanBtn.setOnClickListener{
            startQRScanner()
        }
        val selectBtn = findViewById<Button>(R.id.selectBtn)
        selectBtn.setOnClickListener {
            startSelectImg()
        }

        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val selectedImageUri = result.data?.data
                selectedImageUri?.let {
                    decodeQRCodeAndConnectWiFi(selectedImageUri)
                }
            }
        }

    }

    private fun startSelectImg(){
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
//        startActivityForResult(intent, REQUEST_IMAGE_PICKER)
        imagePickerLauncher.launch(intent)

    }

    private fun decodeQRCodeAndConnectWiFi(imageURI: Uri){
        val wifiInfo = decodeQRCode(imageURI)
        if (wifiInfo != null){
            connectToWifi(wifiInfo.ssid, wifiInfo.password)
        }
        else{
            Toast.makeText(applicationContext, "Null wifiInfo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToWifi(ssid: String, password: String){
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val wifiNetworkSpecifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(password)
            .build()


        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(wifiNetworkSpecifier)
            .build()
        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCallback = object :ConnectivityManager.NetworkCallback(){
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Toast.makeText(applicationContext, "Connect success", Toast.LENGTH_SHORT).show()
            }

            override fun onUnavailable() {
                super.onUnavailable()
                Toast.makeText(applicationContext, "Connect not success", Toast.LENGTH_SHORT).show()
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                Toast.makeText(applicationContext, "Lost connection", Toast.LENGTH_SHORT).show()
            }
        }
        connectivityManager.requestNetwork(networkRequest, networkCallback)



    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
//        if (requestCode == REQUEST_IMAGE_PICKER && resultCode == Activity.RESULT_OK && data != null) {
//
//
//            //
//        }
//        else {
            val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
            if (result != null) {
                if (result.contents == null) {
                    Toast.makeText(applicationContext, "Not found", Toast.LENGTH_SHORT).show()
                } else {
                    val scannedText = result.contents
                    Toast.makeText(applicationContext, scannedText, Toast.LENGTH_SHORT).show()
                    val wifiInfo = parseWiFiInfoFromQRCode(scannedText) // Hàm parseWiFiInfoFromQRCode() cần được triển khai để trích xuất thông tin SSID và mật khẩu

                    if (wifiInfo != null) {
                        connectToWifi(wifiInfo.ssid, wifiInfo.password)
                    } else {
                        Toast.makeText(this, "Invalid WiFi information in QR code", Toast.LENGTH_SHORT).show()

                }
            }

       }
    }

    private fun decodeQRCode(imageURI: Uri): WiFiInfo?{
        try {
            // Đọc hình ảnh từ URI
            val inputStream = contentResolver.openInputStream(imageURI)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                // Tiếp tục xử lý như trước để giải mã mã QR code từ bitmap
                val intArray = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val source = RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

                val reader = MultiFormatReader()
                val result = reader.decode(binaryBitmap)

                val qrCodeValue = result.text
                Toast.makeText(this, qrCodeValue, Toast.LENGTH_LONG).show()
                return parseWiFiInfoFromQRCode(qrCodeValue)

            } else {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                return null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            return null
        }
    }

    data class WiFiInfo(val ssid: String, val password: String)

    private fun startQRScanner(){
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        IntentIntegrator(this).initiateScan()
    }

    private fun parseWiFiInfoFromQRCode(qrCodeContents: String): WiFiInfo? {
        val ssidRegex = """WIFI:([\w-]+)""".toRegex()
        val passwordRegex = """P:([\w-]+)""".toRegex()
        val ssidMatch = ssidRegex.find(qrCodeContents)
        val passwordMatch = passwordRegex.find(qrCodeContents)

        if (ssidMatch != null && passwordMatch != null) {
            val ssid = ssidMatch.groupValues[1]
            val password = passwordMatch.groupValues[1]
            Toast.makeText(this, ssid, Toast.LENGTH_SHORT).show()
            Toast.makeText(this, password, Toast.LENGTH_SHORT).show()
            return WiFiInfo(ssid, password)
        }
        return null
    }


}