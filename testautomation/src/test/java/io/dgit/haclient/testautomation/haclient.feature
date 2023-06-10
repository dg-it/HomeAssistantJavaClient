Feature: fetching User Details

  Background:
    * url appURL = 'http://localhost:8080'

  Scenario: Verify health of app is UP

    Given path '/actuator/health'
    When method GET
    Then status 200
