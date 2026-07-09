package com.careertuner.fitanalysis.dto;

import java.util.List;

public record FitActionBoardResponse(
        List<String> todo,
        List<String> inProgress,
        List<String> done
) {
}
