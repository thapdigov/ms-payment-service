package az.kapital.mspaymentservice.model.request;

import az.kapital.mspaymentservice.model.enums.Currency;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class RiskCheckDTO {

    private Long paymentId;
    private Long sourceUserId;
    private BigDecimal amount;
    private Currency currency;
}