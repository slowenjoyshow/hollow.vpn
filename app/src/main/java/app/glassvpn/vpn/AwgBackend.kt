package app.glassvpn.vpn

import android.content.Context
import java.io.BufferedReader
import java.io.InputStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/**
 * Обёртка над amneziawg-android (GoBackend). API библиотеки у форков немного различается,
 * поэтому вызовы идут через рефлексию: проект собирается при любой версии, а несовпадение
 * проявится понятным сообщением при подключении, а не ошибкой компиляции.
 */
class AwgBackend(context: Context) {
    private val pkg = "org.amnezia.awg"
    private val backendCls = load("$pkg.backend.GoBackend")
    private val tunnelCls = load("$pkg.backend.Tunnel")
    private val stateCls = load("$pkg.backend.Tunnel\$State")
    private val configCls = load("$pkg.config.Config")

    private val backend: Any = backendCls.constructors
        .sortedBy { it.parameterCount }
        .firstOrNull { it.parameterTypes.firstOrNull() == Context::class.java }
        ?.let { c -> c.newInstance(context.applicationContext, *arrayOfNulls<Any>(c.parameterCount - 1)) }
        ?: error("У GoBackend нет конструктора с Context (версия библиотеки не подходит)")

    private val tunnel: Any = Proxy.newProxyInstance(tunnelCls.classLoader, arrayOf(tunnelCls)) { proxy, m, args ->
        when (m.name) {
            "getName" -> "glassvpn"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "HollowTunnel"
            else -> when (m.returnType) {   // onStateChange и прочие колбэки; примитивам нельзя вернуть null
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                else -> null
            }
        }
    }

    private fun load(name: String): Class<*> = try {
        Class.forName(name)
    } catch (e: ClassNotFoundException) {
        throw IllegalStateException("Не найден класс $name: библиотека AmneziaWG не подключена или у неё другой пакет", e)
    }

    @Suppress("UNCHECKED_CAST")
    private fun state(name: String): Any = java.lang.Enum.valueOf(stateCls as Class<out Enum<*>>, name)

    private fun parse(conf: String): Any {
        val m = configCls.methods.firstOrNull { it.name == "parse" && it.parameterTypes.size == 1 }
            ?: error("У Config нет метода parse")
        val arg: Any = when (m.parameterTypes[0]) {
            BufferedReader::class.java -> conf.reader().buffered()
            InputStream::class.java -> conf.byteInputStream()
            else -> error("Неожиданная сигнатура Config.parse(${m.parameterTypes[0].name})")
        }
        return call { m.invoke(null, arg) }!!
    }

    private fun setState(s: String, config: Any?) {
        val m = backendCls.methods.firstOrNull { it.name == "setState" && it.parameterTypes.size == 3 }
            ?: error("У GoBackend нет setState(tunnel, state, config)")
        call { m.invoke(backend, tunnel, state(s), config) }
    }

    fun up(conf: String) = setState("UP", parse(conf))
    fun down() = setState("DOWN", null)

    private inline fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: InvocationTargetException) {
        throw (e.targetException ?: e).let { IllegalStateException(it.message ?: it.toString(), it) }
    }
}
