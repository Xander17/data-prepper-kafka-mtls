/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.source.s3;

import com.linecorp.armeria.client.retry.Backoff;
import org.opensearch.dataprepper.common.concurrent.BackgroundThreadFactory;
import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.acknowledgements.AcknowledgementSetManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.sqs.SqsClient;
import org.opensearch.dataprepper.plugins.source.sqs.common.SqsBackoff;
import org.opensearch.dataprepper.plugins.source.sqs.common.SqsClientFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SqsService {
    private static final Logger LOG = LoggerFactory.getLogger(SqsService.class);
    static final long SHUTDOWN_TIMEOUT = 30L;
    private final S3SourceConfig s3SourceConfig;
    private final S3Service s3Accessor;
    private final SqsClient sqsClient;
    private final PluginMetrics pluginMetrics;
    private final AcknowledgementSetManager acknowledgementSetManager;
    private final ExecutorService executorService;
    private final List<SqsWorker> sqsWorkers;
    private final Backoff backoff;

    public SqsService(final AcknowledgementSetManager acknowledgementSetManager,
                      final S3SourceConfig s3SourceConfig,
                      final S3Service s3Accessor,
                      final PluginMetrics pluginMetrics,
                      final AwsCredentialsProvider credentialsProvider) {
        this.s3SourceConfig = s3SourceConfig;
        this.s3Accessor = s3Accessor;
        this.pluginMetrics = pluginMetrics;
        this.acknowledgementSetManager = acknowledgementSetManager;
        this.sqsClient = SqsClientFactory.createSqsClient(s3SourceConfig.getAwsAuthenticationOptions().getAwsRegion(), credentialsProvider);
        executorService = Executors.newFixedThreadPool(s3SourceConfig.getNumWorkers(), BackgroundThreadFactory.defaultExecutorThreadFactory("s3-source-sqs"));
        backoff = SqsBackoff.createExponentialBackoff();
        sqsWorkers = IntStream.range(0, s3SourceConfig.getNumWorkers())
                .mapToObj(i -> new SqsWorker(acknowledgementSetManager, sqsClient, s3Accessor, s3SourceConfig, pluginMetrics, backoff))
                .collect(Collectors.toList());
    }

    public void start() {
        sqsWorkers.forEach(executorService::submit);
    }

    public void stop() {
        executorService.shutdown();
        sqsWorkers.forEach(SqsWorker::stop);
        try {
            if (!executorService.awaitTermination(SHUTDOWN_TIMEOUT, TimeUnit.SECONDS)) {
                LOG.warn("Failed to terminate SqsWorkers");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            if (e.getCause() instanceof InterruptedException) {
                LOG.error("Interrupted during shutdown, exiting uncleanly...", e);
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        sqsClient.close();
    }
}
