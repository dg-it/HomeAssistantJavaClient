package io.dgit.haclient.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthenticatedPrincipalOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@Slf4j
public class WebSecurityConfig {

    public static final String CUSTOM_HOMEASSISTANT_OAUTH2_CLIENT_REGISTRATION_ID = "homeassistant_custom";
    @Value("${app.integration.homeassistant.baseuri}")
    private String baseUri;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .sessionManagement(sessionConfig -> sessionConfig.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(AbstractHttpConfigurer::disable)
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests((authorizeHttpRequests) ->
                    authorizeHttpRequests
                            .requestMatchers("/**").hasRole("USER")
            )
            .httpBasic(withDefaults())
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .build();
    }
    @Bean
    public UserDetailsService userDetailsService() {
        //noinspection deprecation, note: not save for production
        UserDetails user = User.withDefaultPasswordEncoder()
                .username("appadmin").password("appadmin").roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public WebClient webClient(OAuth2AuthorizedClientManager authorizedClientManager) {
        var oauth2OutboundClient = new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2OutboundClient.setDefaultClientRegistrationId(CUSTOM_HOMEASSISTANT_OAUTH2_CLIENT_REGISTRATION_ID);

        // wire the oauth2OutboundClient filter with a WebClient for outbound web requests
        return WebClient.builder()
                .filter(oauth2OutboundClient)
                .build();
    }
    @Bean
    public OAuth2AuthorizedClientProvider oAuth2AuthorizedClientProvider() {
        var delegatingClientProvider = new DelegatingOAuth2AuthorizedClientProvider(new HomeAssistantOAuth2AuthorizedClientProvider(baseUri));
        return OAuth2AuthorizedClientProviderBuilder.builder()
                .provider(delegatingClientProvider)
                .refreshToken()
                .build();
    }

    @Bean
    public DefaultOAuth2AuthorizedClientManager oAuth2AuthorizedClientManager(ClientRegistrationRepository clientRegistrationRepository,
                                                                       OAuth2AuthorizedClientRepository authorizedClientRepository) {
        var clientManager = new DefaultOAuth2AuthorizedClientManager(clientRegistrationRepository, authorizedClientRepository);
        clientManager.setAuthorizedClientProvider(oAuth2AuthorizedClientProvider());
        return clientManager;
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
        //noinspection deprecation, grant type is required, but we use a custom means to obtain access code anyway
        ClientRegistration customClientRegistration = ClientRegistration
                .withRegistrationId(CUSTOM_HOMEASSISTANT_OAUTH2_CLIENT_REGISTRATION_ID)
                .clientId(CUSTOM_HOMEASSISTANT_OAUTH2_CLIENT_REGISTRATION_ID)
                .authorizationGrantType(AuthorizationGrantType.PASSWORD)
                .tokenUri("http://notneededbutrequired")
                .build();
        return new InMemoryClientRegistrationRepository(customClientRegistration);
    }

    @Bean
    OAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository(OAuth2AuthorizedClientService clientService) {
        return new AuthenticatedPrincipalOAuth2AuthorizedClientRepository(clientService);
    }

    @Bean
    OAuth2AuthorizedClientService oAuth2AuthorizedClientService(ClientRegistrationRepository clientRegistrationRepository) {
        return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
    }
}
