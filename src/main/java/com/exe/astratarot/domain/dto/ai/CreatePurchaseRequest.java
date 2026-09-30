package com.exe.astratarot.domain.dto.ai;

import com.exe.astratarot.domain.entity.UserPlanPurchase;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreatePurchaseRequest {

    private UUID planId;
    private UserPlanPurchase.PurchaseType purchaseType;
}