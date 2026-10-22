package de.acorn42.portal.world

/** Handmade tile map. Corridors are one tile wide; every floor tile is reachable. */
object FirstMaze {
    val layout: List<String> = listOf(
        "#####################",
        "#S....#.......#.....#",
        "#.###.#.#####.#.###.#",
        "#...#...#...#...#...#",
        "###.#####.#.#####.#.#",
        "#...#.....#.......#.#",
        "#.###.###########.#.#",
        "#.....#.....#.....#.#",
        "#.#####.###.#.#####.#",
        "#.......#...#.......#",
        "#.#######.#########.#",
        "#...................#",
        "#####################",
    )

    val world: Maze = run {
        require(layout.all { it.length == layout.first().length })
        val walls = buildList {
            layout.forEachIndexed { y, row ->
                row.forEachIndexed { x, tile ->
                    if (tile == '#') add(Wall(x.toDouble(), y.toDouble(), x + 1.0, y + 1.0))
                }
            }
        }
        val startRow = layout.indexOfFirst { 'S' in it }
        Maze(layout.first().length, layout.size, walls, Vec2(layout[startRow].indexOf('S') + 0.5, startRow + 0.5))
    }
}
