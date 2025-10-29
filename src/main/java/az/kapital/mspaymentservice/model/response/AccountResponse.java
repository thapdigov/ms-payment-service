package az.kapital.mspaymentservice.model.response;

import az.kapital.mspaymentservice.model.enums.Currency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AccountResponse {
    private Long id;
    private Long userId;
    private Currency currency;
    private BigDecimal balance;
}

