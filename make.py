# Script to build and test the app
import os
import subprocess

# Vars
proj_dir = os.getcwd()
app_dir = proj_dir + "/app"
ta_dir = proj_dir + "/testautomation"
ta_executable_jar_filepath = ta_dir + "/target/testautomation-0.0.1-SNAPSHOT-jar-with-dependencies.jar"

# Build
print("Building app from folder `%s`..." % app_dir)
subprocess.check_call(["mvn", "clean", "verify", "-q"], cwd=app_dir)
print("Building app from folder `%s` - done." % app_dir)

print("Building testautomation from folder `%s`..." % ta_dir)
subprocess.check_call(["mvn", "clean", "verify", "-q"], cwd=ta_dir)
print("Building testautomation from folder `%s` - done." % ta_dir)

# Test
print("Starting up containers for test automation and waiting for healthy state..")
subprocess.check_call(["docker-compose", "-f", ta_dir + "/docker/docker-compose.yml", "up", "-d", "--wait"], cwd=ta_dir)
print("Starting up containers for test automation and waiting for healthy state - done.")

print("Running test automation..")
subprocess.check_call(
    ["java", "-jar", ta_executable_jar_filepath, "--select-class=io.dgit.haclient.testautomation.KarateRunner"],
    cwd=ta_dir)
print("Running test automation - done.")

print("Cleaning up..")
subprocess.check_call(["docker-compose", "-f", ta_dir + "/docker/docker-compose.yml", "down"], cwd=ta_dir)
print("Cleaning up - done.")
