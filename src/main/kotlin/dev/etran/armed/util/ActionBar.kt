package dev.etran.armed.util

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.lang.reflect.Method

/**
 * Action bar across versions, via reflection:
 *  - 1.9 and newer: Spigot's ChatMessageType.ACTION_BAR through player.spigot()
 *  - 1.8: raw PacketPlayOutChat (type 2) through the version-specific NMS classes
 * If neither works the message is silently dropped; nothing else in the plugin depends on it.
 */
object ActionBar {
    private var initialised = false
    private var mode = 0 // 0 = unavailable, 1 = spigot/bungee API, 2 = 1.8 NMS packet

    // mode 1
    private var spigotMethod: Method? = null
    private var actionBarConstant: Any? = null
    private var fromLegacyText: Method? = null

    // mode 2
    private var getHandle: Method? = null
    private var connectionField: java.lang.reflect.Field? = null
    private var sendPacket: Method? = null
    private var serializer: Method? = null
    private var packetCtor: java.lang.reflect.Constructor<*>? = null

    private fun init() {
        initialised = true
        try {
            val typeClass = Class.forName("net.md_5.bungee.api.ChatMessageType")
            val baseArray = java.lang.reflect.Array.newInstance(Class.forName("net.md_5.bungee.api.chat.BaseComponent"), 0).javaClass
            spigotMethod = Class.forName("org.bukkit.entity.Player\$Spigot").getMethod("sendMessage", typeClass, baseArray)
            actionBarConstant = typeClass.getField("ACTION_BAR").get(null)
            fromLegacyText = Class.forName("net.md_5.bungee.api.chat.TextComponent").getMethod("fromLegacyText", String::class.java)
            mode = 1
            return
        } catch (_: Throwable) {
        }
        try {
            val ver = Bukkit.getServer().javaClass.`package`.name.substringAfterLast('.')
            val craftPlayer = Class.forName("org.bukkit.craftbukkit.$ver.entity.CraftPlayer")
            getHandle = craftPlayer.getMethod("getHandle")
            val entityPlayer = Class.forName("net.minecraft.server.$ver.EntityPlayer")
            connectionField = entityPlayer.getField("playerConnection")
            val packet = Class.forName("net.minecraft.server.$ver.Packet")
            sendPacket = Class.forName("net.minecraft.server.$ver.PlayerConnection").getMethod("sendPacket", packet)
            val chatBase = Class.forName("net.minecraft.server.$ver.IChatBaseComponent")
            serializer = Class.forName("net.minecraft.server.$ver.IChatBaseComponent\$ChatSerializer").getMethod("a", String::class.java)
            packetCtor = Class.forName("net.minecraft.server.$ver.PacketPlayOutChat").getConstructor(chatBase, Byte::class.javaPrimitiveType)
            mode = 2
        } catch (_: Throwable) {
            mode = 0
        }
    }

    fun send(player: Player, message: String) {
        if (!initialised) init()
        try {
            when (mode) {
                1 -> spigotMethod!!.invoke(player.spigot(), actionBarConstant, fromLegacyText!!.invoke(null, message))
                2 -> {
                    val json = "{\"text\":\"" + message.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}"
                    val component = serializer!!.invoke(null, json)
                    val packet = packetCtor!!.newInstance(component, 2.toByte())
                    val handle = getHandle!!.invoke(player)
                    sendPacket!!.invoke(connectionField!!.get(handle), packet)
                }
            }
        } catch (_: Throwable) {
            mode = 0
        }
    }
}
