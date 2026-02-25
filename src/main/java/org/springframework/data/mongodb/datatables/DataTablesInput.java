package org.springframework.data.mongodb.datatables;

import java.util.ArrayList;
import java.util.List;

/**
 * Request parameters sent by jQuery DataTables.
 * Maps the server-side processing protocol from DataTables.
 */
public class DataTablesInput {

  private int draw = 1;
  private int start = 0;
  private int length = 10;
  private Search search = new Search();
  private List<Order> order = new ArrayList<>();
  private List<Column> columns = new ArrayList<>();

  public int getDraw() {
    return draw;
  }

  public void setDraw(int draw) {
    this.draw = draw;
  }

  public int getStart() {
    return start;
  }

  public void setStart(int start) {
    this.start = start;
  }

  public int getLength() {
    return length;
  }

  public void setLength(int length) {
    this.length = length;
  }

  public Search getSearch() {
    return search;
  }

  public void setSearch(Search search) {
    this.search = search;
  }

  public List<Order> getOrder() {
    return order;
  }

  public void setOrder(List<Order> order) {
    this.order = order;
  }

  public List<Column> getColumns() {
    return columns;
  }

  public void setColumns(List<Column> columns) {
    this.columns = columns;
  }

  public static class Search {
    private String value = "";
    private boolean regex = false;

    public Search() {}

    public Search(String value, boolean regex) {
      this.value = value;
      this.regex = regex;
    }

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }

    public boolean isRegex() {
      return regex;
    }

    public void setRegex(boolean regex) {
      this.regex = regex;
    }
  }

  public static class Order {
    private int column;
    private String dir = "asc";

    public Order() {}

    public Order(int column, String dir) {
      this.column = column;
      this.dir = dir;
    }

    public int getColumn() {
      return column;
    }

    public void setColumn(int column) {
      this.column = column;
    }

    public String getDir() {
      return dir;
    }

    public void setDir(String dir) {
      this.dir = dir;
    }
  }

  public static class Column {
    private String data;
    private String name;
    private boolean searchable = true;
    private boolean orderable = true;
    private Search search = new Search();

    public Column() {}

    public String getData() {
      return data;
    }

    public void setData(String data) {
      this.data = data;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public boolean isSearchable() {
      return searchable;
    }

    public void setSearchable(boolean searchable) {
      this.searchable = searchable;
    }

    public boolean isOrderable() {
      return orderable;
    }

    public void setOrderable(boolean orderable) {
      this.orderable = orderable;
    }

    public Search getSearch() {
      return search;
    }

    public void setSearch(Search search) {
      this.search = search;
    }
  }
}
