package com.utdmod.spec;

public final class DesignRules {

    // Rule 1: Tension Spread
    public static final boolean PLAYER_BOUND_TENSION = true;
    public static final boolean GLOBAL_TENSION_FORBIDDEN = true;

    // Rule 2: Reactive Object Limit
    public static final boolean LIMIT_REACTIVE_SYSTEMS = true;

    // Rule 3: Hook Policy
    public static final boolean DIRECT_VANILLA_MODIFICATION_FORBIDDEN = true;

    // Rule 4: Feature Gate
    public static final boolean REQUIRE_ZONE_CHECK = true;

    private DesignRules() {}
}
