Feature: HomeAssistantJavaClient health state

  Background:
    * url appURL = 'http://localhost:8080'

  Scenario: Verify health of app is available

    Given path '/actuator/health'
    When method GET
    Then status 200

  Scenario: Verify health of HomeAssistant instances is available

    Given path '/ha-instances/health'
    When method GET
    Then status 200
