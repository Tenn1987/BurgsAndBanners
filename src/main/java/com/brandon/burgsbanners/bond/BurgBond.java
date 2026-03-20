package com.brandon.burgsbanners.bond;

import java.util.UUID;

public class BurgBond {

    private final UUID bondId;
    private final String burgId;
    private final UUID ownerUuid;
    private final String currency;
    private final long principal;
    private final long payout;
    private final long issuedAt;
    private final long maturesAt;
    private boolean redeemed;

    public BurgBond(UUID bondId,
                    String burgId,
                    UUID ownerUuid,
                    String currency,
                    long principal,
                    long payout,
                    long issuedAt,
                    long maturesAt,
                    boolean redeemed) {
        this.bondId = bondId;
        this.burgId = burgId;
        this.ownerUuid = ownerUuid;
        this.currency = currency;
        this.principal = principal;
        this.payout = payout;
        this.issuedAt = issuedAt;
        this.maturesAt = maturesAt;
        this.redeemed = redeemed;
    }

    public UUID getBondId() { return bondId; }
    public String getBurgId() { return burgId; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public String getCurrency() { return currency; }
    public long getPrincipal() { return principal; }
    public long getPayout() { return payout; }
    public long getIssuedAt() { return issuedAt; }
    public long getMaturesAt() { return maturesAt; }
    public boolean isRedeemed() { return redeemed; }
    public void setRedeemed(boolean redeemed) { this.redeemed = redeemed; }

    public boolean isMature() {
        return System.currentTimeMillis() >= maturesAt;
    }
}