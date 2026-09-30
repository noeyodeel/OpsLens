package com.opslens.application.his;

public class HisErrorNotFoundException extends RuntimeException {

    public HisErrorNotFoundException(long errorId) {
        super("HIS error log not found: " + errorId);
    }
}
