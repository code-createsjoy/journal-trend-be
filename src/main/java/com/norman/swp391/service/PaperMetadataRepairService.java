package com.norman.swp391.service;

/** Re-fetch OpenAlex metadata for rows stored with broken Unicode (VARCHAR / encoding). */
public interface PaperMetadataRepairService {
    int repairFromOpenAlex(int limit);
}
