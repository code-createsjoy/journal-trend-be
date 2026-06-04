package com.norman.swp391.integration.model;

public record ExternalAuthorProfile(
        String openAlexId, String name, String affiliation, Integer citedByCount, Integer worksCount, Integer hIndex) {}

