package org.nibor.git_merge_repos.merger;

import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.nibor.git_merge_repos.vo.MergedRef;
import org.nibor.git_merge_repos.vo.SubtreeConfig;

import java.io.IOException;
import java.util.*;

/**
 * Created by sankarge on 12/21/17.
 */
public class TagMerger extends AbstractMerger {

    private final Map<String, String> tagParentInfo;

    private String previousTag = null;

    public TagMerger(List<SubtreeConfig> subtreeConfigs, Repository repository, Map<String, String> tagParentInfo) {
        super(subtreeConfigs, repository);
        this.tagParentInfo = tagParentInfo;
    }

    public List<MergedRef> mergeTags(Collection<String> tags) throws IOException {
        List<MergedRef> mergedRefs = new ArrayList<>();
        for (String tag : tags) {
            MergedRef mergedTag = mergeTag(tag);
            mergedRefs.add(mergedTag);
        }
        return mergedRefs;
    }

    public MergedRef mergeTag(String tagName) throws IOException {
        Map<SubtreeConfig, ObjectId> resolvedRefs = resolveRefs(
                "refs/tags/original/", tagName);

        // Annotated tag that should be used for creating the merged tag, null
        // if only lightweight tags exist
        RevTag referenceTag = null;
        Map<SubtreeConfig, RevCommit> parentCommits = new LinkedHashMap<>();

        try (RevWalk revWalk = new RevWalk(repository)) {
            for (Map.Entry<SubtreeConfig, ObjectId> entry : resolvedRefs
                    .entrySet()) {
                SubtreeConfig config = entry.getKey();
                ObjectId objectId = entry.getValue();
                RevCommit commit;
                RevObject revObject = revWalk.parseAny(objectId);
                if (revObject instanceof RevCommit) {
                    // Lightweight tag (ref points directly to commit)
                    commit = (RevCommit) revObject;
                } else if (revObject instanceof RevTag) {
                    // Annotated tag (ref points to tag object with message,
                    // which in turn points to commit)
                    RevTag tag = (RevTag) revObject;
                    RevObject peeled = revWalk.peel(tag);
                    if (peeled instanceof RevCommit) {
                        commit = (RevCommit) peeled;

                        if (referenceTag == null) {
                            referenceTag = tag;
                        } else {
                            // We already have one, but use the last (latest)
                            // tag as reference
                            PersonIdent referenceTagger = referenceTag.getTaggerIdent();
                            PersonIdent thisTagger = tag.getTaggerIdent();
                            if (thisTagger != null && referenceTagger != null
                                    && thisTagger.getWhen().after(referenceTagger.getWhen())) {
                                referenceTag = tag;
                            }
                        }
                    } else {
                        String msg = "Peeled tag " + tag.getTagName()
                                + " does not point to a commit, but to the following object: "
                                + peeled;
                        throw new IllegalStateException(msg);
                    }
                } else {
                    throw new IllegalArgumentException("Object with ID "
                            + objectId + " has invalid type for a tag: "
                            + revObject);
                }
                parentCommits.put(config, commit);
            }
        }

        if (previousTag == null) {
            previousTag = tagParentInfo.get(tagName);
        }

        MergedRef mergedRef = getMergedRef("tag", tagName, parentCommits.keySet());
        ObjectId mergeCommit;

        if (previousTag == null) {
            mergeCommit = new SubtreeMerger(repository).createMergeCommit(parentCommits,
                    mergedRef.getMessage());
        } else {
            RevCommit prevTagCommit = getCommitOfTag(previousTag);
            mergeCommit = new SubtreeMerger(repository).createMergeCommit(parentCommits, prevTagCommit,
                    mergedRef.getMessage());
        }

        ObjectId objectToReference;
        if (referenceTag != null) {
            TagBuilder tagBuilder = new TagBuilder();
            tagBuilder.setTag(tagName);
            tagBuilder.setMessage(referenceTag.getFullMessage());
            tagBuilder.setTagger(referenceTag.getTaggerIdent());
            tagBuilder.setObjectId(mergeCommit, Constants.OBJ_COMMIT);
            try (ObjectInserter inserter = repository.newObjectInserter()) {
                objectToReference = inserter.insert(tagBuilder);
                inserter.flush();
            }
        } else {
            objectToReference = mergeCommit;
        }

        String ref = Constants.R_TAGS + tagName;
        RefUpdate refUpdate = repository.updateRef(ref);
        refUpdate.setExpectedOldObjectId(ObjectId.zeroId());
        refUpdate.setNewObjectId(objectToReference);
        RefUpdate.Result result = refUpdate.update();
        if (result != RefUpdate.Result.NEW) {
            throw new IllegalStateException("Creating tag ref " + ref + " for "
                    + objectToReference + " failed with result " + result);
        }

        previousTag = tagName;
        return mergedRef;
    }

    public String getPreviousTag() {
        return previousTag;
    }
}
