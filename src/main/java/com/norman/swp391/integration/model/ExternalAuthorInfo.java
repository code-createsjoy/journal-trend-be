package com.norman.swp391.integration.model;

public record ExternalAuthorInfo(String name, String sourceType, String sourceIdentifier, String affiliation, String authorPosition) {

    // Backward compat: no position
    public ExternalAuthorInfo(String name, String sourceType, String sourceIdentifier, String affiliation) {
        this(name, sourceType, sourceIdentifier, affiliation, null);
    }

    // Legacy 3-param
    public ExternalAuthorInfo(String name, String sourceIdentifier, String affiliation) {
        this(name, null, sourceIdentifier, affiliation, null);
    }
}
