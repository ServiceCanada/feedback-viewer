package ca.gc.tbs.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.datatables.DataTablesInput;
import org.springframework.data.mongodb.datatables.DataTablesOutput;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import ca.gc.tbs.domain.Problem;
import ca.gc.tbs.service.DashboardService;
import ca.gc.tbs.service.ErrorKeywordService;
import ca.gc.tbs.service.ProblemDateService;
import ca.gc.tbs.service.UserService;

@Controller
public class DashboardController {

    private final ProblemDateService problemDateService;
    private final DashboardService dashboardService;
    private final UserService userService;
    private final ErrorKeywordService errorKeywordService;
    private final MongoTemplate mongoTemplate;

    public DashboardController(
        ProblemDateService problemDateService,
        DashboardService dashboardService,
        UserService userService,
        ErrorKeywordService errorKeywordService,
        MongoTemplate mongoTemplate) {
      this.problemDateService = problemDateService;
      this.dashboardService = dashboardService;
      this.userService = userService;
      this.errorKeywordService = errorKeywordService;
      this.mongoTemplate = mongoTemplate;
    }

    private static final Map<String, List<String>> institutionMappings = new HashMap<>();
    private static final Map<String, List<String>> sectionMappings = new HashMap<>();

    static {
        // Initialize section mappings
        sectionMappings.put("disability", Arrays.asList("disability", "disability benefits"));
        sectionMappings.put("news", Arrays.asList("news"));

        // Initialize institution mappings (kept as in the original file)
        institutionMappings.put("AAFC", Arrays.asList("AAFC", "AAC", "AGRICULTURE AND AGRI-FOOD CANADA", "AGRICULTURE ET AGROALIMENTAIRE CANADA", "AAFC/AAC"));
        institutionMappings.put("ACOA", Arrays.asList("ACOA", "APECA", "ATLANTIC CANADA OPPORTUNITIES AGENCY", "AGENCE DE PROMOTION ÉCONOMIQUE DU CANADA ATLANTIQUE", "ACOA/APECA"));
        institutionMappings.put("ATSSC", Arrays.asList("ATSSC", "SCDATA", "ADMINISTRATIVE TRIBUNALS SUPPORT SERVICE OF CANADA", "SERVICE CANADIEN D’APPUI AUX TRIBUNAUX ADMINISTRATIFS", "ATSSC/SCDATA"));
        institutionMappings.put("CANNOR", Arrays.asList("CANNOR", "RNCAN", "CANADIAN NORTHERN ECONOMIC DEVELOPMENT AGENCY", "AGENCE CANADIENNE DE DÉVELOPPEMENT ÉCONOMIQUE DU NORD", "CANNOR/RNCAN"));
        institutionMappings.put("CATSA", Arrays.asList("CATSA", "ACSTA", "CANADIAN AIR TRANSPORT SECURITY AUTHORITY", "ADMINISTRATION CANADIENNE DE LA SÛRETÉ DU TRANSPORT AÉRIEN", "CATSA/ACSTA"));
        institutionMappings.put("CBSA", Arrays.asList("CBSA", "ASFC", "CANADA BORDER SERVICES AGENCY", "AGENCE DES SERVICES FRONTALIERS DU CANADA", "CBSA/ASFC"));
        institutionMappings.put("CCG", Arrays.asList("CCG", "GCC", "CANADIAN COAST GUARD", "GARDE CÔTIÈRE CANADIENNE", "CCG/GCC"));
        institutionMappings.put("CER", Arrays.asList("CER", "REC", "CANADA ENERGY REGULATOR", "RÉGIE DE L'ÉNERGIE DU CANADA", "CER/REC"));
        institutionMappings.put("CFIA", Arrays.asList("CFIA", "ACIA", "CANADIAN FOOD INSPECTION AGENCY", "AGENCE CANADIENNE D’INSPECTION DES ALIMENTS", "CFIA/ACIA"));
        institutionMappings.put("CIHR", Arrays.asList("CIHR", "IRSC", "CANADIAN INSTITUTES OF HEALTH RESEARCH", "INSTITUTS DE RECHERCHE EN SANTÉ DU CANADA", "CIHR/IRSC"));
        institutionMappings.put("CIPO", Arrays.asList("CIPO", "OPIC", "CANADIAN INTELLECTUAL PROPERTY OFFICE", "OFFICE DE LA PROPRIÉTÉ INTELLECTUELLE DU CANADA", "CIPO/OPIC"));
        institutionMappings.put("CIRNAC", Arrays.asList("CIRNAC", "RCAANC", "CROWN-INDIGENOUS RELATIONS AND NORTHERN AFFAIRS CANADA", "RELATIONS COURONNE-AUTOCHTONES ET AFFAIRES DU NORD CANADA", "CIRNAC/RCAANC"));
        institutionMappings.put("CRA", Arrays.asList("CRA", "ARC", "CANADA REVENUE AGENCY", "AGENCE DU REVENU DU CANADA", "CRA/ARC"));
        institutionMappings.put("CRTC", Arrays.asList("CRTC", "CRTC", "CANADIAN RADIO-TELEVISION AND TELECOMMUNICATIONS COMMISSION", "CONSEIL DE LA RADIODIFFUSION ET DES TÉLÉCOMMUNICATIONS CANADIENNES"));
        institutionMappings.put("CSA", Arrays.asList("CSA", "ASC", "CANADIAN SPACE AGENCY", "AGENCE SPATIALE CANADIENNE", "CSA/ASC"));
        institutionMappings.put("CSC", Arrays.asList("CSC", "SCC", "CORRECTIONAL SERVICE CANADA", "SERVICE CORRECTIONNEL CANADA", "CSC/SCC"));
        institutionMappings.put("CSE", Arrays.asList("CSE", "CST", "COMMUNICATIONS SECURITY ESTABLISHMENT", "CENTRE DE LA SÉCURITÉ DES TÉLÉCOMMUNICATIONS", "CSE/CST"));
        institutionMappings.put("CSEC", Arrays.asList("CSEC", "CSTC", "COMMUNICATIONS SECURITY ESTABLISHMENT CANADA", "CENTRE DE LA SÉCURITÉ DES TÉLÉCOMMUNICATIONS CANADA", "CSEC/CSTC"));
        institutionMappings.put("CSPS", Arrays.asList("CSPS", "EFPC", "CANADA SCHOOL OF PUBLIC SERVICE", "ÉCOLE DE LA FONCTION PUBLIQUE DU CANADA", "CSPS/EFPC"));
        institutionMappings.put("DFO", Arrays.asList("DFO", "MPO", "FISHERIES AND OCEANS CANADA", "PÊCHES ET OCÉANS CANADA", "DFO/MPO", "GOVERNMENT OF CANADA, FISHERIES AND OCEANS CANADA, COMMUNICATIONS BRANCH"));
        institutionMappings.put("DND", Arrays.asList("DND", "MDN", "NATIONAL DEFENCE", "DÉFENSE NATIONALE", "DND/MDN"));
        institutionMappings.put("ECCC", Arrays.asList("ECCC", "ENVIRONMENT AND CLIMATE CHANGE CANADA", "ENVIRONNEMENT ET CHANGEMENT CLIMATIQUE CANADA", "ECCC"));
        institutionMappings.put("ESDC", Arrays.asList("ESDC", "EDSC", "EMPLOYMENT AND SOCIAL DEVELOPMENT CANADA", "EMPLOI ET DÉVELOPPEMENT SOCIAL CANADA", "ESDC/EDSC", "EMPLOI ET DÉVÉLOPPEMENT SOCIALE CANADA"));
        institutionMappings.put("FCAC", Arrays.asList("FCAC", "ACFC", "FINANCIAL CONSUMER AGENCY OF CANADA", "AGENCE DE LA CONSOMMATION EN MATIÈRE FINANCIÈRE DU CANADA", "FCAC/ACFC"));
        institutionMappings.put("FIN", Arrays.asList("FIN", "FIN", "FINANCE CANADA", "MINISTÈRE DES FINANCES CANADA", "DEPARTMENT OF FINANCE CANADA", "GOVERNMENT OF CANADA, DEPARTMENT OF FINANCE", "MINISTÈRE DES FINANCES", "FIN"));
        institutionMappings.put("GAC", Arrays.asList("GAC", "AMC", "GLOBAL AFFAIRS CANADA", "AFFAIRES MONDIALES CANADA", "GAC/AMC"));
        institutionMappings.put("HC", Arrays.asList("HC", "SC", "HEALTH CANADA", "SANTÉ CANADA", "HC/SC"));
        institutionMappings.put("HICC", Arrays.asList("HICC", "LICC", "HOUSING, INFRASTRUCTURE AND COMMUNITIES CANADA", "LOGEMENT, INFRASTRUCTURES ET COLLECTIVITÉS CANADA", "HICC/LICC"));
        institutionMappings.put("INFC", Arrays.asList("INFC", "INFC", "INFRASTRUCTURE CANADA", "INFRASTRUCTURE CANADA", "INFC / INFC"));
        institutionMappings.put("IOGC", Arrays.asList("IOGC", "BPGI", "INDIAN OIL AND GAS CANADA", "BUREAU DU PÉTROLE ET DU GAZ DES INDIENS", "IOGC/BPGI"));
        institutionMappings.put("IRCC", Arrays.asList("IRCC", "IRCC", "IMMIGRATION, REFUGEES AND CITIZENSHIP CANADA", "IMMIGRATION, RÉFUGIÉS ET CITOYENNETÉ CANADA"));
        institutionMappings.put("ISC", Arrays.asList("ISC", "SAC", "INDIGENOUS SERVICES CANADA", "SERVICES AUX AUTOCHTONES CANADA", "ISC/SAC"));
        institutionMappings.put("ISED", Arrays.asList("ISED", "ISDE", "INNOVATION, SCIENCE AND ECONOMIC DEVELOPMENT CANADA", "INNOVATION, SCIENCES ET DÉVELOPPEMENT ÉCONOMIQUE CANADA", "ISED/ISDE"));
        institutionMappings.put("JUS", Arrays.asList("JUS", "JUSTICE CANADA", "MINISTÈRE DE LA JUSTICE CANADA", "JUS"));
        institutionMappings.put("LAC", Arrays.asList("LAC", "BAC", "LIBRARY AND ARCHIVES CANADA", "BIBLIOTHÈQUE ET ARCHIVES CANADA", "LAC/BAC"));
        institutionMappings.put("NFB", Arrays.asList("NFB", "ONF", "NATIONAL FILM BOARD", "OFFICE NATIONAL DU FILM", "NFB/ONF"));
        institutionMappings.put("NRC", Arrays.asList("NRC", "CNRC", "NATIONAL RESEARCH COUNCIL", "CONSEIL NATIONAL DE RECHERCHES CANADA", "NRC/CNRC"));
        institutionMappings.put("NRCAN", Arrays.asList("NRCAN", "RNCAN", "NATURAL RESOURCES CANADA", "RESSOURCES NATURELLES CANADA", "NRCAN/RNCAN"));
        institutionMappings.put("NSERC", Arrays.asList("NSERC", "CRSNG", "NATURAL SCIENCES AND ENGINEERING RESEARCH CANADA", "CONSEIL DE RECHERCHES EN SCIENCES NATURELLES ET EN GÉNIE DU CANADA", "NSERC/CRSNG"));
        institutionMappings.put("OMBDNDCAF", Arrays.asList("OMBDNDCAF", "OMBMDNFAC", "DND/CAF OMBUDSMAN", "OMBUDSMAN DU MDN/FAC", "OFFICE OF THE NATIONAL DEFENCE AND CANADIAN ARMED FORCES OMBUDSMAN", "BUREAU DE L'OMBUDSMAN DE LA DÉFENSE NATIONALE ET DES FORCES ARMÉES CANADIENNES", "OMBDNDCAF/OMBMDNFAC"));
        institutionMappings.put("OSB", Arrays.asList("OSB", "BSF", "SUPERINTENDENT OF BANKRUPTCY CANADA", "BUREAU DU SURINTENDANT DES FAILLITES CANADA", "OSB/BSF"));
        institutionMappings.put("PBC", Arrays.asList("PBC", "CLCC", "PAROLE BOARD OF CANADA", "COMMISSION DES LIBÉRATIONS CONDITIONNELLES DU CANADA", "PBC/CLCC"));
        institutionMappings.put("PC", Arrays.asList("PC", "PC", "PARCS CANADA", "PARKS CANADA"));
        institutionMappings.put("PCH", Arrays.asList("PCH", "PCH", "CANADIAN HERITAGE", "PATRIMOINE CANADIEN"));
        institutionMappings.put("PCO", Arrays.asList("PCO", "BCP", "PRIVY COUNCIL OFFICE", "BUREAU DU CONSEIL PRIVÉ", "PCO/BCP"));
        institutionMappings.put("PHAC", Arrays.asList("PHAC", "ASPC", "PUBLIC HEALTH AGENCY OF CANADA", "AGENCE DE LA SÉAUTÉ PUBLIQUE DU CANADA", "PHAC/ASPC"));
        institutionMappings.put("PS", Arrays.asList("PS", "SP", "PUBLIC SAFETY CANADA", "SÉCURITÉ PUBLIQUE CANADA", "PS/SP"));
        institutionMappings.put("PSC", Arrays.asList("PSC", "CFP", "PUBLIC SERVICE COMMISSION OF CANADA", "COMMISSION DE LA FONCTION PUBLIQUE DU CANADA", "PSC/CFP"));
        institutionMappings.put("PSPC", Arrays.asList("PSPC", "SPAC", "PUBLIC SERVICES AND PROCUREMENT CANADA", "SERVICES PUBLICS ET APPROVISIONNEMENT CANADA", "GOUVERNEMENT DU CANADA, SERVICES PUBLICS ET APPROVISIONNEMENT CANADA", "GOVERNMENT OF CANADA, PUBLIC SERVICES AND PROCUREMENT CANADA", "PSPC/SPAC"));
        institutionMappings.put("RCMP", Arrays.asList("RCMP", "GRC", "ROYAL CANADIAN MOUNTED POLICE", "GENDARMERIE ROYALE DU CANADA", "RCMP/GRC"));
        institutionMappings.put("SC", Arrays.asList("SC", "SC", "SERVICE CANADA", "SERVICE CANADA", "SC/SC"));
        institutionMappings.put("SSC", Arrays.asList("SSC", "PSC", "SHARED SERVICES CANADA", "SERVICES PARTAGÉS CANADA", "SSC/PSC"));
        institutionMappings.put("SSHRC", Arrays.asList("SSHRC", "CRSH", "SOCIAL SCIENCES AND HUMANITIES RESEARCH COUNCIL", "CONSEIL DE RECHERCHES EN SCIENCES HUMAINES", "SSHRC/CRSH"));
        institutionMappings.put("SST", Arrays.asList("SST", "TSS", "SOCIAL SECURITY TRIBUNAL OF CANADA", "TRIBUNAL DE LA SÉCURITÉ SOCIALE DU CANADA", "SST/TSS"));
        institutionMappings.put("STATCAN", Arrays.asList("STATCAN", "STATISTIQUE CANADA"));
        institutionMappings.put("TBS", Arrays.asList("TBS", "SCT", "TREASURY BOARD OF CANADA SECRETARIAT", "SECRÉTARIAT DU CONSEIL DU TRÉSOR DU CANADA", "TBS/SCT"));
        institutionMappings.put("TC", Arrays.asList("TC", "TC", "TRANSPORT CANADA", "TRANSPORTS CANADA"));
        institutionMappings.put("VAC", Arrays.asList("VAC", "ACC", "VETERANS AFFAIRS CANADA", "ANCIENS COMBATTANTS CANADA", "VAC/ACC"));
        institutionMappings.put("WAGE", Arrays.asList("WAGE", "FEGC", "WOMEN AND GENDER EQUALITY CANADA", "FEMMES ET ÉGALITÉ DES GENRES CANADA", "WAGE/FEGC"));
        institutionMappings.put("WD", Arrays.asList("WD", "DEO", "WESTERN ECONOMIC DIVERSIFICATION CANADA", "DIVERSIFICATION DE L’ÉCONOMIE DE L’OUEST CANADA", "WD/DEO"));
    }

    @RequestMapping(value = "/pageFeedback/totalCommentsCount")
    @ResponseBody
    public String totalCommentsCount() {
        return String.valueOf(dashboardService.getDashboardStats().totalComments());
    }

    @RequestMapping(value = "/pageFeedback/totalPagesCount")
    @ResponseBody
    public String totalPagesCount() {
        return String.valueOf(dashboardService.getDashboardStats().totalPages());
    }

    @GetMapping(value = "/dashboard")
    public ModelAndView pageFeedback(HttpServletRequest request) {
        var mav = new ModelAndView();
        String lang = (String) request.getSession().getAttribute("lang");
        mav.addObject("lang", lang);
        var dateMap = problemDateService.getProblemDates();
        if (dateMap != null) {
            mav.addObject("earliestDate", dateMap.get("earliestDate"));
            var latestDate = LocalDate.parse(dateMap.get("latestDate"), DateTimeFormatter.ISO_LOCAL_DATE);
            var previousDate = latestDate.minusDays(1);
            var modifiedLatestDate = previousDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
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
        String error_keyword_param = request.getParameter("error_keyword");
        boolean error_keyword = "true".equals(error_keyword_param);
        String comments = request.getParameter("comments");
        String startDate = request.getParameter("startDate");
        String endDate = request.getParameter("endDate");
        String theme = request.getParameter("theme");
        String section = request.getParameter("section");
        String language = request.getParameter("language");
        String url = request.getParameter("url");
        String department = request.getParameter("department");

        boolean useDatabase = error_keyword || (comments != null && !comments.trim().isEmpty() && !"null".equalsIgnoreCase(comments.trim()));

        if (useDatabase) {
            var criteria = buildFilterCriteria(startDate, endDate, theme, section, language, url, department);
            var regexCriteria = new ArrayList<Criteria>();
            if (error_keyword) {
                var keywordsToCheck = new HashSet<String>();
                keywordsToCheck.addAll(errorKeywordService.getEnglishKeywords());
                keywordsToCheck.addAll(errorKeywordService.getFrenchKeywords());
                keywordsToCheck.addAll(errorKeywordService.getBilingualKeywords());
                if (!keywordsToCheck.isEmpty()) {
                    String combinedRegex = keywordsToCheck.stream().map(Pattern::quote).collect(Collectors.joining("|"));
                    regexCriteria.add(Criteria.where("problemDetails").regex(combinedRegex, "i"));
                }
            }
            if (comments != null && !comments.trim().isEmpty() && !"null".equalsIgnoreCase(comments.trim())) {
                regexCriteria.add(Criteria.where("problemDetails").regex(escapeSpecialRegexCharacters(comments.trim()), "i"));
            }

            var finalCriteria = !regexCriteria.isEmpty()
                ? new Criteria().andOperator(criteria, new Criteria().andOperator(regexCriteria.toArray(new Criteria[0])))
                : criteria;

            var groupByDate = Aggregation.group("problemDate").count().as("comments");
            var sortByDate = Aggregation.sort(Sort.Direction.ASC, "_id");
            var aggResults = mongoTemplate.aggregate(
                    Aggregation.newAggregation(Aggregation.match(finalCriteria), groupByDate, sortByDate),
                    "problem", Document.class);

            var dailyCommentsList = new ArrayList<Map<String, Object>>();
            for (Document doc : aggResults) {
                var map = new HashMap<String, Object>();
                map.put("date", doc.getString("_id"));
                map.put("comments", doc.getInteger("comments", 0));
                dailyCommentsList.add(map);
            }
            return dailyCommentsList;
        }

        var stats = dashboardService.getDashboardStats();
        var problemsByDate = stats.problemsByDate();

        var dateToCommentCountMap = new HashMap<String, Integer>();
        for (Problem problem : problemsByDate) {
            if (problem != null && problem.getProblemDate() != null) {
                dateToCommentCountMap.merge(problem.getProblemDate(), problem.getUrlEntries(), Integer::sum);
            }
        }

        var dailyCommentsList = new ArrayList<Map<String, Object>>();
        dateToCommentCountMap.forEach((date, count) -> {
            var entry = new HashMap<String, Object>();
            entry.put("date", date);
            entry.put("comments", count);
            dailyCommentsList.add(entry);
        });
        dailyCommentsList.sort(Comparator.comparing(map -> (String) map.get("date")));
        return dailyCommentsList;
    }

    @GetMapping(value = "/dashboardData")
    @ResponseBody
    public DataTablesOutput<Problem> getDashboardData(@Valid DataTablesInput input, HttpServletRequest request) {
        String pageLang = (String) request.getSession().getAttribute("lang");
        String department = request.getParameter("department");
        String startDate = request.getParameter("startDate");
        String endDate = request.getParameter("endDate");
        String language = request.getParameter("language");
        String url = request.getParameter("url");
        String comments = request.getParameter("comments");
        String section = request.getParameter("section");
        String theme = request.getParameter("theme");
        boolean error_keyword = "true".equals(request.getParameter("error_keyword"));

        boolean hasRegexFilter = error_keyword || (comments != null && !comments.trim().isEmpty() && !"null".equalsIgnoreCase(comments.trim()));

        if (hasRegexFilter) {
            return getDashboardDataViaAggregation(input, pageLang, startDate, endDate, theme, section, language, url, department, comments, error_keyword);
        }

        var stats = dashboardService.getDashboardStats();
        var filtered = applyFilters(new ArrayList<>(stats.problemsByDate()), department, startDate, endDate, language, url, section, theme);
        var merged = mergeProblems(filtered);
        merged.sort(Comparator.comparingInt(Problem::getUrlEntries).reversed());

        int filteredTotalPages = merged.size();
        var page = merged.stream().skip(input.getStart()).limit(input.getLength()).collect(Collectors.toList());

        var output = new DataTablesOutput<Problem>();
        output.setData(page);
        output.setDraw(input.getDraw());
        output.setRecordsTotal(filteredTotalPages);
        output.setRecordsFiltered(filteredTotalPages);
        setInstitutionNames(output, pageLang);
        return output;
    }

    private DataTablesOutput<Problem> getDashboardDataViaAggregation(
            DataTablesInput input, String pageLang,
            String startDate, String endDate, String theme, String section,
            String language, String url, String department,
            String comments, boolean error_keyword) {

        var criteria = buildFilterCriteria(startDate, endDate, theme, section, language, url, department);
        var regexCriteria = new ArrayList<Criteria>();
        if (error_keyword) {
            var keywords = new HashSet<String>();
            keywords.addAll(errorKeywordService.getEnglishKeywords());
            keywords.addAll(errorKeywordService.getFrenchKeywords());
            keywords.addAll(errorKeywordService.getBilingualKeywords());
            if (!keywords.isEmpty()) {
                String combinedRegex = keywords.stream().map(Pattern::quote).collect(Collectors.joining("|"));
                regexCriteria.add(Criteria.where("problemDetails").regex(combinedRegex, "i"));
            }
        }
        if (comments != null && !comments.trim().isEmpty() && !"null".equalsIgnoreCase(comments.trim())) {
            regexCriteria.add(Criteria.where("problemDetails").regex(escapeSpecialRegexCharacters(comments.trim()), "i"));
        }
        if (!regexCriteria.isEmpty()) {
            criteria = new Criteria().andOperator(criteria, new Criteria().andOperator(regexCriteria.toArray(new Criteria[0])));
        }

        var match = Aggregation.match(criteria);
        var groupByUrl = Aggregation.group("url")
                .first("url").as("url")
                .first("problemDate").as("problemDate")
                .first("institution").as("institution")
                .first("title").as("title")
                .first("language").as("language")
                .first("section").as("section")
                .first("theme").as("theme")
                .count().as("urlEntries");
        var sortDesc = Aggregation.sort(Sort.Direction.DESC, "urlEntries");

        var page = mongoTemplate.aggregate(
                Aggregation.newAggregation(match, groupByUrl, sortDesc, Aggregation.skip((long) input.getStart()), Aggregation.limit(input.getLength())),
                "problem", Problem.class).getMappedResults();

        var totalsDoc = mongoTemplate.aggregate(
                Aggregation.newAggregation(match, groupByUrl, Aggregation.group().count().as("pages").sum("urlEntries").as("comments")),
                "problem", Document.class).getUniqueMappedResult();

        int totalP = 0;
        if (totalsDoc != null) {
            totalP = totalsDoc.getInteger("pages", 0);
        }

        var output = new DataTablesOutput<Problem>();
        output.setData(page);
        output.setDraw(input.getDraw());
        output.setRecordsTotal(totalP);
        output.setRecordsFiltered(totalP);
        setInstitutionNames(output, pageLang);
        return output;
    }

    private Criteria buildFilterCriteria(String startDate, String endDate, String theme, String section, String language, String url, String department) {
        var criteria = Criteria.where("processed").is("true");
        var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        if (startDate != null && !startDate.isEmpty() && endDate != null && !endDate.isEmpty()) {
            criteria.and("problemDate").gte(startDate).lte(endDate);
        }
        if (theme != null && !theme.isEmpty()) criteria.and("theme").is(theme);
        if (section != null && !section.isEmpty()) {
            criteria.and("section").in(sectionMappings.getOrDefault(section.toLowerCase(), Collections.singletonList(section)));
        }
        if (language != null && !language.isEmpty()) criteria.and("language").is(language);
        if (url != null && !url.isEmpty()) criteria.and("url").regex(url, "i");
        if (department != null && !department.isEmpty()) {
            var variations = new HashSet<String>();
            for (List<String> list : institutionMappings.values()) {
                if (list.stream().anyMatch(v -> v.equalsIgnoreCase(department))) variations.addAll(list);
            }
            if (!variations.isEmpty()) criteria.and("institution").in(variations);
        }
        return criteria;
    }

    private List<Problem> applyFilters(List<Problem> problems, String department, String startDate, String endDate, String language, String url, String section, String theme) {
        var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        var stream = problems.stream();

        if (department != null && !department.isEmpty()) {
            var variations = new HashSet<String>();
            for (List<String> list : institutionMappings.values()) {
                if (list.stream().anyMatch(v -> v.equalsIgnoreCase(department))) variations.addAll(list);
            }
            if (!variations.isEmpty()) stream = stream.filter(p -> variations.contains(p.getInstitution()));
        }

        if (startDate != null && endDate != null) {
            var start = LocalDate.parse(startDate, formatter);
            var end = LocalDate.parse(endDate, formatter);
            stream = stream.filter(p -> {
                try {
                    LocalDate d = LocalDate.parse(p.getProblemDate(), formatter);
                    return !d.isBefore(start) && !d.isAfter(end);
                } catch (Exception e) { return false; }
            });
        }
        if (language != null && !language.isEmpty()) stream = stream.filter(p -> language.equals(p.getLanguage()));
        if (url != null && !url.isEmpty()) stream = stream.filter(p -> p.getUrl().toLowerCase().contains(url.toLowerCase()));
        if (section != null && !section.isEmpty()) {
            List<String> sections = sectionMappings.getOrDefault(section.toLowerCase(), Collections.singletonList(section));
            stream = stream.filter(p -> sections.contains(p.getSection()));
        }
        if (theme != null && !theme.isEmpty()) stream = stream.filter(p -> theme.equals(p.getTheme()));

        return stream.collect(Collectors.toList());
    }

    private List<Problem> mergeProblems(List<Problem> problems) {
        var map = new LinkedHashMap<String, Problem>();
        for (Problem p : problems) {
            map.merge(p.getUrl(), p, (o, n) -> {
                Problem updated = new Problem(o);
                updated.setUrlEntries(o.getUrlEntries() + n.getUrlEntries());
                return updated;
            });
        }
        return new ArrayList<>(map.values());
    }

    private void setInstitutionNames(DataTablesOutput<Problem> output, String lang) {
        for (Problem p : output.getData()) {
            for (List<String> variations : institutionMappings.values()) {
                if (variations.contains(p.getInstitution())) {
                    p.setInstitution(variations.get("fr".equalsIgnoreCase(lang) ? 1 : 0));
                    break;
                }
            }
        }
    }

    private String escapeSpecialRegexCharacters(String input) {
        return input.replaceAll("([\\\\.^$|()\\[\\]{}*+?])", "\\\\$1");
    }

}
