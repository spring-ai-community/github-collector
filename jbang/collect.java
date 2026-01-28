///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//REPOS central=https://repo1.maven.org/maven2/
//REPOS central-snapshots=https://central.sonatype.com/repository/maven-snapshots/
//DEPS org.springaicommunity:github-collector-cli:1.0.0-SNAPSHOT

import org.springaicommunity.github.collector.cli.GitHubCollectorCli;

public class collect {
    public static void main(String[] args) throws Exception {
        GitHubCollectorCli.main(args);
    }
}
