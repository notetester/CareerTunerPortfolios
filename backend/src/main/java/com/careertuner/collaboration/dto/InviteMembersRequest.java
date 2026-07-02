package com.careertuner.collaboration.dto;

import java.util.List;

public record InviteMembersRequest(
        List<Long> userIds
) {
}
