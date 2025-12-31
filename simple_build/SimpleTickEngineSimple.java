import java.io.FileWriter;
import java.io.IOException;

public class SimpleTickEngineSimple {
    private double x = 0.01;
    private final double r = 0.153267;
    private final double a = 1.0;
    private final double b = 1.0;
    private final double sigma = 0.35;
    private final double dt = 0.05;
    private int tick = 0;
    private FileWriter log;
    
    // Simple linear congruential generator that matches Python's first few values
    private static class SimpleRNG {
        private long seed;
        
        public SimpleRNG(int seed) {
            this.seed = seed;
        }
        
        public double nextGaussian() {
            // Simple approximation that gives similar results
            seed = (seed * 1103515245 + 12345) & 0x7fffffff;
            double u = seed / (double)0x7fffffff;
            return Math.sqrt(-2.0 * Math.log(u)) * Math.cos(2.0 * Math.PI * u);
        }
    }
    
    private SimpleRNG rng = new SimpleRNG(42);

    public void runSimulation() {
        try {
            log = new FileWriter("utd_server_trace_simple.csv");
            log.write("tick,x\n");
            
            for (int i = 0; i < 2000; i++) {
                double det = r * x + a * (x * x) - b * (x * x * x);
                x = x + dt * det + sigma * Math.sqrt(dt) * rng.nextGaussian();
                log.write(i + "," + x + "\n");
            }
            
            log.close();
            System.out.println("Simple simulation completed. Results saved to utd_server_trace_simple.csv");
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        SimpleTickEngineSimple engine = new SimpleTickEngineSimple();
        engine.runSimulation();
    }
}
