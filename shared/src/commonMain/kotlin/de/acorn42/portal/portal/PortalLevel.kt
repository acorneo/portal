package de.acorn42.portal.portal

import de.acorn42.portal.world.*

/** Authored desktop chapter rooms and their separate phone-entry / chapter-exit markers. */
object PortalLevel {
    val entrance = Vec2(4.5, 1.5)
    val exit = Vec2(5.5, 3.5)
    val gate = Wall(5.0, 2.0, 6.0, 3.0)
    val closedMaze = FirstMaze.world.let { Maze(it.width, it.height, it.walls + gate, it.start) }
    val openMaze = FirstMaze.world
    fun atEntrance(position: Vec2) = (position - entrance).length <= 0.45

    data class Room(
        val id: Int,
        val title: String,
        val maze: Maze,
        val start: Vec2,
        val phonePortal: Vec2,
        val chapterExit: Vec2?,
    )

    private val secondMaze = Maze(15, 13, listOf(
        Wall(0.0, 0.0, 15.0, 0.5), Wall(0.0, 12.5, 15.0, 13.0),
        Wall(0.0, 0.5, 0.5, 12.5), Wall(14.5, 0.5, 15.0, 12.5),
        Wall(4.0, 0.5, 4.5, 8.5),
        Wall(8.0, 4.5, 8.5, 12.5),
        Wall(12.0, 0.5, 12.5, 8.5),
    ), Vec2(1.5, 2.5))
    private val rooms = listOf(
        Room(1, "The First Maze", closedMaze, closedMaze.start, entrance, exit),
        // The violet ring sits in the narrow south lane, reachable from the start
        // without passing through a wall. The second phone return completes the chapter.
        Room(2, "The Echo Maze", secondMaze, secondMaze.start, Vec2(13.0, 10.5), null),
    )
    fun room(id: Int): Room = rooms.single { it.id == id }
    val chapterSize: Int get() = rooms.size
    fun atPhonePortal(roomId: Int, position: Vec2, radius: Double = 0.45) =
        (position - room(roomId).phonePortal).length <= radius
    fun atChapterExit(roomId: Int, position: Vec2, radius: Double = 0.45) =
        room(roomId).chapterExit?.let { (position - it).length <= radius } == true
}
