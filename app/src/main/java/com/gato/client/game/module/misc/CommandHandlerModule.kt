package com.gato.client.game.module.misc

import com.gato.client.game.InterceptablePacket
import com.gato.client.game.Module
import com.gato.client.game.FriendManager
import com.gato.client.game.ModuleCategory
import com.gato.client.game.ModuleManager
import org.cloudburstmc.protocol.bedrock.packet.TextPacket

class CommandHandlerModule : Module("command_handler", ModuleCategory.Misc, true, true) {
    private val prefix = "."

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled) return

        val packet = interceptablePacket.packet
        if (packet is TextPacket && packet.type == TextPacket.Type.CHAT) {
            val message = packet.message
            if (!message.startsWith(prefix)) return

            interceptablePacket.intercept()

            val args = message.substring(prefix.length).split(" ")
            val command = args[0].lowercase()

            when (command) {
                "help" -> {
                    displayHelp(args.getOrNull(1))
                }
                "goto" -> {
                    val baritoneModule = ModuleManager.modules.find { it is BaritoneModule } as? BaritoneModule
                    if (baritoneModule == null) {
                        session.displayClientMessage("§cBaritone module not found")
                        return
                    }
                    baritoneModule.handleGotoCommand(message)
                }
                "replay" -> {
                    val replayModule = ModuleManager.modules.find { it is ReplayModule } as? ReplayModule
                    if (replayModule == null) {
                        session.displayClientMessage("§cReplay module not found")
                        return
                    }

                    when (args.getOrNull(1)?.lowercase()) {
                        "record" -> replayModule.startRecording()
                        "play" -> replayModule.startPlayback()
                        "stop" -> {
                            replayModule.stopRecording()
                            replayModule.stopPlayback()
                        }
                        "save" -> {
                            val name = args.getOrNull(2)
                            if (name == null) {
                                session.displayClientMessage("§cUsage: .replay save <name>")
                                return
                            }
                            replayModule.saveReplay(name)
                        }
                        "load" -> {
                            val name = args.getOrNull(2)
                            if (name == null) {
                                session.displayClientMessage("§cUsage: .replay load <name>")
                                return
                            }
                            replayModule.loadReplay(name)
                        }
                        else -> {
                            session.displayClientMessage("""
                                §l§b[Replay] §r§7Commands:
                                §f.replay record §7- Start recording
                                §f.replay play §7- Play last recording  
                                §f.replay stop §7- Stop recording/playback
                                §f.replay save <name> §7- Save recording
                                §f.replay load <name> §7- Load recording
                            """.trimIndent())
                        }
                    }
                }
                "friend" -> {
                    when (args.getOrNull(1)?.lowercase()) {
                        "add" -> {
                            val name = args.getOrNull(2)
                            if (name == null) {
                                session.displayClientMessage("§cUsage: .friend add <name>")
                                return
                            }
                            if (FriendManager.add(name)) {
                                session.displayClientMessage("§l§b[Gato Client Mobile] §r§aAdded friend: §f$name")
                            } else {
                                session.displayClientMessage("§l§b[Gato Client Mobile] §r§c$name is already a friend")
                            }
                        }
                        "remove" -> {
                            val name = args.getOrNull(2)
                            if (name == null) {
                                session.displayClientMessage("§cUsage: .friend remove <name>")
                                return
                            }
                            if (FriendManager.remove(name)) {
                                session.displayClientMessage("§l§b[Gato Client Mobile] §r§aRemoved friend: §f$name")
                            } else {
                                session.displayClientMessage("§l§b[Gato Client Mobile] §r§c$name is not a friend")
                            }
                        }
                        "list" -> {
                            if (FriendManager.friends.isEmpty()) {
                                session.displayClientMessage("§l§b[Gato Client Mobile] §r§7No friends added")
                            } else {
                                session.displayClientMessage("§l§b[Gato Client Mobile] §r§7Friends: §f${FriendManager.friends.joinToString("§7, §f")}")
                            }
                        }
                        else -> {
                            session.displayClientMessage("""
                                §l§b[Friends] §r§7Commands:
                                §f.friend add <name> §7- Add a friend
                                §f.friend remove <name> §7- Remove a friend
                                §f.friend list §7- List friends
                            """.trimIndent())
                        }
                    }
                }
                else -> {
                    val module = ModuleManager.modules.find { it.name.equals(command, ignoreCase = true) }
                    if (module != null && !module.private) {
                        module.isEnabled = !module.isEnabled
                    } else {
                        session.displayClientMessage("§l§b[Gato Client Mobile] §r§cModule not found: §f$command")
                    }
                }
            }
        }
    }

    private fun displayHelp(category: String?) {
        val header = """
            §l§b[Gato Client Mobile] §r§7Module List
            §7Commands:
            §f.help <category> §7- View modules in a category
            §f.<module> §7- Toggle a module
            §f.help §7- Show all categories
            §r§7
        """.trimIndent()

        session.displayClientMessage(header)

        if (category != null) {
            try {
                val moduleCategory = ModuleCategory.valueOf(category.uppercase())
                displayCategoryModules(moduleCategory)
            } catch (e: IllegalArgumentException) {
                session.displayClientMessage("§cInvalid category: $category")
                session.displayClientMessage("§7Available categories: ${ModuleCategory.entries.joinToString("§f, §7") { it.name.lowercase() }}")
            }
            return
        }

        ModuleCategory.entries.forEach { cat ->
            displayCategoryModules(cat)
        }
    }

    private fun displayCategoryModules(category: ModuleCategory) {
        val modules = ModuleManager.modules
            .filterNot { it.private }
            .filter { it.category == category }

        if (modules.isEmpty()) return

        session.displayClientMessage("§l§b§m--------------------")
        session.displayClientMessage("§l§b${category.name} Modules:")
        session.displayClientMessage("§r§7")

        modules.chunked(3).forEach { row ->
            val formattedRow = row.joinToString("   ") { module ->
                val status = if (module.isEnabled) "§a✔️" else "§c✘"
                "$status §f${module.name}"
            }
            session.displayClientMessage(formattedRow)
        }

        session.displayClientMessage("§r§7")
    }
}