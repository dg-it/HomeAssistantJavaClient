package io.dgit.haclient.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebSecurity
@EnableAutoConfiguration(exclude = {OAuth2ClientAutoConfiguration.class, ReactiveOAuth2ClientAutoConfiguration.class})
@Slf4j
public class WebSecurityConfig {

    public static final String HOMEASSISTANT_CUSTOM_CLIENT_REGISTRATION_ID = "homeassistant_custom";

    //incoming
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring()
                // Spring Security should completely ignore URLs starting with /resources/
//                .requestMatchers("/resources/**");
                .requestMatchers("/**");
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.withDefaultPasswordEncoder()
                .username("appadmin")
                .password("appadmin")
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    /** Configure WebClient with OAuth2 client support */
    @Bean
//    public WebClient webClient(ClientRegistrationRepository clientRegistrationRepository,
//                               OAuth2AuthorizedClientRepository authorizedClientRepository) {
    public WebClient webClient(OAuth2AuthorizedClientManager authorizedClientManager) {

        //this constructor configures a RemoveAuthorizedClientOAuth2AuthorizationFailureHandler automatically
//        var oauth2Client = new ServletOAuth2AuthorizedClientExchangeFilterFunction(clientRegistrationRepository, authorizedClientRepository);
        var oauth2Client2 = new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2Client2.setDefaultClientRegistrationId(HOMEASSISTANT_CUSTOM_CLIENT_REGISTRATION_ID);

        // a RemoveAuthorizedClientOAuth2AuthorizationFailureHandler will NOT be configured automatically.
        // It is recommended that you configure one via setAuthorizationFailureHandler(OAuth2AuthorizationFailureHandler).
        return WebClient.builder()
                .filter(oauth2Client2)
//                .apply(oauth2Client.oauth2Configuration())
                .filters(exchangeFilterFunctions -> {
                    exchangeFilterFunctions.add(logRequest());
                    exchangeFilterFunctions.add(logResponse());
                })
                .build();
    }

    @Bean
    public OAuth2AuthorizedClientProvider oAuth2AuthorizedClientProvider() {
        //alternative is, to configure a OAuth2AuthorizedClientProvider composite with our custom provider
        DelegatingOAuth2AuthorizedClientProvider delegatingOAuth2AuthorizedClientProvider
                = new DelegatingOAuth2AuthorizedClientProvider(new CustomOAuth2AuthorizedClientProvider());
            OAuth2AuthorizedClientProvider authorizedClientProvider =
                    OAuth2AuthorizedClientProviderBuilder.builder()
                            .provider(delegatingOAuth2AuthorizedClientProvider)
        //                        .authorizationCode()
                            .refreshToken()
        //                        .clientCredentials()
        //                        .password()
                            .build();
        return delegatingOAuth2AuthorizedClientProvider;
    }

    @Bean
    @Primary
    public DefaultOAuth2AuthorizedClientManager oAuth2AuthorizedClientManager(ClientRegistrationRepository clientRegistrationRepository,
                                                                       OAuth2AuthorizedClientService oAuth2AuthorizedClientService,
                                                                       OAuth2AuthorizedClientRepository authorizedClientRepository) {
        //AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, oAuth2AuthorizedClientService);



        DefaultOAuth2AuthorizedClientManager authorizedClientManager =
                new DefaultOAuth2AuthorizedClientManager(
                        clientRegistrationRepository, authorizedClientRepository);

        authorizedClientManager.setAuthorizedClientProvider(oAuth2AuthorizedClientProvider());
        return authorizedClientManager;
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration customClientRegistration = ClientRegistration
                .withRegistrationId(HOMEASSISTANT_CUSTOM_CLIENT_REGISTRATION_ID)
                .clientId(HOMEASSISTANT_CUSTOM_CLIENT_REGISTRATION_ID)
                .authorizationGrantType(AuthorizationGrantType.PASSWORD)
                .tokenUri("http://notneeded")
                .build();
        return new InMemoryClientRegistrationRepository(customClientRegistration);
    }
    ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            if (log.isDebugEnabled()) {
                StringBuilder sb = new StringBuilder("Request: \n");
                //append clientRequest method and url
                clientRequest
                        .headers()
                        .forEach((name, values) -> values.forEach(value -> sb.append(value) /* append header key/value */));
                log.debug(sb.toString());
            }
            return Mono.just(clientRequest);
        });
    }

    ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            if (log.isDebugEnabled()) {
                StringBuilder sb = new StringBuilder("Response: \n");
                sb.append(clientResponse);
//                clientResponse
//                        .headers()
//                        .forEach((name, values) -> values.forEach(value -> sb.append(value) /* append header key/value */));
                log.debug(sb.toString());
            }
            return Mono.just(clientResponse);
        });
    }

    @Bean
    OAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository() {
        return new HttpSessionOAuth2AuthorizedClientRepository();
    }


    @Bean
    OAuth2AuthorizedClientService oAuth2AuthorizedClientService(ClientRegistrationRepository clientRegistrationRepository) {
        return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
    }
}
