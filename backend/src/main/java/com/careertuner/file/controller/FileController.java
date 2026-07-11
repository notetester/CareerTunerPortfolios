package com.careertuner.file.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.file.dto.FileAssetResponse;
import com.careertuner.file.service.FileService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileAssetResponse> upload(@AuthenticationPrincipal AuthUser authUser,
                                                 @RequestParam("file") MultipartFile file,
                                                 @RequestParam("kind") String kind,
                                                 @RequestParam(value = "refType", required = false) String refType,
                                                 @RequestParam(value = "refId", required = false) Long refId) {
        return ApiResponse.ok(fileService.upload(authUser.id(), kind, refType, refId, file));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(@AuthenticationPrincipal AuthUser authUser,
                                          @PathVariable Long id) {
        FileService.Download download = fileService.download(authUser.id(), id);
        String contentType = download.asset().getContentType();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE,
                        contentType == null || contentType.isBlank()
                                ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + safeName(download.asset().getOriginalName(), id) + "\"")
                .body(download.bytes());
    }

    /** 업로드 소유자만 파일 메타데이터와 실제 저장 파일을 제거할 수 있다. */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthUser authUser,
                                    @PathVariable Long id) {
        fileService.deleteOwnedUnlinked(authUser.id(), id);
        return ApiResponse.ok();
    }

    private String safeName(String originalName, Long id) {
        if (originalName == null || originalName.isBlank()) {
            return "file-" + id;
        }
        // 헤더 인젝션 방지: 따옴표/개행 제거.
        return originalName.replaceAll("[\"\\r\\n]", "_");
    }
}
