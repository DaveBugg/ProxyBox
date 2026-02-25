package com.dave_cli.proxybox.ui.add

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dave_cli.proxybox.data.db.AppDatabase
import com.dave_cli.proxybox.data.repository.ProfileRepository
import com.dave_cli.proxybox.databinding.ActivityAddProfileBinding
import com.dave_cli.proxybox.import_config.ConfigParser
import com.dave_cli.proxybox.import_config.QrDecoder
import com.dave_cli.proxybox.ui.main.MainViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AddProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddProfileBinding
    private val viewModel: MainViewModel by viewModels()

    private val qrCameraLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { qrText -> importString(qrText) }
    }

    private val qrImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { decodeQrFromImageUri(it) }
    }

    private val cameraPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchQrCamera()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Add Profile"

        binding.btnImportText.setOnClickListener {
            val text = binding.etConfig.text.toString().trim()
            if (text.isNotEmpty()) importString(text)
            else Toast.makeText(this, "Paste a config URL or JSON", Toast.LENGTH_SHORT).show()
        }

        binding.btnScanQr.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
                launchQrCamera()
            } else {
                cameraPermLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        binding.btnPickQrImage.setOnClickListener {
            qrImageLauncher.launch("image/*")
        }

        binding.btnAddSubscription.setOnClickListener {
            val name = binding.etSubName.text.toString().trim()
            val url  = binding.etSubUrl.text.toString().trim()
            if (name.isEmpty() || url.isEmpty()) {
                Toast.makeText(this, "Enter name and URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.addSubscription(name, url) { ok ->
                runOnUiThread {
                    if (ok) {
                        Toast.makeText(this, "Subscription added", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, "Failed to fetch subscription", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun importString(text: String) {
        viewModel.addProfileFromString(text) { ok ->
            runOnUiThread {
                if (ok) {
                    Toast.makeText(this, "Config added!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Invalid config format", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun decodeQrFromImageUri(uri: Uri) {
        try {
            val stream = contentResolver.openInputStream(uri) ?: return
            val bitmap = BitmapFactory.decodeStream(stream)
            stream.close()
            val text = QrDecoder.decode(bitmap)
            if (text != null) importString(text)
            else Toast.makeText(this, "No QR code found in image", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error reading image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchQrCamera() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Scan proxy QR code")
            setBeepEnabled(false)
            setOrientationLocked(false)
        }
        qrCameraLauncher.launch(options)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
