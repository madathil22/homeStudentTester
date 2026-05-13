package com.homestudenttester.dto;

import java.util.Map;

public record SaveSubmissionRequest(String studentName, Map<String, Object> answers) {
}
