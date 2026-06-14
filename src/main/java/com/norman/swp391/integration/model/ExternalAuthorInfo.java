package com.norman.swp391.integration.model;

public record ExternalAuthorInfo(String name, String sourceType, String sourceIdentifier, String affiliation) {
    
    // Compatibility constructor
    public ExternalAuthorInfo(String name, String sourceIdentifier, String affiliation) {
        this(name, null, sourceIdentifier, affiliation);
    }
}
