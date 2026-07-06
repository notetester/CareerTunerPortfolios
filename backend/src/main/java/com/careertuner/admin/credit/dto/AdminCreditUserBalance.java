package com.careertuner.admin.credit.dto;

import lombok.Data;

@Data
public class AdminCreditUserBalance {
    private Long id;
    private String email;
    private String name;
    private int credit;
}
