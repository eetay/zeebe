/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.exporter.kudu;

public class KuduExporterConfiguration {
  private static final String KUDU_ENDPOINT =
      "kudu-master-0.kudu-masters.default.svc.cluster.local:7051,kudu-master-1.kudu-masters.default.svc.cluster.local:7051,kudu-master-2.kudu-masters.default.svc.cluster.local:7051";
  private static final String CURRENT_WORKFLOWS_TABLE = "workflows_current";
  public String url = KUDU_ENDPOINT;
  public String currentWorkflowsTable = CURRENT_WORKFLOWS_TABLE;

  public void checkApplyDefaults() {
    if (url == null || url.length() == 0) {
      url = KUDU_ENDPOINT;
    }
    if (currentWorkflowsTable == null || currentWorkflowsTable.length() == 0) {
      currentWorkflowsTable = CURRENT_WORKFLOWS_TABLE;
    }
  }

  @Override
  public String toString() {
    return "KuduExporterConfiguration [url="
        + url
        + ", currentWorkflowsTable="
        + currentWorkflowsTable
        + "]";
  }
}
