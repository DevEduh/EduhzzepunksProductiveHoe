package com.example.examplemod.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.List;

public final class ProductiveHoeConfig {
    private static final double[] DEFAULT_PENALTIES = {0.0D, 0.2D, 0.4D, 0.6D, 0.8D, 0.95D};
    private static final int MAX_ROTATION_BONUS_TICKS = 200;

    public static final ForgeConfigSpec SPEC;
    public static final Server SERVER;

    private static double[] penaltyCache = Arrays.copyOf(DEFAULT_PENALTIES, DEFAULT_PENALTIES.length);

    static {
        Pair<Server, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = pair.getLeft();
        SPEC = pair.getRight();
        bake();
    }

    private ProductiveHoeConfig() {
    }

    public static void bake() {
        List<? extends Double> list = SERVER.penaltyByFatigue.get();
        double[] next = Arrays.copyOf(DEFAULT_PENALTIES, DEFAULT_PENALTIES.length);
        int limit = Math.min(list.size(), next.length);
        for (int i = 0; i < limit; i++) {
            Object obj = list.get(i);
            if (!(obj instanceof Number number)) {
                continue;
            }
            double value = number.doubleValue();
            if (value < 0.0D || value > 1.0D) {
                continue;
            }
            next[i] = value;
        }
        penaltyCache = next;
    }

    public static double getPenaltyForFatigue(int fatigue) {
        int idx = Math.max(0, Math.min(5, fatigue));
        return penaltyCache[idx];
    }

    public static int getRotationBonusTicks() {
        int value = SERVER.rotationBonusTicks.get();
        if (value < 0) {
            return 0;
        }
        if (value > MAX_ROTATION_BONUS_TICKS) {
            return MAX_ROTATION_BONUS_TICKS;
        }
        return value;
    }

    public static int getMaxRotationBonusTicks() {
        return MAX_ROTATION_BONUS_TICKS;
    }

    public static double getRotationBonusPenaltyMultiplier() {
        double value = SERVER.rotationBonusPenaltyMultiplier.get();
        if (value < 0.0D) {
            return 0.0D;
        }
        if (value > 1.0D) {
            return 1.0D;
        }
        return value;
    }

    public static final class Server {
        public final ForgeConfigSpec.ConfigValue<List<? extends Double>> penaltyByFatigue;
        public final ForgeConfigSpec.IntValue rotationBonusTicks;
        public final ForgeConfigSpec.DoubleValue rotationBonusPenaltyMultiplier;

        private Server(ForgeConfigSpec.Builder builder) {
            builder.push("soilFatigue");

            penaltyByFatigue = builder
                    .comment(
                            "Chance (0.0-1.0) to block a random growth tick at each fatigue level.",
                            "Index 0 = fatigue 0, index 5 = fatigue 5."
                    )
                    .defineList(
                            "penaltyByFatigue",
                            Arrays.asList(0.0D, 0.2D, 0.4D, 0.6D, 0.8D, 0.95D),
                            value -> {
                                if (!(value instanceof Number number)) {
                                    return false;
                                }
                                double v = number.doubleValue();
                                return v >= 0.0D && v <= 1.0D;
                            }
                    );

            rotationBonusTicks = builder
                    .comment(
                            "Number of growth attempts with a rotation bonus after planting a different crop.",
                            "Set to 0 to disable the bonus."
                    )
                    .defineInRange("rotationBonusTicks", 4, 0, MAX_ROTATION_BONUS_TICKS);

            rotationBonusPenaltyMultiplier = builder
                    .comment(
                            "Multiplier applied to the fatigue penalty while rotation bonus is active.",
                            "Lower values mean faster growth during the bonus."
                    )
                    .defineInRange("rotationBonusPenaltyMultiplier", 0.5D, 0.0D, 1.0D);

            builder.pop();
        }
    }
}
