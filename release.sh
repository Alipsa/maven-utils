#!/usr/bin/env bash
if [[ $(git status --porcelain) ]]; then
  echo "Git changes detected, commit all changes first before releasing"
  exit
fi
if [[ ! -d build ]]; then
  mkdir "build"
fi
rm target/*.jar
if command -v jdk17; then
  . jdk17
fi
mvn -Prelease clean site deploy || exit 1
cp target/*.jar build/
echo "done! jar files are in the build dir"