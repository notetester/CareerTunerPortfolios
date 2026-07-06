package com.careertuner.interview.handoff;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/**
 * 폰 마이크 핸드오프 시그널링 REST (음성 모의면접 — 데스크탑 화면 + 폰 마이크).
 * 흐름: 데스크탑 POST "" → 코드 표시 → 폰 GET {code}?role=phone 합류 →
 *       데스크탑 offer 게시 → 폰 폴링으로 offer 수신 → answer 게시 → 데스크탑 폴링 수신 → P2P 연결.
 */
@RestController
@RequestMapping("/api/interview/mic-handoff")
@RequiredArgsConstructor
public class MicHandoffController {

    private final MicHandoffService service;

    public record CreateRequest(@NotNull Long sessionId) {
    }

    public record CreateResponse(String code) {
    }

    public record SdpRequest(@NotBlank String sdp) {
    }

    public record StateResponse(Long sessionId, boolean phoneJoined, String offerSdp, String answerSdp) {
    }

    @PostMapping
    public ApiResponse<CreateResponse> create(@AuthenticationPrincipal AuthUser authUser,
                                              @Valid @RequestBody CreateRequest request) {
        return ApiResponse.ok(new CreateResponse(service.create(authUser.id(), request.sessionId())));
    }

    @GetMapping("/{code}")
    public ApiResponse<StateResponse> state(@AuthenticationPrincipal AuthUser authUser,
                                            @PathVariable String code,
                                            @RequestParam(defaultValue = "desktop") String role) {
        MicHandoffService.State s = service.state(code, authUser.id(), "phone".equalsIgnoreCase(role));
        return ApiResponse.ok(new StateResponse(s.sessionId(), s.phoneJoined(), s.offerSdp(), s.answerSdp()));
    }

    @PostMapping("/{code}/offer")
    public ApiResponse<Void> offer(@AuthenticationPrincipal AuthUser authUser,
                                   @PathVariable String code,
                                   @Valid @RequestBody SdpRequest request) {
        service.putOffer(code, authUser.id(), request.sdp());
        return ApiResponse.ok();
    }

    @PostMapping("/{code}/answer")
    public ApiResponse<Void> answer(@AuthenticationPrincipal AuthUser authUser,
                                    @PathVariable String code,
                                    @Valid @RequestBody SdpRequest request) {
        service.putAnswer(code, authUser.id(), request.sdp());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{code}")
    public ApiResponse<Void> close(@AuthenticationPrincipal AuthUser authUser, @PathVariable String code) {
        service.close(code, authUser.id());
        return ApiResponse.ok();
    }
}
