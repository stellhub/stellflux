package io.github.stellflux.stellflow;

import io.github.stellflux.stellflow.producer.DefaultStellflowProducerOperations;
import io.github.stellflux.stellflow.producer.StellflowProducerInterceptor;
import io.github.stellflux.stellflow.producer.StellflowProducerTopicOptions;
import io.github.stellhub.stellflow.sdk.admin.StellflowAdminClient;
import io.github.stellhub.stellflow.sdk.producer.StellflowProducer;
import java.util.List;
import java.util.function.Function;

/** Stellflow 生产者模板。 */
public class StellflowTemplate extends DefaultStellflowProducerOperations {

    public StellflowTemplate(
            StellflowProducer producer, List<StellflowProducerInterceptor> interceptors) {
        super(producer, interceptors);
    }

    public StellflowTemplate(
            StellflowProducer producer,
            StellflowAdminClient adminClient,
            Function<String, StellflowProducerTopicOptions> topicOptionsResolver,
            List<StellflowProducerInterceptor> interceptors) {
        super(producer, adminClient, topicOptionsResolver, interceptors);
    }
}
