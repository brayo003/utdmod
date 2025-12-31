import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

public class SimpleTickEngine {
    private double x = 0.01;
    private final double r = 0.153267;
    private final double a = 1.0;
    private final double b = 1.0;
    private final double sigma = 0.35;
    private final double dt = 0.05;
    private final Random rng = new Random(42);
    private int tick = 0;
    private FileWriter log;

    public void runSimulation() {
        try {
            log = new FileWriter("utd_server_trace.csv");
            log.write("tick,x\n");
            
            for (int i = 0; i < 2000; i++) {
                double det = r * x + a * (x * x) - b * (x * x * x);
                x = x + dt * det + sigma * Math.sqrt(dt) * rng.nextGaussian();
                log.write(i + "," + x + "\n");
            }
            
            log.close();
            System.out.println("Simulation completed. Results saved to utd_server_trace.csv");
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        SimpleTickEngine engine = new SimpleTickEngine();
        engine.runSimulation();
    }
}
