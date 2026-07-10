package com.careertuner.legal.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.legal.domain.LegalClause;
import com.careertuner.legal.domain.LegalDocType;
import com.careertuner.legal.domain.LegalDocumentVersion;
import com.careertuner.legal.dto.LegalDocResponse;
import com.careertuner.legal.mapper.LegalMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LegalServiceImpl implements LegalService {

    private final LegalMapper legalMapper;

    @Override
    public LegalDocResponse getPublicDoc(String docType) {
        LegalDocType type = LegalDocType.from(docType);

        LegalDocumentVersion live = legalMapper.findLiveVersion(type.dbValue());
        if (live == null) {
            return DefaultLegalDocumentCatalog.response(type);
        }

        List<LegalClause> clauses = legalMapper.findClausesByVersionId(live.getId());
        List<LegalDocResponse.ClauseDto> sections = clauses.stream()
                .map(c -> new LegalDocResponse.ClauseDto(c.getSeq(), c.getTitle(), c.getBody()))
                .toList();

        return new LegalDocResponse(
                type.dbValue(),
                type.label(),
                live.getVersionLabel(),
                live.getEffectiveDate(),
                live.getUpdatedAt(),
                live.getSummary(),
                sections);
    }
}
