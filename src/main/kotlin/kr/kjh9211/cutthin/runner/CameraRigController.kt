package kr.kjh9211.cutthin.runner

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.PacketContainer
import kr.kjh9211.cutthin.cutscene.CutsceneSession
import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player

/**
 * Binds a player's client-side camera (via the CAMERA packet) to an invisible ArmorStand rig
 * for the duration of a Move/LookAt step, so the client interpolates the rig's per-tick
 * position/rotation smoothly instead of hard-snapping the way direct player.teleport() does.
 * The player entity itself keeps teleporting alongside the rig — only the rendered viewpoint
 * changes — so chunk loading, other-player visibility, etc. are unaffected.
 *
 * Only constructed when ProtocolLib is present (see CutThin.onEnable); every call is a no-op
 * once the session has no active rig.
 */
class CameraRigController {

    /** Spawns the rig (if needed) and moves it to [at]. Call once per tick while active. */
    fun bind(session: CutsceneSession, player: Player, at: Location) {
        val existing = session.cameraRig
        if (existing != null && existing.isValid) {
            existing.teleport(at)
            return
        }

        val world = at.world ?: return
        val rig = world.spawn(at, ArmorStand::class.java) { stand ->
            stand.isVisible = false
            stand.isMarker = true
            stand.setGravity(false)
            stand.isInvulnerable = true
            stand.isPersistent = false
            stand.setAI(false)
            stand.isSilent = true
        }
        session.cameraRig = rig
        sendCameraPacket(player, rig.entityId)
    }

    /** Releases the camera back to the player and despawns the rig, if one is active. */
    fun release(session: CutsceneSession, player: Player?) {
        val rig = session.cameraRig ?: return
        session.cameraRig = null
        if (player != null && player.isOnline) {
            sendCameraPacket(player, player.entityId)
        }
        rig.remove()
    }

    private fun sendCameraPacket(player: Player, entityId: Int) {
        val packet = PacketContainer(PacketType.Play.Server.CAMERA)
        packet.integers.write(0, entityId)
        runCatching {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet, false)
        }
    }
}
