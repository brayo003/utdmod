package com.utdmod.storm;

import com.utdmod.tension.TensionManager;

public class StormManager {

public void triggerStorm() {
    double t = TensionManager.getGlobalTension();
    if (t > 1.0) {
        System.out.println("Storm triggered due to high tension: " + t);
    } else {
        System.out.println("No storm. Current tension: " + t);
    }
}


}
