#!/usr/bin/env bash
if command -v jdk17; then
  . jdk17
fi
mvn versions:display-plugin-updates versions:display-dependency-updates versions:display-property-updates