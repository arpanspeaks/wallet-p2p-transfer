package com.paytm.wallet.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import com.paytm.wallet.util.EntryLogger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    private final EntryLogger entryLogger;

    public OpenApiConfig(EntryLogger entryLogger) {
        this.entryLogger = entryLogger;
    }

    @Bean
    OpenAPI walletOpenApi() {
        entryLogger.log(getClass(), "walletOpenApi");
        String scheme = "bearerAuth";
        return new OpenAPI()
                .info(new Info().title("Paytm Wallet API").version("v1")
                        .description("P2P wallet transfers. Amounts are integer paise."))
                .components(new Components().addSecuritySchemes(scheme, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("opaque user token")))
                .addSecurityItem(new SecurityRequirement().addList(scheme));
    }
}
