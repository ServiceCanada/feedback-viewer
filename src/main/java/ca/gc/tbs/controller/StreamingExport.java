package ca.gc.tbs.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.DeferredSXSSFSheet;
import org.apache.poi.xssf.streaming.DeferredSXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Streams rows to the client as a CSV or Excel download, writing them as they are read.
 *
 * <p>Cosmos DB paces cursors by the collection's RU/s, so rows are read ahead on a background
 * thread and the database keeps fetching while earlier rows are written. The Excel file is
 * generated during {@code write()}, so bytes flow from the first row instead of after the last,
 * and the CSV is gzip-compressed when the browser accepts it. Once bytes have been sent, a failure
 * is rethrown so the container aborts the connection instead of ending a truncated file as if it
 * were complete.
 */
final class StreamingExport<T> {

  /** What to send when the query matches no rows. */
  enum WhenEmpty {
    /** 204 No Content, for pages that show their own "no data" message. */
    NO_CONTENT,
    /** A file that contains only the header row. */
    HEADER_ONLY
  }

  private static final Logger LOG = LoggerFactory.getLogger(StreamingExport.class);

  // Larger batches mean fewer round trips to Cosmos DB
  private static final int CURSOR_BATCH_SIZE = 5000;

  // Rows read ahead of the writer: two cursor batches
  private static final int PREFETCH_ROWS = 2 * CURSOR_BATCH_SIZE;

  private static final int CSV_BUFFER_SIZE = 64 * 1024;

  private static final String EXCEL_CONTENT_TYPE =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  private final String name;
  private final String[] columns;
  private final Function<T, Object[]> cells;
  private final UnaryOperator<String> csvSanitizer;
  private final WhenEmpty whenEmpty;

  /**
   * @param name the export's name in log messages
   * @param columns the header row
   * @param cells one row's cells in column order: strings, numbers (written as numeric cells) or
   *     null for an empty cell
   * @param csvSanitizer applied to each string before it is quoted for the CSV
   * @param whenEmpty what to send when there are no rows
   */
  StreamingExport(
      String name,
      String[] columns,
      Function<T, Object[]> cells,
      UnaryOperator<String> csvSanitizer,
      WhenEmpty whenEmpty) {
    this.name = name;
    this.columns = columns;
    this.cells = cells;
    this.csvSanitizer = csvSanitizer;
    this.whenEmpty = whenEmpty;
  }

  /**
   * The query for an export. No field projection: Cosmos DB charges about 20% more RU to project
   * fields than to return whole documents, and in production the collections' RU/s is what limits
   * export speed. Whole documents are larger, so over a slow link (running the app locally against
   * the production database) they can make an export slower rather than faster.
   */
  static Query exportQuery(Criteria criteria) {
    return new Query(criteria).cursorBatchSize(CURSOR_BATCH_SIZE);
  }

  void writeExcel(
      Supplier<Stream<T>> rows,
      String contentDisposition,
      String sheetName,
      HttpServletResponse response)
      throws IOException {
    LOG.info("Starting {} Excel export...", name);
    long startTime = System.nanoTime();
    long[] rowCount = {0};

    try (PrefetchingIterator<T> iterator = new PrefetchingIterator<>(rows.get(), PREFETCH_ROWS)) {
      if (sentNoContent(iterator, startTime, response)) {
        return;
      }
      setDownloadHeaders(response, EXCEL_CONTENT_TYPE, contentDisposition);

      // close() also deletes the workbook's temporary files
      try (DeferredSXSSFWorkbook workbook = new DeferredSXSSFWorkbook(100)) {
        DeferredSXSSFSheet sheet = workbook.createSheet(sheetName);
        sheet.setRowGenerator(
            s -> {
              writeExcelRow(s.createRow(0), columns);
              while (iterator.hasNext()) {
                writeExcelRow(s.createRow((int) ++rowCount[0]), cells.apply(iterator.next()));
              }
            });
        // write() closes the stream it is given, even when row generation fails. Closing the
        // servlet stream would end the response normally and deliver a truncated workbook, so
        // the shield leaves closing to the container and a failure can still abort the response.
        workbook.write(CloseShieldOutputStream.wrap(response.getOutputStream()));
        response.flushBuffer();
      }
      LOG.info(
          "{} Excel export completed: {} rows in {} ms",
          name,
          rowCount[0],
          elapsedMillis(startTime));
    } catch (Exception e) {
      handleError("Excel", rowCount[0], response, e);
    }
  }

  void writeCsv(
      Supplier<Stream<T>> rows,
      String contentDisposition,
      HttpServletRequest request,
      HttpServletResponse response)
      throws IOException {
    LOG.info("Starting {} CSV export...", name);
    long startTime = System.nanoTime();
    long rowCount = 0;

    try (PrefetchingIterator<T> iterator = new PrefetchingIterator<>(rows.get(), PREFETCH_ROWS)) {
      if (sentNoContent(iterator, startTime, response)) {
        return;
      }
      setDownloadHeaders(response, "text/csv", contentDisposition);
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());

      // Survey text compresses about 8x and browsers decompress gzip transparently
      OutputStream body = response.getOutputStream();
      if (acceptsGzip(request)) {
        response.setHeader("Content-Encoding", "gzip");
        response.setHeader("Vary", "Accept-Encoding");
        body = new GZIPOutputStream(body, CSV_BUFFER_SIZE);
      }
      // Unlike the servlet's PrintWriter, this writer reports a client disconnect as an IOException.
      // It is closed only on success (see below), so it is deliberately not try-with-resources.
      Writer writer =
          new BufferedWriter(new OutputStreamWriter(body, StandardCharsets.UTF_8), CSV_BUFFER_SIZE);
      // Byte order mark so Excel opens the file as UTF-8 and keeps French accents
      writer.write('\uFEFF');
      writer.write(String.join(",", columns));
      writer.write('\n');

      StringBuilder line = new StringBuilder(1024);
      while (iterator.hasNext()) {
        line.setLength(0);
        Object[] values = cells.apply(iterator.next());
        for (int i = 0; i < values.length; i++) {
          if (i > 0) {
            line.append(',');
          }
          appendCsvCell(line, values[i]);
        }
        writer.append(line.append('\n'));
        rowCount++;
      }
      // Closing finishes the gzip stream, so it only happens once every row is written. After a
      // failure the stream is left unterminated and the response aborted, so a truncated file is
      // never delivered as a complete one.
      writer.close();
      LOG.info(
          "{} CSV export completed: {} rows in {} ms", name, rowCount, elapsedMillis(startTime));
    } catch (Exception e) {
      handleError("CSV", rowCount, response, e);
    }
  }

  /**
   * Waits for the first row. When there is none and this export answers that with 204, sends it
   * and returns true.
   */
  private boolean sentNoContent(
      PrefetchingIterator<T> iterator, long startTime, HttpServletResponse response)
      throws IOException {
    if (iterator.hasNext()) {
      LOG.info("{} export: first row received after {} ms", name, elapsedMillis(startTime));
      return false;
    }
    LOG.warn("No data found for {} export with the given criteria", name);
    if (whenEmpty == WhenEmpty.NO_CONTENT) {
      response.setStatus(HttpServletResponse.SC_NO_CONTENT);
      response.getWriter().write("No data found for export");
      return true;
    }
    return false;
  }

  private static void setDownloadHeaders(
      HttpServletResponse response, String contentType, String contentDisposition) {
    response.setContentType(contentType);
    response.setHeader("Content-Disposition", contentDisposition);
    response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Expires", "0");
  }

  private static boolean acceptsGzip(HttpServletRequest request) {
    String acceptEncoding = request.getHeader("Accept-Encoding");
    if (acceptEncoding == null) {
      return false;
    }
    for (String coding : acceptEncoding.split(",")) {
      String[] parts = coding.split(";");
      if (parts[0].trim().equalsIgnoreCase("gzip")) {
        // "gzip;q=0" means the client refuses gzip
        return parts.length == 1 || !parts[1].trim().matches("q=0(\\.0*)?");
      }
    }
    return false;
  }

  private void handleError(
      String format, long rowsWritten, HttpServletResponse response, Exception e)
      throws IOException {
    if (response.isCommitted()) {
      // Part of the file has already been sent; abort the connection so the client sees a failed
      // download rather than a file that silently ends early.
      LOG.error("{} {} export aborted after {} rows", name, format, rowsWritten, e);
      throw e instanceof IOException ioException
          ? ioException
          : new IOException(name + " " + format + " export aborted", e);
    }
    LOG.error("Error exporting {} {}", name, format, e);
    // reset() also releases a previously obtained output stream, so the writer can be used
    response.reset();
    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    response.setContentType("text/plain;charset=UTF-8");
    response.getWriter().write("Error exporting data");
  }

  private static void writeExcelRow(Row row, Object[] values) {
    for (int i = 0; i < values.length; i++) {
      Cell cell = row.createCell(i);
      if (values[i] instanceof Number number) {
        cell.setCellValue(number.doubleValue());
      } else {
        cell.setCellValue((String) values[i]);
      }
    }
  }

  private void appendCsvCell(StringBuilder line, Object value) {
    if (value == null) {
      return;
    }
    if (value instanceof Number) {
      line.append(value);
      return;
    }
    String text = csvSanitizer.apply((String) value);
    line.append('"').append(text.replace("\"", "\"\"")).append('"');
  }

  private static long elapsedMillis(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000;
  }
}
