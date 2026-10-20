/*
 * Copyright (c) 2026, vividflash
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.vividflash.efficienttearsofguthix;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Fixed geometry of the Tears of Guthix cave floor. No client types, so it can be unit tested. */
final class TogLayout
{
    static final int WALLS = 9;
    static final int PLANE = 2;
    /** Region id of the Tears of Guthix cave. */
    static final int REGION = 12948;

    private static final int[] WALL_X = {3257, 3258, 3259, 3261, 3261, 3261, 3259, 3258, 3257};
    private static final int[] WALL_Y = {9520, 9520, 9520, 9518, 9517, 9516, 9514, 9514, 9514};
    private static final int[] COLLECT_X = {3257, 3258, 3259, 3260, 3260, 3260, 3259, 3258, 3257};
    private static final int[] COLLECT_Y = {9519, 9519, 9519, 9518, 9517, 9516, 9515, 9515, 9515};

    private static final int BASE_X = 3256;
    private static final int BASE_Y = 9515;
    /** The tile where the walk in from the entrance ends. */
    static final int ENTRY_X = 3257;
    static final int ENTRY_Y = 9517;
    private static final int SIZE = 5;
    private static final int UNREACHABLE = 99;

    private static final int[][] FLOOR_DISTANCE = buildFloorDistances();
    private static final int[] COLLECT_CELL = buildCollectCells();

    private TogLayout()
    {
    }

    static int wallX(int wall)
    {
        return WALL_X[wall];
    }

    static int wallY(int wall)
    {
        return WALL_Y[wall];
    }

    static int collectX(int wall)
    {
        return COLLECT_X[wall];
    }

    static int collectY(int wall)
    {
        return COLLECT_Y[wall];
    }

    /** The cross: a 3 wide column and a 3 high row, so the four corners of the 5x5 block are solid. */
    static boolean isFloor(int x, int y)
    {
        int dx = x - BASE_X;
        int dy = y - BASE_Y;
        if (dx < 0 || dx >= SIZE || dy < 0 || dy >= SIZE)
        {
            return false;
        }
        return (dx >= 1 && dx <= 3) || (dy >= 1 && dy <= 3);
    }

    /**
     * Walking ticks from this tile on the cross to each cell of the 5x5 block;
     * {@link #collectCell} gives a wall's index into it. Read only.
     */
    static int[] floorDistances(int x, int y)
    {
        return FLOOR_DISTANCE[cell(x, y)];
    }

    /** The cell of this wall's collect tile, for {@link #floorDistances}. */
    static int collectCell(int wall)
    {
        return COLLECT_CELL[wall];
    }

    /** Wall served by this collect tile, or -1. */
    static int wallAtCollectTile(int x, int y)
    {
        for (int wall = 0; wall < WALLS; wall++)
        {
            if (COLLECT_X[wall] == x && COLLECT_Y[wall] == y)
            {
                return wall;
            }
        }
        return -1;
    }

    /** Wall standing on this tile, or -1. */
    static int wallOnTile(int x, int y)
    {
        for (int wall = 0; wall < WALLS; wall++)
        {
            if (WALL_X[wall] == x && WALL_Y[wall] == y)
            {
                return wall;
            }
        }
        return -1;
    }

    private static int cell(int x, int y)
    {
        return (x - BASE_X) + SIZE * (y - BASE_Y);
    }

    private static int[] buildCollectCells()
    {
        int[] cells = new int[WALLS];
        for (int wall = 0; wall < WALLS; wall++)
        {
            cells[wall] = cell(COLLECT_X[wall], COLLECT_Y[wall]);
        }
        return cells;
    }

    private static int[][] buildFloorDistances()
    {
        int cells = SIZE * SIZE;
        int[][] distances = new int[cells][cells];
        ArrayDeque<Integer> open = new ArrayDeque<>();

        for (int start = 0; start < cells; start++)
        {
            int[] row = distances[start];
            Arrays.fill(row, UNREACHABLE);
            int startX = BASE_X + start % SIZE;
            int startY = BASE_Y + start / SIZE;
            if (!isFloor(startX, startY))
            {
                continue;
            }

            row[start] = 0;
            open.clear();
            open.add(start);
            while (!open.isEmpty())
            {
                int current = open.poll();
                int x = BASE_X + current % SIZE;
                int y = BASE_Y + current / SIZE;
                for (int dx = -1; dx <= 1; dx++)
                {
                    for (int dy = -1; dy <= 1; dy++)
                    {
                        if ((dx == 0 && dy == 0) || !isFloor(x + dx, y + dy))
                        {
                            continue;
                        }
                        if (dx != 0 && dy != 0 && (!isFloor(x + dx, y) || !isFloor(x, y + dy)))
                        {
                            continue;
                        }

                        int next = cell(x + dx, y + dy);
                        if (row[next] == UNREACHABLE)
                        {
                            row[next] = row[current] + 1;
                            open.add(next);
                        }
                    }
                }
            }
        }
        return distances;
    }
}
