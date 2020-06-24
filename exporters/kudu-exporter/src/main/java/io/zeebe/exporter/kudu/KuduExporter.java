/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.exporter.kudu;

import io.zeebe.exporter.api.Exporter;
import io.zeebe.exporter.api.context.Context;
import io.zeebe.exporter.api.context.Controller;
import io.zeebe.protocol.record.Record;
import org.slf4j.Logger;

public class KuduExporter implements Exporter {

  private Logger log;
  private KuduExporterConfiguration configuration;
  private KuduExportClient kuduClient;
  private Controller controller;

  @Override
  public void configure(final Context context) {
    log = context.getLogger();
    configuration = context.getConfiguration().instantiate(KuduExporterConfiguration.class);
    configuration.checkApplyDefaults();
    log.debug("Exporter configured with {}", configuration);
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;
    kuduClient = createKuduClient();
    log.info("Exporter opened");
  }

  @Override
  public void close() {
    try {
      kuduClient.close();
    } catch (final Exception e) {
      log.warn("Failed to close kudu client", e);
    }
    log.info("Exporter closed");
  }

  @Override
  public void export(final Record record) {
    try {
      kuduClient.store(record);
    } catch (Exception e) {
      log.error("Exception when persisting record using kudu client", e);
    }
    // TODO: Move this into the above try catch
    // And actually do this for every flushed batch of Kudu
    // For now, we do per record flush and we ignore if we cannot
    // process a record
    controller.updateLastExportedRecordPosition(record.getPosition());
  }

  protected KuduExportClient createKuduClient() {
    return new KuduExportClient(log, configuration);
  }
}
