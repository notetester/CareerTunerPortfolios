package com.careertuner.community.service;

import com.careertuner.community.dto.ToggleReactionRequest;

public interface ReactionService {

    boolean toggleReaction(ToggleReactionRequest request, Long userId);
}
