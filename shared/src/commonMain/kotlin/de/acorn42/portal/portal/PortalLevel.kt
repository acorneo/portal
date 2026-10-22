package de.acorn42.portal.portal

import de.acorn42.portal.world.*

/** One fixed portal, return point, and gate added to the existing handmade maze. */
object PortalLevel {
    val entrance = Vec2(4.5, 1.5)
    val exit = Vec2(5.5, 3.5)
    val gate = Wall(5.0, 2.0, 6.0, 3.0)
    val closedMaze = FirstMaze.world.let { Maze(it.width, it.height, it.walls + gate, it.start) }
    val openMaze = FirstMaze.world
    fun atEntrance(position: Vec2) = (position - entrance).length <= 0.45
}
