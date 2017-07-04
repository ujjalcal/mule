/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.util.store;

import org.mule.runtime.api.store.ObjectStore;

import java.io.Serializable;

public class MuleDefaultObjectStoreFactory implements DefaultObjectStoreFactory {

  @Override
  public ObjectStore<Serializable> createDefaultInMemoryObjectStore() {
    return new PartitionedInMemoryObjectStore<Serializable>();
  }

  @Override
  public ObjectStore<Serializable> createDefaultPersistentObjectStore() {
    return new PartitionedPersistentObjectStore<Serializable>();
  }

  @Override
  public ObjectStore<Serializable> createDefaultUserObjectStore() {
    return new PartitionedPersistentObjectStore<Serializable>();
  }

  @Override
  public ObjectStore<Serializable> createDefaultUserTransientObjectStore() {
    return new PartitionedInMemoryObjectStore<Serializable>();
  }
}