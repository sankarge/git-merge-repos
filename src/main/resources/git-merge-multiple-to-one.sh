#!/usr/bin/env bash

source ~/.bashrc

git pull

today=`date '+%d-%^h-%Y'`
dir=bare_$today

echo `date`: Started git-filter-branch

# Re-write all old SHA1 to include repo name as parent directories as this needed
# for merging multiple repo into one [following mvn job will do that].
# Note: before executing, switch into  resources dir, ex: cd /udir/sankarge/git/merge/git-merge-repos/src/main/resources

./src/main/resources/git-filter-branch.sh | tee /udir/$USER/git/merge/input/git_filter_branch_$today.log

sleep 60

count1=0
count2=0

update(){
    count1=`ps -eaf | grep -v grep | grep "filter-branch" | wc -l`
    sleep 30
    count2=`ps -eaf | grep -v grep | grep "filter-branch" | wc -l`
}

update

while [[ $count1 != 0 || $count2 != 0 ]]
do
    echo `date`: Gonna retry after 30 mins, waiting for $count1 , $count2 bg process to complete
    sleep 1800
    update
done

echo `date`: Completed git-filter-branch

sleep 300

echo `date`: Started merging multiple git repo into one, from $dir

mvn --file=pom.xml clean compile exec:java -Dexec.args="merge /udir/sankarge/git/merge/output/merged /udir/sankarge/git/merge/input/$dir/sdcna-super:. /udir/sankarge/git/merge/input/$dir/sdcna:. /udir/sankarge/git/merge/input/$dir/commons:. /udir/sankarge/git/merge/input/$dir/dsl:. /udir/sankarge/git/merge/input/$dir/drdsl-webapp:. /udir/sankarge/git/merge/input/$dir/platform:. /udir/sankarge/git/merge/input/$dir/optical:. /udir/sankarge/git/merge/input/$dir/ipm:. /udir/sankarge/git/merge/input/$dir/na-birt:." | tee mvn.log 2>&1

echo `date`: Completed merging multiple git repo into one, from $dir

./src/main/resources/post-merge.sh