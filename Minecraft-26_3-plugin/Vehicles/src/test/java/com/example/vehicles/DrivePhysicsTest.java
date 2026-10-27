package com.example.vehicles;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DrivePhysicsTest {
    private static final DrivePhysics.Settings SETTINGS = new DrivePhysics.Settings(.38, .14, .009, .025, .004, 2.8, .6);
    private static final DrivePhysics.Controls FORWARD = new DrivePhysics.Controls(true, false, false, false, false);
    private static final DrivePhysics.Controls REVERSE = new DrivePhysics.Controls(false, true, false, false, false);
    private static final DrivePhysics.Terrain FLAT = b -> b.minY() >= 0;

    private static DrivePhysics.State initial() { return new DrivePhysics.State(0, 0, 0, 0, 0, 0, 0, true); }
    private static DrivePhysics.State run(DrivePhysics.State state, DrivePhysics.Controls input, int ticks, DrivePhysics.Terrain terrain) {
        for (int i = 0; i < ticks; i++) state = DrivePhysics.tick(state, input, true, SETTINGS, terrain);
        return state;
    }

    @Test void accelerationAndReverseRespectIndependentSpeedLimits() {
        var fast = run(initial(), FORWARD, 150, FLAT);
        var reverse = run(initial(), REVERSE, 150, FLAT);
        assertEquals(.38, fast.speed(), 1e-8);
        assertEquals(-.14, reverse.speed(), 1e-8);
        assertTrue(fast.z() > 40);
        assertTrue(reverse.z() < -10);
    }

    @Test void brakeStopsTheCarWithoutReversingIt() {
        var fast = run(initial(), FORWARD, 70, FLAT);
        var stopped = run(fast, new DrivePhysics.Controls(true, false, false, false, true), 30, FLAT);
        assertEquals(0, stopped.speed());
        assertTrue(stopped.z() - fast.z() < 3);
    }

    @Test void maximumSpeedCannotTunnelThroughAThinWall() {
        DrivePhysics.Terrain wall = b -> b.minY() >= 0 && !(b.minZ() < 8.1 && b.maxZ() > 8 && b.minY() < 3);
        var stopped = run(initial(), FORWARD, 200, wall);
        assertTrue(stopped.z() + DrivePhysics.HALF_LENGTH <= 8.001, "front bumper must stay before the wall");
        assertEquals(0, stopped.speed());
    }

    @Test void climbsHalfSlabsButCannotClimbAFullBlock() {
        DrivePhysics.Terrain slab = b -> b.minY() >= 0 && !(b.minZ() < 10 && b.maxZ() > 4 && b.minY() < .5);
        var onSlab = run(initial(), FORWARD, 30, slab);
        assertTrue(onSlab.z() > 3);
        assertEquals(.5, onSlab.y(), .025);
        DrivePhysics.Terrain block = b -> b.minY() >= 0 && !(b.minZ() < 10 && b.maxZ() > 4 && b.minY() < 1);
        var stopped = run(initial(), FORWARD, 80, block);
        assertTrue(stopped.z() + DrivePhysics.HALF_LENGTH <= 4.001);
    }

    @Test void parkingAndCoastingSettleWithoutGroundDrift() {
        var state = run(initial(), FORWARD, 50, FLAT);
        for (int i = 0; i < 100; i++) state = DrivePhysics.tick(state, FORWARD, false, SETTINGS, FLAT);
        assertEquals(0, state.speed());
        assertEquals(0, state.y(), .025);
        assertTrue(state.grounded());
    }

    @Test void fallingCarLandsOnGroundInsteadOfPassingThroughIt() {
        var falling = new DrivePhysics.State(0, 12, 0, 0, 0, 0, 0, false);
        var landed = run(falling, DrivePhysics.Controls.NONE, 100, FLAT);
        assertEquals(0, landed.y(), .025);
        assertTrue(landed.grounded());
        assertEquals(0, landed.falling());
    }

    @Test void steeringFollowsTravelDirectionInReverse() {
        var right = new DrivePhysics.Controls(true, false, false, true, false);
        var reverseRight = new DrivePhysics.Controls(false, true, false, true, false);
        assertTrue(run(initial(), right, 30, FLAT).yaw() > 0);
        assertTrue(run(initial(), reverseRight, 30, FLAT).yaw() < 0);
    }

    @Test void oppositeThrottleKeysDoNotAccelerate() {
        var stopped = run(initial(), new DrivePhysics.Controls(true, true, false, false, false), 100, FLAT);
        assertEquals(0, stopped.speed());
        assertEquals(0, stopped.z());
    }
}
