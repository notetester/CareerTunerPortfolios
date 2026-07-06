package com.careertuner.reward.service;

import com.careertuner.reward.dto.MyRewardResponse;
import com.careertuner.reward.dto.RewardGrantResult;

/** 활동 이벤트 기반 포인트/크레딧 적립 및 레벨업 처리 서비스. */
public interface RewardService {

    /**
     * 활동 이벤트에 대해 리워드를 적립한다. 규칙이 없거나(off) 일일 캡 초과면 미적립 결과를 돌려준다.
     * 집행 훅에서 호출된다. 적립은 호출한 트랜잭션에 참여한다.
     */
    RewardGrantResult grant(Long userId, String eventCode, String refType, Long refId);

    /** 마이페이지 "내 리워드/레벨" 조회. */
    MyRewardResponse myReward(Long userId);
}
