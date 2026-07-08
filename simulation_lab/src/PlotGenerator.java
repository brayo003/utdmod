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

    public static void writeComparisonPlot(Path outputPath, String title, List<Double> baseline, List<Double> hybrid, String xLabel, String yLabel) throws IOException {
        Files.createDirectories(outputPath.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns='http://www.w3.org/2000/svg' width='900' height='420'>");
        sb.append("<rect width='100%' height='100%' fill='white'/>");
        sb.append("<text x='20' y='30' font-size='18'>").append(title).append("</text>");
        double maxValue = Math.max(maxValue(baseline), maxValue(hybrid));
        double minValue = Math.min(minValue(baseline), minValue(hybrid));
        double span = Math.max(1e-9, maxValue - minValue);
        sb.append("<line x1='40' y1='360' x2='840' y2='360' stroke='gray'/>");
        sb.append("<line x1='40' y1='60' x2='40' y2='360' stroke='gray'/>");
        appendSeries(sb, baseline, "#4c78a8", minValue, span, 40, 840, 60, 360);
        appendSeries(sb, hybrid, "#f58518", minValue, span, 40, 840, 60, 360);
        sb.append("<text x='40' y='390' font-size='12'>").append(xLabel).append("</text>");
        sb.append("<text x='12' y='210' transform='rotate(-90 12 210)' font-size='12'>").append(yLabel).append("</text>");
        sb.append("<rect x='650' y='70' width='12' height='12' fill='#4c78a8'/><text x='668' y='80' font-size='12'>baseline</text>");
        sb.append("<rect x='650' y='90' width='12' height='12' fill='#f58518'/><text x='668' y='100' font-size='12'>hybrid</text>");
        sb.append("</svg>");
        Files.writeString(outputPath, sb.toString());
    }

    private static void appendSeries(StringBuilder sb, List<Double> values, String color, double minValue, double span, int x0, int x1, int y0, int y1) {
        if (values.isEmpty()) return;
        for (int i = 0; i < values.size() - 1; i++) {
            double x1v = x0 + (i * (x1 - x0) / Math.max(1, values.size() - 1));
            double x2v = x0 + ((i + 1) * (x1 - x0) / Math.max(1, values.size() - 1));
            double y1v = y1 - ((values.get(i) - minValue) / span * (y1 - y0));
            double y2v = y1 - ((values.get(i + 1) - minValue) / span * (y1 - y0));
            sb.append("<line x1='").append((int) x1v).append("' y1='").append((int) y1v).append("' x2='").append((int) x2v).append("' y2='").append((int) y2v).append("' stroke='").append(color).append("' stroke-width='1.5'/>");
        }
    }

    private static double maxValue(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
    }

    private static double minValue(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
    }
}
