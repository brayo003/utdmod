import java.util.List;
import java.util.Stack;

public class MetricEvaluator {
    public static int countActiveCells(double[][] field, double threshold, double peak) {
        int count = 0;
        for (double[] row : field) {
            for (double value : row) {
                if (value >= threshold) {
                    count++;
                }
            }
        }
        return count;
    }

    public static int countHotspots(double[][] field, double threshold, double peak) {
        int count = 0;
        int height = field.length;
        int width = field[0].length;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (field[y][x] < threshold) continue;
                boolean localMax = true;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx >= 0 && nx < width && ny >= 0 && ny < height && field[ny][nx] >= field[y][x]) {
                            localMax = false;
                        }
                    }
                }
                if (localMax) count++;
            }
        }
        return count;
    }

    public static int largestRegion(double[][] field, double threshold, double peak) {
        int height = field.length;
        int width = field[0].length;
        boolean[][] visited = new boolean[height][width];
        int best = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (field[y][x] >= threshold && !visited[y][x]) {
                    int size = floodFill(field, x, y, threshold, visited);
                    best = Math.max(best, size);
                }
            }
        }
        return best;
    }

    public static double diffusionRadius(double[][] field, double threshold, double peak, int sourceX, int sourceY) {
        double max = 0.0;
        int height = field.length;
        int width = field[0].length;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (field[y][x] >= threshold) {
                    double distance = Math.sqrt(Math.pow(x - sourceX, 2) + Math.pow(y - sourceY, 2));
                    max = Math.max(max, distance);
                }
            }
        }
        return max;
    }

    public static double centerOfMass(double[][] field) {
        double total = 0.0;
        double sumX = 0.0;
        double sumY = 0.0;
        for (int y = 0; y < field.length; y++) {
            for (int x = 0; x < field[0].length; x++) {
                total += field[y][x];
                sumX += field[y][x] * x;
                sumY += field[y][x] * y;
            }
        }
        if (total <= 0.0) return 0.0;
        return Math.sqrt(Math.pow(sumX / total - field[0].length / 2.0, 2) + Math.pow(sumY / total - field.length / 2.0, 2));
    }

    public static double maxGradient(double[][] field) {
        double max = 0.0;
        int height = field.length;
        int width = field[0].length;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                            max = Math.max(max, Math.abs(field[y][x] - field[ny][nx]));
                        }
                    }
                }
            }
        }
        return max;
    }

    public static double oscillationAmplitude(List<Double> history) {
        if (history.size() < 2) return 0.0;
        double min = history.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = history.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        return max - min;
    }

    public static double halfLife(List<Double> history) {
        if (history.isEmpty()) return Double.NaN;
        double initial = history.get(0);
        if (initial <= 0.0) return 0.0;
        for (int index = 0; index < history.size(); index++) {
            if (history.get(index) <= initial * 0.5) {
                return index;
            }
        }
        return Double.NaN;
    }

    private static int floodFill(double[][] field, int startX, int startY, double threshold, boolean[][] visited) {
        Stack<int[]> stack = new Stack<>();
        stack.push(new int[]{startX, startY});
        visited[startY][startX] = true;
        int size = 0;
        while (!stack.isEmpty()) {
            int[] cell = stack.pop();
            size++;
            int[][] directions = {{-1,0},{1,0},{0,-1},{0,1}};
            for (int[] direction : directions) {
                int nx = cell[0] + direction[0];
                int ny = cell[1] + direction[1];
                if (nx >= 0 && nx < field[0].length && ny >= 0 && ny < field.length && !visited[ny][nx] && field[ny][nx] >= threshold) {
                    visited[ny][nx] = true;
                    stack.push(new int[]{nx, ny});
                }
            }
        }
        return size;
    }
}
