#!/usr/bin/env bash

git reset --hard

git checkout 9.4

source env.sh

./tools/gen-dev-env.py -n 94

mciwt

git remote add origin ssh://sankarge@gerrit.ext.net.nokia.com:29418/ANALYTICS/na/na

git push --all

git push --tags