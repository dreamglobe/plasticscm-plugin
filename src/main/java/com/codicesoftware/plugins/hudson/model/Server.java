package com.codicesoftware.plugins.hudson.model;

import com.codicesoftware.plugins.hudson.PlasticTool;
import com.codicesoftware.plugins.hudson.commands.*;
import com.codicesoftware.plugins.hudson.util.MaskedArgumentListBuilder;
import java.io.IOException;
import java.io.Reader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import org.apache.commons.io.IOUtils;

public class Server implements ServerConfigurationProvider {
    private Workspaces workspaces;
    private final PlasticTool tool;

    public Server(PlasticTool tool) {
        this.tool = tool;
    }

    public Workspaces getWorkspaces() {
        if (workspaces == null) {
            workspaces = new Workspaces(this);
        }

        return workspaces;
    }

    public Reader execute(MaskedArgumentListBuilder arguments) throws IOException, InterruptedException {
        return tool.execute(arguments.toCommandArray(), arguments.toMaskArray());
    }

    private List<ChangeSet> getChangesets(Calendar fromTimestamp, Calendar toTimestamp)
            throws IOException, InterruptedException, ParseException {
        List<ChangeSet> list = new ArrayList<ChangeSet>();
        Reader reader = null;

        WorkspaceInfo wi;
        GetWorkspaceInfoCommand wiCommand = new GetWorkspaceInfoCommand(this);
        try {
            reader = execute(wiCommand.getArguments());
            wi = wiCommand.parse(reader);
        } finally {
            IOUtils.closeQuietly(reader);
        }

        String branch;

        if (wi.getBranch().equals("Multiple")) {
            List<ChangesetID> cslist;
            GetWorkspaceStatusCommand statusCommand = new GetWorkspaceStatusCommand(this);
            try {
                reader = execute(statusCommand.getArguments());
                cslist = statusCommand.parse(reader);
            } finally {
                IOUtils.closeQuietly(reader);
            }

            for (ChangesetID cs : cslist) {
                branch = GetBranchFromChangeset(cs.getId(), cs.getRepoName());

                DetailedHistoryCommand histCommand = new DetailedHistoryCommand(this, fromTimestamp, toTimestamp, branch, cs.getRepository());
                try {
                    reader = execute(histCommand.getArguments());
                    list.addAll(histCommand.parse(reader));
                } finally {
                    IOUtils.closeQuietly(reader);
                }
            }
        } else {
            branch = GetBranchFromWorkspaceInfo(wi);
            DetailedHistoryCommand histCommand = new DetailedHistoryCommand(this,
                    fromTimestamp, toTimestamp, branch, wi.getRepoName());
            try {
                reader = execute(histCommand.getArguments());
                list = histCommand.parse(reader);
            } finally {
                IOUtils.closeQuietly(reader);
            }
        }

        return list;
    }

    public List<ChangeSet> getDetailedHistory(Calendar fromTimestamp, Calendar toTimestamp)
            throws IOException, InterruptedException, ParseException {
        List<ChangeSet> list = getChangesets(fromTimestamp, toTimestamp);
        String workspaceDir;

        Reader reader = null;
        GetWorkspaceFromPathCommand gwpCommand = new GetWorkspaceFromPathCommand(this);
        try {
            reader = execute(gwpCommand.getArguments());
            workspaceDir = gwpCommand.parse(reader);
        } finally {
            IOUtils.closeQuietly(reader);
        }

        for(ChangeSet cs : list) {
            cs.setWorkspaceDir(workspaceDir);

            GetChangesetRevisionsCommand revs = new GetChangesetRevisionsCommand(this,
                    cs.getVersion(), cs.getRepository());

            try {
                reader = execute(revs.getArguments());
                revs.parse(reader, cs);
            } finally {
                IOUtils.closeQuietly(reader);
            }
        }

        return list;
    }

    /*
     * Same as getDetailedHistory, but doesn't fill in the list of revisions in each changeset
     */
    public List<ChangeSet> getBriefHistory(Calendar fromTimestamp, Calendar toTimestamp)
            throws IOException, InterruptedException, ParseException {
        List<ChangeSet> list = getChangesets(fromTimestamp, toTimestamp);

        return list;
    }

    public void getFiles(String localPath) throws IOException, InterruptedException {
        GetFilesToWorkFolderCommand command = new GetFilesToWorkFolderCommand(this, localPath);
        execute(command.getArguments()).close();
    }

    private String GetBranchFromWorkspaceInfo(WorkspaceInfo wi) throws InterruptedException, ParseException, IOException {
        String branch = wi.getBranch();
        if (branch != null && !branch.isEmpty())
            return branch;

        String label = wi.getLabel();
        if (label != null && !label.isEmpty())
            return GetBranchFromLabel(label, wi.getRepoName());

        String changeset = wi.getChangeset();
        if (changeset != null && !changeset.isEmpty())
            return GetBranchFromChangeset(changeset, wi.getRepoName());

        return "";
    }

    private String GetBranchFromLabel(String label, String repositoryName) throws InterruptedException, ParseException, IOException {
        GetBranchForLabelCommand brCommand = new GetBranchForLabelCommand(this,
                label, repositoryName);
        Reader reader = null;
        try {
            reader = execute(brCommand.getArguments());
            return brCommand.parse(reader);
        } finally {
            IOUtils.closeQuietly(reader);
        }
    }

    private String GetBranchFromChangeset(String id, String repositoryName) throws InterruptedException, ParseException, IOException {
        GetBranchForChangesetCommand brCommand = new GetBranchForChangesetCommand(this,
                id, repositoryName);
        Reader reader = null;
        try {
            reader = execute(brCommand.getArguments());
            return brCommand.parse(reader);
        } finally {
            IOUtils.closeQuietly(reader);
        }
    }
}