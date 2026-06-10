package com.careertuner.file.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.file.domain.FileAsset;

@Mapper
public interface FileAssetMapper {

    void insert(FileAsset asset);

    FileAsset findById(@Param("id") Long id);

    List<FileAsset> findByRef(@Param("refType") String refType, @Param("refId") Long refId);
}
