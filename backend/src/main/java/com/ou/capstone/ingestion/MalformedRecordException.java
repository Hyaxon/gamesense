package com.ou.capstone.ingestion;

/** thrown by the mappers when a record is missing something required or has a value we can't use */
public class MalformedRecordException extends RuntimeException {
  public MalformedRecordException(String message) {
    super(message);
  }
}
