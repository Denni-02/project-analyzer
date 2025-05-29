package analyzer.jira;

import analyzer.csv.CsvTicketDebugWriter;
import analyzer.exception.JiraParsingException;
import analyzer.exception.JsonDownloadException;
import analyzer.model.Release;
import analyzer.model.TicketInfo;
import analyzer.util.Configuration;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

public class TicketParser {

    private final static String RELEASE_DATE_STRING = "releaseDate";
    private final static String VERSIONS_STRING = "versions";

    public static Map<String, TicketInfo> parseTicketsFromJira() throws JiraParsingException {
        try {
            int totalTickets = 0;
            int skippedNoFixVersion = 0;
            Map<String, TicketInfo> ticketMap = new HashMap<>();

            int startAt = 0;
            int maxResults = 1000;
            int total = 1;

            while (startAt < total) {
                String jql = "project=" + Configuration.PROJECT1_NAME + " AND issuetype=Bug AND status in (Resolved, Closed) AND resolution=Fixed";
                String url = String.format("https://issues.apache.org/jira/rest/api/2/search?jql=%s&startAt=%d&maxResults=%d",
                        jql.replace(" ", "%20"), startAt, maxResults);
                JSONObject response = readJsonFromUrl(url);
                total = response.getInt("total");
                JSONArray issues = response.getJSONArray("issues");

                for (int i = 0; i < issues.length(); i++) {
                    JSONObject issue = issues.getJSONObject(i);
                    totalTickets++;

                    TicketInfo ticket = parseSingleTicket(issue);
                    if (ticket == null) {
                        skippedNoFixVersion++;
                        continue;
                    }

                    ticketMap.put(ticket.getId(), ticket);
                }

                startAt += maxResults;
            }

            return ticketMap;

        } catch (Exception e) {
            throw new JiraParsingException("Errore durante il parsing dei ticket da JIRA", e);
        }
    }


    private static TicketInfo parseSingleTicket(JSONObject issue) {
        String key = issue.getString("key");
        JSONObject fields = issue.getJSONObject("fields");
        String createdStr = fields.getString("created").substring(0, 10);
        LocalDate createdDate = LocalDate.parse(createdStr);
        JSONArray fixVersions = fields.getJSONArray("fixVersions");

        if (fixVersions.length() == 0) return null;

        TicketInfo ticket = new TicketInfo(key);
        ticket.setOpeningVersion(createdDate);

        // Fase A: Fix Versions
        LocalDate earliestFVDate = null;
        String earliestFVName = null;

        for (int j = 0; j < fixVersions.length(); j++) {
            JSONObject fv = fixVersions.getJSONObject(j);
            if (fv.has(RELEASE_DATE_STRING) && fv.has("name")) {
                String fvName = fv.getString("name");
                LocalDate fvDate = LocalDate.parse(fv.getString(RELEASE_DATE_STRING));
                ticket.addFixVersion(fvName, fvDate);
                if (earliestFVDate == null || fvDate.isBefore(earliestFVDate)) {
                    earliestFVDate = fvDate;
                    earliestFVName = fvName;
                }
            }
        }

        if (earliestFVDate == null) return null;

        ticket.setFixVersion(earliestFVDate);
        ticket.setFixVersionName(earliestFVName);

        // Fase B: Affected Versions
        JSONArray affectedVersions = fields.optJSONArray(VERSIONS_STRING);
        if (affectedVersions != null) {
            for (int j = 0; j < affectedVersions.length(); j++) {
                JSONObject av = affectedVersions.getJSONObject(j);
                if (av.has("name")) {
                    ticket.addAffectedVersion(av.getString("name"));
                }
            }
        }

        return ticket;
    }


    private static JSONObject readJsonFromUrl(String url) throws JsonDownloadException {
        try (InputStream is = new URL(url).openStream();
             BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            StringBuilder sb = new StringBuilder();
            int cp;
            while ((cp = rd.read()) != -1) {
                sb.append((char) cp);
            }

            return new JSONObject(sb.toString());

        } catch (RuntimeException | IOException e) {
            throw new JsonDownloadException("Errore nel download o parsing del JSON da URL: " + url, e);
        }
    }


    public static Map<String, TicketInfo> parseTicketsFromProject(String projectKey) throws Exception {
        Map<String, TicketInfo> ticketMap = new HashMap<>();
        int startAt = 0;
        int maxResults = 1000;
        int total = 1;

        while (startAt < total) {
            String jql = "project=" + projectKey + " AND issuetype=Bug AND status in (Resolved, Closed) AND resolution=Fixed";
            String url = String.format("https://issues.apache.org/jira/rest/api/2/search?jql=%s&startAt=%d&maxResults=%d",
                    jql.replace(" ", "%20"), startAt, maxResults);

            JSONObject response = readJsonFromUrl(url);
            total = response.getInt("total");
            JSONArray issues = response.getJSONArray("issues");

            for (int i = 0; i < issues.length(); i++) {
                JSONObject issue = issues.getJSONObject(i);
                String key = issue.getString("key");
                JSONObject fields = issue.getJSONObject("fields");
                String createdStr = fields.getString("created").substring(0, 10);
                LocalDate createdDate = LocalDate.parse(createdStr);

                JSONArray fixVersions = fields.getJSONArray("fixVersions");
                if (fixVersions.length() == 0) continue;

                JSONArray affectedVersions = fields.optJSONArray(VERSIONS_STRING);

                TicketInfo ticket = new TicketInfo(key);
                ticket.setOpeningVersion(createdDate);

                LocalDate earliestFVDate = null;
                String earliestFVName = null;

                for (int j = 0; j < fixVersions.length(); j++) {
                    JSONObject fv = fixVersions.getJSONObject(j);
                    if (fv.has(RELEASE_DATE_STRING) && fv.has("name")) {
                        String fvName = fv.getString("name");
                        LocalDate fvDate = LocalDate.parse(fv.getString(RELEASE_DATE_STRING));
                        ticket.addFixVersion(fvName, fvDate);

                        if (earliestFVDate == null || fvDate.isBefore(earliestFVDate)) {
                            earliestFVDate = fvDate;
                            earliestFVName = fvName;
                        }
                    }
                }

                if (earliestFVDate == null) continue;

                ticket.setFixVersion(earliestFVDate);
                ticket.setFixVersionName(earliestFVName);

                if (affectedVersions != null) {
                    for (int j = 0; j < affectedVersions.length(); j++) {
                        JSONObject av = affectedVersions.getJSONObject(j);
                        if (av.has("name")) {
                            ticket.addAffectedVersion(av.getString("name"));
                        }
                    }
                }

                ticketMap.put(key, ticket);
            }

            startAt += maxResults;
        }

        return ticketMap;
    }

    public static List<Release> getReleasesFromProject(String projectKey) throws Exception {
        List<Release> releases = new ArrayList<>();

        String url = "https://issues.apache.org/jira/rest/api/2/project/" + projectKey;
        JSONObject json = readJsonFromUrl(url);
        JSONArray versions = json.getJSONArray(VERSIONS_STRING);

        for (int i = 0; i < versions.length(); i++) {
            JSONObject version = versions.getJSONObject(i);
            if (version.has(RELEASE_DATE_STRING) && version.has("released") && version.getBoolean("released")) {
                Release r = new Release();
                r.setName(version.optString("name", "unknown"));
                r.setId(version.optString("id", "0"));
                r.setReleaseDate(LocalDate.parse(version.getString(RELEASE_DATE_STRING)));
                r.setReleased(true);
                releases.add(r);
            }
        }

        // Ordina le release per data
        releases.sort(Comparator.comparing(Release::getReleaseDate));
        return releases;
    }


    public static void main(String[] args) throws Exception {
        Map<String, TicketInfo> tickets = parseTicketsFromJira();
        CsvTicketDebugWriter.writeTicketCsv(
                "/home/denni/isw2/project-analyzer/debug_file/ticket_debug.csv",
                tickets
        );
    }
}
