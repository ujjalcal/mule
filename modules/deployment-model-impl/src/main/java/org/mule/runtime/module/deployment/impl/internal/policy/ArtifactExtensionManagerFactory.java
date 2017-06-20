/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.module.deployment.impl.internal.policy;

import static java.lang.String.format;
import static org.mule.runtime.api.dsl.DslResolvingContext.getDefault;
import static org.mule.runtime.module.deployment.impl.internal.artifact.ArtifactExtensionManagerConfigurationBuilder.META_INF_FOLDER;
import static org.mule.runtime.module.extension.internal.ExtensionProperties.EXTENSION_MANIFEST_FILE_NAME;
import static org.mule.runtime.module.extension.internal.loader.java.DefaultJavaExtensionModelLoader.TYPE_PROPERTY_NAME;
import static org.mule.runtime.module.extension.internal.loader.java.DefaultJavaExtensionModelLoader.VERSION;
import static org.slf4j.LoggerFactory.getLogger;

import org.mule.runtime.api.deployment.meta.MulePluginModel;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.extension.ExtensionManager;
import org.mule.runtime.deployment.model.api.plugin.ArtifactPlugin;
import org.mule.runtime.deployment.model.api.plugin.LoaderDescriber;
import org.mule.runtime.extension.api.loader.ExtensionModelLoader;
import org.mule.runtime.extension.api.manifest.ExtensionManifest;
import org.mule.runtime.module.extension.internal.loader.ExtensionModelLoaderRepository;
import org.mule.runtime.module.extension.internal.loader.java.DefaultJavaExtensionModelLoader;
import org.mule.runtime.module.extension.internal.manager.ExtensionManagerFactory;
import org.slf4j.Logger;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Creates {@link ExtensionManager} for mule artifacts that own a {@link MuleContext}
 */
public class ArtifactExtensionManagerFactory implements ExtensionManagerFactory {

  private static Logger LOGGER = getLogger(ArtifactExtensionManagerFactory.class);

  private final ExtensionModelLoaderRepository extensionModelLoaderRepository;
  private final List<ArtifactPlugin> artifactPlugins;
  private final ExtensionManagerFactory extensionManagerFactory;

  /**
   * Creates a extensionManager factory
   *
   * @param artifactPlugins artifact plugins deployed inside the artifact. Non null.
   * @param extensionModelLoaderRepository {@link ExtensionModelLoaderRepository} with the available extension loaders. Non null.
   * @param extensionManagerFactory creates the {@link ExtensionManager} for the artifact. Non null
   */
  public ArtifactExtensionManagerFactory(List<ArtifactPlugin> artifactPlugins,
                                         ExtensionModelLoaderRepository extensionModelLoaderRepository,
                                         ExtensionManagerFactory extensionManagerFactory) {
    this.artifactPlugins = artifactPlugins;
    this.extensionModelLoaderRepository = extensionModelLoaderRepository;
    this.extensionManagerFactory = extensionManagerFactory;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ExtensionManager create(MuleContext muleContext) {
    final ExtensionManager extensionManager = extensionManagerFactory.create(muleContext);
    final Set<ExtensionModel> extensions = new HashSet<>();
    artifactPlugins.forEach(artifactPlugin -> {
      Optional<LoaderDescriber> loaderDescriber = artifactPlugin.getDescriptor().getExtensionModelDescriptorProperty();
      ClassLoader artifactClassloader = artifactPlugin.getArtifactClassLoader().getClassLoader();
      String artifactName = artifactPlugin.getArtifactName();
      if (loaderDescriber.isPresent()) {
        discoverExtensionThroughJsonDescriber(loaderDescriber.get(), extensions, artifactClassloader, artifactName);
      } else {
        URL manifest = artifactPlugin.getArtifactClassLoader().findResource(META_INF_FOLDER + "/" + EXTENSION_MANIFEST_FILE_NAME);
        if (manifest != null) {
          //TODO: Remove when MULE-11136
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Discovered extension " + artifactName);
          }
          discoverExtensionThroughManifest(extensionManager, extensions, artifactClassloader, manifest);
        } else {
          LOGGER.warn("Extension [" + artifactName + "] could not being discovered");
        }
      }
    });
    extensions.forEach(extensionManager::registerExtension);
    return extensionManager;
  }

  /**
   * Parses the extension-manifest.xml file, and gets the extension type and version to use the
   * {@link DefaultJavaExtensionModelLoader} to load the extension.
   *
   * @param extensionManager the {@link ExtensionManager} used to parse the manifest.
   * @param extensions with the previously generated {@link ExtensionModel}s that will be used to generate the current {@link ExtensionModel}
   *                   and store it in {@code extensions} once generated.
   * @param artifactClassloader the loaded artifact {@link ClassLoader} to find the required resources.
   * @param manifestUrl the location of the extension-manifest.xml file.
   */
  private void discoverExtensionThroughManifest(ExtensionManager extensionManager, Set<ExtensionModel> extensions,
                                                ClassLoader artifactClassloader, URL manifestUrl) {
    ExtensionManifest extensionManifest = extensionManager.parseExtensionManifestXml(manifestUrl);
    Map<String, Object> params = new HashMap<>();
    params.put(TYPE_PROPERTY_NAME, extensionManifest.getDescriberManifest().getProperties().get("type"));
    params.put(VERSION, extensionManifest.getVersion());
    extensions.add(new DefaultJavaExtensionModelLoader().loadExtensionModel(artifactClassloader, getDefault(extensions), params));
  }

  /**
   * Looks for an extension using the mule-plugin.json file, where if available it will parse it
   * using the {@link ExtensionModelLoader} which {@link ExtensionModelLoader#getId() ID} matches the plugin's
   * descriptor ID.
   *
   * @param loaderDescriber a descriptor that contains parametrization to construct an {@link ExtensionModel}
   * @param extensions with the previously generated {@link ExtensionModel}s that will be used to generate the current {@link ExtensionModel}
   *                   and store it in {@code extensions} once generated.
   * @param artifactClassloader the loaded artifact {@link ClassLoader} to find the required resources.
   * @param artifactName the name of the artifact being loaded.
   * @throws IllegalArgumentException there is no {@link ExtensionModelLoader} for the ID in the {@link MulePluginModel}.
   */
  private void discoverExtensionThroughJsonDescriber(LoaderDescriber loaderDescriber, Set<ExtensionModel> extensions,
                                                     ClassLoader artifactClassloader, String artifactName) {
    ExtensionModelLoader loader = extensionModelLoaderRepository.getExtensionModelLoader(loaderDescriber)
        .orElseThrow(() -> new IllegalArgumentException(format("The identifier '%s' does not match with the describers available "
            + "to generate an ExtensionModel (working with the plugin '%s')",
                                                               loaderDescriber.getId(), artifactName)));
    extensions.add(loader.loadExtensionModel(artifactClassloader, getDefault(extensions), loaderDescriber.getAttributes()));
  }
}
