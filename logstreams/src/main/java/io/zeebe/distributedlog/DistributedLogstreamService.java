/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog;

import io.atomix.primitive.operation.Command;
import io.atomix.primitive.operation.Query;

public interface DistributedLogstreamService {

  /* Returns a positive value if the append was successful */
  @Command
  long append(String nodeId, long appendIndex, long commitPosition, byte[] blockBuffer);

  /* Returns the last successful appended index by the primitive. */
  @Query
  long lastAppendIndex();

  /* Return true if operation was successful */
  @Command
  boolean claimLeaderShip(String nodeId, long term);
}
