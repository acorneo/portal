package de.acorn42.portal.portal

import androidx.compose.runtime.*
import de.acorn42.portal.phone.PhonePuzzleScreen
import de.acorn42.portal.phone.rememberPhoneHost

/** Native discovery owns transport/lifecycle; the existing phone host owns motion. */
@Composable
fun NearbyPhoneRoom(game: NearbyPhoneGame) {
    val connection by game.state.collectAsState()
    val snapshot = connection.snapshot
    var prepared by remember { mutableStateOf<String?>(null) }
    var roomVariant by remember { mutableStateOf(0) }
    val host = rememberPhoneHost(connection.connected && connection.foreground &&
        snapshot?.phoneCanMove == true && prepared == snapshot.transferId)
    LaunchedEffect(snapshot?.transferId, snapshot?.phoneRoom, snapshot?.runId) {
        if (snapshot?.phase == TransferPhase.Phone && prepared != snapshot.transferId) {
            roomVariant = snapshot.phoneRoom
            host.loadRoom(snapshot.phoneRoom)
            host.restore((game.replica.phone ?: snapshot.phone).toState())
            prepared = snapshot.transferId
        }
    }
    LaunchedEffect(host) {
        snapshotFlow { host.state to prepared }.collect { (phone, transfer) ->
            if (transfer != null && transfer == game.replica.snapshot?.transferId)
                game.progress(PhoneCheckpoint.from(phone, roomVariant))
        }
    }
    PhonePuzzleScreen(host, "PHONE", connectionMessage = connection.message)
}
