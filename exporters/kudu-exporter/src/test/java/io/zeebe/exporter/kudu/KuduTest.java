/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.exporter.kudu;

import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RecordValue;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KuduTest {
  private static final Logger LOG = LoggerFactory.getLogger(KuduTest.class);

  public static void main(String[] args) {
    final KuduExporterConfiguration config = new KuduExporterConfiguration();
    config.url = "localhost:7051";
    final KuduExportClient client = new KuduExportClient(LOG, config);
    final WorkflowInstanceRecordValue wfirv =
        new WorkflowInstanceRecordTest(
            "service-activation", 1, 12345, 34335453, BpmnElementType.PROCESS);
    client.store(
        new TestRecord(
            wfirv,
            RecordType.EVENT,
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            System.currentTimeMillis(),
            0));
    final Map<String, Object> variables = new HashMap<>();
    final JobRecordValueTest jrv1 =
        new JobRecordValueTest(
            "service-activation", 1, "job-1", null, null, 12345, 34335453, variables);
    client.store(
        new TestRecord(
            jrv1,
            RecordType.EVENT,
            ValueType.JOB,
            JobIntent.ACTIVATED,
            System.currentTimeMillis(),
            0));
    final JobRecordValueTest jrv2 =
        new JobRecordValueTest(
            "service-activation", 1, "job-1", null, null, 12345, 34335453, variables);
    client.store(
        new TestRecord(
            jrv2,
            RecordType.EVENT,
            ValueType.JOB,
            JobIntent.COMPLETED,
            System.currentTimeMillis(),
            0));
    variables.put("vin", "VF123");
    final JobRecordValueTest jrv3 =
        new JobRecordValueTest(
            "service-activation", 1, "job-Error-2", null, null, 12345, 34335453, variables);
    client.store(
        new TestRecord(
            jrv1,
            RecordType.EVENT,
            ValueType.JOB,
            JobIntent.ACTIVATED,
            System.currentTimeMillis(),
            0));
    final WorkflowInstanceRecordValue wfirvc =
        new WorkflowInstanceRecordTest(
            "service-activation", 1, 12345, 34335453, BpmnElementType.PROCESS);
    client.store(
        new TestRecord(
            wfirv,
            RecordType.EVENT,
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.ELEMENT_COMPLETED,
            System.currentTimeMillis(),
            0));
  }

  public static class TestRecord<T extends RecordValue> implements Record<T> {

    private final long position;
    private T value;
    private RecordType rt;
    private ValueType vt;
    private Intent i;
    private long t;

    public TestRecord(
        T value, RecordType rt, ValueType vt, Intent i, long timestamp, long position) {
      this.value = value;
      this.rt = rt;
      this.vt = vt;
      this.i = i;
      this.position = position;
      this.t = timestamp;
    }

    @Override
    public long getPosition() {
      return position;
    }

    @Override
    public long getSourceRecordPosition() {
      return 0;
    }

    @Override
    public long getKey() {
      return 0;
    }

    @Override
    public long getTimestamp() {
      return t;
    }

    @Override
    public Intent getIntent() {
      return i;
    }

    @Override
    public int getPartitionId() {
      return 0;
    }

    @Override
    public RecordType getRecordType() {
      return rt;
    }

    @Override
    public RejectionType getRejectionType() {
      return null;
    }

    @Override
    public String getRejectionReason() {
      return null;
    }

    @Override
    public ValueType getValueType() {
      return vt;
    }

    @Override
    public T getValue() {
      return value;
    }

    @Override
    public String toJson() {
      return null;
    }

    @Override
    public Record<T> clone() {
      return this;
    }
  }

  public static class WorkflowInstanceRecordTest implements WorkflowInstanceRecordValue {

    private String bpmnProcessId;
    private int version;
    private long workflowKey;
    private long workflowInstanceKey;
    private BpmnElementType elementType;

    public WorkflowInstanceRecordTest(
        String bpmnProcessId,
        int version,
        long workflowKey,
        long workflowInstanceKey,
        BpmnElementType elementType) {
      this.bpmnProcessId = bpmnProcessId;
      this.version = version;
      this.workflowKey = workflowKey;
      this.workflowInstanceKey = workflowInstanceKey;
      this.elementType = elementType;
    }

    @Override
    public String toJson() {
      return null;
    }

    @Override
    public String getBpmnProcessId() {
      return bpmnProcessId;
    }

    @Override
    public int getVersion() {
      return version;
    }

    @Override
    public long getWorkflowKey() {
      return workflowKey;
    }

    @Override
    public long getWorkflowInstanceKey() {
      return workflowInstanceKey;
    }

    @Override
    public String getElementId() {
      return null;
    }

    @Override
    public long getFlowScopeKey() {
      return 0;
    }

    @Override
    public BpmnElementType getBpmnElementType() {
      return elementType;
    }

    @Override
    public long getParentWorkflowInstanceKey() {
      // TODO Auto-generated method stub
      return 0;
    }

    @Override
    public long getParentElementInstanceKey() {
      // TODO Auto-generated method stub
      return 0;
    }
  }

  public static class JobRecordValueTest implements JobRecordValue {
    private String bpmnProcessId;
    private int workflowDefinitionVersion;
    private String elementId;
    private String errorCode;
    private String errorMessage;
    private long workflowKey;
    private long workflowInstanceKey;
    private Map<String, Object> variables;

    public JobRecordValueTest(
        String bpmnProcessId,
        int workflowDefinitionVersion,
        String elementId,
        String errorCode,
        String errorMessage,
        long workflowKey,
        long workflowInstanceKey,
        Map<String, Object> variables) {
      super();
      this.bpmnProcessId = bpmnProcessId;
      this.workflowDefinitionVersion = workflowDefinitionVersion;
      this.elementId = elementId;
      this.errorCode = errorCode;
      this.errorMessage = errorMessage;
      this.workflowKey = workflowKey;
      this.workflowInstanceKey = workflowInstanceKey;
      this.variables = variables;
    }

    public String getBpmnProcessId() {
      return bpmnProcessId;
    }

    public int getWorkflowDefinitionVersion() {
      return workflowDefinitionVersion;
    }

    public String getElementId() {
      return elementId;
    }

    public String getErrorCode() {
      return errorCode;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public long getWorkflowKey() {
      return workflowKey;
    }

    public long getWorkflowInstanceKey() {
      return workflowInstanceKey;
    }

    public Map<String, Object> getVariables() {
      return variables;
    }

    @Override
    public String toJson() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String getType() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Map<String, String> getCustomHeaders() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String getWorker() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public int getRetries() {
      // TODO Auto-generated method stub
      return 0;
    }

    @Override
    public long getDeadline() {
      // TODO Auto-generated method stub
      return 0;
    }

    @Override
    public long getElementInstanceKey() {
      // TODO Auto-generated method stub
      return 0;
    }
  }
}
