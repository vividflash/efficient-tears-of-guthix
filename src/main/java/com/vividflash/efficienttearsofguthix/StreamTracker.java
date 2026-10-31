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

import java.util.Arrays;

// Colour and age of the stream on each wall. Fed with live spawn and despawn observations plus a
// per tick read of the walls; a stream whose spawn was not seen live has an unknown age. No client
// types, so it can be unit tested.
final class StreamTracker
{
    static final int STREAMS = 6;
    static final int DEFAULT_PERIOD = 16;
    static final String EXPECTED_ORDER = "GGGBBB";
    // The blue streams move first. Same rule, but a green stream can land on a dry wall right
    // after.
    static final String BLUE_FIRST_ORDER = "BBBGGG";

    private static final int MIN_LIFETIME = 4;
    private static final int MAX_LIFETIME = 100;
    private static final int WALLS = TogLayout.WALLS;

    private final int[] colour = new int[WALLS];
    private final int[] spawnTick = new int[WALLS];
    private final boolean[] known = new boolean[WALLS];

    private int period = DEFAULT_PERIOD;
    private int lastSpawnTick = Integer.MIN_VALUE;
    private int lastSpawnWall = -1;
    // The result of order() as of the last change of the state above.
    private String order;
    private boolean orderStale = true;

    void reset()
    {
        orderStale = true;
        Arrays.fill(colour, TargetRule.EMPTY);
        Arrays.fill(known, false);
        period = DEFAULT_PERIOD;
        lastSpawnTick = Integer.MIN_VALUE;
        lastSpawnWall = -1;
    }

    // A stream seen appearing live. Two in one tick cannot be ordered, so both lose their age.
    void spawn(int wall, int streamColour, int tick)
    {
        if (colour[wall] == streamColour)
        {
            return;
        }

        orderStale = true;
        colour[wall] = streamColour;
        spawnTick[wall] = tick;
        known[wall] = true;
        if (lastSpawnTick == tick)
        {
            known[wall] = false;
            if (lastSpawnWall >= 0 && lastSpawnWall != wall && spawnTick[lastSpawnWall] == tick)
            {
                known[lastSpawnWall] = false;
            }
        }
        lastSpawnTick = tick;
        lastSpawnWall = wall;
    }

    // A stream seen leaving live. A known age gives the stream lifetime.
    void despawn(int wall, int streamColour, int tick)
    {
        if (colour[wall] != streamColour)
        {
            return;
        }

        orderStale = true;
        if (known[wall])
        {
            int lifetime = tick - spawnTick[wall];
            if (lifetime >= MIN_LIFETIME && lifetime <= MAX_LIFETIME)
            {
                period = lifetime;
            }
        }
        colour[wall] = TargetRule.EMPTY;
        known[wall] = false;
    }

    // Safety net: what the walls show right now wins over the tracked state.
    void resync(int[] seen, int tick)
    {
        for (int wall = 0; wall < WALLS; wall++)
        {
            if (colour[wall] != seen[wall])
            {
                orderStale = true;
                colour[wall] = seen[wall];
                spawnTick[wall] = tick;
                known[wall] = false;
            }
        }
    }

    int colour(int wall)
    {
        return colour[wall];
    }

    int spawnTick(int wall)
    {
        return spawnTick[wall];
    }

    int period()
    {
        return period;
    }

    boolean isSynced()
    {
        int streams = 0;
        for (int wall = 0; wall < WALLS; wall++)
        {
            if (colour[wall] == TargetRule.EMPTY)
            {
                continue;
            }
            if (!known[wall])
            {
                return false;
            }
            streams++;
        }
        return streams == STREAMS;
    }

    // Stream colours in spawn order, starting after the largest pause between two consecutive
    // spawns. Null while not synced.
    String order()
    {
        if (orderStale)
        {
            order = computeOrder();
            orderStale = false;
        }
        return order;
    }

    private String computeOrder()
    {
        if (!isSynced())
        {
            return null;
        }

        int[] walls = new int[STREAMS];
        int count = 0;
        for (int wall = 0; wall < WALLS; wall++)
        {
            if (colour[wall] != TargetRule.EMPTY)
            {
                int j = count++;
                while (j > 0 && spawnTick[walls[j - 1]] > spawnTick[wall])
                {
                    walls[j] = walls[j - 1];
                    j--;
                }
                walls[j] = wall;
            }
        }

        int start = 0;
        int largest = spawnTick[walls[0]] + period - spawnTick[walls[STREAMS - 1]];
        for (int i = 1; i < STREAMS; i++)
        {
            int gap = spawnTick[walls[i]] - spawnTick[walls[i - 1]];
            if (gap > largest)
            {
                largest = gap;
                start = i;
            }
        }

        StringBuilder order = new StringBuilder(STREAMS);
        for (int i = 0; i < STREAMS; i++)
        {
            order.append(colour[walls[(start + i) % STREAMS]] == TargetRule.BLUE ? 'B' : 'G');
        }
        return order.toString();
    }
}
