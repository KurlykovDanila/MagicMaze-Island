package tfr.korge.jam.roguymaze.model.network

import com.soywiz.klogger.Logger
import com.soywiz.korinject.AsyncDependency
import com.soywiz.korinject.AsyncInjector
import com.soywiz.korio.async.launch
import com.soywiz.korio.net.ws.WebSocketClient
import com.soywiz.korio.net.ws.readString
import com.soywiz.korio.util.OS
import kotlinx.coroutines.CoroutineScope
import tfr.korge.jam.roguymaze.GameMechanics
import tfr.korge.jam.roguymaze.lib.EventBus
import tfr.korge.jam.roguymaze.model.World
import tfr.korge.jam.roguymaze.renderer.animation.TileAnimator

class NetworkBridge(val bus: EventBus,
        val scope: CoroutineScope,
        val world: World,
        val mechanics: GameMechanics,
        val tileAnimator: TileAnimator,
        var userName: String = "unknown") : AsyncDependency {

    var socket: WebSocketClient? = null

    val log = Logger<NetworkBridge>()

    init {
        bus.register<Update> { handleUpdate(it) }
    }

    suspend fun broadcastCommand(command: String, room: String, sender: String, message: String) {
        broadcast("@$room#$sender->/$command:$message")
    }

    suspend fun broadcast(messageData: String) {
        log.debug { messageData }
        socket?.send(messageData)
    }

    suspend fun handleUpdate(update: Update) {
        log.debug { "Handle update$update" }
        broadcastCommand(update.action, Room.Demo.name, userName, update.playerUpdate())
    }

    companion object {
        suspend operator fun invoke(injector: AsyncInjector): NetworkBridge {
            injector.mapSingleton {
                NetworkBridge(get(), get(), get(), get(), get())
            }
            return injector.get()
        }
    }

    suspend fun openSocket() {
        val port = "8080"
        val host = "localhost"
        val url = "ws://$host:$port/ws"
        val urlsTest = "ws://echo.websocket.org"
        log.info { "Creating WebSocketClient: $url" }
        socket = WebSocketClient(url, debug = false)
        socket?.let {
            configureSocket(it)
        }
    }

    suspend fun configureSocket(socket: WebSocketClient) {
        socket.onStringMessage.add {
            scope.launch {
                handleData(it)
            }
        }
        //socket.onAnyMessage.add { println("received any:" + it) }
        socket.onError.add { println("failed" + it) }

        socket.send("hello")
        socket.send("world")
        println("socket:" + socket.readString())
        println("socket:" + socket.readString())
        socket.onOpen.add {
            println("connected")
            scope.launch {
                socket.send("hallo 42")
            }

        }
    }

    suspend fun handleData(networkData: String) {
        log.debug { "handleData: $networkData" }

        if (networkData.startsWith("@")) {

            //"@$room#$sender->/$command:$message"
            val room = networkData.substringAfter("@").substringBefore("#").trim()
            val sender = networkData.substringAfter("#").substringBefore("->").trim()
            val action = networkData.substringAfter("->/").substringBefore(":").trim()
            val message = networkData.substringAfter(":")

            log.debug { "parsed:room=$room,sender=$sender,action=$action,message=$message" }

            if (sender == userName) {
                log.debug { "Ignoring own command $networkData" }
            } else if (action == Update.action) {
                executePlayerUpdate(message)
            } else {
                log.info { "Unknown action: $action" }
            }
        }


    }

    fun executePlayerUpdate(updateCommand: String) {
        val update = Update.readCommandMessage(updateCommand)
        log.debug { "Parsed Update: $update" }
        update.players.forEach { player ->
            /*world.getPlayer(player.number)?.let { playerPos ->
                val start = playerPos.position
                val target = Position(player.pos.x, player.pos.y)
                log.debug { "Execute Update: $start-$target" }
                mechanics.swapTiles(start, target)
                tileAnimator.animateSwap(start, target)
            }*/


        }
    }

    override suspend fun init() {
        if (OS.isJsBrowserOrWorker) {
            scope.launch {
                try {
                    openSocket()
                } catch (e: Exception) {
                    log.warn { "Failed creating web Socket" }
                }
            }
        } else {
            log.info { "No network support on Windows " }
        }

    }

}