Feature: HomeAssistantJavaClient health state

  Background:
    * url appURL = 'http://localhost:8080'
    * url haURL = 'http://localhost:8123'
    * def adminUser = callonce read('common-add-admin-user.feature')
    * def adminUserAccessTokens = callonce read('common-login-access-token.feature')
    * def access_token = (adminUserAccessTokens.access_token)
    * def refresh_token = (adminUserAccessTokens.refresh_token)

  Scenario: Verify health of app is available

    Given url appURL
    And path '/actuator/health'
    When method GET
    Then status 200

  Scenario: Set-up HomeAssistant - verify onboarding state, user set-up is done.
    Given url haURL
    And path '/api/onboarding'
    When method GET
    Then status 200
    And match response == [{"step":"user","done":true},{"step":"core_config","done":false},{"step":"analytics","done":false},{"step":"integration","done":false}]

  Scenario: Set-up HomeAssistant: configure core config
    # interestingly, the post does not contain any actual data..
#     curl 'http://localhost:8123/api/onboarding/core_config' \
#      -X 'POST' \
#      -H 'Accept: */*' \
#      -H 'Accept-Language: en-GB,en;q=0.9' \
#      -H 'Cache-Control: no-cache' \
#      -H 'Connection: keep-alive' \
#      -H 'Content-Length: 0' \
#      -H 'Origin: http://localhost:8123' \
#      -H 'Pragma: no-cache' \
#      -H 'Referer: http://localhost:8123/onboarding.html' \
#      -H 'Sec-Fetch-Dest: empty' \
#      -H 'Sec-Fetch-Mode: cors' \
#      -H 'Sec-Fetch-Site: same-origin' \
#      -H 'authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJhYzJhODcxNzBlN2Y0YzQ4OTYxOWUwZjcyM2E4ZGY3NSIsImlhdCI6MTY4NjYwMTM3OSwiZXhwIjoxNjg2NjAzMTc5fQ.RPsORU02fMsX0u2g-i8BHcHcA3AomrvyC-vFhc1ped0' \
#      --compressed
    Given url haURL
    And path '/api/onboarding/core_config'
    And header Authorization = 'Bearer ' + access_token
    And json data = ''
    And request data
    When method POST
    Then status 200

  Scenario: Set-up HomeAssistant - verify onboarding state, core config set-up is done.
    Given url haURL
    And path '/api/onboarding'
    When method GET
    Then status 200
    And match response == [{"step":"user","done":true},{"step":"core_config","done":true},{"step":"analytics","done":false},{"step":"integration","done":false}]

  Scenario: Set-up HomeAssistant - verify response of auth providers remains the same
    Given url haURL
    And path '/auth/providers'
    When method GET
    Then status 200
    And match response == [{"name":"Home Assistant Local","id":null,"type":"homeassistant"}]

  # to login screen here: http://localhost:8123/auth/authorize?response_type=code&redirect_uri=http%3A%2F%2Flocalhost%3A8123%2Fonboarding.html%3Fauth_callback%3D1&client_id=http%3A%2F%2Flocalhost%3A8123%2F&state=eyJoYXNzVXJsIjoiaHR0cDovL2xvY2FsaG9zdDo4MTIzIiwiY2xpZW50SWQiOiJodHRwOi8vbG9jYWxob3N0OjgxMjMvIn0%3D
  # with state being the 'eyJoYXNzVXJsIjoiaHR0cDovL2xvY2FsaG9zdDo4MTIzIiwiY2xpZW50SWQiOiJodHRwOi8vbG9jYWxob3N0OjgxMjMvIn0' encoded payload for
  # {
  #  "hassUrl": "http://localhost:8123",
  #  "clientId": "http://localhost:8123/"
  # }
  # followed by logging POSTING password here
  # POST http://localhost:8123/auth/login_flow/608d0119bcc129a5b8092e2d48aabb89

  # but we first need to get the code ..
  #    curl 'http://localhost:8123/auth/login_flow' \
  #  -H 'Accept: */*' \
  #  -H 'Accept-Language: en-GB,en;q=0.9' \
  #  -H 'Cache-Control: no-cache' \
  #  -H 'Connection: keep-alive' \
  #  -H 'Content-Type: text/plain;charset=UTF-8' \
  #  -H 'Origin: http://localhost:8123' \
  #  -H 'Pragma: no-cache' \
  #  -H 'Referer: http://localhost:8123/auth/authorize?response_type=code&redirect_uri=http%3A%2F%2Flocalhost%3A8123%2Fonboarding.html%3Fauth_callback%3D1&client_id=http%3A%2F%2Flocalhost%3A8123%2F&state=eyJoYXNzVXJsIjoiaHR0cDovL2xvY2FsaG9zdDo4MTIzIiwiY2xpZW50SWQiOiJodHRwOi8vbG9jYWxob3N0OjgxMjMvIn0%3D' \
  #  -H 'Sec-Fetch-Dest: empty' \
  #  -H 'Sec-Fetch-Mode: cors' \
  #  -H 'Sec-Fetch-Site: same-origin' \
  #  --data-raw '{"client_id":"http://localhost:8123/","handler":["homeassistant",null],"redirect_uri":"http://localhost:8123/onboarding.html?auth_callback=1"}' \
  #  --compressed
  Scenario: Set-up HomeAssistant: obtain code for password login flow
    Given url haURL
    And path '/auth/login_flow'
    And header Content-Type = 'text/plain;charset=UTF-8'
    And json data = '{"client_id":"http://localhost:8123/","handler":["homeassistant",null],"redirect_uri":"http://localhost:8123/onboarding.html?auth_callback=1"}'
    And request data
    When method POST
    Then status 200
    And match response == {"type":"form","flow_id":"#present","handler":["homeassistant",null],"step_id":"init","data_schema":[{"type":"string","name":"username","required":true},{"type":"string","name":"password","required":true}],"errors":{},"description_placeholders":null,"last_step":null}
    * def flow_id = response.flow_id

    # TODO create callonce feature for the flow_id for reusability
    # Continuing with given flow_id and given user, obtain auth code
    Given url haURL
    And path 'auth/login_flow/' + flow_id
    And json data = '{"username":"admin","password":"admin","client_id":"http://localhost:8123/"}'
    And request data
    When method POST
    Then status 200
    And match response == {"version":1,"type":"create_entry","flow_id":"##(flow_id)","handler":["homeassistant",null],"description":null,"description_placeholders":null,"result":"#present"}
    * def result_id = response.result
    * print 'HomeAssistant set-up resolved result id: ' + result_id

    # result_id appears to be enough for continuing onboarding
    # this one works
    Given url haURL
    And path '/onboarding.html'
    And param auth_callback = 1
    And param code = result_id
    And string state = 'eyJoYXNzVXJsIjoiaHR0cDovL2xvY2FsaG9zdDo4MTIzIiwiY2xpZW50SWQiOiJodHRwOi8vbG9jYWxob3N0OjgxMjMvIn0'
    When method GET
    Then status 200
    * print 'HomeAssistant set-up logged in and continued to next onboarding step'


  Scenario: HomeAssistant API should be available (https://developers.home-assistant.io/docs/api/rest/) given access token
    Given url haURL
    # not that the trailing slash is material, if absent results in 404
    # however, karate removes the trailing slash https://github.com/karatelabs/karate/issues/1863
    # credits to https://github.com/karatelabs/karate/issues/1561#issuecomment-821928620 for workaround
    And path  'api', ''
    And header Authorization = 'Bearer ' + access_token
    And header Content-Type = 'application/json'
    When method GET
    Then status 200
    And match response == {"message":"API running."}

  Scenario: Verify health of HomeAssistant instances is available

    Given url appUrl
    When path '/ha-instances/health'
    When method GET
    Then status 200
