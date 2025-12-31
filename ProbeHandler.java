package com.utdmod.probe;

import com.utdmod.storm.TensionManager;

public class ProbeHandler {

public void checkTension() {
    double tension = TensionManager.getGlobalTension();
    System.out.println("Current global tension: " + tension);
}


}
