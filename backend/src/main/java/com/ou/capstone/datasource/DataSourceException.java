package com.ou.capstone.datasource;

// Thrown when the current-season data source can't be reached or returns something unusable.
public class DataSourceException extends RuntimeException {

  public DataSourceException(String message) {
    super(message);
  }

  public DataSourceException(String message, Throwable cause) {
    super(message, cause);
  }
}
