package analyzer.csv;

import analyzer.model.TicketInfo;
import analyzer.util.Configuration;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

public class CsvTicketDebugWriter {

    public static void writeTicketCsv(String outputPath, Map<String, TicketInfo> tickets) {
        try (FileWriter fw = new FileWriter(outputPath)) {
            fw.write("TicketID;OpeningVersion;FixVersionName;FixVersionDate;AllFixVersions;AffectedVersions\n");

            for (Map.Entry<String, TicketInfo> entry : tickets.entrySet()) {
                TicketInfo t = entry.getValue();
                fw.write(String.format("%s;%s;%s;%s;%s;%s\n",
                        t.getId(),
                        t.getOpeningVersion(),
                        t.getFixVersionName(),
                        t.getFixVersion(),
                        String.join(",", t.getFixVersionNames()),
                        String.join(",", t.getAffectedVersions())
                ));

            }

            Configuration.logger.info("File CSV ticket salvato in: " + outputPath);

        } catch (IOException e) {
            Configuration.logger.info("Errore nella scrittura del file CSV dei ticket.");
            e.printStackTrace();
        }
    }
}
