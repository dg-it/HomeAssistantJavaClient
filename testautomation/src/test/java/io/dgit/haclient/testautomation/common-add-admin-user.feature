@ignore
Feature: common routine that creates a new admin user for a fresh HomeAssistant instance

  Background:
    * url haURL = 'http://localhost:8123'
  # Note 1: Turned out to not be working to just track the .storage/auth_provider.homeassistant file, so we need another way to
  #         automatically configure HomeAssistant with an admin user.
  # Note 2: Below web requests are captured by inspecting browser network in debug mode, as we don't seem to have fully
  #         non-interactive scripted set-up possibility unfortunately.

  Scenario: Set-up HA: add admin user with and obtain access token for further set-up
    Given url haURL
    And path '/api/onboarding/users'
    And header Content-Type = 'text/plain;charset=UTF-8'
    And header Referer = 'http://localhost:8123/onboarding.html'
    And json data = '{"client_id":"http://localhost:8123/","name":"admin","username":"admin","password":"admin","language":"en-US"}'
    And request data
    When method POST
    Then status 200
    And match response == {"auth_code":"#present"}
    * def auth_code = response.auth_code

    Given url haURL
    And path '/auth/token'
    And multipart field client_id = 'http://localhost:8123/'
    And multipart field code = auth_code
    And multipart field grant_type = 'authorization_code'
    When method POST
    Then status 200
    And match response ==
    """
    {
    "access_token": "#present",
    "token_type": "Bearer",
    "refresh_token": "#present",
    "expires_in": 1800,
    "ha_auth_provider": "homeassistant"
    }
    """
    * def access_token = response.access_token
    * def refresh_token = response.refresh_token
    * print 'Obtained access token: ' + access_token
