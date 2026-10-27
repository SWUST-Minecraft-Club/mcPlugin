package com.example.vehicles;

import org.bukkit.configuration.file.FileConfiguration;

record VehicleSettings(int maximum, DrivePhysics.Settings physics, boolean installToCraftEngine) {
    static VehicleSettings load(FileConfiguration config) {
        return new VehicleSettings(Math.clamp(config.getInt("maximum-active-vehicles", 12), 1, 100),
                new DrivePhysics.Settings(number(config, "forward-speed", .38, .05, .65),
                        number(config, "reverse-speed", .14, .02, .25),
                        number(config, "acceleration", .009, .001, .04),
                        number(config, "brake", .025, .005, .1),
                        number(config, "rolling-resistance", .004, .001, .03),
                        number(config, "steering-degrees", 2.8, .5, 6),
                        number(config, "step-height", .6, 0, .6)),
                config.getBoolean("resources.install-to-craftengine", true));
    }

    private static double number(FileConfiguration config, String key, double fallback, double min, double max) {
        double value = config.getDouble("driving." + key, fallback);
        return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }
}
