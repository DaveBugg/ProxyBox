# ── Stack traces ─────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Gson (used for JSON serialization) ──────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# Keep data classes that Gson deserializes
-keep class com.dave_cli.proxybox.data.db.** { *; }
-keep class com.dave_cli.proxybox.core.UpdateResult { *; }
-keep class com.dave_cli.proxybox.ui.main.IpCheckResult { *; }

# ── Room ────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# ── Retrofit / OkHttp ──────────────────────────────────────
-keep class retrofit2.** { *; }
-keepattributes Exceptions
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ── libv2ray / Xray native ─────────────────────────────────
-keep class libv2ray.** { *; }
-keep class go.** { *; }
-keep class Libv2ray.** { *; }

# ── hev-socks5-tunnel (tun2socks JNI) ─────────────────────
-keep class hev.htproxy.** { *; }

# ── NanoHTTPD ───────────────────────────────────────────────
-keep class fi.iki.elonen.** { *; }

# ── ZXing ───────────────────────────────────────────────────
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }

# ── Widget (referenced from XML) ───────────────────────────
-keep class com.dave_cli.proxybox.widget.VpnWidgetProvider { *; }

# ── BroadcastReceivers ─────────────────────────────────────
-keep class com.dave_cli.proxybox.core.BootReceiver { *; }

# ── VPN Service ─────────────────────────────────────────────
-keep class com.dave_cli.proxybox.core.CoreService { *; }
