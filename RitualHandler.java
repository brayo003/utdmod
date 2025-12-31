package com.utdmod.ritual;

import com.utdmod.storm.TensionManager;

public class RitualHandler {

public void performRitual() {
    double before = TensionManager.getGlobalTension();
    TensionManager.reduceTension(0.2);
    double after = TensionManager.getGlobalTension();
    System.out.println("Ritual performed: Tension reduced from " + before + " to " + after);
}


}
