/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.exporter.kudu;

import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.kudu.client.Insert;
import org.apache.kudu.client.KuduClient;
import org.apache.kudu.client.KuduClient.KuduClientBuilder;
import org.apache.kudu.client.KuduException;
import org.apache.kudu.client.KuduSession;
import org.apache.kudu.client.KuduTable;
import org.apache.kudu.client.PartialRow;
import org.apache.kudu.client.SessionConfiguration.FlushMode;
import org.apache.kudu.client.Update;
import org.slf4j.Logger;

public class KuduExportClient {

  public static final String EMPTY = "";
  private static final String WAITING = "WAITING";
  private static final String COMPLETED = "COMPLETED";
  private static final String COMPLETED_W_ERROR = "COMPLETED_W_ERROR";
  private static final String TERMINATED = "TERMINATED";
  private static final String AUTOMATIC = "AUTOMATIC";
  private static final String RUNNING = "RUNNING";
  protected final KuduClient client;
  protected final KuduTable table;
  private final KuduExporterConfiguration configuration;
  private final Logger log;
  private final KuduSession session;
  // this is needed because we don't have VIN as a first class attribute for workflows
  private Map<Long, WorkflowDetails> workflowToVinMap = new HashMap<>();

  public KuduExportClient(final Logger log, KuduExporterConfiguration configuration) {
    this.log = log;
    this.configuration = configuration;
    this.client = createClient(configuration);
    try {
      table = client.openTable(configuration.currentWorkflowsTable);
      log.info("Opened kudu table " + configuration.currentWorkflowsTable);
      session = client.newSession();
      session.setTimeoutMillis(10000);
      session.setFlushInterval(0);
      session.setFlushMode(FlushMode.AUTO_FLUSH_SYNC);
    } catch (KuduException e) {
      throw new RuntimeException(
          "Could not open kudu workflows table " + configuration.currentWorkflowsTable);
    }
  }

  public void close() throws IOException {
    try {
      session.flush();
    } catch (KuduException ke) {
      log.error("Exception when flushing during close of exporter", ke);
    }
    try {
      session.close();
    } catch (KuduException ke) {
      log.error("Exception when closing session during close of exporter", ke);
    }
    try {
      client.close();
    } catch (KuduException ke) {
      log.error("Exception when closing kudu client during close of exporter", ke);
    }
  }

  private void updateRowForInstace(
      final Record<?> record, WorkflowInstanceRecordValue value, String status, String vin) {
    log.info(
        "Received event with intent "
            + record.getIntent()
            + " from instance: "
            + value.getWorkflowInstanceKey()
            + " with status: "
            + status);

    final PartialRow row = table.getSchema().newPartialRow();
    final Update update = table.newUpdate();
    row.addString("bpmnprocessid", value.getBpmnProcessId());
    row.addString("status", status);
    row.addLong("lastupdatets", record.getTimestamp());
    row.addLong("workflowinstanceid", value.getWorkflowInstanceKey());
    row.addString("vin", vin);

    if (status.contains(COMPLETED)) {
      row.addLong("endts", record.getTimestamp());
    }

    update.setRow(row);

    try {
      session.apply(update);
    } catch (KuduException ke) {
      log.error("Exception when applying insert on session", ke);
    }
  }

  public void store(final Record<?> record) {
    if (record.getValue() instanceof WorkflowInstanceRecordValue) {
      final WorkflowInstanceRecordValue value = (WorkflowInstanceRecordValue) record.getValue();
      final WorkflowDetails vinStatus = workflowToVinMap.get(value.getWorkflowInstanceKey());
      final String vin =
          (vinStatus != null && vinStatus.getVin() != null) ? vinStatus.getVin() : "DUMMY_VIN";
      if (value.getBpmnElementType().equals(BpmnElementType.PROCESS)) {
        if (record.getIntent().equals(WorkflowInstanceIntent.ELEMENT_ACTIVATED)) {
          // TODO: Insert into workflows table from here when we have VIN in workflowinstancerecord
          // Till then we update the hashtable and insert later
          final WorkflowDetails vs = new WorkflowDetails();
          vs.setStartTs(record.getTimestamp());
          workflowToVinMap.put(value.getWorkflowInstanceKey(), vs);
          log.info(
              "Created workflow instance entry in map for workflow instance key "
                  + value.getWorkflowInstanceKey());
        } else if (record.getIntent().equals(WorkflowInstanceIntent.ELEMENT_COMPLETED)) {
          final String status = vinStatus.isError() ? COMPLETED_W_ERROR : COMPLETED;
          updateRowForInstace(record, value, status, vin);
        } else if (record.getIntent().equals(WorkflowInstanceIntent.ELEMENT_TERMINATED)) {
          updateRowForInstace(record, value, TERMINATED, vin);
        }
      } else if (value.getBpmnElementType().equals(BpmnElementType.RECEIVE_TASK)) {
        if (record.getIntent().equals(WorkflowInstanceIntent.ELEMENT_ACTIVATED)) {
          updateRowForInstace(record, value, WAITING, vin);
        } else if (record.getIntent().equals(WorkflowInstanceIntent.ELEMENT_COMPLETED)) {
          updateRowForInstace(record, value, RUNNING, vin);
        }
      }
    } else if (record.getValueType() == ValueType.JOB) {
      final JobRecordValue value = (JobRecordValue) record.getValue();
      WorkflowDetails wfDetails = workflowToVinMap.get(value.getWorkflowInstanceKey());
      long startTs = record.getTimestamp();
      boolean createdWorkflowRecordAlready = false;
      if (wfDetails != null) {
        createdWorkflowRecordAlready = wfDetails.getVin() != null;
        if (wfDetails.getStartTs() != 0) {
          startTs = wfDetails.getStartTs();
        }
      }
      // create workflow instance record if we have vin
      if (!createdWorkflowRecordAlready
          && (record.getIntent() == JobIntent.ACTIVATED)
          && value.getVariables().containsKey("vin")) {
        // now that we have the vin, let's create the workflow instance in workflows table
        final PartialRow row = table.getSchema().newPartialRow();
        final Insert insert = table.newInsert();
        row.addString("bpmnprocessid", value.getBpmnProcessId());
        row.addInt("bizversion", value.getWorkflowDefinitionVersion());
        row.addLong("techversion", value.getWorkflowKey());
        row.addString("triggertype", AUTOMATIC);
        row.addString("status", RUNNING);
        row.addLong("startts", startTs);
        row.addLong("lastupdatets", record.getTimestamp());
        row.addLong("workflowinstanceid", value.getWorkflowInstanceKey());
        row.addString("vin", (String) value.getVariables().get("vin"));
        if (value.getVariables().containsKey("serviceNames")) {
          row.addString("workflowentity", (String) value.getVariables().get("serviceNames"));
        }
        if (wfDetails == null) {
          wfDetails = new WorkflowDetails();
          workflowToVinMap.put(value.getWorkflowInstanceKey(), wfDetails);
        }
        wfDetails.setVin((String) value.getVariables().get("vin"));
        insert.setRow(row);
        try {
          session.apply(insert);
        } catch (KuduException ke) {
          log.error("Exception when applying insert on session", ke);
        }
      }
      // update error state if we are handling an error element
      if (value.getElementId().contains("Error")) {
        if (wfDetails == null) {
          wfDetails = new WorkflowDetails();
          workflowToVinMap.put(value.getWorkflowInstanceKey(), wfDetails);
        }
        wfDetails.setErrorPath(value.getElementId());
        if ((value.getErrorCode() != null) && !value.getErrorCode().isEmpty()) {
          wfDetails.setErrorCode(value.getErrorCode());
          wfDetails.setErrorMessage(value.getErrorMessage());
        }
      }
      // update error state if we have error info
      if ((value.getErrorCode() != null) && !value.getErrorCode().isEmpty()) {
        if (wfDetails == null) {
          wfDetails = new WorkflowDetails();
          workflowToVinMap.put(value.getWorkflowInstanceKey(), wfDetails);
        }
        wfDetails.setErrorCode(value.getErrorCode());
        wfDetails.setErrorMessage(value.getErrorMessage());
      }
    }
  }

  private KuduClient createClient(KuduExporterConfiguration configuration) {
    final KuduClient client = new KuduClientBuilder(configuration.url).build();
    log.info("Created kudu client for endpoint " + configuration.url);
    return client;
  }

  private static final class WorkflowDetails {
    private String vin;
    private long startTs;
    private String triggerType;
    private String errorCode;
    private String errorPath;
    private String errorMessage;
    private String errorCategory;

    public long getStartTs() {
      return startTs;
    }

    public void setStartTs(long startTs) {
      this.startTs = startTs;
    }

    public boolean isError() {
      return errorCode != null
          || errorPath != null
          || errorMessage != null
          || errorCategory != null;
    }

    public String getVin() {
      return vin;
    }

    public void setVin(String vin) {
      this.vin = vin;
    }

    public String getErrorCode() {
      return errorCode;
    }

    public void setErrorCode(String errorCode) {
      this.errorCode = errorCode;
    }

    public String getErrorPath() {
      return errorPath;
    }

    public void setErrorPath(String errorPath) {
      this.errorPath = errorPath;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
    }

    public String getErrorCategory() {
      return errorCategory;
    }

    public void setErrorCategory(String errorCategory) {
      this.errorCategory = errorCategory;
    }
  }
}
