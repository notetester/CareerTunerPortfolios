package com.careertuner.admin.settings.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.settings.dto.ModerationExport;
import com.careertuner.admin.settings.dto.RuntimeSettingExport;
import com.careertuner.admin.settings.dto.SettingsExport;
import com.careertuner.admin.settings.dto.SettingsImportResult;
import com.careertuner.community.moderation.domain.ModerationSetting;
import com.careertuner.community.moderation.domain.Strictness;
import com.careertuner.community.moderation.service.ModerationSettingService;
import com.careertuner.runtimesetting.domain.RuntimeSetting;
import com.careertuner.runtimesetting.service.RuntimeSettingService;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 설정 export/import 서비스.
 *
 * <p>런타임 설정·콘텐츠 중재 정책을 섹션별 JSON 으로 내보내고, 같은 형식을 upsert 로 되돌린다.
 * 코드에 존재하지 않는 항목은 skip 하고 적용/스킵 건수를 리포팅한다. TripTogether
 * {@code InitialSettingsService} 의 섹션 export/import·skip 리포팅을 이식했다.</p>
 */
@Service
@RequiredArgsConstructor
public class SettingsExportService {

    private static final Logger log = LoggerFactory.getLogger(SettingsExportService.class);

    public static final String SEC_RUNTIME = "runtimeSettings";
    public static final String SEC_MODERATION = "moderation";
    public static final Set<String> SECTION_KEYS = Set.of(SEC_RUNTIME, SEC_MODERATION);

    private final RuntimeSettingService runtimeSettingService;
    private final ModerationSettingService moderationSettingService;

    /** 요청 섹션만 채운 export 봉투 반환(sections 비면 전체). */
    public SettingsExport export(Set<String> sections) {
        boolean all = sections == null || sections.isEmpty();

        List<RuntimeSettingExport> runtime = null;
        if (all || sections.contains(SEC_RUNTIME)) {
            runtime = runtimeSettingService.getRuntimeSettings(null, null, true).stream()
                    .map(s -> new RuntimeSettingExport(
                            s.getSettingKey(), s.getSettingGroup(), s.getDisplayName(),
                            s.getSettingValue(), s.getFallbackValue(), s.getValueType(),
                            s.isSecret(), s.isEditable(), s.isActive(), s.getDescription()))
                    .toList();
        }

        ModerationExport moderation = null;
        if (all || sections.contains(SEC_MODERATION)) {
            ModerationSetting m = moderationSettingService.getCurrent();
            moderation = new ModerationExport(
                    m.getStrictness() == null ? null : m.getStrictness().name(),
                    m.getHideThreshold(), m.getSanctionThreshold(), m.getBlockDays(),
                    m.getReportBlurThreshold(),
                    m.getPostRateWindowSeconds(), m.getPostRateMax(),
                    m.getCommentRateWindowSeconds(), m.getCommentRateMax(),
                    m.getInquiryRateWindowSeconds(), m.getInquiryRateMax());
        }

        return new SettingsExport(SettingsExport.SCHEMA_VERSION, LocalDateTime.now().toString(), runtime, moderation);
    }

    /** import 적용(upsert). null 섹션은 건너뛰고, 항목 단위로 적용/스킵을 집계한다. */
    @Transactional
    public SettingsImportResult importSettings(SettingsExport data, Long actorId) {
        List<SettingsImportResult.Section> sections = new ArrayList<>();
        int totalApplied = 0;
        int totalSkipped = 0;

        if (data.runtimeSettings() != null) {
            SettingsImportResult.Section sec = importRuntimeSettings(data.runtimeSettings(), actorId);
            sections.add(sec);
            totalApplied += sec.applied();
            totalSkipped += sec.skipped();
        }

        if (data.moderation() != null) {
            SettingsImportResult.Section sec = importModeration(data.moderation());
            sections.add(sec);
            totalApplied += sec.applied();
            totalSkipped += sec.skipped();
        }

        log.info("[SettingsImport] applied={} skipped={} sections={} actor={}",
                totalApplied, totalSkipped, sections.size(), actorId);
        return new SettingsImportResult(sections, totalApplied, totalSkipped);
    }

    private SettingsImportResult.Section importRuntimeSettings(List<RuntimeSettingExport> items, Long actorId) {
        int applied = 0;
        int skipped = 0;
        List<String> messages = new ArrayList<>();
        for (RuntimeSettingExport r : items) {
            if (r.settingKey() == null || r.settingKey().isBlank()) {
                skipped++;
                messages.add("설정 키가 비어 스킵");
                continue;
            }
            try {
                runtimeSettingService.saveRuntimeSetting(RuntimeSetting.builder()
                        .settingKey(r.settingKey())
                        .settingGroup(r.settingGroup())
                        .displayName(r.displayName())
                        .settingValue(r.settingValue())
                        .fallbackValue(r.fallbackValue())
                        .valueType(r.valueType())
                        .secret(r.secret())
                        .editable(r.editable())
                        .active(r.active())
                        .description(r.description())
                        // reason: 가져오기 경로로 인한 변경임을 이력에 남긴다
                        .build(), actorId, "가져오기(IMPORT)");
                applied++;
            } catch (Exception e) {
                skipped++;
                messages.add(r.settingKey() + " 적용 실패: " + e.getMessage());
            }
        }
        return new SettingsImportResult.Section(SEC_RUNTIME, applied, skipped, messages);
    }

    private SettingsImportResult.Section importModeration(ModerationExport m) {
        List<String> messages = new ArrayList<>();
        Strictness strictness;
        try {
            strictness = m.strictness() == null ? moderationSettingService.getStrictness()
                    : Strictness.valueOf(m.strictness());
        } catch (IllegalArgumentException e) {
            return new SettingsImportResult.Section(SEC_MODERATION, 0, 1,
                    List.of("strictness 값이 올바르지 않아 스킵: " + m.strictness()));
        }
        ModerationSetting setting = new ModerationSetting();
        setting.setStrictness(strictness);
        setting.setHideThreshold(m.hideThreshold());
        setting.setSanctionThreshold(m.sanctionThreshold());
        setting.setBlockDays(m.blockDays());
        setting.setReportBlurThreshold(m.reportBlurThreshold());
        setting.setPostRateWindowSeconds(m.postRateWindowSeconds());
        setting.setPostRateMax(m.postRateMax());
        setting.setCommentRateWindowSeconds(m.commentRateWindowSeconds());
        setting.setCommentRateMax(m.commentRateMax());
        setting.setInquiryRateWindowSeconds(m.inquiryRateWindowSeconds());
        setting.setInquiryRateMax(m.inquiryRateMax());
        moderationSettingService.update(setting);
        return new SettingsImportResult.Section(SEC_MODERATION, 1, 0, messages);
    }
}
