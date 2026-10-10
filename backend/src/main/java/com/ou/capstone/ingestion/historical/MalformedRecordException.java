package com.ou.capstone.ingestion.historical;

public class MalformedRecordException extends RuntimeException {
    public MalformedRecordException(String message) {
        super(message);
    }
}
