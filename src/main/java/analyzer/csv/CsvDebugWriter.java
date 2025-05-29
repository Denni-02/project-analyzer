package analyzer.csv;

import analyzer.model.Commit;
import analyzer.model.Release;
import analyzer.util.Configuration;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class CsvDebugWriter {

    public static void writeCommitCsv(String path, List<Commit> commits) {
        try (FileWriter fw = new FileWriter(path)) {
            fw.write("CommitID;Author;Date;Message\n");
            for (Commit c : commits) {
                fw.write(String.format("%s;%s;%s;%s\n",
                        c.getId(),
                        c.getAuthor(),
                        c.getDate(),
                        c.getMessage().replace(";", " ")));
            }
        } catch (IOException e) {
            Configuration.logger.info("Errore nella scrittura del CSV dei commit");
            e.printStackTrace();
        }
    }

    public static void writeReleaseCsv(String path, List<Release> all, List<Release> selected) {
        try (FileWriter fw = new FileWriter(path)) {
            fw.write("Index;Version ID;Version Name;Release Date;Selected\n");
            int i = 1;
            for (Release r : all) {
                fw.write(String.format("%d;%s;%s;%s;%s\n",
                        i++,
                        r.getId(),
                        r.getName(),
                        r.getReleaseDate(),
                        selected.contains(r) ? "Y" : "N"));
            }
        } catch (IOException e) {
            Configuration.logger.info("Errore nella scrittura del CSV delle release");
            e.printStackTrace();
        }
    }
}
