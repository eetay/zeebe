/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.impl;

import io.atomix.primitive.Synchronous;
import io.zeebe.distributedlog.AsyncDistributedLogstream;
import io.zeebe.distributedlog.DistributedLogstream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class BlockingDistributedLogstream extends Synchronous<AsyncDistributedLogstream>
    implements DistributedLogstream {

  private final DistributedLogstreamProxy distributedLogstreamProxy;
  private final long timeout;

  public BlockingDistributedLogstream(
      DistributedLogstreamProxy distributedLogstreamProxy, long timeout) {
    super(distributedLogstreamProxy);
    this.distributedLogstreamProxy = distributedLogstreamProxy;
    this.timeout = timeout;
  }

  @Override
  public long append(
      String partition, String nodeId, long appendIndex, long commitPosition, byte[] blockBuffer)
      throws InterruptedException, ExecutionException, TimeoutException {
    return distributedLogstreamProxy
        .append(partition, nodeId, appendIndex, commitPosition, blockBuffer)
        .get(timeout, TimeUnit.MILLISECONDS);
  }

  @Override
  public long lastAppendIndex(String partition)
      throws InterruptedException, ExecutionException, TimeoutException {
    return distributedLogstreamProxy.lastAppendIndex(partition).get(timeout, TimeUnit.MILLISECONDS);
  }

  @Override
  public AsyncDistributedLogstream async() {
    return distributedLogstreamProxy;
  }
}
