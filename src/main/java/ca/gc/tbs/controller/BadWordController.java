package ca.gc.tbs.controller;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import ca.gc.tbs.domain.BadWordEntry;
import ca.gc.tbs.repository.BadWordEntryRepository;
import ca.gc.tbs.service.BadWords;

/**
 * Controller for managing BadWordEntry entities.
 * Provides CRUD operations and CSV import/export functionality.
 * All routes are restricted to ADMIN users only.
 */
@Controller
@PreAuthorize("hasAuthority('ADMIN')")
public class BadWordController {

  private static final Logger LOG = LoggerFactory.getLogger(BadWordController.class);

  @Autowired private BadWordEntryRepository repository;
  @Autowired private BadWords badWordsService;

  /**
   * Displays the keywords management page.
   * 
   * @param request HttpServletRequest to get session language
   * @return ModelAndView with the keywords table data
   */
  @GetMapping(value = "/keywords/index")
  public ModelAndView index(HttpServletRequest request) {
    ModelAndView mav = new ModelAndView();
    String lang = (String) request.getSession().getAttribute("lang");
    if (lang == null || lang.isEmpty()) {
      lang = "en";
    }
    mav.addObject("data", this.getData(lang));
    mav.setViewName("keywords_" + lang);
    return mav;
  }

  /**
   * Generates HTML table rows for all badword entries.
   * 
   * @param lang The language for labels ("en" or "fr")
   * @return HTML string containing table rows
   */
  private String getData(String lang) {
    StringBuilder builder = new StringBuilder();
    try {
      List<BadWordEntry> entries = repository.findAll();

      for (BadWordEntry entry : entries) {
        String word = escapeHtml(entry.getWord());
        String id = entry.getId();
        String type = entry.getType();
        String language = entry.getLanguage();
        boolean active = entry.getActive();

        String langLabel = switch (language) {
          case "en" -> "EN";
          case "fr" -> "FR";
          default -> "BOTH";
        };

        String langClass = switch (language) {
          case "en" -> "tag-en";
          case "fr" -> "tag-fr";
          default -> "tag-both";
        };

        String typeLabel = (lang.equals("fr")) ? switch (type) {
          case "profanity" -> "VULGAIRE";
          case "threat" -> "MENACE";
          case "allowed" -> "AUTORISÉ";
          case "error" -> "ERREUR";
          default -> type.toUpperCase();
        } : type.toUpperCase();

        String activeLabel = lang.equals("en") ? (active ? "ACTIVE" : "INACTIVE") : (active ? "ACTIF" : "INACTIF");
        String activeClass = active ? "tag-active" : "tag-inactive";

        String actionButtons;
        if (lang.equals("en")) {
          String toggleBtn = active
              ? """
                <button id='deactivate%s' class='btn-action btn-deactivate deactivateBtn' title='Deactivate keyword'>Deactivate</button>"""
                .formatted(id)
              : """
                <button id='activate%s' class='btn-action btn-activate activateBtn' title='Activate keyword'>Activate</button>"""
                .formatted(id);

          actionButtons = """
              <button id='edit%s' class='btn-action btn-edit editBtn' \
              data-id='%s' data-word='%s' data-language='%s' data-type='%s' data-active='%b' \
              title='Edit keyword'>Edit</button>
              %s
              <button id='delete%s' class='btn-action btn-delete deleteBtn' title='Delete keyword'>Delete</button>"""
              .formatted(id, id, word, language, type, active, toggleBtn, id);
        } else {
          String toggleBtn = active
              ? """
                <button id='deactivate%s' class='btn-action btn-deactivate deactivateBtn' title='Désactiver le mot-clé'>Désactiver</button>"""
                .formatted(id)
              : """
                <button id='activate%s' class='btn-action btn-activate activateBtn' title='Activer le mot-clé'>Activer</button>"""
                .formatted(id);

          actionButtons = """
              <button id='edit%s' class='btn-action btn-edit editBtn' \
              data-id='%s' data-word='%s' data-language='%s' data-type='%s' data-active='%b' \
              title='Modifier le mot-clé'>Modifier</button>
              %s
              <button id='delete%s' class='btn-action btn-delete deleteBtn' title='Supprimer le mot-clé'>Supprimer</button>"""
              .formatted(id, id, word, language, type, active, toggleBtn, id);
        }

        builder.append("""
            <tr>
              <td><strong>%s</strong></td>
              <td><span class='tag tag-language %s'>%s</span></td>
              <td><span class='tag tag-%s'>%s</span></td>
              <td><span class='tag %s'>%s</span></td>
              <td>%s</td>
            </tr>""".formatted(word, langClass, langLabel, type, typeLabel, activeClass, activeLabel, actionButtons));
      }
    } catch (Exception e) {
      LOG.error("Error generating keywords table data", e);
    }
    return builder.toString();
  }

  /**
   * Creates a new badword entry.
   * 
   * @param word The word text
   * @param language The language ("en", "fr", or "both")
   * @param type The type ("profanity", "threat", "allowed", "error")
   * @param active Whether the word is active
   * @return Success or error message
   */
  @PostMapping(value = "/keywords/create")
  public @ResponseBody String create(
      @RequestParam String word,
      @RequestParam String language,
      @RequestParam String type,
      @RequestParam(defaultValue = "true") Boolean active) {
    try {
      // Validate inputs
      if (word == null || word.trim().isEmpty()) {
        return "Error: Word cannot be empty";
      }
      if (!isValidLanguage(language)) {
        return "Error: Invalid language. Must be 'en', 'fr', or 'both'";
      }
      if (!isValidType(type)) {
        return "Error: Invalid type. Must be 'profanity', 'threat', 'allowed', or 'error'";
      }
      
      String normalizedWord = word.trim().toLowerCase();
      
      // Check for duplicates
      BadWordEntry existing = repository.findByWordAndLanguageAndType(normalizedWord, language, type);
      if (existing != null) {
        return "Error: This word already exists for the specified language and type";
      }
      
      // Create new entry
      BadWordEntry entry = new BadWordEntry();
      entry.setWord(normalizedWord);
      entry.setLanguage(language);
      entry.setType(type);
      entry.setActive(active);
      
      repository.save(entry);
      
      // Reload the in-memory cache
      badWordsService.reload();
      
      LOG.info("Created new badword entry: word={}, language={}, type={}, active={}", 
          normalizedWord, language, type, active);
      
      return "Success";
    } catch (Exception e) {
      LOG.error("Error creating badword entry", e);
      return "Error: " + e.getMessage();
    }
  }

  /**
   * Updates an existing badword entry.
   * 
   * @param id The entry ID
   * @param word The new word text (optional)
   * @param language The new language (optional)
   * @param type The new type (optional)
   * @param active The new active status (optional)
   * @return Success or error message
   */
  @PostMapping(value = "/keywords/update")
  public @ResponseBody String update(
      @RequestParam String id,
      @RequestParam(required = false) String word,
      @RequestParam(required = false) String language,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) Boolean active) {
    try {
      BadWordEntry entry = repository.findById(id).orElse(null);
      if (entry == null) {
        return "Error: Entry not found";
      }
      
      boolean changed = false;
      
      // Update word if provided
      if (word != null && !word.trim().isEmpty()) {
        String normalizedWord = word.trim().toLowerCase();
        if (!normalizedWord.equals(entry.getWord())) {
          // Check for duplicates with new word
          String checkLanguage = language != null ? language : entry.getLanguage();
          String checkType = type != null ? type : entry.getType();
          BadWordEntry existing = repository.findByWordAndLanguageAndType(normalizedWord, checkLanguage, checkType);
          if (existing != null && !existing.getId().equals(id)) {
            return "Error: This word already exists for the specified language and type";
          }
          entry.setWord(normalizedWord);
          changed = true;
        }
      }
      
      // Update language if provided
      if (language != null && !language.isEmpty()) {
        if (!isValidLanguage(language)) {
          return "Error: Invalid language. Must be 'en', 'fr', or 'both'";
        }
        if (!language.equals(entry.getLanguage())) {
          // Check for duplicates with new language
          BadWordEntry existing = repository.findByWordAndLanguageAndType(
              entry.getWord(), language, entry.getType());
          if (existing != null && !existing.getId().equals(id)) {
            return "Error: This word already exists for the specified language and type";
          }
          entry.setLanguage(language);
          changed = true;
        }
      }
      
      // Update type if provided
      if (type != null && !type.isEmpty()) {
        if (!isValidType(type)) {
          return "Error: Invalid type. Must be 'profanity', 'threat', 'allowed', or 'error'";
        }
        if (!type.equals(entry.getType())) {
          // Check for duplicates with new type
          BadWordEntry existing = repository.findByWordAndLanguageAndType(
              entry.getWord(), entry.getLanguage(), type);
          if (existing != null && !existing.getId().equals(id)) {
            return "Error: This word already exists for the specified language and type";
          }
          entry.setType(type);
          changed = true;
        }
      }
      
      // Update active status if provided
      if (active != null && active != entry.getActive()) {
        entry.setActive(active);
        changed = true;
      }
      
      if (changed) {
        repository.save(entry);
        badWordsService.reload();
        LOG.info("Updated badword entry: id={}", id);
      }
      
      return "Success";
    } catch (Exception e) {
      LOG.error("Error updating badword entry", e);
      return "Error: " + e.getMessage();
    }
  }

  /**
   * Deletes a badword entry.
   * 
   * @param id The entry ID
   * @return Success or error message
   */
  @PostMapping(value = "/keywords/delete")
  public @ResponseBody String delete(@RequestParam String id) {
    try {
      if (!repository.existsById(id)) {
        return "Error: Entry not found";
      }
      
      repository.deleteById(id);
      badWordsService.reload();
      
      LOG.info("Deleted badword entry: id={}", id);
      return "Success";
    } catch (Exception e) {
      LOG.error("Error deleting badword entry", e);
      return "Error: " + e.getMessage();
    }
  }

  /**
   * Exports all badword entries to CSV.
   * 
   * @param response HttpServletResponse to write CSV data
   */
  @GetMapping(value = "/keywords/export")
  public void exportCsv(HttpServletResponse response) {
    try {
      response.setContentType("text/csv; charset=UTF-8");
      response.setHeader("Content-Disposition", "attachment; filename=\"keywords.csv\"");
      
      List<BadWordEntry> entries = repository.findAll();
      
      try (PrintWriter writer = response.getWriter();
           CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
               .withHeader("word", "language", "type", "active"))) {
        
        // Write data
        for (BadWordEntry entry : entries) {
          csvPrinter.printRecord(
            entry.getWord(),
            entry.getLanguage(),
            entry.getType(),
            entry.getActive().toString()
          );
        }
        csvPrinter.flush();
      }
      
      LOG.info("Exported {} keyword entries to CSV", entries.size());
    } catch (Exception e) {
      LOG.error("Error exporting keywords to CSV", e);
    }
  }

  /**
   * Imports badword entries from CSV file.
   * Expected CSV format: word,language,type,active
   * 
   * @param file The uploaded CSV file
   * @return Success or error message with import statistics
   */
  @PostMapping(value = "/keywords/import")
  public @ResponseBody String importCsv(@RequestParam("file") MultipartFile file) {
    try {
      if (file.isEmpty()) {
        return "Error: No file uploaded";
      }
      
      int imported = 0;
      int skipped = 0;
      int errors = 0;
      
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
           CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
               .withFirstRecordAsHeader()
               .withIgnoreHeaderCase()
               .withTrim())) {
        
        for (CSVRecord record : csvParser) {
          try {
            String word = record.get("word");
            String language = record.get("language");
            String type = record.get("type");
            String activeStr = record.get("active");
            
            // Validate
            if (word == null || word.trim().isEmpty()) {
              errors++;
              continue;
            }
            if (!isValidLanguage(language)) {
              errors++;
              continue;
            }
            if (!isValidType(type)) {
              errors++;
              continue;
            }
            
            String normalizedWord = word.trim().toLowerCase();
            Boolean active = activeStr != null ? Boolean.parseBoolean(activeStr) : true;
            
            // Check for duplicates
            BadWordEntry existing = repository.findByWordAndLanguageAndType(
                normalizedWord, language, type);
            
            if (existing != null) {
              skipped++;
              continue;
            }
            
            // Create entry
            BadWordEntry entry = new BadWordEntry();
            entry.setWord(normalizedWord);
            entry.setLanguage(language);
            entry.setType(type);
            entry.setActive(active);
            
            repository.save(entry);
            imported++;
            
          } catch (Exception e) {
            LOG.error("Error importing CSV row: {}", record, e);
            errors++;
          }
        }
      }
      
      // Reload cache after import
      badWordsService.reload();
      
      LOG.info("CSV import completed: imported={}, skipped={}, errors={}", imported, skipped, errors);
      
      return String.format("Import completed: %d imported, %d skipped (duplicates), %d errors", 
          imported, skipped, errors);
      
    } catch (Exception e) {
      LOG.error("Error importing CSV file", e);
      return "Error: " + e.getMessage();
    }
  }

  /**
   * Validates language value.
   */
  private boolean isValidLanguage(String language) {
    return language != null && (language.equals("en") || language.equals("fr") || language.equals("both"));
  }

  /**
   * Validates type value.
   */
  private boolean isValidType(String type) {
    return type != null && (type.equals("profanity") || type.equals("threat") 
        || type.equals("allowed") || type.equals("error"));
  }

  /**
   * Escapes HTML special characters to prevent XSS.
   */
  private String escapeHtml(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
}
