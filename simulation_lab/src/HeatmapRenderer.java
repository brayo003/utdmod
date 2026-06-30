import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public class HeatmapRenderer {
    public static void writeHeatmap(double[][] values, Path outputPath, String title) throws IOException {
        Files.createDirectories(outputPath.getParent());
        int width = values[0].length;
        int height = values.length;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double v = values[y][x];
                int gray = clamp((int) (255.0 * Math.min(1.0, Math.max(0.0, v / 5.0))));
                graphics.setColor(new Color(gray, gray, gray, 255));
                graphics.fillRect(x, y, 1, 1);
            }
        }
        graphics.dispose();
        ImageIO.write(image, "png", outputPath.toFile());
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
