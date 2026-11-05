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

// Records the streams of a session tick by tick and plays the target rule over them afterwards:
// per tick a step towards the clicked wall, a collected tick after TargetRule.START_DELAY on a
// blue wall counts 1, on a green wall -1 down to 0, and clicks follow the targets by the reaction in ticks.
// No client types, so it can be unit tested.
final class RuleReplay
{
    private static final int WALLS = TogLayout.WALLS;

    // First session tick, -1 while no session is recorded.
    private int start = -1;
    private int length;
    // Session ticks recorded so far.
    private int recorded;
    private int startX;
    private int startY;
    // Per session tick and wall, at u * WALLS + wall.
    private int[] colours = new int[0];
    private int[] untilMoves = new int[0];

    void reset()
    {
        start = -1;
        recorded = 0;
    }

    // A session of this many ticks starts on this tick with the player on this tile.
    void begin(int tick, int ticks, int x, int y)
    {
        start = tick;
        length = ticks;
        recorded = 0;
        startX = x;
        startY = y;
        if (colours.length < ticks * WALLS)
        {
            colours = new int[ticks * WALLS];
            untilMoves = new int[ticks * WALLS];
        }
    }

    // The walls on this tick; ticks outside the session are ignored. A stream of unknown age moves
    // in a full period.
    void record(int tick, StreamTracker tracker)
    {
        int u = tick - start;
        if (start < 0 || u < 0 || u >= length)
        {
            return;
        }
        int period = tracker.period();
        for (int wall = 0; wall < WALLS; wall++)
        {
            colours[u * WALLS + wall] = tracker.colour(wall);
            untilMoves[u * WALLS + wall] = Math.max(1,
                tracker.ageKnown(wall) ? tracker.spawnTick(wall) + period - tick : period);
        }
        recorded++;
    }

    // Every tick of the session was recorded.
    boolean complete()
    {
        return start >= 0 && recorded == length;
    }

    int length()
    {
        return length;
    }

    int recorded()
    {
        return recorded;
    }

    int startX()
    {
        return startX;
    }

    int startY()
    {
        return startY;
    }

    int replay(int reaction, boolean earlyLast)
    {
        return replay(colours, untilMoves, length, startX, startY, reaction, earlyLast);
    }

    // Replay tears (blue ticks minus green ticks collected, never below 0) over a session of this many ticks.
    // The walk starts from the given tile when it is on the floor, else from the entry tile.
    //
    // - colours, untilMoves: per session tick and wall, at u * WALLS + wall
    static int replay(int[] colours, int[] untilMoves, int length, int x, int y, int reaction, boolean earlyLast)
    {
        int px = TogLayout.startX(x, y);
        int py = TogLayout.startY(x, y);
        int arrival = Integer.MIN_VALUE / 2;
        int destWall = -1;
        int prevTarget = -1;
        int[] targets = new int[length];
        int tears = 0;
        int[] colour = new int[WALLS];
        int[] untilMove = new int[WALLS];
        int[] distance = new int[WALLS];
        boolean[] reachable = new boolean[WALLS];
        int[] collect = new int[WALLS];

        for (int u = 0; u < length; u++)
        {
            System.arraycopy(colours, u * WALLS, colour, 0, WALLS);
            System.arraycopy(untilMoves, u * WALLS, untilMove, 0, WALLS);

            int prevX = px;
            int prevY = py;
            if (destWall >= 0)
            {
                int tx = TogLayout.collectX(destWall);
                int ty = TogLayout.collectY(destWall);
                if (px != tx || py != ty)
                {
                    int[] next = step(px, py, tx, ty);
                    px = next[0];
                    py = next[1];
                }
            }
            boolean walking = px != prevX || py != prevY;
            if (walking)
            {
                arrival = u;
            }

            int here = TogLayout.wallAtCollectTile(px, py);
            if (here >= 0 && u >= arrival + TargetRule.START_DELAY)
            {
                if (colour[here] == TargetRule.BLUE)
                {
                    tears++;
                }
                // a green wall takes nothing from 0 tears
                else if (colour[here] == TargetRule.GREEN && tears > 0)
                {
                    tears--;
                }
            }

            int target = TargetRule.decide(colour, untilMove, px, py, prevTarget, walking, length - u,
                earlyLast, distance, reachable, collect).target;
            targets[u] = target;
            prevTarget = target;

            int clickFrom = u - reaction;
            if (clickFrom >= 0 && targets[clickFrom] >= 0)
            {
                destWall = targets[clickFrom];
            }
        }
        return tears;
    }

    // One step along a shortest floor path; first neighbour in scan order on ties.
    static int[] step(int x, int y, int tx, int ty)
    {
        int d = TogLayout.distance(x, y, tx, ty);
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dy = -1; dy <= 1; dy++)
            {
                if ((dx == 0 && dy == 0) || !TogLayout.isFloor(x + dx, y + dy))
                {
                    continue;
                }
                if (dx != 0 && dy != 0 && (!TogLayout.isFloor(x + dx, y) || !TogLayout.isFloor(x, y + dy)))
                {
                    continue;
                }
                if (TogLayout.distance(x + dx, y + dy, tx, ty) == d - 1)
                {
                    return new int[]{x + dx, y + dy};
                }
            }
        }
        throw new IllegalStateException("no step from " + x + "," + y + " to " + tx + "," + ty);
    }
}
