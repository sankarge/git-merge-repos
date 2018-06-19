#!/usr/bin/env bash

merged_dir=$1

cd /udir/sankarge/git/merge/output/$merged_dir && git reset --hard && git checkout 9.4

source env.sh && ./tools/gen-dev-env.py -n 94 && mvn -T1C clean install -DskipTests -DskipLongRunningTests -DskipOpticalInterfaceTests

git remote add origin ssh://sankarge@gerrit.ext.net.nokia.com:29418/ANALYTICS/na/na

#git push --all

#git push --tags