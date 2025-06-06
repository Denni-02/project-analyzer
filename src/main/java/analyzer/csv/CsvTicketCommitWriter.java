package analyzer.csv;

import analyzer.model.TicketInfo;
import util.Configuration;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

public class CsvTicketCommitWriter {

    private CsvTicketCommitWriter() {
        // Utility class → no instances allowed
    }

    public static void write(String path, Map<String, TicketInfo> ticketMap) {
        try (FileWriter fw = new FileWriter(path)) {
            fw.write("TicketID;CommitID;JavaFileModified%n");

            for (TicketInfo ticket : ticketMap.values()) {
                for (String commit : ticket.getCommitIds()) {
                    if (ticket.getFixedFiles().isEmpty()) {
                        fw.write(String.format("%s;%s;%s%n", ticket.getId(), commit, "(NO JAVA FILES)"));
                    } else {
                        for (String file : ticket.getFixedFiles()) {
                            fw.write(String.format("%s;%s;%s%n", ticket.getId(), commit, file));
                        }
                    }
                }
            }

        } catch (IOException e) {
            Configuration.logger.info("Errore nella scrittura del CSV dei commit per ticket");
            e.printStackTrace();
        }
    }
}
