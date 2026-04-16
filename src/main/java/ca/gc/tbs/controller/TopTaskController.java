package ca.gc.tbs.controller;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.datatables.DataTablesInput;
import org.springframework.data.mongodb.datatables.DataTablesOutput;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import ca.gc.tbs.domain.TopTaskSurvey;
import ca.gc.tbs.domain.User;
import ca.gc.tbs.repository.TopTaskRepository;
import ca.gc.tbs.security.JWTUtil;
import ca.gc.tbs.service.ProblemDateService;
import ca.gc.tbs.service.UserService;

@Controller
public class TopTaskController {

  private static final Logger LOG = LoggerFactory.getLogger(TopTaskController.class);

  private final TopTaskRepository topTaskRepository;

  private int totalDistinctTasks = 0;

  private int totalTaskCount = 0;

  private final UserService userService;

  private final ProblemDateService problemDateService;

  private final MongoTemplate mongoTemplate;

  private final JWTUtil jwtUtil;

  public TopTaskController(
      TopTaskRepository topTaskRepository,
      UserService userService,
      ProblemDateService problemDateService,
      MongoTemplate mongoTemplate,
      JWTUtil jwtUtil) {
    this.topTaskRepository = topTaskRepository;
    this.userService = userService;
    this.problemDateService = problemDateService;
    this.mongoTemplate = mongoTemplate;
    this.jwtUtil = jwtUtil;
  }
  private static final Map<String, List<String>> institutionMappings = new HashMap<>();

  static {
    institutionMappings.put(
        "AAFC",
        Arrays.asList(
            "AAFC",
            "AAC",
            "AGRICULTURE AND AGRI-FOOD CANADA",
            "AGRICULTURE ET AGROALIMENTAIRE CANADA",
            "AAFC / AAC"));
    institutionMappings.put(
        "ACOA",
        Arrays.asList(
            "ACOA",
            "APECA",
            "ATLANTIC CANADA OPPORTUNITIES AGENCY",
            "AGENCE DE PROMOTION ÉCONOMIQUE DU CANADA ATLANTIQUE",
            "ACOA / APECA"));
    institutionMappings.put(
        "ATSSC",
        Arrays.asList(
            "ATSSC",
            "SCDATA",
            "ADMINISTRATIVE TRIBUNALS SUPPORT SERVICE OF CANADA",
            "SERVICE CANADIEN D’APPUI AUX TRIBUNAUX ADMINISTRATIFS",
            "ATSSC / SCDATA"));
    institutionMappings.put(
        "CANNOR",
        Arrays.asList(
            "CANNOR",
            "RNCAN",
            "CANADIAN NORTHERN ECONOMIC DEVELOPMENT AGENCY",
            "AGENCE CANADIENNE DE DÉVELOPPEMENT ÉCONOMIQUE DU NORD",
            "CANNOR / RNCAN"));
    institutionMappings.put(
        "CATSA",
        Arrays.asList(
            "CATSA",
            "ACSTA",
            "CANADIAN AIR TRANSPORT SECURITY AUTHORITY",
            "ADMINISTRATION CANADIENNE DE LA SÛRETÉ DU TRANSPORT AÉRIEN",
            "CATSA / ACSTA"));
    institutionMappings.put(
        "CBSA",
        Arrays.asList(
            "CBSA",
            "ASFC",
            "CANADA BORDER SERVICES AGENCY",
            "AGENCE DES SERVICES FRONTALIERS DU CANADA",
            "CBSA / ASFC"));
    institutionMappings.put(
        "CCG",
        Arrays.asList(
            "CCG", "GCC", "CANADIAN COAST GUARD", "GARDE CÔTIÈRE CANADIENNE", "CCG / GCC"));
    institutionMappings.put(
        "CER",
        Arrays.asList(
            "CER", "REC", "CANADA ENERGY REGULATOR", "RÉGIE DE L'ÉNERGIE DU CANADA", "CER / REC"));
    institutionMappings.put(
        "CFIA",
        Arrays.asList(
            "CFIA",
            "ACIA",
            "CANADIAN FOOD INSPECTION AGENCY",
            "AGENCE CANADIENNE D’INSPECTION DES ALIMENTS",
            "CFIA / ACIA"));
    institutionMappings.put(
        "CGC",
        Arrays.asList(
            "CGC",
            "CCG",
            "CANADIAN GRAIN COMMISSION",
            "COMMISSION CANADIENNE DES GRAINS",
            "CGC / CCG"));
    institutionMappings.put(
        "CIHR",
        Arrays.asList(
            "CIHR",
            "IRSC",
            "CANADIAN INSTITUTES OF HEALTH RESEARCH",
            "INSTITUTS DE RECHERCHE EN SANTÉ DU CANADA",
            "CIHR / IRSC"));
    institutionMappings.put(
        "CIPO",
        Arrays.asList(
            "CIPO",
            "OPIC",
            "CANADIAN INTELLECTUAL PROPERTY OFFICE",
            "OFFICE DE LA PROPRIÉTÉ INTELLECTUELLE DU CANADA",
            "CIPO / OPIC"));
    institutionMappings.put(
        "CIRNAC",
        Arrays.asList(
            "CIRNAC",
            "RCAANC",
            "CROWN-INDIGENOUS RELATIONS AND NORTHERN AFFAIRS CANADA",
            "RELATIONS COURONNE-AUTOCHTONES ET AFFAIRES DU NORD CANADA",
            "CIRNAC / RCAANC"));
    institutionMappings.put(
        "CRA",
        Arrays.asList(
            "CRA", "ARC", "CANADA REVENUE AGENCY", "AGENCE DU REVENU DU CANADA", "CRA / ARC"));
    institutionMappings.put(
        "CRTC",
        Arrays.asList(
            "CRTC",
            "CRTC",
            "CANADIAN RADIO-TELEVISION AND TELECOMMUNICATIONS COMMISSION",
            "CONSEIL DE LA RADIODIFFUSION ET DES TÉLÉCOMMUNICATIONS CANADIENNES",
            "CRTC / CRTC"));
    institutionMappings.put(
        "CSA",
        Arrays.asList(
            "CSA", "ASC", "CANADIAN SPACE AGENCY", "AGENCE SPATIALE CANADIENNE", "CSA / ASC"));
    institutionMappings.put(
        "CSC",
        Arrays.asList(
            "CSC",
            "SCC",
            "CORRECTIONAL SERVICE CANADA",
            "SERVICE CORRECTIONNEL CANADA",
            "CSC / SCC"));
    institutionMappings.put(
        "CSE",
        Arrays.asList(
            "CSE",
            "CST",
            "COMMUNICATIONS SECURITY ESTABLISHMENT",
            "CENTRE DE LA SÉCURITÉ DES TÉLÉCOMMUNICATIONS",
            "CSE / CST"));
    institutionMappings.put(
        "CSEC",
        Arrays.asList(
            "CSEC",
            "CSTC",
            "COMMUNICATIONS SECURITY ESTABLISHMENT CANADA",
            "CENTRE DE LA SÉCURITÉ DES TÉLÉCOMMUNICATIONS CANADA",
            "CSEC / CSTC"));
    institutionMappings.put(
        "CSPS",
        Arrays.asList(
            "CSPS",
            "EFPC",
            "CANADA SCHOOL OF PUBLIC SERVICE",
            "ÉCOLE DE LA FONCTION PUBLIQUE DU CANADA",
            "CSPS / EFPC"));
    institutionMappings.put(
        "DFO",
        Arrays.asList(
            "DFO", "MPO", "FISHERIES AND OCEANS CANADA", "PÊCHES ET OCÉANS CANADA", "DFO / MPO"));
    institutionMappings.put(
        "DND", Arrays.asList("DND", "MDN", "NATIONAL DEFENCE", "DÉFENSE NATIONALE", "DND / MDN"));
    institutionMappings.put(
        "ECCC",
        Arrays.asList(
            "ECCC",
            "ECCC",
            "ENVIRONMENT AND CLIMATE CHANGE CANADA",
            "ENVIRONNEMENT ET CHANGEMENT CLIMATIQUE CANADA",
            "ECCC / ECCC"));
    institutionMappings.put(
        "ESDC",
        Arrays.asList(
            "ESDC",
            "EDSC",
            "EMPLOYMENT AND SOCIAL DEVELOPMENT CANADA",
            "EMPLOI ET DÉVELOPPEMENT SOCIAL CANADA",
            "ESDC / EDSC"));
    institutionMappings.put(
        "FCAC",
        Arrays.asList(
            "FCAC",
            "ACFC",
            "FINANCIAL CONSUMER AGENCY OF CANADA",
            "AGENCE DE LA CONSOMMATION EN MATIÈRE FINANCIÈRE DU CANADA",
            "FCAC / ACFC"));
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
            "FIN / FIN"));
    institutionMappings.put(
        "GAC",
        Arrays.asList(
            "GAC", "AMC", "GLOBAL AFFAIRS CANADA", "AFFAIRES MONDIALES CANADA", "GAC / AMC"));
    institutionMappings.put(
        "HC", Arrays.asList("HC", "SC", "HEALTH CANADA", "SANTÉ CANADA", "HC / SC"));
    institutionMappings.put(
        "INFC",
        Arrays.asList(
            "HICC",
            "LICC",
            "HOUSING, INFRASTRUCTURE AND COMMUNITIES CANADA",
            "LOGEMENT, INFRASTRUCTURES ET COLLECTIVITÉS CANADA",
            "INFC / INFC",
            "INFC",
            "INFRASTRUCTURE CANADA",
            "INFRASTRUCTURE CANADA",
            "HICC / LICC"));
    institutionMappings.put(
        "IOGC",
        Arrays.asList(
            "IOGC",
            "BPGI",
            "INDIAN OIL AND GAS CANADA",
            "BUREAU DU PÉTROLE ET DU GAZ DES INDIENS",
            "IOGC / BPGI"));
    institutionMappings.put(
        "IRCC",
        Arrays.asList(
            "IRCC",
            "IRCC",
            "IMMIGRATION, REFUGEES AND CITIZENSHIP CANADA",
            "IMMIGRATION, RÉFUGIÉS ET CITOYENNETÉ CANADA",
            "IRCC / IRCC"));
    institutionMappings.put(
        "ISC",
        Arrays.asList(
            "ISC",
            "SAC",
            "INDIGENOUS SERVICES CANADA",
            "SERVICES AUX AUTOCHTONES CANADA",
            "ISC / SAC"));
    institutionMappings.put(
        "ISED",
        Arrays.asList(
            "ISED",
            "ISDE",
            "INNOVATION, SCIENCE AND ECONOMIC DEVELOPMENT CANADA",
            "INNOVATION, SCIENCES ET DÉVELOPPEMENT ÉCONOMIQUE CANADA",
            "ISED / ISDE"));
    institutionMappings.put(
        "JUS",
        Arrays.asList(
            "JUS", "JUS", "JUSTICE CANADA", "MINISTÈRE DE LA JUSTICE CANADA", "JUS / JUS"));
    institutionMappings.put(
        "LAC",
        Arrays.asList(
            "LAC",
            "BAC",
            "LIBRARY AND ARCHIVES CANADA",
            "BIBLIOTHÈQUE ET ARCHIVES CANADA",
            "LAC / BAC"));
    institutionMappings.put(
        "NFB",
        Arrays.asList("NFB", "ONF", "NATIONAL FILM BOARD", "OFFICE NATIONAL DU FILM", "NFB / ONF"));
    institutionMappings.put(
        "NRC",
        Arrays.asList(
            "NRC",
            "CNRC",
            "NATIONAL RESEARCH COUNCIL",
            "CONSEIL NATIONAL DE RECHERCHES CANADA",
            "NRC / CNRC"));
    institutionMappings.put(
        "NRCAN",
        Arrays.asList(
            "NRCAN",
            "RNCAN",
            "NATURAL RESOURCES CANADA",
            "RESSOURCES NATURELLES CANADA",
            "NRCAN / RNCAN"));
    institutionMappings.put(
        "NSERC",
        Arrays.asList(
            "NSERC",
            "CRSNG",
            "NATURAL SCIENCES AND ENGINEERING RESEARCH CANADA",
            "CONSEIL DE RECHERCHES EN SCIENCES NATURELLES ET EN GÉNIE DU CANADA",
            "NSERC / CRSNG"));
    institutionMappings.put(
        "OMBDNDCAF",
        Arrays.asList(
            "OMBDNDCAF",
            "OMBMDNFAC",
            "DND / CAF OMBUDSMAN",
            "OMBUDSMAN DU MDN / FAC",
            "OFFICE OF THE NATIONAL DEFENCE AND CANADIAN ARMED FORCES OMBUDSMAN",
            "BUREAU DE L'OMBUDSMAN DE LA DÉFENSE NATIONALE ET DES FORCES ARMÉES CANADIENNES",
            "OMBDNDCAF / OMBMDNFAC"));
    institutionMappings.put(
        "OSB",
        Arrays.asList(
            "OSB",
            "BSF",
            "SUPERINTENDENT OF BANKRUPTCY CANADA",
            "BUREAU DU SURINTENDANT DES FAILLITES CANADA",
            "OSB / BSF"));
    institutionMappings.put(
        "PBC",
        Arrays.asList(
            "PBC",
            "CLCC",
            "PAROLE BOARD OF CANADA",
            "COMMISSION DES LIBÉRATIONS CONDITIONNELLES DU CANADA",
            "PBC / CLCC"));
    institutionMappings.put(
        "PC", Arrays.asList("PC", "PC", "PARCS CANADA", "PARKS CANADA", "PC / PC"));
    institutionMappings.put(
        "PCH",
        Arrays.asList("PCH", "PCH", "CANADIAN HERITAGE", "PATRIMOINE CANADIEN", "PCH / PCH"));
    institutionMappings.put(
        "PCO",
        Arrays.asList(
            "PCO", "BCP", "PRIVY COUNCIL OFFICE", "BUREAU DU CONSEIL PRIVÉ", "PCO / BCP"));
    institutionMappings.put(
        "PHAC",
        Arrays.asList(
            "PHAC",
            "ASPC",
            "PUBLIC HEALTH AGENCY OF CANADA",
            "AGENCE DE LA SANTÉ PUBLIQUE DU CANADA",
            "PHAC / ASPC"));
    institutionMappings.put(
        "PS",
        Arrays.asList("PS", "SP", "PUBLIC SAFETY CANADA", "SÉCURITÉ PUBLIQUE CANADA", "PS / SP"));
    institutionMappings.put(
        "PSC",
        Arrays.asList(
            "PSC",
            "CFP",
            "PUBLIC SERVICE COMMISSION OF CANADA",
            "COMMISSION DE LA FONCTION PUBLIQUE DU CANADA",
            "PSC / CFP"));
    institutionMappings.put(
        "PSPC",
        Arrays.asList(
            "PSPC",
            "SPAC",
            "PUBLIC SERVICES AND PROCUREMENT CANADA",
            "SERVICES PUBLICS ET APPROVISIONNEMENT CANADA",
            "GOUVERNEMENT DU CANADA, SERVICES PUBLICS ET APPROVISIONNEMENT CANADA",
            "GOVERNMENT OF CANADA, PUBLIC SERVICES AND PROCUREMENT CANADA",
            "PSPC / SPAC"));
    institutionMappings.put(
        "RCMP",
        Arrays.asList(
            "RCMP",
            "GRC",
            "ROYAL CANADIAN MOUNTED POLICE",
            "GENDARMERIE ROYALE DU CANADA",
            "RCMP / GRC"));
    institutionMappings.put(
        "SC", Arrays.asList("SC", "SC", "SERVICE CANADA", "SERVICE CANADA", "SC / SC"));
    institutionMappings.put(
        "SSC",
        Arrays.asList(
            "SSC", "PSC", "SHARED SERVICES CANADA", "SERVICES PARTAGÉS CANADA", "SSC / PSC"));
    institutionMappings.put(
        "SSHRC",
        Arrays.asList(
            "SSHRC",
            "CRSH",
            "SOCIAL SCIENCES AND HUMANITIES RESEARCH COUNCIL",
            "CONSEIL DE RECHERCHES EN SCIENCES HUMAINES",
            "SSHRC / CRSH"));
    institutionMappings.put(
        "SST",
        Arrays.asList(
            "SST",
            "TSS",
            "SOCIAL SECURITY TRIBUNAL OF CANADA",
            "TRIBUNAL DE LA SÉCURITÉ SOCIALE DU CANADA",
            "SST / TSS"));
    institutionMappings.put(
        "STATCAN",
        Arrays.asList(
            "STATCAN",
            "STATISTICS CANADA",
            "STATISTIQUE CANADA",
            "StatCan / StatCan",
            "STATCAN / STATCAN"));
    institutionMappings.put(
        "TBS",
        Arrays.asList(
            "TBS",
            "SCT",
            "TREASURY BOARD OF CANADA SECRETARIAT",
            "SECRÉTARIAT DU CONSEIL DU TRÉSOR DU CANADA",
            "TBS / SCT"));
    institutionMappings.put(
        "TC", Arrays.asList("TC", "TC", "TRANSPORT CANADA", "TRANSPORTS CANADA", "TC / TC"));
    institutionMappings.put(
        "VAC",
        Arrays.asList(
            "VAC", "ACC", "VETERANS AFFAIRS CANADA", "ANCIENS COMBATTANTS CANADA", "VAC / ACC"));
    institutionMappings.put(
        "WAGE",
        Arrays.asList(
            "WAGE",
            "FEGC",
            "WOMEN AND GENDER EQUALITY CANADA",
            "FEMMES ET ÉGALITÉ DES GENRES CANADA",
            "WAGE / FEGC"));
    institutionMappings.put(
        "WD",
        Arrays.asList(
            "WD",
            "DEO",
            "WESTERN ECONOMIC DIVERSIFICATION CANADA",
            "DIVERSIFICATION DE L’ÉCONOMIE DE L’OUEST CANADA",
            "WD / DEO"));
  }

  @RequestMapping(value = "/topTask/totalDistinctTasks")
  @ResponseBody
  public String totalDistinctTasks() {
    return String.valueOf(totalDistinctTasks);
  }

  @RequestMapping(value = "/topTask/totalTaskCount")
  @ResponseBody
  public String totalTaskCount() {
    return String.valueOf(totalTaskCount);
  }

  @RequestMapping(value = "/topTaskData", method = {RequestMethod.GET, RequestMethod.POST})
  @ResponseBody
  public DataTablesOutput<TopTaskSurvey> list(
      @Valid DataTablesInput input, HttpServletRequest request) {

    String pageLang = (String) request.getSession().getAttribute("lang");
    String departmentFilterVal = request.getParameter("department");
    String themeFilterVal = request.getParameter("theme");
    if (themeFilterVal != null) {
        themeFilterVal = themeFilterVal.trim().replaceAll("\\s+", " ");
    }
    String[] taskFilterVals = request.getParameterValues("tasks[]");
    String startDateVal = request.getParameter("startDate");
    String endDateVal = request.getParameter("endDate");
    String groupFilterVal = request.getParameter("group");
    String language = request.getParameter("language");
    String includeCommentsOnlyParam = request.getParameter("includeCommentsOnly");
    boolean includeCommentsOnly = includeCommentsOnlyParam != null && includeCommentsOnlyParam.equals("true");
    String taskCompletionFilterVal = request.getParameter("taskCompletion");
    String comments = request.getParameter("comments");

    Criteria criteria = Criteria.where("processed").is("true");
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    if (startDateVal != null && endDateVal != null) {
      LocalDate start = LocalDate.parse(startDateVal, formatter);
      LocalDate end = LocalDate.parse(endDateVal, formatter);
      criteria.and("dateTime").gte(start.format(formatter)).lte(end.format(formatter));
    }
    if (language != null && !language.isEmpty()) {
      criteria.and("language").is(language);
    }
    if (themeFilterVal != null && !themeFilterVal.isEmpty()) {
      criteria.and("theme").regex(Pattern.quote(themeFilterVal), "i");
    }
    if (groupFilterVal != null && !groupFilterVal.isEmpty()) {
      criteria.and("grouping").is(groupFilterVal);
    }
    if (departmentFilterVal != null && !departmentFilterVal.isEmpty()) {
      Criteria departmentCriteria = applyDepartmentFilter(new Criteria(), departmentFilterVal);
      criteria = new Criteria().andOperator(criteria, departmentCriteria);
    }

    List<Criteria> combinedOrCriteria = new ArrayList<>();
    if (taskFilterVals != null && taskFilterVals.length > 0) {
      for (String task : taskFilterVals) {
        Criteria taskCriteria = Criteria.where("task").is(task);
        combinedOrCriteria.add(taskCriteria);
      }
    }

    if (includeCommentsOnly) {
      List<Criteria> nonEmptyCriteria = createNonEmptyCriteria();
      if (!combinedOrCriteria.isEmpty()) {
        List<Criteria> commentCriteriaWithTasks = new ArrayList<>();
        for (Criteria taskCriteria : combinedOrCriteria) {
          commentCriteriaWithTasks.add(
              new Criteria()
                  .andOperator(
                      taskCriteria,
                      new Criteria().orOperator(nonEmptyCriteria.toArray(new Criteria[0]))));
        }
        criteria.orOperator(commentCriteriaWithTasks.toArray(new Criteria[0]));
      } else {
        criteria.orOperator(nonEmptyCriteria.toArray(new Criteria[0]));
      }
    } else if (!combinedOrCriteria.isEmpty()) {
      criteria.orOperator(combinedOrCriteria.toArray(new Criteria[0]));
    }
    //taskCompletion filter
      if (taskCompletionFilterVal != null && !taskCompletionFilterVal.isEmpty()) {
          List<String> allowed = new ArrayList<>();
          if (taskCompletionFilterVal.equals("Yes")) {
              allowed.add("Yes / Oui");
          } else if (taskCompletionFilterVal.equals("No")) {
              allowed.add("No / Non");
          } else if (taskCompletionFilterVal.equals("I started this survey before I finished my visit")) {
              allowed.add("I started this survey before I finished my visit / J’ai commencé ce sondage avant d’avoir terminé ma visite");
          }
          if (!allowed.isEmpty()) {
              criteria.and("taskCompletion").in(allowed);
          }
      }
      // Comments filtering
      if (comments != null && !comments.trim().isEmpty() && !"null".equalsIgnoreCase(comments. trim())) {
          String escapedComment = escapeSpecialRegexCharacters(comments.trim());
          List<Criteria> commentCriteria = new ArrayList<>();
          commentCriteria.add(Criteria.where("taskImproveComment").regex(escapedComment, "i"));
          commentCriteria.add(Criteria.where("taskWhyNotComment").regex(escapedComment, "i"));
          commentCriteria.add(Criteria.where("taskOther").regex(escapedComment, "i"));

          criteria.andOperator(new Criteria().andOperator(criteria, new Criteria().orOperator(commentCriteria.toArray(new Criteria[0]))));
      }


    List<Map> distinctTaskCounts = topTaskRepository.findDistinctTaskCountsWithFilters(criteria);
    totalDistinctTasks = distinctTaskCounts.size();

    // Use estimatedDocumentCount (metadata-based, instant) when no filters are applied,
    // to avoid an expensive count query against CosmosDB.
    boolean isFiltered = (startDateVal != null && endDateVal != null)
        || (language != null && !language.isEmpty())
        || (departmentFilterVal != null && !departmentFilterVal.isEmpty())
        || (themeFilterVal != null && !themeFilterVal.isEmpty())
        || (groupFilterVal != null && !groupFilterVal.isEmpty())
        || (taskFilterVals != null && taskFilterVals.length > 0)
        || (taskCompletionFilterVal != null && !taskCompletionFilterVal.isEmpty())
        || (comments != null && !comments.trim().isEmpty() && !"null".equalsIgnoreCase(comments.trim()))
        || includeCommentsOnly;
    long cachedCount = isFiltered ? -1
        : mongoTemplate.getCollection("toptasksurvey").estimatedDocumentCount();
    DataTablesOutput<TopTaskSurvey> results = topTaskRepository.findAll(input, criteria, cachedCount);

    totalTaskCount = (int) results.getRecordsFiltered();
    return results;
  }

  @GetMapping("/exportTopTaskExcel")
  public void exportTopTaskExcel(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    LOG.info("Starting Excel export...");

    try {
      DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
      String currentDate = LocalDate.now().format(dateFormatter);

      // Build criteria from request parameters
      Criteria criteria = buildExportCriteria(request);
      Query query = buildExportQuery(criteria);

      // Check if we have any data before proceeding
      long count = mongoTemplate.count(query, TopTaskSurvey.class);
      LOG.info("Found {} records to export", count);

      if (count == 0) {
        LOG.warn("No data found for export with the given criteria");
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        response.getWriter().write("No data found for export");
        return;
      }

      // Set response headers after confirming we have data
      response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
      String filename = "top_task_survey_export_" + currentDate + ".xlsx";
      response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
      response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
      response.setHeader("Pragma", "no-cache");
      response.setHeader("Expires", "0");

      SXSSFWorkbook workbook = new SXSSFWorkbook(100);
      Sheet sheet = workbook.createSheet("Top Task Survey Data");

      // Create header row
      String[] columns = {
        "Date Time",
        "Time Stamp (UTC)",
        "Survey Referrer",
        "Language",
        "Device",
        "Screener",
        "Department",
        "Theme",
        "Theme Other",
        "Grouping",
        "Task",
        "Task Other",
        "Task Satisfaction",
        "Task Ease",
        "Task Completion",
        "Task Improve",
        "Task Improve Comment",
        "Task Why Not",
        "Task Why Not Comment",
        "Task Sampling",
        "Sampling Invitation",
        "Sampling GC",
        "Sampling Canada",
        "Sampling Theme",
        "Sampling Institution",
        "Sampling Grouping",
        "Sampling Task"
      };
      Row headerRow = sheet.createRow(0);
      for (int i = 0; i < columns.length; i++) {
        headerRow.createCell(i).setCellValue(columns[i]);
      }

      final int[] rowNum = {1};
      try (ServletOutputStream outputStream = response.getOutputStream();
           java.util.stream.Stream<TopTaskSurvey> stream = mongoTemplate.stream(query, TopTaskSurvey.class)) {
        stream.forEach(
                survey -> {
                  try {
                    Row row = sheet.createRow(rowNum[0]++);
                    row.createCell(0).setCellValue(survey.getDateTime());
                    row.createCell(1).setCellValue(survey.getTimeStamp());
                    row.createCell(2).setCellValue(survey.getSurveyReferrer());
                    row.createCell(3).setCellValue(survey.getLanguage());
                    row.createCell(4).setCellValue(survey.getDevice());
                    row.createCell(5).setCellValue(survey.getScreener());
                    row.createCell(6).setCellValue(survey.getDept());
                    row.createCell(7).setCellValue(survey.getTheme());
                    row.createCell(8).setCellValue(survey.getThemeOther());
                    row.createCell(9).setCellValue(survey.getGrouping());
                    row.createCell(10).setCellValue(survey.getTask());
                    row.createCell(11).setCellValue(survey.getTaskOther());
                    row.createCell(12).setCellValue(survey.getTaskSatisfaction());
                    row.createCell(13).setCellValue(survey.getTaskEase());
                    row.createCell(14).setCellValue(survey.getTaskCompletion());
                    row.createCell(15).setCellValue(survey.getTaskImprove());
                    row.createCell(16).setCellValue(survey.getTaskImproveComment());
                    row.createCell(17).setCellValue(survey.getTaskWhyNot());
                    row.createCell(18).setCellValue(survey.getTaskWhyNotComment());
                    row.createCell(19).setCellValue(survey.getTaskSampling());
                    row.createCell(20).setCellValue(survey.getSamplingInvitation());
                    row.createCell(21).setCellValue(survey.getSamplingGC());
                    row.createCell(22).setCellValue(survey.getSamplingCanada());
                    row.createCell(23).setCellValue(survey.getSamplingTheme());
                    row.createCell(24).setCellValue(survey.getSamplingInstitution());
                    row.createCell(25).setCellValue(survey.getSamplingGrouping());
                    row.createCell(26).setCellValue(survey.getSamplingTask());

                    if (rowNum[0] % 100 == 0) {
                      ((SXSSFSheet) sheet).flushRows(100);
                      LOG.debug("Flushed {} rows", rowNum[0]);
                    }
                  } catch (Exception e) {
                    LOG.error("Error writing row {}: {}", rowNum[0], e.getMessage());
                  }
                });

        LOG.info("Writing {} rows to Excel file", rowNum[0] - 1);
        workbook.write(outputStream);
        outputStream.flush();
        LOG.info("Excel export completed successfully");
      } catch (Exception e) {
        LOG.error("Error writing to output stream", e);
        throw e;
      } finally {
        workbook.dispose();
        workbook.close();
      }
    } catch (Exception e) {
      LOG.error("Error exporting Excel", e);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      response.getWriter().write("Error exporting data: " + e.getMessage());
    }
  }

  @GetMapping("/exportTopTaskCSV")
  public void exportTopTaskCSV(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    LOG.info("Starting CSV export...");

    try {
      DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
      String currentDate = LocalDate.now().format(dateFormatter);

      // Build criteria from request parameters
      Criteria criteria = buildExportCriteria(request);
      Query query = buildExportQuery(criteria);

      // Check if we have any data before proceeding
      long count = mongoTemplate.count(query, TopTaskSurvey.class);
      LOG.info("Found {} records to export", count);

      if (count == 0) {
        LOG.warn("No data found for export with the given criteria");
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        response.getWriter().write("No data found for export");
        return;
      }

      // Set response headers after confirming we have data
      response.setContentType("text/csv");
      String filename = "top_task_survey_export_" + currentDate + ".csv";
      response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
      response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
      response.setHeader("Pragma", "no-cache");
      response.setHeader("Expires", "0");

      try (Writer writer = response.getWriter()) {
        // Write CSV header
        writer.write(
            "Date Time,Time Stamp (UTC),Survey"
                + " Referrer,Language,Device,Screener,Department,Theme,Theme"
                + " Other,Grouping,Task,Task Other,Task Satisfaction,Task Ease,Task Completion,Task"
                + " Improve,Task Improve Comment,Task Why Not,Task Why Not Comment,Task"
                + " Sampling,Sampling Invitation,Sampling GC,Sampling Canada,Sampling"
                + " Theme,Sampling Institution,Sampling Grouping,Sampling Task\n");

        // Stream and write data
        try (java.util.stream.Stream<TopTaskSurvey> stream = mongoTemplate.stream(query, TopTaskSurvey.class)) {
          stream.forEach(
                survey -> {
                  try {
                    writer.write(
                        String.format(
                            "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                            escapeCSV(survey.getDateTime()),
                            escapeCSV(survey.getTimeStamp()),
                            escapeCSV(survey.getSurveyReferrer()),
                            escapeCSV(survey.getLanguage()),
                            escapeCSV(survey.getDevice()),
                            escapeCSV(survey.getScreener()),
                            escapeCSV(survey.getDept()),
                            escapeCSV(survey.getTheme()),
                            escapeCSV(survey.getThemeOther()),
                            escapeCSV(survey.getGrouping()),
                            escapeCSV(survey.getTask()),
                            escapeCSV(survey.getTaskOther()),
                            escapeCSV(survey.getTaskSatisfaction()),
                            escapeCSV(survey.getTaskEase()),
                            escapeCSV(survey.getTaskCompletion()),
                            escapeCSV(survey.getTaskImprove()),
                            escapeCSV(survey.getTaskImproveComment()),
                            escapeCSV(survey.getTaskWhyNot()),
                            escapeCSV(survey.getTaskWhyNotComment()),
                            escapeCSV(survey.getTaskSampling()),
                            escapeCSV(survey.getSamplingInvitation()),
                            escapeCSV(survey.getSamplingGC()),
                            escapeCSV(survey.getSamplingCanada()),
                            escapeCSV(survey.getSamplingTheme()),
                            escapeCSV(survey.getSamplingInstitution()),
                            escapeCSV(survey.getSamplingGrouping()),
                            escapeCSV(survey.getSamplingTask())));
                  } catch (IOException e) {
                    LOG.error("Error writing CSV row: {}", e.getMessage());
                  }
                });
        }

        writer.flush();
        LOG.info("CSV export completed successfully");
      } catch (Exception e) {
        LOG.error("Error writing to CSV output", e);
        throw e;
      }
    } catch (Exception e) {
      LOG.error("Error exporting CSV", e);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      response.getWriter().write("Error exporting data: " + e.getMessage());
    }
  }

  // Helper methods for the export endpoints
  private Criteria buildExportCriteria(HttpServletRequest request) {
    String department = request.getParameter("department");
    String theme = request.getParameter("theme");
    String[] tasks = request.getParameterValues("tasks[]");
    String group = request.getParameter("group");
    String language = request.getParameter("language");
    String startDate = request.getParameter("startDate");
    String endDate = request.getParameter("endDate");
    String comments = request.getParameter("comments");
    boolean includeCommentsOnly = Boolean.parseBoolean(request.getParameter("includeCommentsOnly"));

    Criteria criteria = Criteria.where("processed").is("true");
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    if (startDate != null && endDate != null) {
      LocalDate start = LocalDate.parse(startDate, formatter);
      LocalDate end = LocalDate.parse(endDate, formatter);
      criteria.and("dateTime").gte(start.format(formatter)).lte(end.format(formatter));
    }
    if (language != null && !language.isEmpty()) {
      criteria.and("language").is(language);
    }
    if (theme != null && !theme.isEmpty()) {
      criteria.and("theme").regex(theme, "i");
    }
    if (group != null && !group.isEmpty()) {
      criteria.and("grouping").is(group);
    }
    if (department != null && !department.isEmpty()) {
      Criteria departmentCriteria = applyDepartmentFilter(new Criteria(), department);
      criteria = new Criteria().andOperator(criteria, departmentCriteria);
    }

    List<Criteria> combinedOrCriteria = new ArrayList<>();
    if (tasks != null && tasks.length > 0) {
      for (String task : tasks) {
        Criteria taskCriteria = Criteria.where("task").is(task);
        combinedOrCriteria.add(taskCriteria);
      }
    }

    if (includeCommentsOnly) {
      List<Criteria> nonEmptyCriteria = createNonEmptyCriteria();
      if (!combinedOrCriteria.isEmpty()) {
        List<Criteria> commentCriteriaWithTasks = new ArrayList<>();
        for (Criteria taskCriteria : combinedOrCriteria) {
          commentCriteriaWithTasks.add(
              new Criteria()
                  .andOperator(
                      taskCriteria,
                      new Criteria().orOperator(nonEmptyCriteria.toArray(new Criteria[0]))));
        }
        criteria.andOperator(
            new Criteria().orOperator(commentCriteriaWithTasks.toArray(new Criteria[0])));
      } else {
        criteria.andOperator(new Criteria().orOperator(nonEmptyCriteria.toArray(new Criteria[0])));
      }
    } else if (!combinedOrCriteria.isEmpty()) {
      criteria.andOperator(new Criteria().orOperator(combinedOrCriteria.toArray(new Criteria[0])));
    }

      if (comments != null && !comments.isEmpty()) {
          String escapedComment = escapeSpecialRegexCharacters(comments);
          List<Criteria> commentCriteria = new ArrayList<>();
          commentCriteria.add(Criteria.where("taskImproveComment").regex(escapedComment, "i"));
          commentCriteria.add(Criteria.where("taskWhyNotComment").regex(escapedComment, "i"));
          commentCriteria.add(Criteria.where("themeOther").regex(escapedComment, "i"));
          commentCriteria.add(Criteria. where("taskOther").regex(escapedComment, "i"));

          criteria.andOperator(new Criteria().andOperator(criteria, new Criteria().orOperator(commentCriteria.toArray(new Criteria[0]))));
      }

    return criteria;
  }

  private Query buildExportQuery(Criteria criteria) {
    Query query = new Query(criteria);
    query
        .fields()
        .include("dateTime")
        .include("timeStamp")
        .include("surveyReferrer")
        .include("language")
        .include("device")
        .include("screener")
        .include("dept")
        .include("theme")
        .include("themeOther")
        .include("grouping")
        .include("task")
        .include("taskOther")
        .include("taskSatisfaction")
        .include("taskEase")
        .include("taskCompletion")
        .include("taskImprove")
        .include("taskImproveComment")
        .include("taskWhyNot")
        .include("taskWhyNotComment")
        .include("taskSampling")
        .include("samplingInvitation")
        .include("samplingGC")
        .include("samplingCanada")
        .include("samplingTheme")
        .include("samplingInstitution")
        .include("samplingGrouping")
        .include("samplingTask");
    return query;
  }

  private String escapeCSV(String value) {
    if (value == null) {
      return "";
    }
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }

  private List<Criteria> createNonEmptyCriteria() {
    List<Criteria> criteriaList = new ArrayList<>();
    criteriaList.add(Criteria.where("taskOther").exists(true).ne(""));
    criteriaList.add(Criteria.where("themeOther").exists(true).ne(""));
    criteriaList.add(Criteria.where("taskWhyNotComment").exists(true).ne(""));
    criteriaList.add(Criteria.where("taskImproveComment").exists(true).ne(""));
    return criteriaList;
  }

  @RequestMapping(value = "/topTaskSurvey/departments", produces = "application/json")
  @ResponseBody
  public List<Map<String, String>> departmentData(HttpServletRequest request) {
    String lang = (String) request.getSession().getAttribute("lang");

    return institutionMappings.entrySet().stream()
        .map(
            entry -> {
              String value =
                  entry
                      .getValue()
                      .get(entry.getValue().size() - 1); // Get the last element (with slashes)
              // Keep same format for French
              String display = value; // Keep same format for English

              Map<String, String> departmentMap = new HashMap<>();
              departmentMap.put("value", value);
              departmentMap.put("display", display);
              return departmentMap;
            })
        .sorted(
            (a, b) -> a.get("display").compareToIgnoreCase(b.get("display"))) // Sort alphabetically
        .collect(Collectors.toList());
  }


  @GetMapping("/api/toptasks")
  public ResponseEntity<?> getProblemsJson(
      @RequestParam Map<String, String> requestParams,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) String processedStartDate,
      @RequestParam(required = false) String processedEndDate,
      @RequestParam(required = false) String institution,
      @RequestParam(required = false) String url,
      @RequestHeader(name = "Authorization") String authorizationHeader) {
    String token = null;
    String userName = null;

    if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
      token = authorizationHeader.substring(7);
      userName = jwtUtil.extractUsername(token);
    }

    if (userName != null) {
      User user = userService.findUserByEmail(userName);
      if (!userService.isAdmin(user) && !userService.isAPI(user)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body("Access denied. Only API users & Admins can access this endpoint.");
      }
    } else {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body("Authorization header is missing or invalid.");
    }

    Set<String> validParams =
        new HashSet<>(
            Arrays.asList(
                "startDate",
                "endDate",
                "processedStartDate",
                "processedEndDate",
                "institution",
                "url",
                "authorizationHeader"));

    for (String param : requestParams.keySet()) {
      if (!validParams.contains(param)) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", "Invalid parameter: " + param);
        return ResponseEntity.badRequest().body(errorResponse);
      }
    }

    Criteria criteria = new Criteria("processed").is("true");

    DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    if ((startDate != null || endDate != null)
        && (processedStartDate != null || processedEndDate != null)) {
      Map<String, String> errorResponse = new HashMap<>();
      errorResponse.put(
          "error", "You can only filter by normal date range or processed date range, not both.");
      return ResponseEntity.badRequest().body(errorResponse);
    }

    // Validate and apply normal date range filter
    if (startDate != null || endDate != null) {
      if (startDate == null || endDate == null) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", "Both startDate and endDate are required.");
        return ResponseEntity.badRequest().body(errorResponse);
      } else {
        try {
          LocalDate start = LocalDate.parse(startDate, dateFormat);
          LocalDate end = LocalDate.parse(endDate, dateFormat);
          if (end.isBefore(start)) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "endDate must be greater than or equal to startDate.");
            return ResponseEntity.badRequest().body(errorResponse);
          }
          criteria.and("dateTime").gte(startDate).lte(endDate);
        } catch (DateTimeParseException e) {
          Map<String, String> errorResponse = new HashMap<>();
          errorResponse.put("error", "Invalid date format. Please use yyyy-MM-dd.");
          return ResponseEntity.badRequest().body(errorResponse);
        }
      }
    }

    // Validate and apply processed date range filter
    if (processedStartDate != null || processedEndDate != null) {
      if (processedStartDate == null || processedEndDate == null) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", "Both processedStartDate and processedEndDate are required.");
        return ResponseEntity.badRequest().body(errorResponse);
      } else {
        try {
          LocalDate processedStart = LocalDate.parse(processedStartDate, dateFormat);
          LocalDate processedEnd = LocalDate.parse(processedEndDate, dateFormat);
          if (processedEnd.isBefore(processedStart)) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put(
                "error", "processedEndDate must be greater than or equal to processedStartDate.");
            return ResponseEntity.badRequest().body(errorResponse);
          }
          criteria.and("processedDate").gte(processedStartDate).lte(processedEndDate);
        } catch (DateTimeParseException e) {
          Map<String, String> errorResponse = new HashMap<>();
          errorResponse.put("error", "Invalid date format. Please use yyyy-MM-dd.");
          return ResponseEntity.badRequest().body(errorResponse);
        }
      }
    }

    try {
      if (institution != null && !institution.isEmpty()) {
        criteria = applyDepartmentFilter(criteria, institution);
      }
    } catch (IllegalArgumentException e) {
      Map<String, String> errorResponse = new HashMap<>();
      errorResponse.put("error", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    if (url != null && !url.isEmpty()) {
      System.out.println("URL: " + url);
      criteria.and("surveyReferrer").regex(url, "i");
    }
    Query query = new Query(criteria);
    query
        .fields()
        .exclude("_id")
        .exclude("processed")
        .exclude("personalInfoProcessed")
        .exclude("autoTagProcessed")
        .exclude("_class");
    List<Document> documents = mongoTemplate.find(query, Document.class, "toptasksurvey");
    return ResponseEntity.ok(documents);
  }

  private Criteria applyDepartmentFilter(Criteria criteria, String department) {
    List<String> variations = new ArrayList<>();
      for (Map.Entry<String, List<String>> entry : institutionMappings.entrySet()) {
          List<String> mappingValues = entry.getValue();
          if (mappingValues.stream().anyMatch(v -> v.equalsIgnoreCase(department))) {
              variations.addAll(mappingValues);
              break;
          }
      }
      if (variations.isEmpty()) {
    criteria.and("dept").regex("^" + Pattern.quote(department) + "$", "i");
      } else {
          List<Criteria> deptCriteria = new ArrayList<>();
          for (String variation :  variations) {
              deptCriteria.add(Criteria.where("dept").regex("^" + Pattern.quote(variation) + "$", "i"));
          }
          criteria.orOperator(deptCriteria.toArray(new Criteria[0]));
      }

      return criteria;
  }

  @GetMapping(value = "/topTaskSurvey")
  public ModelAndView topTaskSurvey(HttpServletRequest request) throws Exception {
    ModelAndView mav = new ModelAndView();
    String lang = (String) request.getSession().getAttribute("lang");
    Map<String, String> dateMap = problemDateService.getProblemDates();
    mav.setViewName("topTaskSurvey_" + lang);
    mav.addObject("earliestDate", "2021-01-14");
    mav.addObject("latestDate", dateMap.get("latestDate"));

    return mav;
  }

  @GetMapping("/taskNames")
  @ResponseBody
  public List<String> getTaskNames(
      @RequestParam(name = "search", required = false) String search,
      @RequestParam(name = "department", required = false) String department,
      @RequestParam(name = "theme", required = false) String theme,
      @RequestParam(name = "group", required = false) String group,
      @RequestParam(name = "language", required = false) String language,
      @RequestParam(name = "startDate", required = false) String startDate,
      @RequestParam(name = "endDate", required = false) String endDate) {

    // Build criteria based on applied filters
    Criteria criteria = Criteria.where("processed").is("true");
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Apply date range filter
    if (startDate != null && endDate != null) {
      LocalDate start = LocalDate.parse(startDate, formatter);
      LocalDate end = LocalDate.parse(endDate, formatter);
      criteria.and("dateTime").gte(start.format(formatter)).lte(end.format(formatter));
    }

    // Apply language filter
    if (language != null && !language.isEmpty()) {
      criteria.and("language").is(language);
    }

    // Apply theme filter
    if (theme != null && !theme.isEmpty()) {
      String cleanedTheme = theme.trim().replaceAll("\\s+", " ");
      criteria.and("theme").regex(Pattern.quote(cleanedTheme), "i");
    }

    // Apply group filter
    if (group != null && !group.isEmpty()) {
      criteria.and("grouping").is(group);
    }

    // Apply department filter
    if (department != null && !department.isEmpty()) {
      Criteria departmentCriteria = applyDepartmentFilter(new Criteria(), department);
      criteria = new Criteria().andOperator(criteria, departmentCriteria);
    }

    if (search != null && !search.isEmpty()) {
      // Use the new repository method with filters applied
      return topTaskRepository.findTaskNamesBySearchWithFilters(search, criteria);
    } else {
      // Return all tasks matching the current filters (without search term)
      // This uses the existing findDistinctTaskCountsWithFilters method
      List<Map> distinctTaskCounts = topTaskRepository.findDistinctTaskCountsWithFilters(criteria);
      return distinctTaskCounts.stream()
          .map(map -> (String) map.get("_id"))
          .sorted()
          .collect(Collectors.toList());
    }
  }

  private String escapeSpecialRegexCharacters(String input) {
      if (input == null) {
          return null;
      }
      // Escape all regex metacharacters
      return input.replaceAll("([\\\\.|^$|()\\[\\]{}*+?])", "\\\\$1");
  }

}
