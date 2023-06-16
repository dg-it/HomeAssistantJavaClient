# HomeAssistantJavaClient

Java library to integrate with Home Assistant.

Leverages Spring Security OAuth2 client features and includes a custom OAuth2 Client Authorization Provider to authenticate/authorize
this app with a HomeAssistant instance.

## Build

Build all and execute test automation in one go:

```shell
python make.py
```

requires (version):
- Java (19)
- Maven (3.5.2)
- Python (3.9.15)
- Docker (20.10.21)
- Docker Compose (v2.12.2)

Works with versions mentioned, YMMV with other versions. 

This executes
- building the javaclient app
- building the testautomation
- building and starting of docker containers: a fresh homeassistant docker container, and the javaclient app
- the testautomation against the app backed by the homeassistant docker container
Please note that for repeated execution, the folder ./testautomation/docker/homeassistant/config is removed.

## Target use case of this Java client
Ease separation of concern: separate the concern of local set-up of a HomeAssistant instance (e.g. in a particular holiday home) and the concern of managing multiple HomeAssistant instances (e.g. of all holiday homes in a particular holiday home park). 

The idea is to have local HomeAssistant instances remain as simple, plain-vanilla and focussed on local devices as much as possible - which by itself can be already quite challenging -, while a locally running (Java) agent monitors and controls such instance using this library. 

Such agent can take on other concerns, like secured integration with the outside world over the internet, e.g. with a cloud-based app for managing a holiday home park. This leaves the HomeAssistant instance unaware of such integrations and may ease proper firewalling and security.
