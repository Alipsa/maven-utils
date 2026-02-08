#!/usr/bin/env bash
set -e
if [[ $(git status --porcelain) ]]; then
  echo "Git changes detected, commit all changes first before releasing"
  exit 1
fi
if [[ ! -d build ]]; then
  mkdir "build"
fi
rm target/*.jar
if command -v jdk17; then
  . jdk17
fi
mvn -Prelease clean site deploy -Dmavenutils.runSystemIT=true
cp target/*.jar build/
echo "done! jar files are in the build dir"