package com.careertuner.jobposting.service;

import java.nio.file.Path;

/**
 * 공고 업로드 파일의 저장 provider. reference 의 scheme prefix(예 {@code "local:"})로 provider 를 식별한다.
 *
 * <p>facade({@link JobPostingFileStorage})가 검증(size/type)·네이밍·{@code StoredJobPostingFile} 조립을 맡고,
 * provider 는 <b>원시 바이트 저장/조회</b>만 담당한다. 새 저장소(예: Cloudinary/S3)는 이 인터페이스만 구현하면 된다.
 */
public interface JobPostingStorageProvider {

    /** reference prefix(콜론 앞). 예: {@code "local"}. store provider 선택·load 라우팅 키. */
    String scheme();

    /**
     * 바이트를 저장하고 scheme-prefixed reference 와 로컬 {@link Path}(원격 저장소면 {@code null})를 돌려준다.
     * path 가 null 이면 OCR 워커 전송이 {@code sendBytes}(base64) 경로로 전환된다.
     */
    Written write(Long applicationCaseId, String storedName, byte[] bytes, String contentType);

    /** 이 provider 소유의 reference(scheme + {@code ':'} 로 시작)에서 바이트를 읽는다. */
    Loaded read(Long applicationCaseId, String fileReference);

    /** 저장 결과: 저장된 reference 와 로컬 경로(원격이면 null). */
    record Written(String reference, Path path) {
    }

    /** 조회 결과: 바이트, 로컬 경로(원격이면 null), 저장 파일명. */
    record Loaded(byte[] bytes, Path path, String storedName) {
    }
}
