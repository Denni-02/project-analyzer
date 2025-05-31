package analyzer.csv;

import util.Configuration;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

public class CsvBugLabelerDebug {

    private CsvBugLabelerDebug() {
        // Utility class → no instances allowed
    }

    public static void writeCsv(String path, List<String[]> rows) {
        try (FileWriter fw = new FileWriter(path)) {
            fw.write("TicketID;CommitHash;Method;Release\n");
            for (String[] row : rows) {
                fw.write(String.join(";", row) + "\n");
            }
        } catch (IOException e) {
            Configuration.logger.log(Level.SEVERE, "Errore scrivendo il debug CSV dei metodi buggy.", e);
            e.printStackTrace();
        }
    }
}
