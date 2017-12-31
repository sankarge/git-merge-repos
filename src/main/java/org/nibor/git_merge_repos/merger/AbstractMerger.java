package org.nibor.git_merge_repos.merger;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.nibor.git_merge_repos.log.LoggerUtil;
import org.nibor.git_merge_repos.vo.MergedRef;
import org.nibor.git_merge_repos.vo.SubtreeConfig;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class AbstractMerger {

    protected final List<SubtreeConfig> subtreeConfigs;

    protected final Repository repository;

    public AbstractMerger(List<SubtreeConfig> subtreeConfigs, Repository repository) {
        this.subtreeConfigs = subtreeConfigs;
        this.repository = repository;
    }

    protected Map<SubtreeConfig, ObjectId> resolveRefs(String refPrefix,
                                                       String name) throws IOException {
        Map<SubtreeConfig, ObjectId> result = new LinkedHashMap<>();
        for (SubtreeConfig config : subtreeConfigs) {
            String repositoryName = config.getRemoteName();
            String remoteBranch = refPrefix + repositoryName + "/" + name;
            ObjectId objectId = repository.resolve(remoteBranch);
            if (objectId != null) {
                result.put(config, objectId);
            }
        }
        return result;
    }

    protected RevCommit getCommitOfTag(String tagName) {
        try {
            ObjectId objectId = repository.resolve("refs/tags/" + tagName);
            RevWalk revWalk = new RevWalk(repository);
            RevObject revObject = revWalk.parseAny(objectId);
            RevCommit revCommit = null;

            if (revObject instanceof RevCommit) {
                revCommit = (RevCommit) revObject;
            } else if (revObject instanceof RevTag) {
                RevTag tag = (RevTag) revObject;
                revCommit = (RevCommit) revWalk.peel(tag);
            }
            return revCommit;
        } catch (Exception e) {
            LoggerUtil.log.log(Level.SEVERE, "Unable to getCommitOfTag " + tagName + " due to " + e.getMessage());
        }
        return null;
    }

    protected MergedRef getMergedRef(String refType, String refName,
                                   Set<SubtreeConfig> configsWithRef) {
        LinkedHashSet<SubtreeConfig> configsWithoutRef = new LinkedHashSet<>(
                subtreeConfigs);
        configsWithoutRef.removeAll(configsWithRef);

        return new MergedRef(refType, refName, configsWithRef, configsWithoutRef);
    }
}