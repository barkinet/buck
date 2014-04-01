/*
 * Copyright 2012-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.android;

import static com.facebook.buck.rules.BuildableProperties.Kind.ANDROID;
import static com.facebook.buck.rules.BuildableProperties.Kind.LIBRARY;

import com.facebook.buck.java.AnnotationProcessingParams;
import com.facebook.buck.java.DefaultJavaLibrary;
import com.facebook.buck.java.JavacOptions;
import com.facebook.buck.java.JavacVersion;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetPattern;
import com.facebook.buck.rules.BuildRuleBuilderParams;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildRuleType;
import com.facebook.buck.rules.BuildableProperties;
import com.facebook.buck.rules.SourcePath;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

public class AndroidLibrary extends DefaultJavaLibrary {

  private static final BuildableProperties PROPERTIES = new BuildableProperties(ANDROID, LIBRARY);

  /**
   * Manifest to associate with this rule. Ultimately, this will be used with the upcoming manifest
   * generation logic.
   */
  private final Optional<Path> manifestFile;

  @VisibleForTesting
  public AndroidLibrary(
      BuildRuleParams buildRuleParams,
      Set<Path> srcs,
      Set<SourcePath> resources,
      Optional<Path> proguardConfig,
      Set<BuildRule> exportedDeps,
      ImmutableSet<String> additionalClasspathEntries,
      JavacOptions javacOptions,
      Optional<Path> manifestFile) {
    super(buildRuleParams,
        srcs,
        resources,
        proguardConfig,
        exportedDeps,
        additionalClasspathEntries,
        javacOptions);
    this.manifestFile = Preconditions.checkNotNull(manifestFile);
  }

  @Override
  public BuildRuleType getType() {
    return BuildRuleType.ANDROID_LIBRARY;
  }

  @Override
  public BuildableProperties getProperties() {
    return PROPERTIES;
  }

  public Optional<Path> getManifestFile() {
    return manifestFile;
  }

  @Override
  public Collection<Path> getInputsToCompareToOutput() {
    if (manifestFile.isPresent()) {
      return ImmutableList.<Path>builder()
          .addAll(super.getInputsToCompareToOutput())
          .add(manifestFile.get())
          .build();
    } else {
      return super.getInputsToCompareToOutput();
    }
  }

  public static Builder newAndroidLibraryRuleBuilder(BuildRuleBuilderParams params) {
    return newAndroidLibraryRuleBuilder(
      Optional.<Path>absent(),
      Optional.<JavacVersion>absent(),
      params);
  }

  public static Builder newAndroidLibraryRuleBuilder(
      Optional<Path> javac,
      Optional<JavacVersion> javacVersion,
      BuildRuleBuilderParams params) {
    return new Builder(javac, javacVersion, params);
  }

  public static class Builder extends DefaultJavaLibrary.Builder {
    private Optional<Path> manifestFile = Optional.absent();

    private Builder(
        Optional<Path> javac,
        Optional<JavacVersion> javacVersion,
        BuildRuleBuilderParams params) {
      super(javac, javacVersion, params);
    }

    @Override
    public AndroidLibrary build(BuildRuleResolver ruleResolver) {
      // TODO(user): Avoid code duplication by calling super.build() and defining a new
      // constructor in DefaultJavaLibraryRule that takes an instance of itself.
      BuildRuleParams buildRuleParams = createBuildRuleParams(ruleResolver);
      AnnotationProcessingParams processingParams =
          annotationProcessingBuilder.build(ruleResolver);
      javacOptions.setAnnotationProcessingData(processingParams);
      JavacOptions options = javacOptions.build();

      AndroidLibraryGraphEnhancer.Result result =
          new AndroidLibraryGraphEnhancer(buildTarget, buildRuleParams, params, options)
              .createBuildableForAndroidResources(
                  ruleResolver, /* createBuildableIfEmptyDeps */ false);

      Optional<DummyRDotJava> uberRDotJava = result.getOptionalDummyRDotJava();
      ImmutableSet<String> additionalClasspathEntries = uberRDotJava.isPresent()
          ? ImmutableSet.of(uberRDotJava.get().getRDotJavaBinFolder().toString())
          : ImmutableSet.<String>of();

      return new AndroidLibrary(
          result.getBuildRuleParams(),
          srcs,
          resources,
          proguardConfig,
          getBuildTargetsAsBuildRules(ruleResolver, exportedDeps),
          additionalClasspathEntries,
          options,
          manifestFile);
    }

    @Override
    public Builder setBuildTarget(BuildTarget buildTarget) {
      super.setBuildTarget(buildTarget);
      return this;
    }

    @Override
    public Builder addDep(BuildTarget dep) {
      super.addDep(dep);
      return this;
    }

    @Override
    public AndroidLibrary.Builder addSrc(Path src) {
      return (AndroidLibrary.Builder)super.addSrc(src);
    }

    @Override
    public Builder addVisibilityPattern(BuildTargetPattern visibilityPattern) {
      super.addVisibilityPattern(visibilityPattern);
      return this;
    }

    @Override
    public AnnotationProcessingParams.Builder getAnnotationProcessingBuilder() {
      return annotationProcessingBuilder;
    }

    public Builder setManifestFile(Optional<Path> manifestFile) {
      this.manifestFile = Preconditions.checkNotNull(manifestFile);
      return this;
    }

  }
}