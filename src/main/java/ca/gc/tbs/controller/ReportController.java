package ca.gc.tbs.controller;

import ca.gc.tbs.domain.Problem;
import ca.gc.tbs.repository.ProblemRepository;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;
import org.apache.commons.csv.CSVFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class ReportController {

  private static final Logger log = LoggerFactory.getLogger(ReportController.class);

  @Autowired ProblemRepository problemRepository;

  @Value("${pagesuccess.pythonPath}")
  private String pythonPath;

  @Value("${pagesuccess.pythonScriptPath}")
  private String pythonScriptPath;

  public static String INPUT_FILENAME = "page_success_may_24.csv";

  public static String PYTHON_SCRIPT = "page_success_widget.py";

  public ReportController() {}

  @GetMapping("/reports")
  public View generateReports() throws Exception {
    new File(Paths.get(pythonPath + "/" + INPUT_FILENAME).toString()).delete();
    BufferedWriter writer =
        Files.newBufferedWriter(Paths.get(pythonScriptPath + "/" + INPUT_FILENAME));

    try (CSVPrinter csvPrinter =
        new CSVPrinter(
            writer,
            CSVFormat.DEFAULT.withHeader(
                "Ref Number",
                "Date/time received",
                "Page Title",
                "Page URL",
                "Y/N",
                "What's wrong",
                "Details",
                "Topic",
                "Personal info",
                "(Y/N)",
                "Notes",
                "",
                "Test - auto-topic generator"))) {

      List<Problem> problems = this.problemRepository.findAll();
      for (Problem problem : problems) {
        csvPrinter.printRecord(
            problem.getId(),
            problem.getProblemDate(),
            problem.getTitle(),
            problem.getUrl(),
            "N",
            problem.getProblemDetails(),
            String.join(", ", problem.getTags()),
            "N",
            "",
            "");
      }
      csvPrinter.flush();
    }
    // call python
    this.executePython();

    return new RedirectView("/reports/view");
  }

  @GetMapping("/reports/view")
  public String viewReports() {
    return "reports";
  }

  public int executePython() throws Exception {
    File pathToExecutable = new File(this.pythonPath);
    ProcessBuilder builder = new ProcessBuilder(pathToExecutable.getAbsolutePath(), PYTHON_SCRIPT);
    builder.directory(new File(this.pythonScriptPath).getAbsoluteFile());
    builder.redirectErrorStream(true);
    Process process = builder.start();

    Scanner s = new Scanner(process.getInputStream());
    StringBuilder text = new StringBuilder();
    while (s.hasNextLine()) {
      text.append(s.nextLine());
      text.append("\n");
    }
    log.info("{}", text);
    s.close();

    Thread vt = Thread.ofVirtual().start(() -> {
      try {
        process.waitFor();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
    vt.join();
    return process.exitValue();
  }
}
