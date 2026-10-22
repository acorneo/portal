package de.acorn42.portal

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.runtime.remember
import de.acorn42.portal.world.FirstMaze
import de.acorn42.portal.world.WorldSimulation

fun MainViewController() = ComposeUIViewController {
    val simulation = remember { WorldSimulation(FirstMaze.world) }
    App(simulation.maze, simulation.sphere, simulation.exploration.state)
}
