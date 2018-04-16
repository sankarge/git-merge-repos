package org.nibor.git_merge_repos.merger;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.nibor.git_merge_repos.util.FileUtil;
import org.nibor.git_merge_repos.vo.MergedRef;
import org.nibor.git_merge_repos.vo.SubtreeConfig;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Constants.R_TAGS;
import static org.nibor.git_merge_repos.log.LoggerUtil.MERGE_LOG;

/**
 * Fetches original repos, merges original branches/tags of different repos and
 * creates branches/tags that point to new merge commits.
 */
public class RepoMerger {

    public static final String HEADS = R_HEADS + "original/";

    public static final String TAGS = R_TAGS + "original/";

    protected final Git git;

    protected final Repository repository;

    protected final List<SubtreeConfig> subtreeConfigs;

    private Map tagParentInfo;

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

    public void loadParentTagInfo() {
        tagParentInfo = FileUtil.loadMap();
    }

    public void run() throws IOException, GitAPIException {

        fetch();

        loadParentTagInfo();

        Collection<String> branches = getRefSet(HEADS);

        Collection<String> tags = getRefSet(TAGS);

        Map<String, TreeSet<String>> tagsOfBranch = groupTagsUnderBranch(branches, tags);

        mergeOlder(tags, tagsOfBranch);

        mergeNewer(tagsOfBranch);

        deleteOriginalRefs();

        resetToBranch();
    }

    private void fetch() throws GitAPIException {
        for (SubtreeConfig config : subtreeConfigs) {
            RefSpec branchesSpec = new RefSpec("refs/heads/*:refs/heads/original/" + config.getRemoteName() + "/*");
            RefSpec tagsSpec = new RefSpec("refs/tags/*:refs/tags/original/" + config.getRemoteName() + "/*");
            git.fetch().setRemote(config.getFetchUri().toPrivateString()).setRefSpecs(branchesSpec, tagsSpec).call();
        }
    }

    private void mergeOlder(Collection<String> tags, Map<String, TreeSet<String>> tagsOfBranch) {
        tags.removeAll(tagsOfBranch.values().stream().flatMap(Collection::stream).collect(Collectors.toList()));
        logSevere("Following are the list of tags that incrementing pattern [tag number + 1] cannot be applied.");
        logSevere(tags.toString());

        TagMerger tagMerger = new TagMerger(subtreeConfigs, repository, Collections.emptyMap());
        tags.forEach(s -> {
            try {
                tagMerger.mergeTag(s);
            } catch (IOException e) {
                logSevere("Problem in merging tag " + s + " due to " + e.getMessage());
            }
        });
    }

    private void mergeNewer(Map<String, TreeSet<String>> tagsOfBranch) {
        TagMerger tagMerger = new TagMerger(subtreeConfigs, repository, tagParentInfo);
        BranchMerger branchMerger = new BranchMerger(subtreeConfigs, repository);

        for (Map.Entry<String, TreeSet<String>> entry : tagsOfBranch.entrySet()) {
            String branch = entry.getKey();
            TreeSet<String> tagsSorted = entry.getValue();
            try {
                String latestTag = tagMerger.mergeTags(tagsSorted);
                branchMerger.mergeBranch(branch, latestTag);
            } catch (Exception e) {
                logSevere("Problem in merging tags & branch of " + branch + " due to " + e.getMessage());
            }
        }
    }

    protected Map<String, TreeSet<String>> groupTagsUnderBranch(Collection<String> branches, Collection<String> tags) throws IOException {
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

    protected Collection<String> getRefSet(String prefix) throws IOException {
        Map<String, Ref> refs = repository.getRefDatabase().getRefs(prefix);
        TreeSet<String> result = new TreeSet<>();
        for (String refName : refs.keySet()) {
            String branch = refName.split("/", 2)[1];
            result.add(branch);
        }
        return result;
    }

    public List<MergedRef> getMergedRefs() {
        List<MergedRef> mergedRefs = new ArrayList<>();
        mergedRefs.addAll(TagMerger.getMergedRefs());
        mergedRefs.addAll(BranchMerger.getMergedRefs());
        return mergedRefs;
    }

    private void logSevere(String msg) {
        MERGE_LOG.log(Level.SEVERE, msg);
    }
}