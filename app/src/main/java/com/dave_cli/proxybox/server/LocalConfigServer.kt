package com.dave_cli.proxybox.server

import android.content.Context
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import com.dave_cli.proxybox.data.db.AppDatabase
import com.dave_cli.proxybox.data.repository.ProfileRepository
import com.dave_cli.proxybox.import_config.ConfigParser
import com.dave_cli.proxybox.import_config.QrDecoder
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class LocalConfigServer(
    private val context: Context,
    port: Int = PORT
) : NanoHTTPD(port) {

    companion object {
        const val PORT = 8765
        const val TAG = "LocalConfigServer"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val db = AppDatabase.getInstance(context)
    private val repo = ProfileRepository(db)

    override fun serve(session: IHTTPSession): Response {
        return when {
            session.method == Method.GET && session.uri == "/" -> serveMainPage()
            session.method == Method.POST && session.uri == "/import" -> handleImport(session)
            session.method == Method.POST && session.uri == "/subscribe" -> handleSubscription(session)
            session.method == Method.POST && session.uri == "/import-rule" -> handleImportRule(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun serveMainPage(): Response {
        val html = """
<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>ProxyBox — Add Config</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
         background: #0f0f1a; color: #e0e0ff; min-height: 100vh;
         display: flex; flex-direction: column; align-items: center;
         justify-content: flex-start; padding: 24px 16px; }
  h1 { font-size: 1.6rem; font-weight: 700; color: #7c6fff;
       margin-bottom: 4px; text-align: center; }
  .subtitle { color: #888; font-size: 0.9rem; margin-bottom: 24px; text-align: center; }
  .card { background: #1a1a2e; border: 1px solid #2a2a4a; border-radius: 16px;
          padding: 24px; width: 100%; max-width: 500px; margin-bottom: 20px; }
  h2 { font-size: 1rem; color: #aaa; margin-bottom: 12px; font-weight: 600;
       text-transform: uppercase; letter-spacing: 1px; }
  textarea, input[type=text] {
    width: 100%; background: #0f0f1a; border: 1px solid #333;
    border-radius: 10px; color: #e0e0ff; font-size: 0.9rem; padding: 12px;
    resize: vertical; outline: none; transition: border-color 0.2s;
  }
  textarea:focus, input[type=text]:focus { border-color: #7c6fff; }
  textarea { min-height: 120px; font-family: monospace; }
  button {
    width: 100%; background: linear-gradient(135deg, #7c6fff, #a78bfa);
    color: white; border: none; border-radius: 10px; padding: 14px;
    font-size: 1rem; font-weight: 600; cursor: pointer; margin-top: 12px;
    transition: opacity 0.2s;
  }
  button:hover { opacity: 0.85; }
  .divider { height: 1px; background: #2a2a4a; margin: 16px 0; }
  label { color: #aaa; font-size: 0.85rem; display: block; margin-bottom: 6px; }
  .file-btn { background: #2a2a4a; font-size: 0.9rem; }
  .success { color: #4ade80; font-size: 0.9rem; margin-top: 10px; display: none; }
  .error { color: #f87171; font-size: 0.9rem; margin-top: 10px; display: none; }
</style>
</head>
<body>
<h1>📦 ProxyBox</h1>
<p class="subtitle">Add proxy config from your phone</p>

<div class="card">
  <h2>Paste URL or JSON</h2>
  <form id="textForm">
    <textarea id="configText" placeholder="vless://... or vmess://... or full JSON config"></textarea>
    <button type="submit">➕ Add Config</button>
    <div class="success" id="textSuccess">✅ Config added!</div>
    <div class="error" id="textError">❌ Invalid config</div>
  </form>
</div>

<div class="card">
  <h2>Upload QR Code Image</h2>
  <form id="qrForm" enctype="multipart/form-data">
    <label>Select QR code image from camera roll or screenshots</label>
    <input type="file" name="qr" accept="image/*" style="color:#e0e0ff; padding: 8px 0;">
    <button type="submit" class="file-btn">🔍 Decode & Add QR</button>
    <div class="success" id="qrSuccess">✅ QR decoded and added!</div>
    <div class="error" id="qrError">❌ Could not decode QR from image</div>
  </form>
</div>

<div class="card">
  <h2>Subscription URL</h2>
  <form id="subForm">
    <label>Subscription Name</label>
    <input type="text" id="subName" placeholder="My VPN Sub" style="margin-bottom: 10px;">
    <label>Subscription URL</label>
    <input type="text" id="subUrl" placeholder="https://example.com/sub?token=...">
    <button type="submit">📡 Add Subscription</button>
    <div class="success" id="subSuccess">✅ Subscription added!</div>
    <div class="error" id="subError">❌ Failed to add subscription</div>
  </form>
</div>

<div class="card">
  <h2>Routing Rules (v2rayN JSON)</h2>
  <form id="ruleForm">
    <label>Rule Set Name</label>
    <input type="text" id="ruleName" placeholder="My custom rules" style="margin-bottom: 10px;">
    <label>Paste v2rayN routing rules JSON array</label>
    <textarea id="ruleJson" placeholder='[{"type":"field","outboundTag":"proxy","domain":["domain:example.com"]}]'></textarea>
    <button type="submit" style="background: linear-gradient(135deg, #4ade80, #22c55e);">Import Rules</button>
    <div class="success" id="ruleSuccess"></div>
    <div class="error" id="ruleError"></div>
  </form>
</div>

<script>
document.getElementById('textForm').addEventListener('submit', async e => {
  e.preventDefault();
  const text = document.getElementById('configText').value.trim();
  if (!text) return;
  const fd = new FormData(); fd.append('text', text);
  const r = await fetch('/import', { method: 'POST', body: fd });
  const ok = r.ok;
  document.getElementById('textSuccess').style.display = ok ? 'block' : 'none';
  document.getElementById('textError').style.display = ok ? 'none' : 'block';
  if (ok) document.getElementById('configText').value = '';
});

document.getElementById('qrForm').addEventListener('submit', async e => {
  e.preventDefault();
  const fd = new FormData(e.target);
  const r = await fetch('/import', { method: 'POST', body: fd });
  const ok = r.ok;
  document.getElementById('qrSuccess').style.display = ok ? 'block' : 'none';
  document.getElementById('qrError').style.display = ok ? 'none' : 'block';
});

document.getElementById('subForm').addEventListener('submit', async e => {
  e.preventDefault();
  const name = document.getElementById('subName').value.trim();
  const url = document.getElementById('subUrl').value.trim();
  if (!url) return;
  const fd = new FormData();
  fd.append('name', name || 'Subscription');
  fd.append('url', url);
  const r = await fetch('/subscribe', { method: 'POST', body: fd });
  const ok = r.ok;
  const msg = await r.text();
  document.getElementById('subSuccess').style.display = ok ? 'block' : 'none';
  document.getElementById('subSuccess').textContent = ok ? msg : '';
  document.getElementById('subError').style.display = ok ? 'none' : 'block';
  document.getElementById('subError').textContent = ok ? '' : msg;
  if (ok) { document.getElementById('subName').value = ''; document.getElementById('subUrl').value = ''; }
});

document.getElementById('ruleForm').addEventListener('submit', async e => {
  e.preventDefault();
  const name = document.getElementById('ruleName').value.trim();
  const json = document.getElementById('ruleJson').value.trim();
  if (!json) return;
  const fd = new FormData();
  fd.append('name', name || 'Custom Rules');
  fd.append('json', json);
  const r = await fetch('/import-rule', { method: 'POST', body: fd });
  const msg = await r.text();
  document.getElementById('ruleSuccess').style.display = r.ok ? 'block' : 'none';
  document.getElementById('ruleSuccess').textContent = r.ok ? msg : '';
  document.getElementById('ruleError').style.display = r.ok ? 'none' : 'block';
  document.getElementById('ruleError').textContent = r.ok ? '' : msg;
  if (r.ok) { document.getElementById('ruleName').value = ''; document.getElementById('ruleJson').value = ''; }
});
</script>
</body>
</html>
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    private fun handleImport(session: IHTTPSession): Response {
        return try {
            val files = mutableMapOf<String, String>()
            session.parseBody(files)

            val params = session.parameters

            // Text import
            val text = params["text"]?.firstOrNull()
                ?: files["text"]
                ?: files["POST"]?.let { session.parameters["text"]?.firstOrNull() }

            if (!text.isNullOrBlank()) {
                var result = false
                val latch = java.util.concurrent.CountDownLatch(1)
                scope.launch {
                    result = repo.addProfileFromString(text)
                    latch.countDown()
                }
                latch.await(10, java.util.concurrent.TimeUnit.SECONDS)

                return if (result) {
                    newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
                } else {
                    newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Invalid config")
                }
            }

            // QR image import
            val tmpFile = files["qr"]
            if (tmpFile != null) {
                val bitmap = android.graphics.BitmapFactory.decodeFile(tmpFile)
                    ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Not an image")
                val qrText = QrDecoder.decode(bitmap)
                    ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No QR found in image")
                val profile = ConfigParser.parse(qrText)
                    ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Invalid QR content")
                scope.launch {
                    db.profileDao().insertOrReplace(profile)
                }
                return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
            }

            newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No data")
        } catch (e: Exception) {
            Log.e(TAG, "Import error", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Server error: ${e.message}")
        }
    }

    private fun handleSubscription(session: IHTTPSession): Response {
        return try {
            val files = mutableMapOf<String, String>()
            session.parseBody(files)
            val params = session.parameters

            val name = params["name"]?.firstOrNull() ?: "Subscription"
            val url = params["url"]?.firstOrNull()
                ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "URL is required")

            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Invalid URL format")
            }

            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            scope.launch {
                result = repo.addSubscription(name, url)
                latch.countDown()
            }
            latch.await(30, java.util.concurrent.TimeUnit.SECONDS)

            if (result) {
                newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Subscription added & profiles fetched")
            } else {
                newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Failed to fetch subscription")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Subscription error", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: ${e.message}")
        }
    }

    private fun handleImportRule(session: IHTTPSession): Response {
        return try {
            val files = mutableMapOf<String, String>()
            session.parseBody(files)
            val params = session.parameters

            val name = params["name"]?.firstOrNull() ?: "Custom Rules"
            val json = params["json"]?.firstOrNull()
                ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No JSON provided")

            var error: String? = null
            val latch = java.util.concurrent.CountDownLatch(1)
            scope.launch {
                error = repo.addRoutingRule(name, json)
                latch.countDown()
            }
            latch.await(10, java.util.concurrent.TimeUnit.SECONDS)

            if (error == null) {
                newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Rule set \"$name\" added!")
            } else {
                newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Validation failed: $error")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Import rule error", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: ${e.message}")
        }
    }

    fun getLocalIp(): String {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        return Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
    }

    fun getServerUrl(): String = "http://${getLocalIp()}:$PORT"
}
