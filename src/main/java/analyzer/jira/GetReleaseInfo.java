package analyzer.jira;

import analyzer.csv.CsvDebugWriter;
import analyzer.model.Release;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Level;

import util.Configuration;

public class GetReleaseInfo {

    private static final ArrayList<LocalDateTime> releases = new ArrayList<>();
    private static final HashMap<LocalDateTime, String> releaseNames = new HashMap<>();
    private static final HashMap<LocalDateTime, String> releaseIDs = new HashMap<>();
    private static final String RELEASE_DATE_STRING = "releaseDate";
    private static final String RELEASE_STRING = "released" ;

    private GetReleaseInfo(){
        // Prevent instantiation
    }

    // Ottiene la lista di release (primo 33%) dal progetto JIRA
    public static List<Release> getDatasetReleases() throws IOException, JSONException {
        releases.clear();
        releaseNames.clear();
        releaseIDs.clear();

        if (Configuration.BASIC_DEBUG) Configuration.logger.info("Recupero release da JIRA per il progetto " + Configuration.getProjectName());

        // 1. Richiesta HTTP
        String url = "https://issues.apache.org/jira/rest/api/2/project/" + Configuration.getProjectName();
        JSONObject json = readJsonFromUrl(url);
        JSONArray versions = json.getJSONArray("versions");

        // 2. Parsing delle versioni
        /*for (int i = 0; i < versions.length(); i++) {
            JSONObject version = versions.getJSONObject(i);
            if (version.has(RELEASE_DATE_STRING) && version.has(RELEASE_STRING) && version.getBoolean(RELEASE_STRING)) {
                String dateStr = version.getString(RELEASE_DATE_STRING);
                String name = version.optString("name", "unknown");
                String id = version.optString("id", "0");
                addRelease(dateStr, name, id);
            }
        }

         */
        for (int i = 0; i < versions.length(); i++) {
            JSONObject version = versions.getJSONObject(i);
            if (version.has(RELEASE_DATE_STRING) && version.has(RELEASE_STRING) && version.getBoolean(RELEASE_STRING)) {
                String name = version.optString("name", "unknown");

                if (!name.matches("^\\d+\\.\\d+\\.\\d+$")) continue;

                String dateStr = version.getString(RELEASE_DATE_STRING);
                String id = version.optString("id", "0");
                addRelease(dateStr, name, id);
            }
        }

        // 3. Ordina
        releases.sort(Comparator.naturalOrder());

        // 4. Prendi il primo 33%
        int cutoff = (int) Math.ceil(releases.size() * 0.33);
        List<LocalDateTime> selected = releases.subList(0, cutoff);

        if (Configuration.BASIC_DEBUG && Configuration.logger.isLoggable(Level.INFO)) {
            Configuration.logger.info("Numero totale release: " + releases.size());
            Configuration.logger.info("Release selezionate (33%): " + selected.size());
        }

        // 5. Costruisci oggetti Release
        List<Release> allReleases = new ArrayList<>();
        List<Release> selectedReleases = new ArrayList<>();

        for (LocalDateTime dt : releases) {
            Release r = new Release();
            r.setName(releaseNames.get(dt));
            r.setId(releaseIDs.get(dt));
            r.setReleaseDate(dt.toLocalDate());
            r.setReleased(true);
            allReleases.add(r);

            if (selected.contains(dt)) {
                selectedReleases.add(r);
            }
        }

        // 6. Scrivi su CSV tramite CsvDebugWriter
        CsvDebugWriter.writeReleaseCsv(
                Configuration.getDebugVersionInfoPath(),
                allReleases,
                selectedReleases
        );

        // 7. Converti in List<Release>
        List<Release> output = new ArrayList<>();
        for (LocalDateTime dt : selected) {
            Release r = new Release();
            r.setName(releaseNames.get(dt));
            r.setId(releaseIDs.get(dt));
            r.setReleaseDate(dt.toLocalDate());
            r.setReleased(true);
            output.add(r);
        }

        return output;
    }

    public static List<Release> getAllReleases() throws IOException, JSONException {
        releases.clear();
        releaseNames.clear();
        releaseIDs.clear();

        if (Configuration.LABELING_DEBUG)
            Configuration.logger.info("Recupero TUTTE le release da JIRA per il progetto " + Configuration.getProjectName());

        // 1. Richiesta HTTP
        String url = "https://issues.apache.org/jira/rest/api/2/project/" + Configuration.getProjectName();
        JSONObject json = readJsonFromUrl(url);
        JSONArray versions = json.getJSONArray("versions");

        // 2. Parsing delle versioni
        /*for (int i = 0; i < versions.length(); i++) {
            JSONObject version = versions.getJSONObject(i);
            if (version.has(RELEASE_DATE_STRING) && version.has(RELEASE_STRING) && version.getBoolean(RELEASE_STRING)) {
                String dateStr = version.getString(RELEASE_DATE_STRING);
                String name = version.optString("name", "unknown");
                String id = version.optString("id", "0");
                addRelease(dateStr, name, id);
            }
        }

         */

        for (int i = 0; i < versions.length(); i++) {
            JSONObject version = versions.getJSONObject(i);
            if (version.has(RELEASE_DATE_STRING) && version.has(RELEASE_STRING) && version.getBoolean(RELEASE_STRING)) {
                String name = version.optString("name", "unknown");

                if (!name.matches("^\\d+\\.\\d+\\.\\d+$")) continue;

                String dateStr = version.getString(RELEASE_DATE_STRING);
                String id = version.optString("id", "0");
                addRelease(dateStr, name, id);
            }
        }

        releases.sort(Comparator.naturalOrder());

        List<Release> all = new ArrayList<>();
        for (LocalDateTime dt : releases) {
            Release r = new Release();
            r.setName(releaseNames.get(dt));
            r.setId(releaseIDs.get(dt));
            r.setReleaseDate(dt.toLocalDate());
            r.setReleased(true);
            all.add(r);
        }

        return all;
    }


    // Aggiunge una release alla struttura dati
    private static void addRelease(String strDate, String name, String id) {
        LocalDate date = LocalDate.parse(strDate);
        LocalDateTime dateTime = date.atStartOfDay();
        if (!releases.contains(dateTime)) {
            releases.add(dateTime);
        }
        releaseNames.put(dateTime, name);
        releaseIDs.put(dateTime, id);
    }

    // Effettua richiesta HTTP e converte in JSONObject
    private static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
        InputStream is = new URL(url).openStream();
        try (BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")))) {
            StringBuilder sb = new StringBuilder();
            int cp;
            while ((cp = rd.read()) != -1) {
                sb.append((char) cp);
            }
            return new JSONObject(sb.toString());
        }
    }

}

