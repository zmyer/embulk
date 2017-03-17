package org.embulk.cli;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Scanner;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.artifact.versioning.ComparableVersion;

import org.embulk.EmbulkVersion;

// It uses |java.net.HttpURLConnection| so that embulk-cli does not need additional dependedcies.
// TODO(dmikurube): Support HTTP proxy. The original Ruby version did not support as well, though.
public class EmbulkSelfUpdate
{
    // TODO(dmikurube): Remove this Exception catch when embulk_run.rb is replaced to Java.
    public void updateSelf(String specifiedVersion, boolean isForced, String rubyFile)
            throws IOException, URISyntaxException
    {
        try {
            updateSelfWithExceptions(specifiedVersion, isForced, rubyFile);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        }
    }

    private void updateSelfWithExceptions(String specifiedVersion, boolean isForced, String rubyFile)
            throws IOException, URISyntaxException
    {
        final Path jarPathJava = Paths.get(
            EmbulkSelfUpdate.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());

        if ((!Files.exists(jarPathJava)) || (!Files.isRegularFile(jarPathJava))) {
            throw exceptionNoSingleJar();
        }

        if (rubyFile != null) {
            final String[] splitRubyFile = rubyFile.split("!", 2);
            if (splitRubyFile.length < 2) {
                throw exceptionNoSingleJar();
            }
            final Path jarPathRuby = Paths.get(splitRubyFile[0]);
            if (!jarPathJava.equals(jarPathRuby)) {
                throw exceptionNoSingleJar();
            }
        }

        final String targetVersionString;
        if (specifiedVersion != null) {
            System.out.printf("Checking version %s...\n", specifiedVersion);
            targetVersionString = checkTargetVersion(specifiedVersion);
            if (targetVersionString == null) {
                throw new RuntimeException(String.format("Specified version does not exist: %s", specifiedVersion));
            }
            System.out.printf("Found version %s.\n", specifiedVersion);
        }
        else {
            System.out.println("Checking the latest version...");
            final ComparableVersion currentVersion = new ComparableVersion(EmbulkVersion.VERSION);
            targetVersionString = checkLatestVersion();
            final ComparableVersion targetVersion = new ComparableVersion(targetVersionString);
            if (targetVersion.compareTo(currentVersion) <= 0) {
                System.out.printf("Already up-to-date. %s is the latest version.\n", currentVersion);
                return;
            }
            System.out.printf("Found a newer version %s.\n", targetVersion);
        }

        if (!Files.isWritable(jarPathJava)) {
            throw new RuntimeException(String.format("The existing %s is not writable. May need to sudo?",
                                                     jarPathJava.toString()));
        }

        final URL downloadUrl = new URL(String.format("https://dl.bintray.com/embulk/maven/embulk-%s.jar",
                                                      targetVersionString));
        System.out.printf("Downloading %s ...\n", downloadUrl.toString());

        Path jarPathTemp = Files.createTempFile("embulk-selfupdate", ".jar");
        try {
            final HttpURLConnection connection = (HttpURLConnection) downloadUrl.openConnection();
            try {
                // Follow the redicrect from the Bintray URL.
                connection.setInstanceFollowRedirects(true);
                connection.setRequestMethod("GET");
                connection.connect();
                final int statusCode = connection.getResponseCode();
                if (HttpURLConnection.HTTP_OK != statusCode) {
                    throw new FileNotFoundException(
                        String.format("Unexpected HTTP status code: %d", statusCode));
                }
                InputStream input = connection.getInputStream();
                // TODO(dmikurube): Confirm if it is okay to replace a temp file created by Files.createTempFile.
                Files.copy(input, jarPathTemp, StandardCopyOption.REPLACE_EXISTING);
                Files.setPosixFilePermissions(jarPathTemp, Files.getPosixFilePermissions(jarPathJava));
            }
            finally {
                connection.disconnect();
            }

            if (!isForced) {  // Check corruption
                final String versionJarTemp;
                try {
                    versionJarTemp = getJarVersion(jarPathTemp);
                }
                catch (FileNotFoundException ex) {
                    throw new RuntimeException("Failed to check corruption. Downloaded version may include incompatible changes. Try the '-f' option to force updating without checking.", ex);
                }
                if (!versionJarTemp.equals(targetVersionString)) {
                    throw new RuntimeException(
                        String.format("Downloaded version does not match: %s (downloaded) / %s (target)",
                                      versionJarTemp,
                                      targetVersionString));
                }
            }
            Files.move(jarPathTemp, jarPathJava, StandardCopyOption.REPLACE_EXISTING);
        }
        finally {
            Files.deleteIfExists(jarPathTemp);
        }
        System.out.println(String.format("Updated to %s.", targetVersionString));
    }

    private RuntimeException exceptionNoSingleJar()
    {
        return new RuntimeException("Embulk is not installed as a single jar. \"selfupdate\" does not work. If you installed Embulk through gem, run \"gem install embulk\" instead.");
    }

    /**
     * Checks the latest version from bintray.com.
     *
     * It passes all {@code IOException} and {@code RuntimeException} through out.
     */
    private String checkLatestVersion()
            throws IOException
    {
        final URL bintrayUrl = new URL("https://bintray.com/embulk/maven/embulk/_latestVersion");
        final HttpURLConnection connection = (HttpURLConnection) bintrayUrl.openConnection();
        try {
            // Stop HttpURLConnection from following redirects when the status code is 301 or 302.
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.connect();
            final int statusCode = connection.getResponseCode();
            if (HttpURLConnection.HTTP_MOVED_TEMP != statusCode) {
                throw new FileNotFoundException(
                    String.format("Unexpected HTTP status code: %d", statusCode));
            }
            final String location = connection.getHeaderField("Location");
            final Matcher versionMatcher = VERSION_URL_PATTERN.matcher(location);
            if (!versionMatcher.matches()) {
                throw new FileNotFoundException(
                    String.format("Invalid version number in \"Location\" header: %s", location));
            }
            return versionMatcher.group(1);
        }
        finally {
            connection.disconnect();
        }
    }

    /**
     * Checks the target version from bintray.com.
     *
     * It passes all {@code IOException} and {@code RuntimeException} through out.
     */
    private String checkTargetVersion(String version)
            throws IOException
    {
        final URL bintrayUrl = new URL(String.format("https://bintray.com/embulk/maven/embulk/%s", version));
        final HttpURLConnection connection = (HttpURLConnection) bintrayUrl.openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.connect();
            final int statusCode = connection.getResponseCode();
            if (HttpURLConnection.HTTP_NOT_FOUND == statusCode) {
                return null;
            }
            else if (HttpURLConnection.HTTP_OK != statusCode) {
                throw new FileNotFoundException(
                    String.format("Unexpected HTTP status code: %d", statusCode));
            }
            else {
                return version;
            }
        }
        finally {
            connection.disconnect();
        }
    }

    private String getJarVersion(Path jarPath)
            throws IOException
    {
        final URL urlVersionProperty = new URL("jar:" + jarPath.toUri().toURL().toString() + "!/version.properties");
        try {
            final JarURLConnection connectionVersionProperty = (JarURLConnection) urlVersionProperty.openConnection();
            try (final InputStream inputVersionProperty = connectionVersionProperty.getInputStream()) {
                final Properties properties = new Properties();
                properties.load(inputVersionProperty);
                final String version = properties.getProperty("version");
                if (version != null) {
                    return version;
                }
            }
            // TODO(v2)[#562]: Fail when version.properties is not loaded.
            catch (IOException ex) {
                System.out.println("Failed to load version.properties. Skipping.");
            }
        }
        // TODO(v2)[#562]: Fail when version.properties is not found.
        catch (IOException ex) {
            System.out.println("Failed to find version.properties. Skipping.");
        }

        // TODO(v2)[#562]: Remove checking embulk/version.rb.
        // The Ruby file embulk/version.rb has its immediate version number until v2 for forward compatibility.
        // It is replaced with a reference to |org.embulk.EmbulkVersion::VERSION|.
        // https://github.com/embulk/embulk/issues/562
        final URL urlVersionRuby = new URL("jar:" + jarPath.toUri().toURL().toString() + "!/embulk/version.rb");
        try {
            final JarURLConnection connectionVersionRuby = (JarURLConnection) urlVersionRuby.openConnection();
            try (final Scanner scannerVersionRuby = new Scanner(connectionVersionRuby.getInputStream())) {
                while (scannerVersionRuby.hasNext()) {
                    final String line = scannerVersionRuby.nextLine();
                    final Matcher matcherVersionRuby = VERSION_RUBY_PATTERN.matcher(line);
                    if (matcherVersionRuby.matches()) {
                        final String versionFound = matcherVersionRuby.group(1);
                        if (versionFound.startsWith("'") && versionFound.endsWith("'")) {
                            return versionFound.substring(1, versionFound.length() - 1);
                        }
                        return versionFound;
                    }
                }
            }
        }
        catch (IOException ex) {
            System.out.println("Failed to load embulk/version.rb.");
            throw ex;
        }

        throw new FileNotFoundException("Version not found in: " + jarPath);
    }

    private static final Pattern VERSION_URL_PATTERN = Pattern.compile("^https?://.*/embulk/(\\d+\\.\\d+[^\\/]+).*$");
    private static final Pattern VERSION_RUBY_PATTERN = Pattern.compile("^\\s*VERSION\\s*\\=\\s*(\\p{Graph}+)\\s*$");
}
