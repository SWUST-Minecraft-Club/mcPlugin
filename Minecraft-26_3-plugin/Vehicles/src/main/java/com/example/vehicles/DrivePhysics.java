package com.example.vehicles;

/** Pure driving rules. Coordinates describe the centre of the car at ground level. */
public final class DrivePhysics {
    public static final double HALF_WIDTH = 0.87;
    public static final double HALF_LENGTH = 1.39;
    public static final double HEIGHT = 1.9;
    private static final double GRAVITY = 0.04;
    private static final double EPSILON = 0.012;

    public record Settings(double forward, double reverse, double acceleration, double brake,
                           double drag, double steering, double step) {}
    public record Controls(boolean forward, boolean backward, boolean left, boolean right, boolean brake) {
        public static final Controls NONE = new Controls(false, false, false, false, false);
    }
    public record State(double x, double y, double z, float yaw, double speed, double falling,
                        double wheelAngle, boolean grounded) {}
    public record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}
    @FunctionalInterface public interface Terrain { boolean clear(Bounds bounds); }

    private DrivePhysics() {}

    public static Bounds bounds(double x, double y, double z, float yaw) {
        double radians = Math.toRadians(yaw);
        double halfX = Math.abs(Math.cos(radians)) * HALF_WIDTH + Math.abs(Math.sin(radians)) * HALF_LENGTH;
        double halfZ = Math.abs(Math.sin(radians)) * HALF_WIDTH + Math.abs(Math.cos(radians)) * HALF_LENGTH;
        return new Bounds(x - halfX, y + EPSILON, z - halfZ, x + halfX, y + HEIGHT, z + halfZ);
    }

    public static State tick(State old, Controls controls, boolean occupied, Settings settings, Terrain terrain) {
        double speed = old.speed;
        int throttle = (controls.forward ? 1 : 0) - (controls.backward ? 1 : 0);
        if (!occupied) speed = approach(speed, 0, settings.brake);
        else if (controls.brake) speed = approach(speed, 0, settings.brake);
        else if (throttle == 0) speed = approach(speed, 0, settings.drag);
        else {
            boolean reversing = speed * throttle < 0;
            speed += throttle * (reversing ? settings.brake : settings.acceleration);
            speed = Math.clamp(speed, -settings.reverse, settings.forward);
        }
        int steering = (controls.right ? 1 : 0) - (controls.left ? 1 : 0);
        float yaw = old.yaw;
        if (Math.abs(speed) > 0.003 && old.grounded) {
            float next = (float) (yaw + steering * settings.steering
                    * Math.min(1, Math.abs(speed) / 0.12) * Math.signum(speed));
            if (terrain.clear(bounds(old.x, old.y, old.z, next))) yaw = next;
        }
        double x = old.x, y = old.y, z = old.z;
        int steps = Math.max(1, (int) Math.ceil(Math.abs(speed) / 0.09));
        double radians = Math.toRadians(yaw);
        double dx = -Math.sin(radians) * speed / steps, dz = Math.cos(radians) * speed / steps;
        for (int i = 0; i < steps; i++) {
            if (terrain.clear(bounds(x + dx, y, z + dz, yaw))) {
                x += dx; z += dz;
            } else if (old.grounded && terrain.clear(bounds(x + dx, y + settings.step, z + dz, yaw))) {
                double landing = y + settings.step;
                while (landing > y + 0.025 && terrain.clear(bounds(x + dx, landing - 0.025, z + dz, yaw))) {
                    landing -= 0.025;
                }
                x += dx; z += dz; y = landing;
            } else {
                speed = 0;
                break;
            }
        }
        double falling = Math.max(-0.65, old.falling - GRAVITY);
        boolean grounded = false;
        int falls = Math.max(1, (int) Math.ceil(Math.abs(falling) / 0.04));
        double fallStep = falling / falls;
        for (int i = 0; i < falls; i++) {
            if (terrain.clear(bounds(x, y + fallStep, z, yaw))) y += fallStep;
            else {
                double low = y + fallStep, high = y;
                for (int j = 0; j < 8; j++) {
                    double mid = (low + high) / 2;
                    if (terrain.clear(bounds(x, mid, z, yaw))) high = mid;
                    else low = mid;
                }
                y = high; falling = 0; grounded = true;
                break;
            }
        }
        return new State(x, y, z, wrap(yaw), speed, falling,
                old.wheelAngle + speed / 0.3125, grounded);
    }

    private static double approach(double current, double target, double amount) {
        return current < target ? Math.min(target, current + amount) : Math.max(target, current - amount);
    }
    private static float wrap(float yaw) { return (yaw % 360 + 540) % 360 - 180; }
}
