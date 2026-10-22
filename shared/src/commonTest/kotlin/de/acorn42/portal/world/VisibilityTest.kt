package de.acorn42.portal.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class VisibilityTest {
    private fun openMaze() = Maze(20, 12, emptyList(), Vec2(2.5, 2.5))

    @Test
    fun cellsTransitionFromUnknownToVisibleToExploredAndBack() {
        val exploration = Exploration(openMaze(), radius = 2.0)
        val original = exploration.state
        assertEquals(Visibility.Visible, original.at(Cell(2, 2)))
        assertEquals(Visibility.Unexplored, original.at(Cell(10, 2)))
        exploration.update(Vec2(10.5, 2.5))
        assertEquals(Visibility.Explored, exploration.state.at(Cell(2, 2)))
        assertEquals(Visibility.Visible, exploration.state.at(Cell(10, 2)))
        assertEquals(Visibility.Unexplored, exploration.state.at(Cell(19, 11)))
        exploration.update(Vec2(2.5, 2.5))
        assertEquals(Visibility.Visible, exploration.state.at(Cell(2, 2)))
        assertEquals(Visibility.Explored, exploration.state.at(Cell(10, 2)))
        // Old snapshots remain valid even as the session moves on.
        assertEquals(Visibility.Unexplored, original.at(Cell(10, 2)))
    }

    @Test
    fun explorationIsMonotonicAndContainsEveryCurrentlyVisibleCell() {
        val exploration = Exploration(FirstMaze.world)
        var remembered = exploration.state.exploredCells
        for (y in 0 until FirstMaze.world.height) for (x in 0 until FirstMaze.world.width) {
            if (FirstMaze.layout[y][x] == '#') continue
            exploration.update(Vec2(x + 0.5, y + 0.5))
            assertTrue(exploration.state.exploredCells.containsAll(remembered))
            assertTrue(exploration.state.exploredCells.containsAll(exploration.state.visibleCells))
            remembered = exploration.state.exploredCells
        }
    }

    @Test
    fun radiusIsCircularAndExactForDynamicPoints() {
        val exploration = Exploration(openMaze(), Vec2(5.5, 5.5), radius = 2.0)
        val state = exploration.state
        assertEquals(Visibility.Visible, state.at(Cell(7, 5)))
        assertEquals(Visibility.Unexplored, state.at(Cell(8, 5)))
        assertEquals(Visibility.Unexplored, state.at(Cell(7, 7)))
        assertTrue(state.visibleCells.all { (it.center - state.observer).length <= state.radius })
        assertTrue(state.isVisible(Vec2(7.4, 5.5)))
        assertFalse(state.isVisible(Vec2(7.9, 5.5)), "A visible cell must not reveal entities beyond the radius")
        assertFalse(state.isVisible(Vec2(-0.1, 5.5)))
        assertFalse(state.isVisible(Vec2(Double.NaN, 5.5)))
    }

    @Test
    fun wallFrontIsVisibleButCorridorsBehindItAreConcealed() {
        val maze = Maze(12, 10, listOf(Wall(4.0, 0.0, 5.0, 10.0)), Vec2(2.5, 4.5))
        val exploration = Exploration(maze, radius = 8.0)
        assertEquals(Visibility.Visible, exploration.state.at(Cell(4, 4)))
        assertEquals(Visibility.Unexplored, exploration.state.at(Cell(5, 4)))
        assertEquals(Visibility.Unexplored, exploration.state.at(Cell(7, 6)))
        assertFalse(exploration.state.isVisible(Vec2(5.5, 4.5)))
        exploration.update(Vec2(7.5, 4.5))
        assertEquals(Visibility.Explored, exploration.state.at(Cell(2, 4)))
        assertFalse(exploration.state.isVisible(Vec2(2.5, 4.5)), "Memory must not reveal hidden dynamic entities")
    }

    @Test
    fun thickWallsDoNotRevealTheirFarCells() {
        val maze = Maze(12, 10, listOf(Wall(4.0, 0.0, 7.0, 10.0)), Vec2(2.5, 4.5))
        val state = Exploration(maze, radius = 8.0).state
        assertEquals(Visibility.Visible, state.at(Cell(4, 4)))
        assertEquals(Visibility.Unexplored, state.at(Cell(5, 4)))
        assertEquals(Visibility.Unexplored, state.at(Cell(6, 4)))
        assertEquals(Visibility.Unexplored, state.at(Cell(7, 4)))
    }

    @Test
    fun closedDiagonalCornersDoNotLeakSight() {
        val maze = Maze(8, 8, listOf(Wall(2.0, 1.0, 3.0, 2.0), Wall(1.0, 2.0, 2.0, 3.0)), Vec2(1.5, 1.5))
        val exploration = Exploration(maze)
        assertEquals(Visibility.Unexplored, exploration.state.at(Cell(2, 2)))
        assertFalse(exploration.state.isVisible(Vec2(2.5, 2.5)))
        exploration.update(Vec2(3.5, 3.5))
        assertEquals(Visibility.Visible, exploration.state.at(Cell(2, 2)))
    }

    @Test
    fun thinWallsOccludeEvenWithinAnOtherwiseVisibleCell() {
        val maze = Maze(10, 10, listOf(Wall(3.75, 3.0, 3.85, 4.0)), Vec2(2.5, 3.5))
        val state = Exploration(maze).state
        assertEquals(Visibility.Visible, state.at(Cell(3, 3)))
        assertTrue(state.isVisible(Vec2(3.6, 3.5)))
        assertFalse(state.isVisible(Vec2(3.95, 3.5)))
        assertEquals(Visibility.Unexplored, state.at(Cell(4, 3)))
    }

    @Test
    fun mapEdgesAndStationaryUpdatesStayStable() {
        val exploration = Exploration(FirstMaze.world)
        val state = exploration.state
        assertTrue(state.visibleCells.all { it.x in 0 until FirstMaze.world.width && it.y in 0 until FirstMaze.world.height })
        assertTrue(state.isVisible(FirstMaze.world.start))
        exploration.update(FirstMaze.world.start)
        assertSame(state, exploration.state)
        assertEquals(Visibility.Unexplored, state.at(Cell(-1, -1)))
    }

    @Test
    fun simulationMaintainsExplorationDuringMovementAndIdleSteps() {
        val simulation = WorldSimulation(openMaze())
        val initial = simulation.exploration.state.exploredCells
        val clock = FixedStepClock()
        repeat(180) { clock.advance(1.0 / 60) { simulation.step(MoveInput(1.0), it) } }
        assertEquals(simulation.sphere.position, simulation.exploration.state.observer)
        assertEquals(Visibility.Explored, simulation.exploration.state.at(Cell(2, 2)))
        assertTrue(simulation.exploration.state.exploredCells.containsAll(initial))
        assertTrue(Cell(6, 2) in simulation.exploration.state.exploredCells, "Intermediate movement must be remembered")
        val state = simulation.exploration.state
        repeat(10) { simulation.step(MoveInput(), 1.0 / 120) }
        assertSame(state, simulation.exploration.state)
    }

    @Test
    fun invalidVisibilityConfigurationIsRejected() {
        assertFailsWith<IllegalArgumentException> { Exploration(openMaze(), radius = 0.0) }
        assertFailsWith<IllegalArgumentException> { Exploration(openMaze(), radius = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { Exploration(openMaze(), Vec2(-1.0, 1.0)) }
    }
}
