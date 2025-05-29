package analyzer.bugginess;

import analyzer.jira.GetReleaseInfo;
import analyzer.jira.TicketParser;
import analyzer.model.Release;
import analyzer.model.TicketInfo;
import analyzer.util.Configuration;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProportionEstimator {

    private static final int MIN_VALID_TICKETS = 5;
    private final Map<String, Release> releaseMap;
    private final ReleaseIndexMapper mapper;
    private final List<TicketInfo> validTicketsWithAV = new ArrayList<>();
    private double coldStartP;

    public ProportionEstimator(List<Release> orderedReleases) {
        this.mapper = new ReleaseIndexMapper(orderedReleases);
        this.releaseMap = new HashMap<>();
        for (Release r : orderedReleases) {
            releaseMap.put(r.getName(), r);
        }
        this.coldStartP = ColdStartEstimator.computeColdStartP();
    }

    public void registerValidTicket(TicketInfo ticket) {

        if (ticket.getAffectedVersions().isEmpty()) {
            if (Configuration.LABELING_DEBUG) System.out.println("SKIP registerValidTicket: ticket " + ticket.getId() + " ha AV vuote.");
            return;
        }

        if (ticket.getFixVersionName() == null) {
            if (Configuration.LABELING_DEBUG) System.out.println("SKIP registerValidTicket: ticket " + ticket.getId() + " ha FV null.");
            return;
        }

        String avName = ticket.getAffectedVersions().stream()
                .map(this::normalizeVersionName)
                .filter(name -> mapper.getIndex(name) != -1)
                .findFirst()
                .orElse(null);

        if (avName == null) {
            if (Configuration.LABELING_DEBUG) System.out.println("SKIP registerValidTicket: ticket " + ticket.getId() + " nessuna AV valida trovata nel mapper → AVs: " + ticket.getAffectedVersions());
            return;
        }

        int fvIdx = mapper.getIndex(normalizeVersionName(ticket.getFixVersionName()));
        int ivIdx = mapper.getIndex(avName);
        int ovIdx = findClosestReleaseBefore(ticket.getOpeningVersion());

        if (fvIdx == -1 || ivIdx == -1 || ovIdx == -1) {
            if (Configuration.LABELING_DEBUG) System.out.println("SKIP registerValidTicket: ticket " + ticket.getId() + " → fvIdx=" + fvIdx + " ivIdx=" + ivIdx + " ovIdx=" + ovIdx);
            return;
        }

        if (Configuration.LABELING_DEBUG) System.out.println("Ticket REGISTRATO: " + ticket.getId() + " AV=" + avName + " FV=" + ticket.getFixVersionName());
        ticket.setInjectedVersion(ticket.getOpeningVersion());
        validTicketsWithAV.add(ticket);
    }

    private double computeIncrementalP() {
        double sum = 0;
        int count = 0;

        for (TicketInfo t : validTicketsWithAV) {
            int iv = mapper.getIndex(t.getAffectedVersions().get(0));
            int fv = mapper.getIndex(t.getFixVersionName());
            int ov = findClosestReleaseBefore(t.getOpeningVersion());

            if (fv == ov) {
                sum += (double) (fv - iv);
            } else {
                sum += (double) (fv - iv) / (fv - ov);
            }
            count++;
        }

        return count == 0 ? 1.0 : sum / count;
    }

    public String estimateIV(TicketInfo ticket) {
        if (Configuration.LABELING_DEBUG) System.out.println("Estimo IV per ticket " + ticket.getId());
        if (Configuration.LABELING_DEBUG) System.out.println("   → FV: " + ticket.getFixVersionName() + ", OV: " + ticket.getOpeningVersion());

        String normalizedFV = normalizeVersionName(ticket.getFixVersionName());
        int fvIndex = mapper.getIndex(normalizedFV);

        int ovIndex = findClosestReleaseBefore(ticket.getOpeningVersion());
        if (ovIndex == -1) return null;

        double P = (validTicketsWithAV.size() >= MIN_VALID_TICKETS) ? computeIncrementalP() : coldStartP;

        int ivIndex;
        if (fvIndex == ovIndex) {
            ivIndex = (int) Math.round(fvIndex - P);
        } else {
            ivIndex = (int) Math.round(fvIndex - ((fvIndex - ovIndex) * P));
        }

        ivIndex = Math.max(0, ivIndex);
        String estIV = mapper.getReleaseName(ivIndex);

        ticket.setInjectedVersionName(mapper.getReleaseName(ivIndex));

        if (Configuration.LABELING_DEBUG) {
            if (fvIndex == -1) {
                System.out.println("Proportion SKIPPED: FixVersion non trovata → " + ticket.getFixVersionName());
            }
            if (ovIndex == -1) {
                System.out.println("Proportion SKIPPED: OpeningVersion fuori range → " + ticket.getOpeningVersion());
            }
        }

        return estIV;
    }

    public String normalizeVersionName(String name) {
        if (name == null) return null;
        if (name.matches("\\d+\\.\\d+")) {
            return name + ".0";
        }
        return name;
    }

    private int findClosestReleaseBefore(LocalDate targetDate) {
        int bestIdx = -1;
        LocalDate bestDate = null;

        for (int i = 0; i < mapper.size(); i++) {
            String relName = mapper.getReleaseName(i);
            Release r = releaseMap.get(relName);
            if (r == null || r.getReleaseDate().isAfter(targetDate)) continue;

            if (bestDate == null || r.getReleaseDate().isAfter(bestDate)) {
                bestDate = r.getReleaseDate();
                bestIdx = i;
            }
        }

        return bestIdx;
    }

    public ReleaseIndexMapper getMapper() {
        return mapper;
    }

    public static void main(String[] args) throws Exception {
        List<Release> releases = GetReleaseInfo.getDatasetReleases();
        Map<String, TicketInfo> tickets = TicketParser.parseTicketsFromJira();

        ProportionEstimator estimator = new ProportionEstimator(releases);

        // 1Prima registriamo i ticket con AV (da usare per stima incrementale di P)
        for (TicketInfo ticket : tickets.values()) {
            if (!ticket.getAffectedVersions().isEmpty()) {
                estimator.registerValidTicket(ticket);
            }
        }

        // 2️Poi stimiamo IV solo per quelli senza AV
        System.out.println("Ticket con AV mancante:");
        for (TicketInfo ticket : tickets.values()) {
            if (ticket.getAffectedVersions().isEmpty()) {
                String estIV = estimator.estimateIV(ticket);
                System.out.printf("Ticket %s → stimata IV: %s (FV: %s, OV: %s)\n",
                        ticket.getId(), estIV, ticket.getFixVersionName(), ticket.getOpeningVersion());
            }
        }

        int proportionable = 0;
        int skipped = 0;

        for (TicketInfo ticket : tickets.values()) {
            if (ticket.getAffectedVersions().isEmpty()) {
                String iv = estimator.estimateIV(ticket);
                if (iv != null) proportionable++;
                else skipped++;
            }
        }
        System.out.println("Ticket proporzionabili: " + proportionable);
        System.out.println("Ticket saltati: " + skipped);
    }



}
