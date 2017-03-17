package org.embulk.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPOutputStream;

import org.apache.maven.artifact.versioning.ComparableVersion;

public class EmbulkMigrate
{
    public void migratePlugin(String pathInString)
            throws IOException
    {
        migratePlugin(Paths.get(pathInString));
    }

    public void migratePlugin(Path path)
            throws IOException
    {
        Migrator migrator = new Migrator(path);

        final String lang;
        final ComparableVersion fromVersion;

        //if ms = migrator.match("**/build.gradle", /org\.embulk:embulk-core:([\d\.\+]+)?/)
        //  lang = :java
        //  from_ver = version(ms[0][1].gsub(/\++/, '0'))   # replaces "0.8.+" to "0.8.0"
        //  puts "Detected Java plugin for Embulk #{from_ver}..."

        //elsif ms = migrator.match("**/*.gemspec", /add_(?:development_)?dependency\s+\W+embulk\W+\s+([\d\.]+)\W+/)
        //  lang = :ruby
        //  from_ver = version(ms[0][1])
        //  puts "Detected Ruby plugin for Embulk #{from_ver}..."

        //elsif ms = migrator.match("**/*.gemspec", /embulk-/)
        //  lang = :ruby
        //  from_ver = version("0.1.0")
        //  puts "Detected Ruby plugin for unknown Embulk version..."

        //else
        //  raise "Failed to detect plugin language and dependency version"

        switch (lang) {
        case "java":
            migrateJavaPlugin(migrator, fromVersion);
            break;
        case "ruby":
            migrateRubyPlugin(migrator, fromVersion);
            break;
        }

        if (migrator.getModifiedFiles().isEmpty()) {
            System.out.println("Done. No files are modified.");
        }
        else {
            System.out.println("Done. Please check modifieid files.")
        }
    }

    private void migrateJavaPlugin(Migrator migrator, ComparableVersion fromVersion)
    {
        if (fromVersion.compareTo(new ComparableVersion("0.7.0")) < 0) {
            // rename CommitReport to TaskReport
            migrator.replace("**/*.java", /(CommitReport)/, "TaskReport");
            migrator.replace("**/*.java", /(commitReport)/, "taskReport");
        }

        // upgrade gradle version
        if (migrator.match("gradle/wrapper/gradle-wrapper.properties", /gradle-[23]\.\d+(\.\d+)?-/)) {
            // gradle < 3.2.1
            /*
      require 'embulk/data/package_data'
      data = PackageData.new("new", migrator.path)
      migrator.write "gradle/wrapper/gradle-wrapper.properties", data.content("java/gradle/wrapper/gradle-wrapper.properties")
      migrator.write "gradle/wrapper/gradle-wrapper.jar", data.bincontent("java/gradle/wrapper/gradle-wrapper.jar")
            */
        }

        // add jsonColumn method
        if (!migrator.match("**/*.java", /void\s+jsonColumn/) && ms = migrator.match("**/*.java", /^(\W+).*?void\s+timestampColumn/)) {
            /*
      indent = ms.first[1]
      replace =  <<EOF

#{indent}public void jsonColumn(Column column) {
#{indent}    throw new UnsupportedOperationException("This plugin doesn't support json type. Please try to upgrade version of the plugin using 'embulk gem update' command. If the latest version still doesn't support json type, please contact plugin developers, or change configuration of input plugin not to use json type.");
#{indent}}

#{indent}@Override
EOF
*/
            //migrator.replace("**/*.java", /(\r?\n)(\W+).*?void\s+timestampColumn/, replace)
        }

        // add sourceCompatibility and targetCompatibility
        /*
    unless migrator.match("build.gradle", /targetCompatibility/)
      migrator.insert_line("build.gradle", /^([ \t]*)dependencies\s*{/) {|m|
        "#{m[1]}targetCompatibility = 1.7\n"
      }
    end
    unless migrator.match("build.gradle", /sourceCompatibility/)
      migrator.insert_line("build.gradle", /^([ \t]*)targetCompatibility/) {|m|
        "#{m[1]}sourceCompatibility = 1.7"
      }
    end
        */

    # add checkstyle
    unless migrator.match("build.gradle", /id\s+(?<quote>["'])checkstyle\k<quote>/)
      migrator.insert_line("build.gradle", /^([ \t]*)id( +)(["'])java["']/) {|m|
        "#{m[1]}id#{m[2]}#{m[3]}checkstyle#{m[3]}"
      }
      migrator.write "config/checkstyle/checkstyle.xml", migrator.new_data.content("java/config/checkstyle/checkstyle.xml")
    end

    unless migrator.match("build.gradle", /checkstyle\s+{/)
      migrator.write "config/checkstyle/default.xml", migrator.new_data.content("java/config/checkstyle/default.xml")
      migrator.insert_line("build.gradle", /^([ \t]*)task\s+gem\W.*{/) {|m|
        <<-EOF
#{m[1]}checkstyle {
#{m[1]}    configFile = file("${project.rootDir}/config/checkstyle/checkstyle.xml")
#{m[1]}    toolVersion = '6.14.1'
#{m[1]}}
#{m[1]}checkstyleMain {
#{m[1]}    configFile = file("${project.rootDir}/config/checkstyle/default.xml")
#{m[1]}    ignoreFailures = true
#{m[1]}}
#{m[1]}checkstyleTest {
#{m[1]}    configFile = file("${project.rootDir}/config/checkstyle/default.xml")
#{m[1]}    ignoreFailures = true
#{m[1]}}
#{m[1]}task checkstyle(type: Checkstyle) {
#{m[1]}    classpath = sourceSets.main.output + sourceSets.test.output
#{m[1]}    source = sourceSets.main.allJava + sourceSets.test.allJava
#{m[1]}}
EOF
      }
    end

        // add rules...

        // update version at the end
        migrator.replace("**/build.gradle", /org\.embulk:embulk-(?:core|standards):([\d\.\+]+)?/, Embulk::VERSION)
    }

    private void migrateRubyPlugin(Migrator migrator, ComparableVersion fromVersion)
    {
        // add rules...
        migrator.write(".ruby-version", "jruby-9.1.5.0");

        // update version at the end
        if (fromVersion.compareTo(ComparableVersion("0.1.0")) <= 0) {
            // add add_development_dependency
            migrator.insertLine("**/*.gemspec", /([ \t]*\w+)\.add_development_dependency/) { |m|
                    "#{m[1]}.add_development_dependency 'embulk', ['>= #{Embulk::VERSION}']"
            }
        }
        else {
            //unless migrator.replace("**/*.gemspec", /add_(?:development_)?dependency\s+\W+embulk\W+\s*(\~\>\s*[\d\.]+)\W+/, ">= #{Embulk::VERSION}")
            //migrator.replace("**/*.gemspec", /add_(?:development_)?dependency\s+\W+embulk\W+\s*([\d\.]+)\W+/, Embulk::VERSION)
            //end
        }
    }

    private class Migrator {
        private Migrator(Path basePath) {
            this.basePath = basePath;
            this.modifiedFiles = new HashSet<Path>();
            // @new_data = PackageData.new("new", path)
        }

        public Path getBasePath()
        {
            return this.basePath;
        }

        /*
        public ... getNewData
        */

        public Set<Path> getModifiedFiles();
        {
            return this.modifiedFiles;
        }

        public List<Matcher> match(String glob, Pattern pattern)
        {
            ImmutableList.Builder<Matcher> matchers = ImmutableList.<Matcher>builder();
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(this.basePath, glob)) {
                for (Path filePath : directoryStream) {
                    final Matcher matcher = pattern.matcher(read(filePath));
                    matcher.matches();
                    matchers.add(matcher);
                }
            }
            return matchers.build();
        }

        // def replace(glob, pattern, text=nil)
        public List<Matcher> replace(String glob, Pattern pattern/* text=nil*/)
        {
            ImmutableList.Builder<Matcher> matchers = ImmutableList.<Matcher>builder();
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(this.basePath, glob)) {
                for (Path filePath : directoryStream) {
                    String data = read(filePath);
                    // first = nil
                    int pos = 0;

        while pos < data.length
          m = data.match(pattern, pos)
          break unless m
          first ||= m
          replace = text || yield(m)
          data = m.pre_match + data[m.begin(0)..(m.begin(1)-1)] + replace + data[m.end(1)..(m.end(0)-1)] + m.post_match
          pos = m.begin(1) + replace.length + (m.end(0) - m.end(1))
        end
        if first
          modify(file, data)
        end
        first
                }
            }
            return matchers.build();
        }

        // def insert_line(glob, pattern, text: nil)
        public ... insertLine(String glob, Pattern pattern/*, text */)
        {
            ImmutableList.Builder<Matcher> matchers = ImmutableList.<Matcher>builder();
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(this.basePath, glob)) {
                for (Path filePath : directoryStream) {
                    String data = read(filePath);
                    final Matcher matcher = pattern.matcher(data);
                    if (matcher.matches()) {
          ln = m.pre_match.split("\n").count
          replace = text || yield(m)
          lines = data.split("\n", -1)  # preserve the last empty line
          lines.insert(ln + 1, replace)
          data = lines.join("\n")
          modify(file, data)
          m
                    }
                }
            }
            return matchers.build();
        }

        // def write(file, data)
        public ... write(Path filePath, String writtenData)
        {
            modify(filePath, writtenData);
      dst = File.join(@path, file)
      FileUtils.mkdir_p File.dirname(dst)
      modify(dst, data)
        }

        private void modify(Path filePath, List<String> modifiedData)
        {
            List<String> originalData = read(filePath);  // nil if fail
            if (!originalData.equals(modifiedData)) {
                Files.write(filePath, modifiedData.getBytes(StandardCharsets.UTF_8));
                if (not this.modifiedFiles.contains(path)) {
                    if (originalData.empty()) {
                        System.out.println("  Created #{path.sub(/^#{Regexp.escape(@path)}/, '')}");
                    }
                    else {
                        System.out.println("  Modified #{path.sub(/^#{Regexp.escape(@path)}/, '')}");
                    }
                }
                this.modifiedFiles.add(filePath);
            }
        }

        private List<String> read(Path filePath)
                throws IOException
        {
            // assumes source code is written in UTF-8.
            return Files.readAllLines(filePath, StandardCharsets.UTF_8);
        }

        private final Path basePath;
        private final Set<Path> modifiedFiles;
    }

    private static final Pattern patternBuildGradle = Pattern.compile("org\\.embulk:embulk-core:([\\d\\.\\+]+)?");
    private static final Pattern patternGemspec1 = Pattern.compile(/add_(?:development_)?dependency\\s+\\W+embulk\\W+\\s+([\\d\\.]+)\\W+/);
    private static final Pattern patternGemspec2 = Pattern.compile("embulk-");

}
