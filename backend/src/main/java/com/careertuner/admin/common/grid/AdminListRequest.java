package com.careertuner.admin.common.grid;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 목록 공통 요청 파라미터(@ModelAttribute 바인딩).
 *
 * <p>여기 값은 클라이언트 입력 그대로이므로 신뢰하지 않는다.
 * 반드시 {@link AdminListNormalizer#normalize(AdminListRequest, AdminGridSpec)} 를 거쳐
 * 화이트리스트가 적용된 {@link AdminListQuery} 로 변환한 뒤 매퍼에 전달한다.</p>
 *
 * <p>filters 는 도메인별 enum 필터를 {@code filters[status]=ACTIVE} 형태의
 * 쿼리 파라미터로 받는 Map 이다. 허용 키/값은 {@link AdminGridSpec} 이 정의한다.</p>
 */
@Getter
@Setter
public class AdminListRequest {

    /** 검색 키워드. */
    private String keyword;

    /** 키워드가 적용될 컬럼 선택자(all/email/name 등 — 도메인별 화이트리스트). */
    private String searchType;

    /** enum 필터 모음. 예: filters[status]=ACTIVE&amp;filters[role]=ADMIN */
    private Map<String, String> filters = new LinkedHashMap<>();

    /** 기간 시작일(yyyy-MM-dd). */
    private String dateFrom;

    /** 기간 종료일(yyyy-MM-dd, 종일 포함). */
    private String dateTo;

    /** 정렬 키(도메인별 화이트리스트 매핑 대상). */
    private String sortBy;

    /** 정렬 방향(ASC/DESC 만 허용). */
    private String sortDir;

    /** 1부터 시작하는 페이지 번호. */
    private int page = 1;

    /** 페이지 크기({10,20,50,100} 화이트리스트). */
    private int size = 20;

    /** 로드 모드. SERVER=서버 페이징, CLIENT=상한 내 전량 반환 후 클라이언트 처리. */
    private String mode = "SERVER";
}
