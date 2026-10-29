package ca.gc.tbs.domain;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;

public class Problem {

  @Id private String id;
  @Indexed private String url;
  private int urlEntries;
  private String problemDetails;
  @Indexed private String language;
  @Indexed private String problemDate;
  @Indexed private String timeStamp;
  @Indexed private String title;
  private String dataOrigin;
  private List<String> tags;
  // Pipeline fields
  @Indexed private String processed;
  @Indexed private String airTableSync;
  @Indexed private String personalInfoProcessed;
  @Indexed private String autoTagProcessed;
  @Indexed private String processedDate; // New field for processed date

  @Indexed private String institution;
  @Indexed private String theme;
  @Indexed private String section;
  private String oppositeLang;
  private String contact;
  @Indexed private String deviceType;
  @Indexed private String browser;

  public Problem() {
    this.tags = new ArrayList<>();
  }

  public Problem(
      String id,
      String url,
      int urlEntries,
      String deviceType,
      String browser,
      String problemDate,
      String timeStamp,
      String problemDetails,
      String language,
      String title,
      String institution,
      String theme,
      String section,
      String oppositeLang,
      String contact) {
    this.id = id;
    this.url = url;
    this.urlEntries = urlEntries;
    this.deviceType = deviceType;
    this.browser = browser;
    this.problemDetails = problemDetails;
    this.problemDate = problemDate;
    this.timeStamp = timeStamp;
    this.language = language;
    this.title = title;
    this.institution = institution;
    this.theme = theme;
    this.section = section;
    this.oppositeLang = oppositeLang;
    this.contact = contact;
    this.tags = new ArrayList<>();
  }

  public Problem(Problem existingProblem) {
    this(
        existingProblem.id,
        existingProblem.url,
        existingProblem.urlEntries,
        existingProblem.deviceType,
        existingProblem.browser,
        existingProblem.problemDate,
        existingProblem.timeStamp,
        existingProblem.problemDetails,
        existingProblem.language,
        existingProblem.title,
        existingProblem.institution,
        existingProblem.theme,
        existingProblem.section,
        existingProblem.oppositeLang,
        existingProblem.contact);
    this.tags = new ArrayList<>(existingProblem.tags);
  }

  // Getter and Setter methods

  // Add getters and setters for all fields
  // Example:
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public int getUrlEntries() {
    return urlEntries;
  }

  public void setUrlEntries(int urlEntries) {
    this.urlEntries = urlEntries;
  }

  public String getBrowser() {
    return browser;
  }

  public void setBrowser(String browser) {
    this.browser = browser;
  }

  public String getDeviceType() {
    return deviceType;
  }

  public void setDeviceType(String deviceType) {
    this.deviceType = deviceType;
  }

  public String getProblemDetails() {
    return problemDetails;
  }

  public void setProblemDetails(String problemDetails) {
    this.problemDetails = problemDetails;
  }

  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }

  public String getProblemDate() {
    return problemDate;
  }

  public void setProblemDate(String problemDate) {
    this.problemDate = problemDate;
  }

  public String getTimeStamp() {
    return timeStamp;
  }

  public void setTimeStamp(String timeStamp) {
    this.timeStamp = timeStamp;
  }

  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getProcessed() {
    return processed;
  }

  public void setProcessed(String processed) {
    this.processed = processed;
  }

  public String getAirTableSync() {
    return airTableSync;
  }

  public void setAirTableSync(String airTableSync) {
    this.airTableSync = airTableSync;
  }

  public String getDataOrigin() {
    return dataOrigin;
  }

  public void setDataOrigin(String dataOrigin) {
    this.dataOrigin = dataOrigin;
  }

  public String getPersonalInfoProcessed() {
    return personalInfoProcessed;
  }

  public void setPersonalInfoProcessed(String personalInfoProcessed) {
    this.personalInfoProcessed = personalInfoProcessed;
  }

  public String getAutoTagProcessed() {
    return autoTagProcessed;
  }

  public void setAutoTagProcessed(String autoTagProcessed) {
    this.autoTagProcessed = autoTagProcessed;
  }

  public String getInstitution() {
    return institution;
  }

  public void setInstitution(String institution) {
    this.institution = institution;
  }

  public String getTheme() {
    return theme;
  }

  public void setTheme(String theme) {
    this.theme = theme;
  }

  public String getSection() {
    return section;
  }

  public void setSection(String section) {
    this.section = section;
  }

  public String getOppositeLang() {
    return oppositeLang;
  }

  public void setOppositeLang(String oppositeLang) {
    this.oppositeLang = oppositeLang;
  }

  public String getContact() {
    return contact;
  }

  public void setContact(String contact) {
    this.contact = contact;
  }

  public String getProcessedDate() {
    return processedDate;
  }

  public void setProcessedDate(String processedDate) {
    this.processedDate = processedDate;
  }
}
