package com.careertuner.auth.dto;

import java.util.List;

public record MfaBackupCodesResponse(
        List<String> codes
) {
}
