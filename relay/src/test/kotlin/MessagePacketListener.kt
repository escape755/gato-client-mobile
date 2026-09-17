import com.gato.relay.GatoRelaySession
import com.gato.relay.listener.GatoRelayPacketListener
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import org.cloudburstmc.protocol.bedrock.packet.TextPacket

@Suppress("MemberVisibilityCanBePrivate")
class MessagePacketListener(
    val gatoRelaySession: GatoRelaySession
) : GatoRelayPacketListener {

    override fun beforeClientBound(packet: BedrockPacket): Boolean {
        if (packet is PlayerAuthInputPacket && packet.tick % 10 == 0L) {
            gatoRelaySession.clientBound(TextPacket().apply {
                type = TextPacket.Type.TIP
                isNeedsTranslation = false
                sourceName = ""
                message = "[GatoRelay] v1.0"
                xuid = ""
                filteredMessage = ""
            })
        }
        return false
    }

}