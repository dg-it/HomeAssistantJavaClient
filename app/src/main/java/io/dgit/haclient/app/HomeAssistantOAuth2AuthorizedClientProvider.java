package io.dgit.haclient.app;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import static io.dgit.haclient.app.WebSecurityConfig.CUSTOM_HOMEASSISTANT_OAUTH2_CLIENT_REGISTRATION_ID;

/**
 * Custom implementation for authorizin OAuth2 client to an HomeAssitant instance.
 * Similar as the {@link org.springframework.security.oauth2.client.ClientCredentialsReactiveOAuth2AuthorizedClientProvider},
 * however taking into account the specific login flow of HomeAssistant instance.
 */
@Slf4j
@RequiredArgsConstructor
public class HomeAssistantOAuth2AuthorizedClientProvider implements OAuth2AuthorizedClientProvider {
    private final Duration clockSkew = Duration.ofSeconds(60);
    private final Clock clock = Clock.systemUTC();

    private final String baseUri;

    @Override
    public OAuth2AuthorizedClient authorize(OAuth2AuthorizationContext context) {

        ClientRegistration clientRegistration = context.getClientRegistration();
        String clientId = clientRegistration.getClientId();
        if (!CUSTOM_HOMEASSISTANT_OAUTH2_CLIENT_REGISTRATION_ID.equals(clientId)) {
            log.debug("Aborting authorization of OAuth2AuthorizedClient for registration id: [{}]", clientId);
            return null;
        }

        /* see also {@link org.springframework.security.oauth2.client.ClientCredentialsOAuth2AuthorizedClientProvider} */
        OAuth2AuthorizedClient authorizedClient = context.getAuthorizedClient();
        if (authorizedClient != null && !hasTokenExpired(authorizedClient.getAccessToken())) {
            log.debug("Client already authorized, no need for re-authorization");
            return null;
        }

        // The home assistant instance allows for obtaining an access code by consecutively:
        //        #  1  returns '$flow_id': curl http://localhost:8123/auth/login_flow --data '{"client_id":"http://localhost:8123/","handler":["homeassistant",null],"redirect_uri":"http://localhost:8123/onboarding.html?auth_callback=1"}' -H 'Content-Type: text/plain;charset=UTF-8'
        //        #  2  returns '$result': curl http://localhost:8123/auth/login_flow/$flow_id --data '{"username":"admin","password":"admin","client_id":"http://localhost:8123/"}'
        //        #  3  returns access token: curl -F "code=$result" -F "auth_callback=1" -F "state=eyJoYXNzVXJsIjoiaHR0cDovL2xvY2FsaG9zdDo4MTIzIiwiY2xpZW50SWQiOiJodHRwOi8vbG9jYWxob3N0OjgxMjMvIn0" http://localhost:8123/auth/token -F "grant_type=authorization_code" -F "client_id=http://localhost:8123/" http://localhost:8123/auth/token
        String flow_id1 = step1CreateFlowId();
        String resultId = step2CreateAuthCode(flow_id1);
        OAuth2AccessTokenResponse tokenResponse = step3AuthorizeClient(resultId);
        log.debug("Obtained oauth token resp: [{}]", tokenResponse);

        return new OAuth2AuthorizedClient(clientRegistration, context.getPrincipal().getName(),
                tokenResponse.getAccessToken());
    }

    private OAuth2AccessTokenResponse step3AuthorizeClient(String resultId) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("code", resultId);
        builder.part("auth_callback", 1);
        builder.part("state", "eyJoYXNzVXJsIjoiaHR0cDovL2xvY2FsaG9zdDo4MTIzIiwiY2xpZW50SWQiOiJodHRwOi8vbG9jYWxob3N0OjgxMjMvIn0");
        builder.part("grant_type", "authorization_code");
        builder.part("client_id", "http://localhost:8123/");

        var resp3 = WebClient.builder()
                .baseUrl(baseUri)
                .build()
                .post()
                .uri("/auth/token")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchangeToMono(response -> {
                    if (response.statusCode().equals(HttpStatus.OK)) {
                        return response.bodyToMono(JsonNode.class);
                    } else {
                        return response.createException()
                                .flatMap(Mono::error);
                    }

                })
                .block();
        OAuth2AccessTokenResponse.Builder tokenBuilder = OAuth2AccessTokenResponse.withToken(resp3.get("access_token").asText());
        tokenBuilder.refreshToken(resp3.get("refresh_token").asText());
        tokenBuilder.tokenType(OAuth2AccessToken.TokenType.BEARER);
        tokenBuilder.expiresIn(Long.parseLong(resp3.get("expires_in").asText()));
        return tokenBuilder.build();
    }

    private String step2CreateAuthCode(String flow_id1) {
        var body2 =
                """
                {"username":"admin","password":"admin","client_id":"http://localhost:8123/"}
                """;
        var resp2 = WebClient.builder()
                .baseUrl(baseUri)
                .build()
                .post()
                .uri("/auth/login_flow/{flow_id}", flow_id1)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body2)
                .exchangeToMono(response -> {
                    if (response.statusCode().equals(HttpStatus.OK)) {
                        return response.bodyToMono(Map.class);
                    } else {
                        return response.createException()
                                .flatMap(Mono::error);
                    }

                })
                .block();
        String resultId = (String)resp2.get("result");
        log.debug("Obtained result (id) [{}] from resp: [{}]", resultId, resp2);
        return resultId;
    }

    private String step1CreateFlowId() {
        log.debug("Authorizing client with home assistant instance with base uri: [{}]", baseUri);
        var body = """
                    {"client_id":"http://localhost:8123/","handler":["homeassistant",null],"redirect_uri":"http://localhost:8123/onboarding.html?auth_callback=1"}
                """;
        var resp1 = WebClient.builder()
                .baseUrl(baseUri)
                .build()
                .post()
                .uri("/auth/login_flow")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono(response -> {
                    if (response.statusCode().equals(HttpStatus.OK)) {
                        return response.bodyToMono(Map.class);
                    } else {
                        return response.createException()
                                .flatMap(Mono::error);
                    }

                })
                .block();
        String flow_id1 = (String)resp1.get("flow_id");
        log.debug("Obtained flow_id [{}] from resp: [{}]", flow_id1, resp1);
        return flow_id1;
    }

    private boolean hasTokenExpired(OAuth2Token token) {
        return this.clock.instant().isAfter(token.getExpiresAt().minus(this.clockSkew));
    }

}
