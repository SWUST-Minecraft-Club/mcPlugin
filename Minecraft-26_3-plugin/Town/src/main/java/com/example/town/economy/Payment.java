package com.example.town.economy;

import java.util.Map;
import java.util.UUID;

public record Payment(String id, String kind, UUID payer, String payerName, String currency,
                      long cents, UUID beneficiary, String beneficiaryName, long creditCents,
                      Map<String, String> payload, long createdAt, Stage stage) {
    public enum Stage { REQUESTED, PAID, APPLIED, REFUND, DONE, REFUNDED, DECLINED }
    public Payment {
        UUID.fromString(id);
        if (kind == null || kind.isBlank() || payer == null || payerName == null || currency == null
                || cents < 0 || creditCents < 0 || createdAt < 0 || stage == null
                || ((cents > 0 || creditCents > 0) && currency.isBlank())
                || (creditCents > 0 && (beneficiary == null || beneficiaryName == null)))
            throw new IllegalArgumentException("支付数据无效");
        payload = Map.copyOf(payload);
    }
    public static Payment create(String kind, UUID payer, String name, String currency, long cents,
                                 UUID beneficiary, String beneficiaryName, long creditCents, Map<String, String> payload) {
        return new Payment(UUID.randomUUID().toString(), kind, payer, name, currency, cents,
                beneficiary, beneficiaryName, creditCents, payload, System.currentTimeMillis(), Stage.REQUESTED);
    }
    public Payment at(Stage next) { return new Payment(id, kind, payer, payerName, currency, cents,
            beneficiary, beneficiaryName, creditCents, payload, createdAt, next); }
    public boolean terminal() { return stage == Stage.DONE || stage == Stage.REFUNDED || stage == Stage.DECLINED; }
    public String debitKey() { return "town:" + id + ":debit"; }
    public String creditKey() { return "town:" + id + ":credit"; }
    public String refundKey() { return "town:" + id + ":refund"; }
}
