package com.dave_cli.proxybox.ui.server

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dave_cli.proxybox.databinding.ActivityLocalServerBinding
import com.dave_cli.proxybox.server.LocalConfigServer
import com.dave_cli.proxybox.server.QrGenerator

class LocalServerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLocalServerBinding
    private var server: LocalConfigServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocalServerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Config via Phone"

        startServer()

        binding.btnToggleServer.setOnClickListener {
            if (server != null) {
                stopServer()
            } else {
                startServer()
            }
        }

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun startServer() {
        if (server != null) return
        try {
            server = LocalConfigServer(applicationContext)
            server!!.start()
            val url = server!!.getServerUrl()
            binding.tvServerUrl.text = url
            binding.tvInstructions.text =
                "1. Connect your phone to the same WiFi\n" +
                "2. Scan the QR code below with your phone camera\n" +
                "3. In the browser, paste your proxy config and tap Add"
            val qr = QrGenerator.generate(url)
            binding.ivQrCode.setImageBitmap(qr)
            binding.btnToggleServer.text = "Stop Server"
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start server: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopServer() {
        server?.stop()
        server = null
        binding.tvServerUrl.text = "Server stopped"
        binding.ivQrCode.setImageDrawable(null)
        binding.btnToggleServer.text = "Start Server"
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
