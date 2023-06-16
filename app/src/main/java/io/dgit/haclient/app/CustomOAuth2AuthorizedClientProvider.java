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
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import static io.dgit.haclient.app.WebSecurityConfig.CUSTOM_HOMEASSISTANT_OAUTH2_CLIENT_REGISTRATION_ID;

/**
 * Custom implementation for establishing an authorized client similarly as the {@link org.springframework.security.oauth2.client.ClientCredentialsReactiveOAuth2AuthorizedClientProvider}
 * however taking into account the specific login flow of HomeAssistant instance.
 */
@Slf4j
@RequiredArgsConstructor
public class CustomOAuth2AuthorizedClientProvider implements OAuth2AuthorizedClientProvider {
    private final Duration clockSkew = Duration.ofSeconds(60);
    private final Clock clock = Clock.systemUTC();

    private OAuth2AccessTokenResponseHttpMessageConverter converter = new OAuth2AccessTokenResponseHttpMessageConverter();

    @Override
    public OAuth2AuthorizedClient authorize(OAuth2AuthorizationContext context) {

        ClientRegistration clientRegistration = context.getClientRegistration();
        String clientId = clientRegistration.getClientId();
        if (!CUSTOM_HOMEASSISTANT_OAUTH2_CLIENT_REGISTRATION_ID.equals(clientId)) {
            log.debug("Aborting authorization of OAuth2AuthorizedClient for registration id: [{}]", clientId);
            return null;
        }

        /** see also {@link org.springframework.security.oauth2.client.ClientCredentialsOAuth2AuthorizedClientProvider} */
        OAuth2AuthorizedClient authorizedClient = context.getAuthorizedClient();
        if (authorizedClient != null && !hasTokenExpired(authorizedClient.getAccessToken())) {
            // If client is already authorized but access token is NOT expired than no
            // need for re-authorization
            return null;
        }

        // As per spec, in section 4.4.3 Access Token Response
        // https://tools.ietf.org/html/rfc6749#section-4.4.3
        // A refresh token SHOULD NOT be included.
        //
        // Therefore, renewing an expired access token (re-authorization)
        // is the same as acquiring a new access token (authorization).

        // The home assistant instance allows for obtaining an access code by consecutively:
        //        #  1  returns '$flow_id': curl http://localhost:8123/auth/login_flow --data '{"client_id":"http://localhost:8123/","handler":["homeassistant",null],"redirect_uri":"http://localhost:8123/onboarding.html?auth_callback=1"}' -H 'Content-Type: text/plain;charset=UTF-8'
        //        #  2  returns '$result': curl http://localhost:8123/auth/login_flow/$flow_id --data '{"username":"admin","password":"admin","client_id":"http://localhost:8123/"}'
        //        #  3  returns access token: curl -F "code=$result" -F "auth_callback=1" -F "state=eyJoYXNzVXJsIjoiaHR0cDovL2xvY2FsaG9zdDo4MTIzIiwiY2xpZW50SWQiOiJodHRwOi8vbG9jYWxob3N0OjgxMjMvIn0" http://localhost:8123/auth/token -F "grant_type=authorization_code" -F "client_id=http://localhost:8123/" http://localhost:8123/auth/token
        // 1
        var body = """
                    {"client_id":"http://localhost:8123/","handler":["homeassistant",null],"redirect_uri":"http://localhost:8123/onboarding.html?auth_callback=1"}            
                """;
        var resp1 = WebClient.builder()
                .baseUrl("http://localhost:8123")
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

        //2 '$result': curl http://localhost:8123/auth/login_flow/$flow_id --data '{"username":"admin","password":"admin","client_id":"http://localhost:8123/"}'
        var body2 =
                """
                {"username":"admin","password":"admin","client_id":"http://localhost:8123/"}
                """;
        var resp2 = WebClient.builder()
                .baseUrl("http://localhost:8123")
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

        //3  returns access token: curl -F "code=$result" -F "auth_callback=1" -F "state=eyJoYXNzVXJsIjoiaHR0cDovL2xvY2FsaG9zdDo4MTIzIiwiY2xpZW50SWQiOiJodHRwOi8vbG9jYWxob3N0OjgxMjMvIn0" http://localhost:8123/auth/token -F "grant_type=authorization_code" -F "client_id=http://localhost:8123/" http://localhost:8123/auth/token
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("code", resultId);
        builder.part("auth_callback", 1);
        builder.part("state", "eyJoYXNzVXJsIjoiaHR0cDovL2xvY2FsaG9zdDo4MTIzIiwiY2xpZW50SWQiOiJodHRwOi8vbG9jYWxob3N0OjgxMjMvIn0");
        builder.part("grant_type", "authorization_code");
        builder.part("client_id", "http://localhost:8123/");

        var resp3 = WebClient.builder()
                .baseUrl("http://localhost:8123")
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

        //{"access_token":"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJiZjRkYWQ1Njc4N2I0NTJlODE0ZjQ3ODNhYzcxZWZkZiIsImlhdCI6MTY4NjkzNzEyMiwiZXhwIjoxNjg2OTM4OTIyfQ.o5K0gJukTUA1vS83JpSN7UespQdkAL5ll9hNdqpU7Fs","token_type":"Bearer","refresh_token":"548137bfc2dc2060cacc296dd507b06c3a1bc941ff6c6ab31a550fb86196d2d6b5d846eccd916d6088950e1eb6e60998c0108055dadf5b581064a9855e447d3e","expires_in":1800,"ha_auth_provider":"homeassistant"}
        String accessTokenValue = resp3.get("access_token").asText();
        String refreshTokenValue = resp3.get("refresh_token").asText();
        String tokenTypeValue = resp3.get("token_type").asText();
        String expiresInValue = resp3.get("expires_in").asText();

        OAuth2AccessTokenResponse.Builder tokenBuilder = OAuth2AccessTokenResponse.withToken(accessTokenValue);
        tokenBuilder.refreshToken(refreshTokenValue);
        tokenBuilder.tokenType(OAuth2AccessToken.TokenType.BEARER);
        tokenBuilder.expiresIn(Long.valueOf(expiresInValue));

        OAuth2AccessTokenResponse tokenResponse = tokenBuilder.build();

        log.debug("Obtained oauth token resp: [{}]", tokenResponse);
        return new OAuth2AuthorizedClient(clientRegistration, context.getPrincipal().getName(),
                tokenResponse.getAccessToken());
//        return null;

//        OAuth2ClientCredentialsGrantRequest clientCredentialsGrantRequest = new OAuth2ClientCredentialsGrantRequest(
//                clientRegistration);
//        OAuth2AccessTokenResponse tokenResponse = getTokenResponse(clientRegistration, clientCredentialsGrantRequest);
//        return new OAuth2AuthorizedClient(clientRegistration, context.getPrincipal().getName(),
//                tokenResponse.getAccessToken());

        /*
        ClientRegistration{registrationId='custom', clientId='custom', clientSecret='', clientAuthenticationMethod=org.springframework.security.oauth2.core.ClientAuthenticationMethod@4fcef9d3, authorizationGrantType=org.springframework.security.oauth2.core.AuthorizationGrantType@4889ba9b, redirectUri='null', scopes=null, providerDetails=org.springframework.security.oauth2.client.registration.ClientRegistration$ProviderDetails@34cd102b, clientName='custom'}
        authorizedClient = null
        principal: AnonymousAuthenticationToken [Principal=anonymousUser, Credentials=[PROTECTED], Authenticated=true, Details=null, Granted Authorities=[ROLE_ANONYMOUS]]

         */
    }

    private boolean hasTokenExpired(OAuth2Token token) {
        return this.clock.instant().isAfter(token.getExpiresAt().minus(this.clockSkew));
    }

}
