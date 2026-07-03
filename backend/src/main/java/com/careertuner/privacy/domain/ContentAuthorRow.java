package com.careertuner.privacy.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 콘텐츠 id 기반 차단용 작성자 조회 행 (게시글/댓글 → 작성자 id + 익명 여부). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentAuthorRow {

    private Long userId;
    private boolean anonymous;
}
