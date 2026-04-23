package com.dave_cli.proxybox.ui.server

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dave_cli.proxybox.databinding.ActivityLocalServerBinding
import com.dave_cli.proxybox.R
import com.dave_cli.proxybox.core.LocaleHelper
import com.dave_cli.proxybox.server.LocalConfigServer
import com.dave_cli.proxybox.server.QrGenerator

class LocalServerActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private lateinit var binding: ActivityLocalServerBinding
    private var server: LocalConfigServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocalServerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.config_via_phone)

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
            binding.tvInstructions.text = getString(R.string.server_instructions)
            val qr = QrGenerator.generate(url)
            binding.ivQrCode.setImageBitmap(qr)
            binding.btnToggleServer.text = getString(R.string.stop_server)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.server_start_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun stopServer() {
        server?.stop()
        server = null
        binding.tvServerUrl.text = getString(R.string.server_stopped)
        binding.ivQrCode.setImageDrawable(null)
        binding.btnToggleServer.text = getString(R.string.start_server)
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
