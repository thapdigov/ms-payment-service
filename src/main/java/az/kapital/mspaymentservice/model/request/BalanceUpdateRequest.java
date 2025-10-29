package az.kapital.mspaymentservice.model.request;

import az.kapital.mspaymentservice.model.enums.Currency;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.antlr.v4.runtime.misc.NotNull;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BalanceUpdateRequest {

    private Long userId;

    @Enumerated(EnumType.STRING)
    private Currency currency;

    @NotNull
    private BigDecimal amount;
}
