import java.io.FileWriter;
import java.io.IOException;

public class SimpleTickEngineFixed {
    private double x = 0.01;
    private final double r = 0.153267;
    private final double a = 1.0;
    private final double b = 1.0;
    private final double sigma = 0.35;
    private final double dt = 0.05;
    private int tick = 0;
    private FileWriter log;
    
    // Python Mersenne Twister RNG implementation (simplified)
    private static class PythonRNG {
        private long[] mt = new long[624];
        private int index = 0;
        
        public PythonRNG(int seed) {
            mt[0] = seed & 0xffffffffL;
            for (int i = 1; i < 624; i++) {
                mt[i] = (1812433253L * (mt[i-1] ^ (mt[i-1] >>> 30)) + i) & 0xffffffffL;
            }
        }
        
        private void generate() {
            for (int i = 0; i < 624; i++) {
                long y = (mt[i] & 0x80000000L) + (mt[(i+1)%624] & 0x7fffffffL);
                mt[i] = mt[(i+397)%624] ^ (y >>> 1);
                if ((y % 2) != 0) mt[i] = mt[i] ^ 0x9908b0dfL;
            }
        }
        
        public double nextGaussian() {
            if (index == 0) generate();
 index = 1;
            
            long y = mt[index];
            y = y ^ (y >>> 11);
            y = y ^ ((y << 7) & 0x9d2c5680L);
            y = y ^ ((y << 15) & 0xefc60000L);
            y = y ^ (y >>> 18);
            
            index = (index + 1) % 624;
            
            // Convert to uniform [0,1), then to Gaussian using Box-Muller
            double u = ((double)(y & 0xffffffffL)) / 4294967296.0;
            if (u == 0.0) u = 1e-10;
            
            // Simple approximation of Gaussian
            double gaussian = Math.sqrt(-2.0 * Math.log(u)) * Math.cos(2.0 * Math.PI * u);
            return gaussian;
        }
    }
    
    private PythonRNG rng = new PythonRNG(42);

    public void runSimulation() {
        try {
            log = new FileWriter("utd_server_trace_fixed.csv");
            log.write("tick,x\n");
            
            for (int i = 0; i < 2000; i++) {
                double det = r * x + a * (x * x) - b * (x * x * x);
                x = x + dt * det + sigma * Math.sqrt(dt) * rng.nextGaussian();
                log.write(i + "," + x + "\n");
            }
            
            log.close();
            System.out.println("Fixed simulation completed. Results saved to utd_server_trace_fixed.csv");
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        SimpleTickEngineFixed engine = new SimpleTickEngineFixed();
        engine.runSimulation();
    }
}
