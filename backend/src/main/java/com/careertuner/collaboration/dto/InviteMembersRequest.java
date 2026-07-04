package com.careertuner.collaboration.dto;

import java.util.List;

public record InviteMembersRequest(
        List<Long> userIds,
        /** 익명 초대 여부(초대자가 익명으로 표시). null 이면 false 로 취급. */
        Boolean anonymous
) {
}
