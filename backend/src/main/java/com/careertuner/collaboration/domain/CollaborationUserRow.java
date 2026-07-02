package com.careertuner.collaboration.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollaborationUserRow {

    private Long id;
    private String name;
    private String email;
    private String plan;
    private String relationStatus;
}
