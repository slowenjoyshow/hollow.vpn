-keep class kotlinx.serialization.** { *; }
-keepclassmembers class app.glassvpn.model.** { *; }

# AmneziaWG вызывается через рефлексию (AwgBackend), без этих правил R8 вырежет классы из релизной сборки
-keep class org.amnezia.awg.** { *; }
-dontwarn org.amnezia.awg.**

# hev-socks5-tunnel: JNI-методы привязаны к имени класса
-keep class hev.htproxy.** { *; }
