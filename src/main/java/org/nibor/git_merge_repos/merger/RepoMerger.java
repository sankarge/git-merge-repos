package org.nibor.git_merge_repos.merger;

import org.eclipse.jgit.api.DescribeCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.nibor.git_merge_repos.log.LoggerUtil;
import org.nibor.git_merge_repos.vo.MergedRef;
import org.nibor.git_merge_repos.vo.SubtreeConfig;
import org.nibor.git_merge_repos.vo.TagInfo;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Constants.R_TAGS;

/**
 * Fetches original repos, merges original branches/tags of different repos and
 * creates branches/tags that point to new merge commits.
 */
public class RepoMerger {

    private static final String HEADS = R_HEADS + "original/";

    private static final String TAGS = R_TAGS + "original/";

    private final Git git;

    private final Repository repository;

    private final List<SubtreeConfig> subtreeConfigs;

    private Map<String, String> tagParentInfo = new HashMap<>();

    private final List<MergedRef> mergedRefs = new ArrayList<>();

    public RepoMerger(String outputRepositoryPath,
                      List<SubtreeConfig> subtreeConfigs) throws IOException {
        this.subtreeConfigs = subtreeConfigs;
        File file = new File(outputRepositoryPath);
        repository = new RepositoryBuilder().setWorkTree(file).build();
        if (!repository.getDirectory().exists()) {
            repository.create();
        }
        git = new Git(repository);
    }

    private void fetch() throws GitAPIException {
        for (SubtreeConfig config : subtreeConfigs) {
            RefSpec branchesSpec = new RefSpec(
                    "refs/heads/*:refs/heads/original/"
                            + config.getRemoteName() + "/*");
            RefSpec tagsSpec = new RefSpec("refs/tags/*:refs/tags/original/"
                    + config.getRemoteName() + "/*");
            git.fetch().setRemote(config.getFetchUri().toPrivateString())
                    .setRefSpecs(branchesSpec, tagsSpec).call();
        }
    }

    public void run() throws IOException, GitAPIException {

//        fetch();

        Collection<String> branches = getRefSet(HEADS);

        Collection<String> tags = getRefSet(TAGS);

        Map<String, TreeSet<String>> tagsOfBranch = groupTagsUnderBranch(branches, tags);

        loadParentTagForFirstTagOfEachBranch(tagsOfBranch);

        mergeOlder(tags, tagsOfBranch);

        mergeNewer(tagsOfBranch);

        deleteOriginalRefs();

        resetToBranch();
    }

    private void loadParentTagForFirstTagOfEachBranch(Map<String, TreeSet<String>> tagsOfBranch) {
        tagsOfBranch.values().forEach(s -> s.stream().findFirst().ifPresent(this::loadParentTag));
    }

    private void mergeOlder(Collection<String> tags, Map<String, TreeSet<String>> tagsOfBranch) {
        LoggerUtil.log.log(Level.SEVERE, "Following are the list of tags that doesn't start/match with branch prefix.");
        tags.removeAll(tagsOfBranch.values().stream().flatMap(Collection::stream).collect(Collectors.toList()));
        LoggerUtil.log.log(Level.SEVERE, tags.toString());

        TagMerger tagMerger = new TagMerger(subtreeConfigs, repository, Collections.emptyMap());
        tags.forEach(s -> {
            try {
                mergedRefs.add(tagMerger.mergeTag(s));
            } catch (IOException e) {
                LoggerUtil.log.log(Level.SEVERE, "Problem in merging tag " + s + " due to " + e.getMessage());
            }
        });
    }

    private void mergeNewer(Map<String, TreeSet<String>> tagsOfBranch) {
        BranchMerger branchMerger = new BranchMerger(subtreeConfigs, repository);
        TagMerger tagMerger = new TagMerger(subtreeConfigs, repository, tagParentInfo);

        for (Map.Entry<String, TreeSet<String>> entry : tagsOfBranch.entrySet()) {
            String branch = entry.getKey();
            TreeSet<String> tagsSorted = entry.getValue();
            try {
                List<MergedRef> mergedTags = tagMerger.mergeTags(tagsSorted);
                MergedRef mergedBranch = branchMerger.mergeBranch(branch, tagMerger.getPreviousTag());
                mergedRefs.add(mergedBranch);
                mergedRefs.addAll(mergedTags);
            } catch (Exception e) {
                LoggerUtil.log.log(Level.SEVERE, "Problem in merging tags & branch of " + branch + " due to " + e.getMessage());
            }
        }
    }

    private Map<String, TreeSet<String>> groupTagsUnderBranch(Collection<String> branches, Collection<String> tags) throws IOException {
        Map<String, TreeSet<String>> tagsOfBranch = new TreeMap<>();
        for (String branch : branches) {
            tagsOfBranch.put(branch, new TreeSet<>(tagSorter));
            String regEx = branch + "-[0-9]*$";
            for (String tag : tags) {
                if (tag.matches(regEx)) {
                    tagsOfBranch.get(branch).add(tag);
                } else if (tag.equals(branch)) {
                    tagsOfBranch.get(branch).add(tag);
                }
            }
        }
        return tagsOfBranch;
    }

    private void loadParentTag(String tag) {
        LoggerUtil.log.info("Finding parent tag of " + tag);
        List<TagInfo> parentTagSet = new ArrayList<>();

        for (SubtreeConfig config : subtreeConfigs) {
            String tagFullPath = TAGS + config.getRemoteName() + "/" + tag;
            try {
                DescribeCommand describeCommand = git.describe();
                describeCommand.setTarget(tagFullPath);
                String parentTag = describeCommand.call();
                parentTagSet.add(new TagInfo(trim(parentTag), findTag(parentTag).getTaggerIdent().getWhen(), config.getRemoteName()));
            } catch (RefNotFoundException e) {
                LoggerUtil.log.log(Level.SEVERE, e.getMessage());
            } catch (Exception e) {
                LoggerUtil.log.log(Level.SEVERE, "Exception while executing DescribeCommand. " + e.getMessage());
            }
        }
        tagParentInfo.put(tag, TagInfo.findLatestTag(tag, parentTagSet));
    }

    private String trim(String parentTag) {
        return parentTag.substring(parentTag.lastIndexOf("/") + 1, parentTag.length());
    }

    private RevTag findTag(String tagName) throws IOException {
        ObjectId objectId = repository.resolve(tagName);
        RevWalk revWalk = new RevWalk(repository);
        return revWalk.parseTag(objectId);
    }

    private final Comparator<String> tagSorter = (tag1, tag2) -> {
        int tag1Nr = Integer.parseInt(tag1.substring(tag1.lastIndexOf("-") + 1));
        int tag2Nr = Integer.parseInt(tag2.substring(tag2.lastIndexOf("-") + 1));
        return Integer.compare(tag1Nr, tag2Nr);
    };

    private void deleteOriginalRefs() throws IOException {
        try (RevWalk revWalk = new RevWalk(repository)) {
            Collection<Ref> refs = new ArrayList<>();
            RefDatabase refDatabase = repository.getRefDatabase();
            Map<String, Ref> originalBranches = refDatabase.getRefs(HEADS);
            Map<String, Ref> originalTags = refDatabase.getRefs(TAGS);
            refs.addAll(originalBranches.values());
            refs.addAll(originalTags.values());
            for (Ref originalRef : refs) {
                RefUpdate refUpdate = repository.updateRef(originalRef.getName());
                refUpdate.setForceUpdate(true);
                refUpdate.delete(revWalk);
            }
        }
    }

    private void resetToBranch() throws IOException, GitAPIException {
        Ref master = repository.getRef(Constants.R_HEADS + "master");
        if (master != null) {
            Git git = new Git(repository);
            git.reset().setMode(ResetType.HARD).setRef(master.getName()).call();
        }
    }

    private Collection<String> getRefSet(String prefix) throws IOException {
        Map<String, Ref> refs = repository.getRefDatabase().getRefs(prefix);
        TreeSet<String> result = new TreeSet<>();
        for (String refName : refs.keySet()) {
            String branch = refName.split("/", 2)[1];
            result.add(branch);
        }
        return result;
    }

    public List<MergedRef> getMergedRefs() {
        return mergedRefs;
    }
}