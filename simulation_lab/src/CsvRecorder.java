import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CsvRecorder {
    public static void write(String path, List<String> rows) throws IOException {
        Files.write(Path.of(path), rows);
    }
}
