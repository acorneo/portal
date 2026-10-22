package de.acorn42.portal

import androidx.compose.ui.window.ComposeUIViewController
import de.acorn42.portal.portal.NearbyPhoneGame
import de.acorn42.portal.portal.NearbyPhoneRoom

fun MainViewController(game: NearbyPhoneGame) = ComposeUIViewController { NearbyPhoneRoom(game) }
