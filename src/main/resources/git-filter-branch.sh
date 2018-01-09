#!/usr/bin/env bash
#usage: time git-filter-branch.sh > git_filter-branch.PREPARE_LOG 2>&1 &

root=/udir/$USER/git/merge/input/bare1/
mkdir -p $root
cd $root
echo "Initiating git-merge-repo from: " $root

echo "Creating bare repo: sdcna-super"
git clone --mirror git-na@git-1.be.alcatel-lucent.com:/sdcna-super.git sdcna-super

gitSlave=/udir/$USER/git/9.2/.gitslave
echo "Taking list of slave repos from: " $gitSlave

for repo in `awk '!/puppet/{print $2}' $gitSlave | sed -e 's/"//g'`
	do
		echo ""
		echo "Creating bare repo: "$repo

		git clone --mirror git-na@git-1.be.alcatel-lucent.com:/$repo $repo

		echo ""
		echo "Re-writing commit id's via git-filter-branch to add parent directory [background process]: " $repo

		cd $repo

        nohup git filter-branch --index-filter 'tab=$(printf "\t") && git ls-files -s --error-unmatch . >/dev/null 2>&1; [ $? != 0 ] || (git ls-files -s | sed "s~$tab\"*~&'$repo'/~" | GIT_INDEX_FILE=$GIT_INDEX_FILE.new git update-index --index-info && mv "$GIT_INDEX_FILE.new" "$GIT_INDEX_FILE")'  --tag-name-filter cat -- --all > git-filter-branch-$repo.log 2>&1 &

		cd $root
	done