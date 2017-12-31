package org.nibor.git_merge_repos.merger;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.nibor.git_merge_repos.vo.MergedRef;
import org.nibor.git_merge_repos.vo.SubtreeConfig;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by sankarge on 12/21/17.
 */
public class BranchMerger extends AbstractMerger {

    public BranchMerger(List<SubtreeConfig> subtreeConfigs, Repository repository) {
        super(subtreeConfigs, repository);
    }

    public MergedRef mergeBranch(String branch, String previousTag) throws IOException {
        Map<SubtreeConfig, ObjectId> resolvedRefs = resolveRefs(
                "refs/heads/original/", branch);

        Map<SubtreeConfig, RevCommit> parentCommits = new LinkedHashMap<>();
        try (RevWalk revWalk = new RevWalk(repository)) {
            for (SubtreeConfig config : subtreeConfigs) {
                ObjectId objectId = resolvedRefs.get(config);
                if (objectId != null) {
                    RevCommit commit = revWalk.parseCommit(objectId);
                    parentCommits.put(config, commit);
                }
            }
        }

        MergedRef mergedRef = getMergedRef("branch", branch, parentCommits.keySet());
        ObjectId mergeCommit;
        if (previousTag == null) {
            mergeCommit = new SubtreeMerger(repository).createMergeCommit(parentCommits,
                    mergedRef.getMessage());
        } else {
            RevCommit revCommit = getCommitOfTag(previousTag);
            mergeCommit = new SubtreeMerger(repository).createMergeCommit(parentCommits, revCommit,
                    mergedRef.getMessage());
        }

        RefUpdate refUpdate = repository.updateRef("refs/heads/" + branch);
        refUpdate.setNewObjectId(mergeCommit);
        refUpdate.update();
        return mergedRef;
    }
}