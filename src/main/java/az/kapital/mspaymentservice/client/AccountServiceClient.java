package az.kapital.mspaymentservice.client;

import az.kapital.mspaymentservice.model.response.AccountResponse;
import az.kapital.mspaymentservice.model.request.BalanceUpdateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "ms-account-service", url = "${client.ms-account-service.url}")
public interface AccountServiceClient {

    @PostMapping("/api/accounts/internal/update-balance")
    AccountResponse updateBalance(BalanceUpdateRequest request);

}