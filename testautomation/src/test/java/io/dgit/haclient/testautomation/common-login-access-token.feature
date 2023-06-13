@ignore
Feature: common routine that obtains an access token based on akin of OAuth2.0 grant_type=password flow

# Feature to be used in a Karate's callonce construct to obtain an access token for API calls
#
# The login procedure of HomeAssistant reverse engineered by using the following curl's
#  1  returns '$flow_id': curl http://localhost:8123/auth/login_flow --data '{"client_id":"http://localhost:8123/","handler":["homeassistant",null],"redirect_uri":"http://localhost:8123/onboarding.html?auth_callback=1"}' -H 'Content-Type: text/plain;charset=UTF-8'
#  2  returns '$result': curl http://localhost:8123/auth/login_flow/$flow_id --data '{"username":"admin","password":"admin","client_id":"http://localhost:8123/"}'
#  3  returns access token: curl -F "code=$result" -F "auth_callback=1" -F "state=eyJoYXNzVXJsIjoiaHR0cDovL2xvY2FsaG9zdDo4MTIzIiwiY2xpZW50SWQiOiJodHRwOi8vbG9jYWxob3N0OjgxMjMvIn0" http://localhost:8123/auth/token -F "grant_type=authorization_code" -F "client_id=http://localhost:8123/" http://localhost:8123/auth/token
# See also lines 294 and 383 here: https://github.com/home-assistant/frontend/blob/fa1a6affa752a596059e4bd25497f3be7db02ba0/src/auth/ha-auth-flow.ts#L294

  Background:
  * url haURL = 'http://localhost:8123'

  Scenario: Obtain accesss token for user admin

  #1 Obtain flow_id
  Given url haURL
  And path '/auth/login_flow'
  And json data = '{"client_id":"http://localhost:8123/","handler":["homeassistant",null],"redirect_uri":"http://localhost:8123/onboarding.html?auth_callback=1"}'
  And request data
  When method POST
  Then status 200
  # match might prove too strict, as we only need 'flow_id' here
  And match response == {"type":"form","flow_id":"#present","handler":["homeassistant",null],"step_id":"init","data_schema":[{"type":"string","name":"username","required":true},{"type":"string","name":"password","required":true}],"errors":{},"description_placeholders":null,"last_step":null}
  * def flow_id = response.flow_id

  #2 Obtain authCode
  Given url haURL
  And path '/auth/login_flow/', flow_id
  And json data = '{"username":"admin","password":"admin","client_id":"http://localhost:8123/"}'
  And request data
  When method POST
  Then status 200
  # match might prove too strict, as we only need 'result' here
  And match response == {"version":1,"type":"create_entry","flow_id":"##(flow_id)","handler":["homeassistant",null],"description":null,"description_placeholders":null,"result":"#present"}
  * def authCode = response.result

  # 3 obtain access tokens
  Given url haURL
  And path '/auth/token'
  And multipart field code = authCode
  And multipart field auth_callback = '1'
  And multipart field state = 'eyJoYXNzVXJsIjoiaHR0cDovL2xvY2FsaG9zdDo4MTIzIiwiY2xpZW50SWQiOiJodHRwOi8vbG9jYWxob3N0OjgxMjMvIn0'
  And multipart field client_id = 'http://localhost:8123/'
  And multipart field grant_type = 'authorization_code'
  When method POST
  Then status 200
  # match might prove too strict, as we only need 'result' here
  And match response ==
    """
    {
      "access_token": "#present",
      "token_type": "Bearer",
      "refresh_token": "#present",
      "expires_in": "#present",
      "ha_auth_provider": "homeassistant"
    }
    """
  * def access_token = response.access_token
  * def refresh_token = response.refresh_token
  * print 'Obtained access tokens for username [admin] and password [admin]'
