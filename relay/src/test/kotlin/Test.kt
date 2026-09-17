import com.gato.relay.address.GatoAddress
import com.gato.relay.definition.Definitions
import com.gato.relay.listener.AutoCodecPacketListener
import com.gato.relay.listener.GamingPacketHandler
import com.gato.relay.listener.TransferPacketListener
import com.gato.relay.listener.OnlineLoginPacketListener
import com.gato.relay.util.authorize
import com.gato.relay.util.captureGamePacket
import com.gato.relay.util.refresh
import net.raphimc.minecraftauth.MinecraftAuth

fun main() {
    val localAddress = GatoAddress("0.0.0.0", 19132)
    val remoteAddress = GatoAddress("ntest.easecation.net", 19132)

    Definitions.loadBlockPalette()

    var fullBedrockSession = authorize()
    if (fullBedrockSession.isExpired) {
        fullBedrockSession = fullBedrockSession.refresh()
    }

    captureGamePacket(
        localAddress = localAddress,
        remoteAddress = remoteAddress
    ) {
        listeners.add(AutoCodecPacketListener(this))
        listeners.add(OnlineLoginPacketListener(this, fullBedrockSession))
        listeners.add(GamingPacketHandler(this))
        listeners.add(TransferPacketListener(this))
        listeners.add(MessagePacketListener(this))
    }
    println("Relay started at ${localAddress.hostName}:${localAddress.port}")
}