#!/usr/bin/env bash
if [[ $(git status --porcelain) ]]; then
  echo "Git changes detected, commit all changes first before releasing"
  exit
fi
echo "Building 3.3.9 branch"
git checkout mvn339 || exit 1
mvn -Prelease clean site deploy || exit 1
echo "Building main (3.8.4) branch"
git checkout main || exit 1
mvn -Prelease site deploy || exit 1
echo "done!"