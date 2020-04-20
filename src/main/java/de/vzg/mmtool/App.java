package de.vzg.mmtool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import de.vzg.mmtool.config.ApplicationSpecificConfig;
import de.vzg.mmtool.config.Config;
import de.vzg.mmtool.config.HomeDir;

public class App {

    public static final List<String> STRIP_APP_FOLDERS = Stream.of("save/", "webpages/").collect(Collectors.toList());

    public static Scanner in;

    public static void main(String[] args) throws IOException {
        in = new Scanner(System.in);

        if (args.length == 0) {
            printUsage();
            return;
        }
        HomeDir.setUpHomeDir();

        final Config config = HomeDir.getConfig();

        final Map<String, Path> validProjectMap = config.projects.stream().map(project -> {
            final Path projectDir = HomeDir.getHomeDirPath().resolve(project);
            if (!Files.exists(projectDir)) {
                System.err.println("Project " + projectDir + " doesnt exist!");
                return null;
            }
            return projectDir;
        }).filter(Objects::nonNull)
            .filter(projectPath -> {
                try {
                    Git.open(projectPath.toFile());
                    return true;
                } catch (IOException e) {
                    System.out.println(
                        "Seems like the project has invalid git: " + projectPath.toAbsolutePath().toString());
                    e.printStackTrace();
                    return false;
                }
            }).collect(Collectors.toMap((p) -> p.getFileName().toString(), (p) -> p));

        Stream.of(args).map(Paths::get).filter(path -> {
            if (!Files.exists(path)) {
                System.err.println("File " + path.toAbsolutePath().toString() + " doesnt exist!");
                return false;
            }
            return true;
        }).filter(path -> {
            if (!Files.isRegularFile(path)) {
                System.err.println("File " + path.toAbsolutePath().toString() + " is not a regular file!");
                return false;
            }
            return true;
        }).forEach(fileToPatch -> {
            final Path absolute;

            absolute = fileToPatch.toAbsolutePath().normalize().toAbsolutePath();

            Optional<ApplicationSpecificConfig> matchingConfig = Optional.empty();
            matchingConfig = findMatchingApplication(config, absolute);
            if (!matchingConfig.isPresent()) {
                System.err.println("No application found for file: " + absolute.toString());
                return;
            }
            final ApplicationSpecificConfig applicationSpecificConfig = matchingConfig.get();
            final Optional<AbstractMap.SimpleEntry<String, Path>> firstMatchingFileO = validProjectMap.entrySet()
                .stream()
                .map((e) -> {
                    final Path foundFile = findMatchingFile(e.getValue(), absolute,
                        Paths.get(applicationSpecificConfig.projectHome).toAbsolutePath().normalize().toAbsolutePath());
                    if (foundFile != null) {
                        System.out.println(
                            "Found file in project " + e.getKey() + " -> " + e.getValue().relativize(foundFile));
                        return new AbstractMap.SimpleEntry<String, Path>(e.getKey(), foundFile);
                    }
                    return null;
                }).filter(Objects::nonNull)
                .findFirst();

            if (!firstMatchingFileO.isPresent()) {
                System.err.println("The file " + absolute.toString() + " was not found in any projects!");
                return;
            }

            final AbstractMap.SimpleEntry<String, Path> firstMatchingFile = firstMatchingFileO.get();
            final String foundProject = firstMatchingFile.getKey();
            final Path pathToProject = validProjectMap.get(foundProject);
            final Git gitOfProject;

            try {
                gitOfProject = Git.open(pathToProject.toFile());
            } catch (IOException e) {
                // this should never happen!
                e.printStackTrace();
                return;
            }
            final List<Ref> branches;
            try {
                branches = gitOfProject.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
            } catch (GitAPIException e) {
                System.err.println("Error while getting branch list of project: " + foundProject);
                e.printStackTrace();
                return;
            }

            final Optional<Ref> refOfBaseVersionO = branches.stream()
                .filter(
                    branch -> branch.getName().equals("refs/remotes/origin/" + applicationSpecificConfig.baseVersion))
                .findFirst();
            if (!refOfBaseVersionO.isPresent()) {
                System.err.println("Could not find branch " + applicationSpecificConfig.baseVersion);
                return;
            }
            final Ref refOfBaseVersion = refOfBaseVersionO.get();

            final Optional<Ref> refOfTargetVersionO = branches.stream()
                .filter(
                    branch -> branch.getName().equals("refs/remotes/origin/" + applicationSpecificConfig.targetVersion))
                .findFirst();
            if (!refOfTargetVersionO.isPresent()) {
                System.err.println("Could not find branch " + applicationSpecificConfig.targetVersion);
                return;
            }
            final Ref refOfTargetVersion = refOfTargetVersionO.get();

            final Iterable<RevCommit> revCommits;
            final String gitRelativeFile = pathToProject.relativize(firstMatchingFile.getValue()).toString();
            try {
                revCommits = gitOfProject.log()
                    .addPath(gitRelativeFile).
                        addRange(refOfBaseVersion.getObjectId(), refOfTargetVersion.getObjectId()).call();
            } catch (MissingObjectException | IncorrectObjectTypeException | GitAPIException e) {
                System.err.println("Error while getting log!");
                e.printStackTrace();
                return;
            }

            System.out.println("Listing changes since last version:");
            revCommits.forEach(commit -> {
                final PersonIdent authorIdent = commit.getAuthorIdent();
                System.out.println("-----------------------------------");
                System.out.println(
                    commit.getId().getName() + " - " + authorIdent.getName() + " - " + authorIdent.getEmailAddress());
                System.out.println(commit.getShortMessage());
                System.out.println(
                    "https://github.com/MyCoRe-Org/" + foundProject + "/commit/" + commit.getId().getName() + System
                        .lineSeparator());

                final ObjectId id = commit.getId();
                final RevCommit parent = commit.getParent(0);

                DiffFormatter diffFormatter = new DiffFormatter(System.out);
                diffFormatter.setPathFilter(PathFilter.create(gitRelativeFile));
                diffFormatter.setRepository(gitOfProject.getRepository());
                try {
                    for (DiffEntry entry : diffFormatter.scan(parent.getTree(), commit.getTree())) {
                        diffFormatter.format(diffFormatter.toFileHeader(entry));
                    }
                } catch (IOException e) {
                    System.err.println("Error while diffing commit " + id.toString());
                    e.printStackTrace();
                    return;
                }
            });
        });
    }

    private static Optional<ApplicationSpecificConfig> findMatchingApplication(Config config, Path absolute) {
        return config.applications.stream().filter(app -> {
            final Path appPath = Paths.get(app.projectHome);
            return absolute.startsWith(appPath);
        }).findFirst();
    }

    public static Path findMatchingFile(Path projectRoot, Path fileToFind, Path homePath) {
        try {
            return Files.walk(projectRoot).filter(path -> {
                String relative = homePath.relativize(fileToFind).toString();
                for (String stripAppFolder : STRIP_APP_FOLDERS) {
                    if (relative.startsWith(stripAppFolder)) {
                        relative = relative.substring(stripAppFolder.length());
                    }
                }
                return path.endsWith(relative);
            }).filter(path -> Files.exists(path) && Files.isRegularFile(path)).findFirst().orElse(null);
        } catch (IOException e) {
            System.err.println("Error while walking Path " + projectRoot);
            e.printStackTrace();
        }

        return null;
    }

    public static void printUsage() {
        System.out.println("mmtool <file1> <file2> ...");
    }
}
