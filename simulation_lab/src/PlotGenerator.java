import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PlotGenerator {
    public static void writeLinePlot(Path outputPath, String title, List<Double> values, String seriesName, String xLabel, String yLabel) throws IOException {
        Files.createDirectories(outputPath.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns='http://www.w3.org/2000/svg' width='800' height='400'>");
        sb.append("<rect width='100%' height='100%' fill='white'/>");
        sb.append("<text x='20' y='30' font-size='18'>").append(title).append("</text>");
        if (!values.isEmpty()) {
            double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
            double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            double span = Math.max(1e-9, max - min);
            for (int index = 0; index < values.size() - 1; index++) {
                double x1 = 40 + (index * 720.0 / Math.max(1, values.size() - 1));
                double x2 = 40 + ((index + 1) * 720.0 / Math.max(1, values.size() - 1));
                double y1 = 360 - ((values.get(index) - min) / span * 300.0);
                double y2 = 360 - ((values.get(index + 1) - min) / span * 300.0);
                sb.append("<line x1='").append((int) x1).append("' y1='").append((int) y1).append("' x2='").append((int) x2).append("' y2='").append((int) y2).append("' stroke='black' stroke-width='1'/>");
            }
        }
        sb.append("</svg>");
        Files.writeString(outputPath, sb.toString());
    }
}
