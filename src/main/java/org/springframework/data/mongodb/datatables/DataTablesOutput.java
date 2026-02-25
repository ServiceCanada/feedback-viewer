package org.springframework.data.mongodb.datatables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Response object for jQuery DataTables server-side processing.
 * Contains the data plus pagination metadata.
 */
public class DataTablesOutput<T> {

  private int draw;
  private long recordsTotal;
  private long recordsFiltered;
  private List<T> data = Collections.emptyList();
  private String error;

  public DataTablesOutput() {}

  public int getDraw() {
    return draw;
  }

  public void setDraw(int draw) {
    this.draw = draw;
  }

  public long getRecordsTotal() {
    return recordsTotal;
  }

  public void setRecordsTotal(long recordsTotal) {
    this.recordsTotal = recordsTotal;
  }

  public long getRecordsFiltered() {
    return recordsFiltered;
  }

  public void setRecordsFiltered(long recordsFiltered) {
    this.recordsFiltered = recordsFiltered;
  }

  public List<T> getData() {
    return data;
  }

  public void setData(List<T> data) {
    this.data = data != null ? data : new ArrayList<>();
  }

  public String getError() {
    return error;
  }

  public void setError(String error) {
    this.error = error;
  }
}
