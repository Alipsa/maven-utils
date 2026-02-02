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
#echo "Building 3.3.9 branch"
#git checkout mvn339 || exit 1
#mvn -Prelease clean site deploy || exit 1
#cp target/*.jar build/
#echo "Building main (3.8.4) branch"
#git checkout main || exit 1
mvn -Prelease clean site deploy || exit 1
cp target/*.jar build/
echo "done! jar files are in the build dir"