package com.example.town.selection;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundaryParticlesTest {
    @Test void longEdgesRetainTheNearbyPartInEitherDirection() {
        assertArrayEquals(new double[]{0.45,0.55},BoundaryParticles.clip(-1000,64,0,1000,64,0,0,64,0,100),1e-9);
        assertArrayEquals(new double[]{0.45,0.55},BoundaryParticles.clip(1000,64,0,-1000,64,0,0,64,0,100),1e-9);
        assertNull(BoundaryParticles.clip(-1000,64,101,1000,64,101,0,64,0,100));
    }
    @Test void clippingHandlesVerticalMarkersAndWorldHeight() {
        assertArrayEquals(new double[]{0,1},BoundaryParticles.clip(0,64,0,0,66,0,0,64,0,72));
        assertNull(BoundaryParticles.clip(0,-100,0,0,-99,0,0,64,0,72));
        assertArrayEquals(new double[]{0,1},BoundaryParticles.clip(0,64,0,0,64,0,0,64,0,72));
    }
}
