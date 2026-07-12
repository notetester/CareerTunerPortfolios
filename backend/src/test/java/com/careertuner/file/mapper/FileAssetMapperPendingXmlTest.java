package com.careertuner.file.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class FileAssetMapperPendingXmlTest {

    private static final Path MAPPER = Path.of("src/main/resources/mapper/file/FileAssetMapper.xml");

    @Test
    void pendingDeleteUsesOwnerAndNullRefIdButAllowsPredeclaredRefType() throws Exception {
        String xml = Files.readString(MAPPER);
        String delete = statement(xml, "delete", "deleteByIdAndOwnerIfPending");

        assertThat(delete)
                .contains("owner_user_id = #{ownerUserId}")
                .contains("ref_id IS NULL")
                .doesNotContain("ref_type IS NULL");
    }

    @Test
    void collaborationClaimAtomicallyRequiresOwnerKindScopeAndPendingState() throws Exception {
        String xml = Files.readString(MAPPER);
        String claim = statement(xml, "update", "claimPendingCollaborationAttachment");

        assertThat(claim)
                .contains("owner_user_id = #{ownerUserId}")
                .contains("kind = 'ATTACHMENT'")
                .contains("ref_type = 'COLLAB_MESSAGE'")
                .contains("ref_id IS NULL")
                .contains("ref_id = #{messageId}");
    }

    @Test
    void ttlCleanupIsLimitedAndRechecksExactCollaborationPendingScope() throws Exception {
        String xml = Files.readString(MAPPER);
        String find = statement(xml, "select", "findStalePendingCollaborationAttachments");
        String delete = statement(xml, "delete", "deleteStalePendingCollaborationAttachment");

        assertThat(find)
                .contains("kind = 'ATTACHMENT'")
                .contains("ref_type = 'COLLAB_MESSAGE'")
                .contains("ref_id IS NULL")
                .contains("created_at &lt; #{cutoff}")
                .contains("LIMIT #{limit}");
        assertThat(delete)
                .contains("owner_user_id = #{ownerUserId}")
                .contains("kind = 'ATTACHMENT'")
                .contains("ref_type = 'COLLAB_MESSAGE'")
                .contains("ref_id IS NULL")
                .contains("created_at &lt; #{cutoff}");
    }

    @Test
    void interviewMediaClaimAndCleanupRequireDeclaredPurposeAndPendingReference() throws Exception {
        String xml = Files.readString(MAPPER);
        String claim = statement(xml, "update", "claimOwnedPendingFile");
        assertThat(claim)
                .contains("owner_user_id = #{ownerUserId}")
                .contains("kind = #{expectedKind}")
                .contains("ref_type = #{expectedRefType}")
                .contains("ref_id IS NULL");

        String find = statement(xml, "select", "findStalePendingInterviewMedia");
        String delete = statement(xml, "delete", "deleteStalePendingInterviewMedia");
        for (String sql : java.util.List.of(find, delete)) {
            assertThat(sql)
                    .contains("kind IN ('AUDIO', 'VIDEO')")
                    .contains("ref_type = 'INTERVIEW_ANSWER'")
                    .contains("ref_id IS NULL")
                    .contains("created_at &lt; #{cutoff}");
        }
    }

    private static String statement(String xml, String tag, String id) {
        int start = xml.indexOf("<" + tag + " id=\"" + id + "\"");
        assertThat(start).as(id + " 존재").isGreaterThanOrEqualTo(0);
        return xml.substring(start, xml.indexOf("</" + tag + ">", start));
    }
}
