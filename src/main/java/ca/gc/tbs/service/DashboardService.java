package ca.gc.tbs.service;

import ca.gc.tbs.domain.Problem;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardService.class);

    private final ProblemCacheService problemCacheService;

    public DashboardService(ProblemCacheService problemCacheService) {
        this.problemCacheService = problemCacheService;
    }

    public record DashboardStats(
        List<Problem> problems,
        List<Problem> problemsByDate,
        int totalComments,
        int totalPages
    ) {}

    @Cacheable(value = "dashboardStats", key = "'all'", sync = true)
    public DashboardStats getDashboardStats() {
        LOGGER.info("Computing DashboardStats for cache...");
        List<Problem> processedProblems = problemCacheService.getProcessedProblems();

        // 1. Group raw records by (url, problemDate)
        List<Problem> merged = new ArrayList<>(
            processedProblems.stream()
                .collect(Collectors.groupingBy(
                    p -> new AbstractMap.SimpleEntry<>(p.getUrl(), p.getProblemDate()),
                    Collectors.collectingAndThen(Collectors.toList(), list -> {
                        Problem p = new Problem();
                        p.setUrl(list.getFirst().getUrl());
                        p.setProblemDate(list.getFirst().getProblemDate());
                        p.setUrlEntries(list.size());
                        p.setInstitution(list.getFirst().getInstitution());
                        p.setTitle(list.getFirst().getTitle());
                        p.setLanguage(list.getFirst().getLanguage());
                        p.setSection(list.getFirst().getSection());
                        p.setTheme(list.getFirst().getTheme());
                        return p;
                    })))
                .values());

        // 2. Filter out future dates
        LocalDate cutoff = LocalDate.now();
        merged = merged.stream()
            .filter(p -> isValidDate(p.getProblemDate(), DateTimeFormatter.ISO_LOCAL_DATE))
            .filter(p -> !LocalDate.parse(p.getProblemDate(), DateTimeFormatter.ISO_LOCAL_DATE).isAfter(cutoff))
            .collect(Collectors.toList());

        List<Problem> problemsByDate = new ArrayList<>(merged);

        // 3. Merge across dates for final problem list
        List<Problem> fullyMerged = mergeProblemsAcrossDates(merged);
        fullyMerged.sort(Comparator.comparingInt(Problem::getUrlEntries).reversed());

        int totalComments = fullyMerged.stream().mapToInt(Problem::getUrlEntries).sum();
        int totalPages = fullyMerged.size();

        LOGGER.info("DashboardStats computed: {} comments across {} pages", totalComments, totalPages);
        return new DashboardStats(fullyMerged, problemsByDate, totalComments, totalPages);
    }

    private List<Problem> mergeProblemsAcrossDates(List<Problem> problems) {
        Map<String, Problem> urlToProblemMap = new LinkedHashMap<>();
        for (Problem problem : problems) {
            urlToProblemMap.merge(
                problem.getUrl(),
                problem,
                (existingProblem, newProblem) -> {
                    Problem updatedProblem = new Problem(existingProblem);
                    updatedProblem.setUrlEntries(existingProblem.getUrlEntries() + newProblem.getUrlEntries());
                    return updatedProblem;
                });
        }
        return new ArrayList<>(urlToProblemMap.values());
    }

    private boolean isValidDate(String value, DateTimeFormatter formatter) {
        try {
            LocalDate.parse(value, formatter);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
