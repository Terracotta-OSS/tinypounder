#!/bin/bash
# Quick start script for TinyPounder development server
# Starts the application using Jetty on http://localhost:9490

MAVEN_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED" ./mvnw jetty:run