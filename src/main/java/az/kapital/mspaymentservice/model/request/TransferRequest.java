package az.kapital.mspaymentservice.model.request;

import az.kapital.mspaymentservice.model.enums.Currency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {

    @NotBlank
    private String idempotencyKey;

    @NotNull
    private Long sourceUserId;

    @NotNull
    private Long destinationAccountId;

    @NotNull
    @Positive(message = "Amount must be greater than zero.")
    private BigDecimal amount;

    @NotNull
    private Currency currency;
}
