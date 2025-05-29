package analyzer.csv;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class CsvBugLabelerDebug {
    public static void writeCsv(String path, List<String[]> rows) {
        try (FileWriter fw = new FileWriter(path)) {
            fw.write("TicketID;CommitHash;Method;Release\n");
            for (String[] row : rows) {
                fw.write(String.join(";", row) + "\n");
            }
        } catch (IOException e) {
            System.err.println("Errore scrivendo il debug CSV dei metodi buggy.");
            e.printStackTrace();
        }
    }
}
