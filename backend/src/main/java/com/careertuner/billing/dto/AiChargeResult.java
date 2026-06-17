package com.careertuner.billing.dto;

public record AiChargeResult(
        ChargeType chargeType,
        String benefitCode,
        int chargedCredit,
        int remainingTicket,
        int remainingCredit,
        String reason
) {
    public enum ChargeType {
        TICKET,
        CREDIT,
        SKIPPED
    }

    public static AiChargeResult ticket(String benefitCode, int remainingTicket) {
        return new AiChargeResult(ChargeType.TICKET, benefitCode, 0, remainingTicket, 0, "TICKET_CONSUMED");
    }

    public static AiChargeResult credit(int chargedCredit, int remainingCredit) {
        return new AiChargeResult(ChargeType.CREDIT, null, chargedCredit, 0, remainingCredit, "CREDIT_DEDUCTED");
    }

    public static AiChargeResult skipped(String benefitCode, int remainingTicket, int remainingCredit, String reason) {
        return new AiChargeResult(ChargeType.SKIPPED, benefitCode, 0, remainingTicket, remainingCredit, reason);
    }
}
