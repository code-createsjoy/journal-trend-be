package com.norman.swp391.service;

import com.norman.swp391.dto.response.admin.TopicAnomalyResponse;
import org.springframework.stereotype.Component;

import java.util.List;


public interface TopicAnomalyService {

    List<TopicAnomalyResponse> listCurrentAnomalies(int limit);
}
