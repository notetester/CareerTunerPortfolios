package com.careertuner.jobposting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.jobposting.service.JobPostingStorageProvider.Loaded;
import com.careertuner.jobposting.service.JobPostingStorageProvider.Written;
import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.cloudinary.Url;

/**
 * Cloudinary provider 단위테스트 — 키 없이 검증 가능한 범위: scheme·reference 왕복·path null·교차접근 방지.
 * 실 업로드/다운로드 e2e(네트워크)는 자격증명 준비 후 별도.
 */
class CloudinaryJobPostingStorageProviderTest {

    private CloudinaryProperties props() {
        CloudinaryProperties p = new CloudinaryProperties();
        p.setCloudName("demo");
        p.setApiKey("k");
        p.setApiSecret("s");
        return p;
    }

    @Test
    void schemeIsCloudinary() {
        CloudinaryJobPostingStorageProvider provider =
                new CloudinaryJobPostingStorageProvider(mock(Cloudinary.class), props());
        assertThat(provider.scheme()).isEqualTo("cloudinary");
    }

    @Test
    void referenceRoundTripPreservesParts() {
        String ref = CloudinaryJobPostingStorageProvider.buildReference(
                "image", "authenticated", "pdf", "application-postings/42/uuid-abc");

        assertThat(ref).isEqualTo("cloudinary:image/authenticated/pdf/application-postings/42/uuid-abc");
        CloudinaryJobPostingStorageProvider.Ref parsed =
                CloudinaryJobPostingStorageProvider.parseReference(ref);
        assertThat(parsed.resourceType()).isEqualTo("image");
        assertThat(parsed.type()).isEqualTo("authenticated");
        assertThat(parsed.format()).isEqualTo("pdf");
        assertThat(parsed.publicId()).isEqualTo("application-postings/42/uuid-abc");
    }

    @Test
    void parseReferenceRejectsUnknownSchemeAndTraversal() {
        assertThatThrownBy(() -> CloudinaryJobPostingStorageProvider.parseReference(
                "local:application-postings/1/x.pdf")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> CloudinaryJobPostingStorageProvider.parseReference(
                "cloudinary:image/authenticated/pdf/application-postings/1/../secret"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void writeReturnsCloudinaryReferenceAndNullPath() throws Exception {
        Cloudinary cloudinary = mock(Cloudinary.class);
        Uploader uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), any())).thenReturn(Map.of(
                "resource_type", "image",
                "type", "authenticated",
                "public_id", "application-postings/42/uuid-abc",
                "format", "pdf"));
        CloudinaryJobPostingStorageProvider provider =
                new CloudinaryJobPostingStorageProvider(cloudinary, props());

        Written written = provider.write(42L, "uuid-abc.pdf", new byte[]{1, 2, 3}, "application/pdf");

        assertThat(written.path()).isNull();
        assertThat(written.reference())
                .isEqualTo("cloudinary:image/authenticated/pdf/application-postings/42/uuid-abc");
    }

    @Test
    void readRejectsReferenceForDifferentCase() {
        CloudinaryJobPostingStorageProvider provider =
                new CloudinaryJobPostingStorageProvider(mock(Cloudinary.class), props());

        assertThatThrownBy(() -> provider.read(99L,
                "cloudinary:image/authenticated/pdf/application-postings/42/uuid-abc"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void readDownloadsBytesViaSignedAuthenticatedUrl() throws Exception {
        Cloudinary cloudinary = mock(Cloudinary.class);
        Url url = mock(Url.class);
        when(cloudinary.url()).thenReturn(url);
        when(url.resourceType(any())).thenReturn(url);
        when(url.type(any())).thenReturn(url);
        when(url.secure(anyBoolean())).thenReturn(url);
        when(url.signed(anyBoolean())).thenReturn(url);
        when(url.generate(any())).thenReturn("https://res.cloudinary.test/signed");

        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new byte[]{7, 8, 9});
        HttpClient httpClient = mock(HttpClient.class);
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());

        CloudinaryJobPostingStorageProvider provider =
                new CloudinaryJobPostingStorageProvider(cloudinary, props(), httpClient);

        Loaded loaded = provider.read(42L,
                "cloudinary:image/authenticated/pdf/application-postings/42/uuid-abc");

        assertThat(loaded.bytes()).containsExactly(7, 8, 9);
        assertThat(loaded.path()).isNull();
        assertThat(loaded.storedName()).isEqualTo("uuid-abc.pdf");
        // 서명 authenticated URL 은 {publicId}.{format} 로 생성돼야 한다(read 복원 경로 검증).
        verify(url).generate("application-postings/42/uuid-abc.pdf");
    }
}
