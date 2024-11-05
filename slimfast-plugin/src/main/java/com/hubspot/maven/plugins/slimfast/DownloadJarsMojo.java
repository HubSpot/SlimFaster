package com.hubspot.maven.plugins.slimfast;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Paths;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "download", requiresProject = false, threadSafe = true)
public class DownloadJarsMojo extends AbstractMojo {

  @Parameter(
    property = "slimfast.fileDownloader",
    alias = "fileDownloader",
    defaultValue = "com.hubspot.maven.plugins.slimfast.DefaultFileDownloader"
  )
  private String fileDownloaderType;

  @Parameter(
    property = "slimfast.s3.accessKey",
    defaultValue = "${s3.access.key}",
    required = true
  )
  private String s3AccessKey;

  @Parameter(
    property = "slimfast.s3.secretKey",
    defaultValue = "${s3.secret.key}",
    required = true
  )
  private String s3SecretKey;

  @Parameter(property = "slimfast.s3.downloadThreads", defaultValue = "10")
  private int s3DownloadThreads;

  @Parameter(
    property = "slimfast.cacheDirectory",
    defaultValue = "${settings.localRepository}"
  )
  private String cacheDirectory;

  @Parameter(property = "slimfast.outputDirectory", defaultValue = "${basedir}")
  private String outputDirectory;

  @Parameter(property = "slimfast.inputFile", defaultValue = "target/slimfast.json")
  private String inputFile;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    S3ArtifactWrapper wrapper = readArtifactInfo();

    final DownloadConfiguration configuration = buildConfiguration(wrapper.getPrefix());
    FileHelper.ensureDirectoryExists(configuration.getCacheDirectory());

    try (FileDownloader downloader = instantiateFileDownloader()) {
      downloader.init(configuration);
      downloader.download(wrapper.getArtifacts());
    } catch (IOException e) {
      throw new MojoExecutionException(e);
    }
  }

  private S3ArtifactWrapper readArtifactInfo() throws MojoFailureException {
    try {
      return JsonHelper.readArtifactsFromJson(new File(inputFile));
    } catch (IOException e) {
      throw new MojoFailureException("Error reading dependencies from file", e);
    }
  }

  private DownloadConfiguration buildConfiguration(String prefix) {
    return new DownloadConfiguration(
      Paths.get(prefix),
      Paths.get(cacheDirectory),
      Paths.get(outputDirectory),
      s3AccessKey,
      s3SecretKey
    );
  }

  private FileDownloader instantiateFileDownloader() throws MojoExecutionException {
    try {
      return (FileDownloader) Class
        .forName(fileDownloaderType)
        .getDeclaredConstructor()
        .newInstance();
    } catch (ClassNotFoundException e) {
      throw new MojoExecutionException(
        "Unable to find file downloader implementation",
        e
      );
    } catch (
      InstantiationException
      | IllegalAccessException
      | NoSuchMethodException
      | InvocationTargetException e
    ) {
      throw new MojoExecutionException("Unable to instantiate file downloader", e);
    } catch (ClassCastException e) {
      throw new MojoExecutionException("Must implement FileDownloader interface", e);
    }
  }
}
