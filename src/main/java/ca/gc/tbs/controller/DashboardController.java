package ca.gc.tbs.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.datatables.DataTablesInput;
import org.springframework.data.mongodb.datatables.DataTablesOutput;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import ca.gc.tbs.domain.Problem;
import ca.gc.tbs.repository.ProblemRepository;
import ca.gc.tbs.service.ErrorKeywordService;
import ca.gc.tbs.service.ProblemCacheService;
import ca.gc.tbs.service.ProblemDateService;
import ca.gc.tbs.service.UserService;

@Controller
public class DashboardController {
    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardController.class);

    @Autowired
    private ProblemRepository problemRepository;
    private static final boolean ASC = true;
    private static final boolean DESC = false;
    @Autowired
    private ProblemDateService problemDateService;
    @Autowired
    private ProblemCacheService problemCacheService;
    private int totalComments = 0;
    private int totalPages = 0;

    private List<Problem> problems;

    @Autowired
    private UserService userService;
    @Autowired
    private ErrorKeywordService errorKeywordService;
    @Autowired
    private MongoTemplate mongoTemplate;

    private static final Map<String, List<String>> institutionMappings = new HashMap<>();
    private static final Map<String, List<String>> sectionMappings = new HashMap<>();


    static {
        // Initialize section mappings
        sectionMappings.put("disability", Arrays.asList("disability", "disability benefits"));
        sectionMappings.put("news", Arrays.asList("news"));

        // Initialize institution mappings
        institutionMappings.put(
                "AAFC",
                Arrays.asList(
                        "AAFC",
                        "AAC",
                        "AGRICULTURE AND AGRI-FOOD CANADA",
                        "AGRICULTURE ET AGROALIMENTAIRE CANADA",
                        "AAFC/AAC"));
        institutionMappings.put(
                "ACOA",
                Arrays.asList(
                        "ACOA",
                        "APECA",
                        "ATLANTIC CANADA OPPORTUNITIES AGENCY",
                        "AGENCE DE PROMOTION ÉCONOMIQUE DU CANADA ATLANTIQUE",
                        "ACOA/APECA"));
        institutionMappings.put(
                "ATSSC",
                Arrays.asList(
                        "ATSSC",
                        "SCDATA",
                        "ADMINISTRATIVE TRIBUNALS SUPPORT SERVICE OF CANADA",
                        "SERVICE CANADIEN D’APPUI AUX TRIBUNAUX ADMINISTRATIFS",
                        "ATSSC/SCDATA"));
        institutionMappings.put(
                "CANNOR",
                Arrays.asList(
                        "CANNOR",
                        "RNCAN",
                        "CANADIAN NORTHERN ECONOMIC DEVELOPMENT AGENCY",
                        "AGENCE CANADIENNE DE DÉVELOPPEMENT ÉCONOMIQUE DU NORD",
                        "CANNOR/RNCAN"));
        institutionMappings.put(
                "CATSA",
                Arrays.asList(
                        "CATSA",
                        "ACSTA",
                        "CANADIAN AIR TRANSPORT SECURITY AUTHORITY",
                        "ADMINISTRATION CANADIENNE DE LA SÛRETÉ DU TRANSPORT AÉRIEN",
                        "CATSA/ACSTA"));
        institutionMappings.put(
                "CBSA",
                Arrays.asList(
                        "CBSA",
                        "ASFC",
                        "CANADA BORDER SERVICES AGENCY",
                        "AGENCE DES SERVICES FRONTALIERS DU CANADA",
                        "CBSA/ASFC"));
        institutionMappings.put(
                "CCG",
                Arrays.asList("CCG", "GCC", "CANADIAN COAST GUARD", "GARDE CÔTIÈRE CANADIENNE", "CCG/GCC"));
        institutionMappings.put(
                "CER",
                Arrays.asList(
                        "CER", "REC", "CANADA ENERGY REGULATOR", "RÉGIE DE L'ÉNERGIE DU CANADA", "CER/REC"));
        institutionMappings.put(
                "CFIA",
                Arrays.asList(
                        "CFIA",
                        "ACIA",
                        "CANADIAN FOOD INSPECTION AGENCY",
                        "AGENCE CANADIENNE D’INSPECTION DES ALIMENTS",
                        "CFIA/ACIA"));
        institutionMappings.put(
                "CIHR",
                Arrays.asList(
                        "CIHR",
                        "IRSC",
                        "CANADIAN INSTITUTES OF HEALTH RESEARCH",
                        "INSTITUTS DE RECHERCHE EN SANTÉ DU CANADA",
                        "CIHR/IRSC"));
        institutionMappings.put(
                "CIPO",
                Arrays.asList(
                        "CIPO",
                        "OPIC",
                        "CANADIAN INTELLECTUAL PROPERTY OFFICE",
                        "OFFICE DE LA PROPRIÉTÉ INTELLECTUELLE DU CANADA",
                        "CIPO/OPIC"));
        institutionMappings.put(
                "CIRNAC",
                Arrays.asList(
                        "CIRNAC",
                        "RCAANC",
                        "CROWN-INDIGENOUS RELATIONS AND NORTHERN AFFAIRS CANADA",
                        "RELATIONS COURONNE-AUTOCHTONES ET AFFAIRES DU NORD CANADA",
                        "CIRNAC/RCAANC"));
        institutionMappings.put(
                "CRA",
                Arrays.asList(
                        "CRA", "ARC", "CANADA REVENUE AGENCY", "AGENCE DU REVENU DU CANADA", "CRA/ARC"));
        institutionMappings.put(
                "CRTC",
                Arrays.asList(
                        "CRTC",
                        "CRTC",
                        "CANADIAN RADIO-TELEVISION AND TELECOMMUNICATIONS COMMISSION",
                        "CONSEIL DE LA RADIODIFFUSION ET DES TÉLÉCOMMUNICATIONS CANADIENNES"));
        institutionMappings.put(
                "CSA",
                Arrays.asList(
                        "CSA", "ASC", "CANADIAN SPACE AGENCY", "AGENCE SPATIALE CANADIENNE", "CSA/ASC"));
        institutionMappings.put(
                "CSC",
                Arrays.asList(
                        "CSC",
                        "SCC",
                        "CORRECTIONAL SERVICE CANADA",
                        "SERVICE CORRECTIONNEL CANADA",
                        "CSC/SCC"));
        institutionMappings.put(
                "CSE",
                Arrays.asList(
                        "CSE",
                        "CST",
                        "COMMUNICATIONS SECURITY ESTABLISHMENT",
                        "CENTRE DE LA SÉCURITÉ DES TÉLÉCOMMUNICATIONS",
                        "CSE/CST"));
        institutionMappings.put(
                "CSEC",
                Arrays.asList(
                        "CSEC",
                        "CSTC",
                        "COMMUNICATIONS SECURITY ESTABLISHMENT CANADA",
                        "CENTRE DE LA SÉCURITÉ DES TÉLÉCOMMUNICATIONS CANADA",
                        "CSEC/CSTC"));
        institutionMappings.put(
                "CSPS",
                Arrays.asList(
                        "CSPS",
                        "EFPC",
                        "CANADA SCHOOL OF PUBLIC SERVICE",
                        "ÉCOLE DE LA FONCTION PUBLIQUE DU CANADA",
                        "CSPS/EFPC"));
        institutionMappings.put(
                "DFO",
                Arrays.asList(
                        "DFO", "MPO", "FISHERIES AND OCEANS CANADA", "PÊCHES ET OCÉANS CANADA", "DFO/MPO", "GOVERNMENT OF CANADA, FISHERIES AND OCEANS CANADA, COMMUNICATIONS BRANCH"));
        institutionMappings.put(
                "DND", Arrays.asList("DND", "MDN", "NATIONAL DEFENCE", "DÉFENSE NATIONALE", "DND/MDN"));
        institutionMappings.put(
                "ECCC",
                Arrays.asList(
                        "ECCC",
                        "ENVIRONMENT AND CLIMATE CHANGE CANADA",
                        "ENVIRONNEMENT ET CHANGEMENT CLIMATIQUE CANADA",
                        "ECCC"));
        institutionMappings.put(
                "ESDC",
                Arrays.asList(
                        "ESDC",
                        "EDSC",
                        "EMPLOYMENT AND SOCIAL DEVELOPMENT CANADA",
                        "EMPLOI ET DÉVELOPPEMENT SOCIAL CANADA",
                        "ESDC/EDSC",
                        "EMPLOI ET DÉVÉLOPPEMENT SOCIALE CANADA"));
        institutionMappings.put(
                "FCAC",
                Arrays.asList(
                        "FCAC",
                        "ACFC",
                        "FINANCIAL CONSUMER AGENCY OF CANADA",
                        "AGENCE DE LA CONSOMMATION EN MATIÈRE FINANCIÈRE DU CANADA",
                        "FCAC/ACFC"));
        institutionMappings.put(
                "FIN",
                Arrays.asList(
                        "FIN",
                        "FIN",
                        "FINANCE CANADA",
                        "MINISTÈRE DES FINANCES CANADA",
                        "DEPARTMENT OF FINANCE CANADA",
                        "GOVERNMENT OF CANADA, DEPARTMENT OF FINANCE",
                        "MINISTÈRE DES FINANCES",
                        "FIN"));
        institutionMappings.put(
                "GAC",
                Arrays.asList(
                        "GAC", "AMC", "GLOBAL AFFAIRS CANADA", "AFFAIRES MONDIALES CANADA", "GAC/AMC"));
        institutionMappings.put(
                "HC", Arrays.asList("HC", "SC", "HEALTH CANADA", "SANTÉ CANADA", "HC/SC"));
        institutionMappings.put(
                "HICC", Arrays.asList(
                        "HICC", "LICC", "HOUSING, INFRASTRUCTURE AND COMMUNITIES CANADA", "LOGEMENT, INFRASTRUCTURES ET COLLECTIVITÉS CANADA", "HICC/LICC"));
        institutionMappings.put(
                "INFC", Arrays.asList("INFC", "INFC", "INFRASTRUCTURE CANADA", "INFRASTRUCTURE CANADA", "INFC / INFC"));
        institutionMappings.put(
                "IOGC",
                Arrays.asList(
                        "IOGC",
                        "BPGI",
                        "INDIAN OIL AND GAS CANADA",
                        "BUREAU DU PÉTROLE ET DU GAZ DES INDIENS",
                        "IOGC/BPGI"));
        institutionMappings.put(
                "IRCC",
                Arrays.asList(
                        "IRCC",
                        "IRCC",
                        "IMMIGRATION, REFUGEES AND CITIZENSHIP CANADA",
                        "IMMIGRATION, RÉFUGIÉS ET CITOYENNETÉ CANADA"));
        institutionMappings.put(
                "ISC",
                Arrays.asList(
                        "ISC",
                        "SAC",
                        "INDIGENOUS SERVICES CANADA",
                        "SERVICES AUX AUTOCHTONES CANADA",
                        "ISC/SAC"));
        institutionMappings.put(
                "ISED",
                Arrays.asList(
                        "ISED",
                        "ISDE",
                        "INNOVATION, SCIENCE AND ECONOMIC DEVELOPMENT CANADA",
                        "INNOVATION, SCIENCES ET DÉVELOPPEMENT ÉCONOMIQUE CANADA",
                        "ISED/ISDE"));
        institutionMappings.put(
                "JUS", Arrays.asList("JUS", "JUSTICE CANADA", "MINISTÈRE DE LA JUSTICE CANADA", "JUS"));
        institutionMappings.put(
                "LAC",
                Arrays.asList(
                        "LAC",
                        "BAC",
                        "LIBRARY AND ARCHIVES CANADA",
                        "BIBLIOTHÈQUE ET ARCHIVES CANADA",
                        "LAC/BAC"));
        institutionMappings.put(
                "NFB",
                Arrays.asList("NFB", "ONF", "NATIONAL FILM BOARD", "OFFICE NATIONAL DU FILM", "NFB/ONF"));
        institutionMappings.put(
                "NRC",
                Arrays.asList(
                        "NRC",
                        "CNRC",
                        "NATIONAL RESEARCH COUNCIL",
                        "CONSEIL NATIONAL DE RECHERCHES CANADA",
                        "NRC/CNRC"));
        institutionMappings.put(
                "NRCAN",
                Arrays.asList(
                        "NRCAN",
                        "RNCAN",
                        "NATURAL RESOURCES CANADA",
                        "RESSOURCES NATURELLES CANADA",
                        "NRCAN/RNCAN"));
        institutionMappings.put(
                "NSERC",
                Arrays.asList(
                        "NSERC",
                        "CRSNG",
                        "NATURAL SCIENCES AND ENGINEERING RESEARCH CANADA",
                        "CONSEIL DE RECHERCHES EN SCIENCES NATURELLES ET EN GÉNIE DU CANADA",
                        "NSERC/CRSNG"));
        institutionMappings.put(
                "OMBDNDCAF",
                Arrays.asList(
                        "OMBDNDCAF",
                        "OMBMDNFAC",
                        "DND/CAF OMBUDSMAN",
                        "OMBUDSMAN DU MDN/FAC",
                        "OFFICE OF THE NATIONAL DEFENCE AND CANADIAN ARMED FORCES OMBUDSMAN",
                        "BUREAU DE L'OMBUDSMAN DE LA DÉFENSE NATIONALE ET DES FORCES ARMÉES CANADIENNES",
                        "OMBDNDCAF/OMBMDNFAC"));
        institutionMappings.put(
                "OSB",
                Arrays.asList(
                        "OSB",
                        "BSF",
                        "SUPERINTENDENT OF BANKRUPTCY CANADA",
                        "BUREAU DU SURINTENDANT DES FAILLITES CANADA",
                        "OSB/BSF"));
        institutionMappings.put(
                "PBC",
                Arrays.asList(
                        "PBC",
                        "CLCC",
                        "PAROLE BOARD OF CANADA",
                        "COMMISSION DES LIBÉRATIONS CONDITIONNELLES DU CANADA",
                        "PBC/CLCC"));
        institutionMappings.put("PC", Arrays.asList("PC", "PC", "PARCS CANADA", "PARKS CANADA"));
        institutionMappings.put(
                "PCH", Arrays.asList("PCH", "PCH", "CANADIAN HERITAGE", "PATRIMOINE CANADIEN"));
        institutionMappings.put(
                "PCO",
                Arrays.asList("PCO", "BCP", "PRIVY COUNCIL OFFICE", "BUREAU DU CONSEIL PRIVÉ", "PCO/BCP"));
        institutionMappings.put(
                "PHAC",
                Arrays.asList(
                        "PHAC",
                        "ASPC",
                        "PUBLIC HEALTH AGENCY OF CANADA",
                        "AGENCE DE LA SANTÉ PUBLIQUE DU CANADA",
                        "PHAC/ASPC"));
        institutionMappings.put(
                "PS",
                Arrays.asList("PS", "SP", "PUBLIC SAFETY CANADA", "SÉCURITÉ PUBLIQUE CANADA", "PS/SP"));
        institutionMappings.put(
                "PSC",
                Arrays.asList(
                        "PSC",
                        "CFP",
                        "PUBLIC SERVICE COMMISSION OF CANADA",
                        "COMMISSION DE LA FONCTION PUBLIQUE DU CANADA",
                        "PSC/CFP"));
        institutionMappings.put(
                "PSPC",
                Arrays.asList(
                        "PSPC",
                        "SPAC",
                        "PUBLIC SERVICES AND PROCUREMENT CANADA",
                        "SERVICES PUBLICS ET APPROVISIONNEMENT CANADA",
                        "GOUVERNEMENT DU CANADA, SERVICES PUBLICS ET APPROVISIONNEMENT CANADA",
                        "GOVERNMENT OF CANADA, PUBLIC SERVICES AND PROCUREMENT CANADA",
                        "PSPC/SPAC"));
        institutionMappings.put(
                "RCMP",
                Arrays.asList(
                        "RCMP",
                        "GRC",
                        "ROYAL CANADIAN MOUNTED POLICE",
                        "GENDARMERIE ROYALE DU CANADA",
                        "RCMP/GRC"));
        institutionMappings.put(
                "SC", Arrays.asList("SC", "SC", "SERVICE CANADA", "SERVICE CANADA", "SC/SC"));
        institutionMappings.put(
                "SSC",
                Arrays.asList(
                        "SSC", "PSC", "SHARED SERVICES CANADA", "SERVICES PARTAGÉS CANADA", "SSC/PSC"));
        institutionMappings.put(
                "SSHRC",
                Arrays.asList(
                        "SSHRC",
                        "CRSH",
                        "SOCIAL SCIENCES AND HUMANITIES RESEARCH COUNCIL",
                        "CONSEIL DE RECHERCHES EN SCIENCES HUMAINES",
                        "SSHRC/CRSH"));
        institutionMappings.put(
                "SST",
                Arrays.asList(
                        "SST",
                        "TSS",
                        "SOCIAL SECURITY TRIBUNAL OF CANADA",
                        "TRIBUNAL DE LA SÉCURITÉ SOCIALE DU CANADA",
                        "SST/TSS"));
        institutionMappings.put("STATCAN", Arrays.asList("STATCAN", "STATISTIQUE CANADA"));
        institutionMappings.put(
                "TBS",
                Arrays.asList(
                        "TBS",
                        "SCT",
                        "TREASURY BOARD OF CANADA SECRETARIAT",
                        "SECRÉTARIAT DU CONSEIL DU TRÉSOR DU CANADA",
                        "TBS/SCT"));
        institutionMappings.put(
                "TC", Arrays.asList("TC", "TC", "TRANSPORT CANADA", "TRANSPORTS CANADA"));
        institutionMappings.put(
                "VAC",
                Arrays.asList(
                        "VAC", "ACC", "VETERANS AFFAIRS CANADA", "ANCIENS COMBATTANTS CANADA", "VAC/ACC"));
        institutionMappings.put(
                "WAGE",
                Arrays.asList(
                        "WAGE",
                        "FEGC",
                        "WOMEN AND GENDER EQUALITY CANADA",
                        "FEMMES ET ÉGALITÉ DES GENRES CANADA",
                        "WAGE/FEGC"));
        institutionMappings.put(
                "WD",
                Arrays.asList(
                        "WD",
                        "DEO",
                        "WESTERN ECONOMIC DIVERSIFICATION CANADA",
                        "DIVERSIFICATION DE L’ÉCONOMIE DE L’OUEST CANADA",
                        "WD/DEO"));
    }

    @RequestMapping(value = "/pageFeedback/totalCommentsCount")
    @ResponseBody
    public String totalCommentsCount() {
        return String.valueOf(totalComments);
    }

    @RequestMapping(value = "/pageFeedback/totalPagesCount")
    @ResponseBody
    public String totalPagesCount() {
        return String.valueOf(totalPages);
    }

    @GetMapping(value = "/dashboard")
    public ModelAndView pageFeedback(HttpServletRequest request) throws Exception {
        ModelAndView mav = new ModelAndView();
        String lang = (String) request.getSession().getAttribute("lang");
        mav.addObject("lang", lang);
        Map<String, String> dateMap = problemDateService.getProblemDates();
        if (dateMap != null) {
            mav.addObject("earliestDate", dateMap.get("earliestDate"));
            LocalDate latestDate =
                    LocalDate.parse(dateMap.get("latestDate"), DateTimeFormatter.ISO_LOCAL_DATE);
            LocalDate previousDate = latestDate.minusDays(1);
            String modifiedLatestDate = previousDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
            mav.addObject("latestDate", modifiedLatestDate);
        } else {
            mav.addObject("earliestDate", "N/A");
            mav.addObject("latestDate", "N/A");
        }

        mav.setViewName("pageFeedbackDashboard_" + lang);
        return mav;
    }

    @GetMapping(value = "/chartData")
    @ResponseBody
    public List<Map<String, Object>> commentsByDate(HttpServletRequest request) {
        long startTime = System.currentTimeMillis();
        
        Boolean error_keyword = "true".equals(request.getParameter("error_keyword"));
        String comments = request.getParameter("comments");
        String startDate = request.getParameter("startDate");
        String endDate = request.getParameter("endDate");
        String theme = request.getParameter("theme");
        String section = request.getParameter("section");
        String language = request.getParameter("language");
        String url = request.getParameter("url");
        String department = request.getParameter("department");

        // Build base filter criteria
        Criteria criteria = buildFilterCriteria(startDate, endDate, theme, section, language, url, department);
        
        // Add date filtering to exclude future dates
        LocalDate currentDate = LocalDate.now();
        criteria.and("problemDate").lte(currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE));

        List<Criteria> regexCriteria = new ArrayList<>();

        // Add error keyword filtering if enabled
        if (error_keyword) {
            Set<String> keywordsToCheck = new HashSet<>();
            keywordsToCheck.addAll(errorKeywordService.getEnglishKeywords());
            keywordsToCheck.addAll(errorKeywordService.getFrenchKeywords());
            keywordsToCheck.addAll(errorKeywordService.getBilingualKeywords());

            if (!keywordsToCheck.isEmpty()) {
                String combinedRegex = keywordsToCheck.stream()
                        .map(Pattern::quote)
                        .collect(Collectors.joining("|"));
                regexCriteria.add(Criteria.where("problemDetails").regex(combinedRegex, "i"));
            }
        }
        
        // Add comment filtering if provided
        if (comments != null && !comments.trim().isEmpty() && !"null".equalsIgnoreCase(comments.trim())) {
            String escapedComment = escapeSpecialRegexCharacters(comments.trim());
            regexCriteria.add(Criteria.where("problemDetails").regex(escapedComment, "i"));
        }

        // Combine all criteria
        Criteria finalCriteria;
        if (!regexCriteria.isEmpty()) {
            List<Criteria> ands = new ArrayList<>();
            ands.add(criteria);
            ands.addAll(regexCriteria);
            finalCriteria = new Criteria().andOperator(ands.toArray(new Criteria[0]));
        } else {
            finalCriteria = criteria;
        }

        // OPTIMIZED: Always use MongoDB aggregation by problemDate
        GroupOperation groupByDate = Aggregation.group("problemDate").count().as("comments");
        SortOperation sortByDate = Aggregation.sort(Sort.Direction.ASC, "_id");
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(finalCriteria),
                groupByDate,
                sortByDate
        );
        
        AggregationResults<Document> aggResults = mongoTemplate.aggregate(agg, "problem", Document.class);

        // Build dailyCommentsList
        List<Map<String, Object>> dailyCommentsList = new ArrayList<>();
        for (Document doc : aggResults) {
            Map<String, Object> map = new HashMap<>();
            map.put("date", doc.getString("_id"));
            map.put("comments", doc.getInteger("comments", 0));
            dailyCommentsList.add(map);
        }
        
        long elapsedTime = System.currentTimeMillis() - startTime;
        LOGGER.info("OPTIMIZED commentsByDate() - Time: {}ms, Results: {} dates",
                elapsedTime, dailyCommentsList.size());
        
        return dailyCommentsList;
    }



    @Scheduled(cron = "0 1 0 * * *")
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        LOGGER.info("DashboardController: Starting initial data fetch and cache population.");
        
        // Preload dashboard totals for fast initial page load
        try {
            LOGGER.info("DashboardController: Calculating initial totalComments and totalPages...");
            List<Problem> processedProblems = problemCacheService.getProcessedProblems();
            problemDateService.getProblemDates();
            
            // Group by URL and problemDate to get merged problems
            List<Problem> mergedProblems = new ArrayList<>(
                processedProblems.stream()
                    .collect(
                        Collectors.groupingBy(
                            p -> new AbstractMap.SimpleEntry<>(p.getUrl(), p.getProblemDate()),
                            Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> {
                                    Problem problem = new Problem();
                                    problem.setUrl(list.get(0).getUrl());
                                    problem.setProblemDate(list.get(0).getProblemDate());
                                    problem.setUrlEntries(list.size());
                                    problem.setInstitution(list.get(0).getInstitution());
                                    problem.setTitle(list.get(0).getTitle());
                                    problem.setLanguage(list.get(0).getLanguage());
                                    problem.setSection(list.get(0).getSection());
                                    problem.setTheme(list.get(0).getTheme());
                                    return problem;
                                })))
                    .values());
            
            // Filter out future dates
            LocalDate currentDate = LocalDate.now();
            mergedProblems = mergedProblems.stream()
                .filter(p -> isValidDate(p.getProblemDate(), DateTimeFormatter.ISO_LOCAL_DATE))
                .filter(p -> {
                    LocalDate problemDate = LocalDate.parse(p.getProblemDate(), DateTimeFormatter.ISO_LOCAL_DATE);
                    return !problemDate.isAfter(currentDate);
                })
                .collect(Collectors.toList());
            
            // Merge problems with same URL (across different dates)
            mergedProblems = mergeProblems(mergedProblems);
            
            // Calculate totals
            totalComments = mergedProblems.stream().mapToInt(Problem::getUrlEntries).sum();
            totalPages = mergedProblems.size();
            
            LOGGER.info("DashboardController: Preloaded totals - {} comments across {} pages", 
                totalComments, totalPages);
        } catch (Exception e) {
            LOGGER.error("DashboardController: Error calculating initial totals", e);
        }
        
        LOGGER.info("DashboardController: Initial data fetch and cache population complete.");
    }

    @GetMapping(value = "/dashboardData")
    @ResponseBody
    public DataTablesOutput<Problem> getDashboardData(
            @Valid DataTablesInput input, HttpServletRequest request) {
        long startTime = System.currentTimeMillis();
        long beforeUsedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        String pageLang = (String) request.getSession().getAttribute("lang");
        String department = request.getParameter("department");
        String startDate = request.getParameter("startDate");
        String endDate = request.getParameter("endDate");
        String language = request.getParameter("language");
        String url = request.getParameter("url");
        String comments = request.getParameter("comments");
        String section = request.getParameter("section");
        String theme = request.getParameter("theme");
        Boolean error_keyword = "true".equals(request.getParameter("error_keyword"));

        // Build base filter criteria (already includes date range check)
        Criteria criteria = buildFilterCriteria(startDate, endDate, theme, section, language, url, department);

        List<Criteria> regexCriteria = new ArrayList<>();

        // Add error keyword filtering if enabled
        if (error_keyword) {
            Set<String> keywordsToCheck = new HashSet<>();
            keywordsToCheck.addAll(errorKeywordService.getEnglishKeywords());
            keywordsToCheck.addAll(errorKeywordService.getFrenchKeywords());
            keywordsToCheck.addAll(errorKeywordService.getBilingualKeywords());

            if (!keywordsToCheck.isEmpty()) {
                String combinedRegex = keywordsToCheck.stream()
                        .map(Pattern::quote)
                        .collect(Collectors.joining("|"));
                regexCriteria.add(Criteria.where("problemDetails").regex(combinedRegex, "i"));
            }
        }

        // Add comment filtering if provided
        if (comments != null && !comments.trim().isEmpty() && !"null".equalsIgnoreCase(comments.trim())) {
            String escapedComment = escapeSpecialRegexCharacters(comments.trim());
            regexCriteria.add(Criteria.where("problemDetails").regex(escapedComment, "i"));
        }

        // Combine all criteria
        Criteria finalCriteria;
        if (!regexCriteria.isEmpty()) {
            List<Criteria> ands = new ArrayList<>();
            ands.add(criteria);
            ands.addAll(regexCriteria);
            finalCriteria = new Criteria().andOperator(ands.toArray(new Criteria[0]));
        } else {
            finalCriteria = criteria;
        }

        // OPTIMIZED: Use MongoDB aggregation pipeline instead of loading all data into cache
        MatchOperation match = Aggregation.match(finalCriteria);
        
        // Group by URL to get counts per URL
        GroupOperation groupByUrl = Aggregation.group("url")
                .first("url").as("url")
                .first("problemDate").as("problemDate")
                .first("institution").as("institution")
                .first("title").as("title")
                .first("language").as("language")
                .first("section").as("section")
                .first("theme").as("theme")
                .count().as("urlEntries");
        
        SortOperation sortByEntriesDesc = Aggregation.sort(Sort.Direction.DESC, "urlEntries");

        // Get paginated results
        Aggregation paginatedAgg = Aggregation.newAggregation(
                match,
                groupByUrl,
                sortByEntriesDesc,
                Aggregation.skip((long) input.getStart()),
                Aggregation.limit(input.getLength())
        );

        AggregationResults<Problem> results = mongoTemplate.aggregate(paginatedAgg, "problem", Problem.class);
        List<Problem> groupedProblems = results.getMappedResults();

        // Calculate totals (only for the filtered set)
        Aggregation totalAgg = Aggregation.newAggregation(match, groupByUrl);
        List<Problem> allGroupedProblems = mongoTemplate.aggregate(totalAgg, "problem", Problem.class).getMappedResults();
        
        totalPages = allGroupedProblems.size();
        totalComments = allGroupedProblems.stream().mapToInt(Problem::getUrlEntries).sum();

        // Create DataTablesOutput
        DataTablesOutput<Problem> output = new DataTablesOutput<>();
        output.setData(groupedProblems);
        output.setDraw(input.getDraw());
        output.setRecordsTotal(totalPages);
        output.setRecordsFiltered(totalPages);

        // Adjust institution names based on language
        setInstitution(output, pageLang);
        
        long afterUsedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long actualMemUsed = afterUsedMem - beforeUsedMem;
        long elapsedTime = System.currentTimeMillis() - startTime;
        
        LOGGER.info("OPTIMIZED getDashboardData() - Time: {}ms, Memory: {} bytes, Results: {}/{}",
                elapsedTime, actualMemUsed, groupedProblems.size(), totalPages);

        return output;
    }

    private static boolean isValidDate(String value, DateTimeFormatter formatter) {
        try {
            LocalDate.parse(value, formatter);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    //Helper method for criteria building with filters
    private Criteria buildFilterCriteria(String startDate, String endDate, String theme,
                                         String section, String language, String url,
                                         String department) {
        Criteria criteria = Criteria.where("processed").is("true");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        if (startDate != null && !startDate.isEmpty() && endDate != null && !endDate.isEmpty()) {
            LocalDate start = LocalDate.parse(startDate, formatter);
            LocalDate end = LocalDate.parse(endDate, formatter);
            criteria.and("problemDate").gte(start.format(formatter)).lte(end.format(formatter));
        }
        if (theme != null && !theme.isEmpty()) {
            criteria.and("theme").is(theme);
        }
        if (section != null && !section.isEmpty()) {
            criteria.and("section").in(sectionMappings.getOrDefault(section.toLowerCase(), Collections.singletonList(section)));
        }
        if (language != null && !language.isEmpty()) {
            criteria.and("language").is(language);
        }
        if (url != null && !url.isEmpty()) {
            criteria.and("url").regex(url, "i");
        }
        if (department != null && !department.isEmpty()) {
            Set<String> matchingVariations = new HashSet<>();
            for (Map.Entry<String, List<String>> entry : institutionMappings.entrySet()) {
                if (entry.getValue().stream().anyMatch(variation -> variation.equalsIgnoreCase(department))) {
                    matchingVariations.addAll(entry.getValue());
                }
            }
            if (!matchingVariations.isEmpty()) {
                criteria.and("institution").in(matchingVariations);
            }
        }
        return criteria;
    }

    private String escapeSpecialRegexCharacters(String input) {
        // Escape all regex metacharacters
        return input.replaceAll("([\\\\.^$|()\\[\\]{}*+?])", "\\\\$1");
    }


    private Problem createProblemFromResult(Map result) {
        Problem problem = new Problem();
        problem.setUrl((String) result.get("url"));
        problem.setProblemDate((String) result.get("day"));
        problem.setUrlEntries((Integer) result.get("count"));
        problem.setTitle((String) result.get("title"));
        problem.setLanguage((String) result.get("language"));
        problem.setInstitution((String) result.get("institution"));
        problem.setTheme((String) result.get("theme"));
        problem.setSection((String) result.get("section"));
        return problem;
    }

    private List<Problem> applyFilters(
            List<Problem> problems,
            String department,
            String startDate,
            String endDate,
            String language,
            String url,
            String section,
            String theme) {
        problems = applyDepartmentFilter(problems, department, institutionMappings);
        problems = applyDateRangeFilter(problems, startDate, endDate);
        problems = applyLanguageFilter(problems, language);
        problems = applyUrlFilter(problems, url);
        problems = applySectionFilter(problems, section);
        problems = applyThemeFilter(problems, theme);

        return problems;
    }

    // Extract filter methods here...

    private List<Problem> mergeProblems(List<Problem> problems) {
        Map<String, Problem> urlToProblemMap = new LinkedHashMap<>();

        for (Problem problem : problems) {
            urlToProblemMap.merge(
                    problem.getUrl(),
                    problem,
                    (existingProblem, newProblem) -> {
                        Problem updatedProblem = new Problem(existingProblem);
                        updatedProblem.setUrlEntries(
                                existingProblem.getUrlEntries() + newProblem.getUrlEntries());
                        return updatedProblem;
                    });
        }

        return new ArrayList<>(urlToProblemMap.values());
    }

    private List<Problem> applyLanguageFilter(List<Problem> problems, String language) {
        if (language != null && !language.isEmpty()) {
            return new ArrayList<>(problems).stream()
                    .filter(problem -> problem.getLanguage().equals(language))
                    .collect(Collectors.toList());
        }
        return problems;
    }

    // DEPT
    private List<Problem> applyDateRangeFilter(
            List<Problem> problems, String startDate, String endDate) {
        if (startDate != null && endDate != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDate start = LocalDate.parse(startDate, formatter);
            LocalDate end = LocalDate.parse(endDate, formatter);

            return new ArrayList<>(problems).stream()
                    .filter(problem -> isValidDate(problem.getProblemDate(), formatter))
                    .filter(
                            problem -> {
                                LocalDate problemDate = LocalDate.parse(problem.getProblemDate(), formatter);
                                return !problemDate.isBefore(start) && !problemDate.isAfter(end);
                            })
                    .collect(Collectors.toList());
        }
        return problems;
    }



    private List<Problem> applyDepartmentFilter(
            List<Problem> problems, String department, Map<String, List<String>> institutionMappings) {
        if (department != null && !department.isEmpty()) {
            Set<String> matchingVariations = new HashSet<>();
            // Filter variations based on department:
            for (Map.Entry<String, List<String>> entry : institutionMappings.entrySet()) {
                if (entry.getValue().stream()
                        .anyMatch(variation -> variation.equalsIgnoreCase(department))) {
                    matchingVariations.addAll(entry.getValue());
                }
            }
            if (!matchingVariations.isEmpty()) {
                return new ArrayList<>(problems).stream()
                        .filter(problem -> matchingVariations.contains(problem.getInstitution()))
                        .collect(Collectors.toList());
            }
        }
        return problems;
    }

    private List<Problem> applySectionFilter(List<Problem> problems, String section) {
        if (section != null && !section.isEmpty()) {
            return new ArrayList<>(problems).stream()
                    .filter(problem -> sectionMappings.getOrDefault(section.toLowerCase(), Collections.singletonList(section)).contains(problem.getSection()))
                    .collect(Collectors.toList());
        }
        return problems;
    }

    // theme
    private List<Problem> applyThemeFilter(List<Problem> problems, String theme) {
        if (theme != null && !theme.isEmpty()) {
            return new ArrayList<>(problems).stream()
                    .filter(problem -> problem.getTheme().equals(theme))
                    .collect(Collectors.toList());
        }
        return problems;
    }

    private List<Problem> applyUrlFilter(List<Problem> problems, String url) {
        if (url != null && !url.isEmpty()) {
            String filterUrl = url.toLowerCase();
            return new ArrayList<>(problems).stream()
                    .filter(problem -> problem.getUrl().toLowerCase().contains(filterUrl))
                    .collect(Collectors.toList());
        }
        return problems;
    }

    private List<Problem> applyPagination(List<Problem> mergedProblems, int start, int length) {
        return mergedProblems.stream().skip(start).limit(length).collect(Collectors.toList());
    }

    private void setInstitution(DataTablesOutput<Problem> problems, String lang) {
        for (Problem problem : problems.getData()) {
            String currentInstitution = problem.getInstitution();
            for (Map.Entry<String, List<String>> entry : institutionMappings.entrySet()) {
                if (entry.getValue().contains(currentInstitution)) {
                    // Assuming the translated institution name is at index 1 for French and index 0
                    // for other languages
                    problem.setInstitution(entry.getValue().get(lang.equalsIgnoreCase("fr") ? 1 : 0));
                    break; // Exit the loop once the institution is found and updated
                }
            }
        }
    }

    public UserService getUserService() {
        return userService;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }
}
