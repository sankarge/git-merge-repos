package org.nibor.git_merge_repos.merger;

import org.eclipse.jgit.api.DescribeCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.nibor.git_merge_repos.vo.SubtreeConfig;
import org.nibor.git_merge_repos.vo.TagInfo;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

import static org.nibor.git_merge_repos.log.LoggerUtil.PREPARE_LOG;
import static org.nibor.git_merge_repos.util.FileUtil.saveMap;

/**
 * Created by sankarge on 1/8/18.
 */
public class ParentTagCollector extends RepoMerger {

    private final GITCli gitCli;

    public ParentTagCollector(String outputRepositoryPath, List<SubtreeConfig> subtreeConfigs) throws IOException {
        super(outputRepositoryPath, subtreeConfigs);
        gitCli = new GITCli(repository.getDirectory());
    }

    public void collect() throws IOException, GitAPIException {
        fetch();
        Collection<String> branches = getRefSet(HEADS);
        Collection<String> tags = getRefSet(TAGS);
        Map<String, TreeSet<String>> tagsOfBranch = groupTagsUnderBranch(branches, tags);
        loadParentTagOfFirstTagOnEachBranch(tagsOfBranch);
    }

    private void fetch() throws GitAPIException {
        for (SubtreeConfig config : subtreeConfigs) {
            RefSpec branchesSpec = new RefSpec("refs/heads/*:refs/heads/original/" + config.getRemoteName() + "/*");
            RefSpec tagsSpec = new RefSpec("refs/tags/*:refs/tags/original/" + config.getRemoteName() + "/*");
            git.fetch().setRemote(config.getFetchUri().toPrivateString()).setRefSpecs(branchesSpec, tagsSpec).call();
        }
    }

    private void loadParentTagOfFirstTagOnEachBranch(Map<String, TreeSet<String>> tagsOfBranch) {
        Map<String, String> tagParentInfo = new TreeMap<>();
        tagsOfBranch.values().forEach(s -> s.stream().findFirst().ifPresent(tag -> loadParentTag(tagParentInfo, tag)));
        log(Level.INFO, "Parent tag information " + tagParentInfo);
        saveMap(tagParentInfo);
    }

    private void loadParentTag(Map<String, String> tagParentInfo, String tag) {
        PREPARE_LOG.info("Finding parent tag of " + tag);
        List<TagInfo> parentTagSet = new ArrayList<>();
        for (SubtreeConfig config : subtreeConfigs) {
            if (!config.getRemoteName().equals("na-birt")) {
                String parentTag = null;
                String tagFullPath = TAGS + config.getRemoteName() + "/" + tag;
                try {
                    DescribeCommand describeCommand = git.describe();
                    describeCommand.setTarget(resolveRefs(tagFullPath));
                    parentTag = describeCommand.call();
                } catch (RefNotFoundException e) {
                    log(Level.SEVERE, e.getMessage());
                } catch (IncorrectObjectTypeException e) {
                    parentTag = gitCli.describe(tagFullPath);
                } catch (Exception e) {
                    log(Level.SEVERE, "Exception while executing loadParentTag on  " + config.getRemoteName() + ". Exception: " + e.getMessage());
                }
                if (parentTag != null)
                    parentTagSet.add(new TagInfo(trim(parentTag), findTime(parentTag), config.getRemoteName()));
            }
        }
        tagParentInfo.put(tag, TagInfo.findLatestTag(tag, parentTagSet));
    }

    private Date findTime(String tagName) {
        try {
            ObjectId objectId = repository.resolve("refs/tags/" + tagName);
            RevWalk revWalk = new RevWalk(repository);
            RevObject revObject = revWalk.parseAny(objectId);

            if (revObject instanceof RevCommit) {
                return ((RevCommit) revObject).getCommitterIdent().getWhen();
            } else if (revObject instanceof RevTag) {
                return ((RevTag) revObject).getTaggerIdent().getWhen();
            }
        } catch (Exception e) {
            log(Level.SEVERE, "Unable to collect time of " + tagName + " due to " + e.getMessage());
        }
        return null;
    }

    private void log(Level severe, String msg) {
        PREPARE_LOG.log(severe, msg);
    }

    private String trim(String parentTag) {
        return parentTag.substring(parentTag.lastIndexOf("/") + 1, parentTag.length());
    }

    private ObjectId resolveRefs(String tagName) throws IOException {
        return repository.resolve(tagName);
    }
}