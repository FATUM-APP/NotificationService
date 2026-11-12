package fatum.notification.configuration.aws

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition
import software.amazon.awssdk.services.dynamodb.model.BillingMode
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement
import software.amazon.awssdk.services.dynamodb.model.KeyType
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveSpecification
import software.amazon.awssdk.services.dynamodb.model.UpdateTimeToLiveRequest
import java.net.URI

/**
 * Credenciales del rol de la tarea ECS, sin llaves fijas.
 *
 * <p>En ECS Fargate, `DefaultCredentialsProvider` toma las credenciales del task role sin que haya
 * que configurar nada más.</p>
 */
@Configuration
class DynamoConfig(
    @Value("\${aws.region:\${AWS_REGION:us-east-1}}") private val region: String,
    @Value("\${fatum.dynamodb.endpoint-override:}") private val endpoint: String
) {

    @Bean
    fun dynamoDbClient(): DynamoDbClient {
        val builder = DynamoDbClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .httpClient(UrlConnectionHttpClient.create())
        if (endpoint.isNotBlank()) builder.endpointOverride(URI.create(endpoint))
        return builder.build()
    }

    @Bean
    fun dynamoDbEnhancedClient(client: DynamoDbClient): DynamoDbEnhancedClient =
        DynamoDbEnhancedClient.builder().dynamoDbClient(client).build()

    /**
     * Crea la tabla y el TTL si no existen.
     *
     * <p>Está apagado por defecto: en producción la tabla se crea con IaC, no desde la aplicación.
     * Sirve para DynamoDB Local y para el primer despliegue manual.</p>
     */
    @Bean
    fun notificationTableInitializer(
        client: DynamoDbClient,
        @Value("\${fatum.dynamodb.create-table:false}") createTable: Boolean,
        @Value("\${fatum.dynamodb.table-name}") tableName: String
    ) = ApplicationRunner {
        if (!createTable) return@ApplicationRunner
        val log = LoggerFactory.getLogger(DynamoConfig::class.java)

        try {
            client.createTable(
                CreateTableRequest.builder()
                    .tableName(tableName)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("userId").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build()
                    )
                    .keySchema(
                        KeySchemaElement.builder().attributeName("userId").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("id").keyType(KeyType.RANGE).build()
                    )
                    .build()
            )
            log.info("Tabla {} creada", tableName)
        } catch (e: ResourceInUseException) {
            log.info("La tabla {} ya existe", tableName)
        } catch (e: SdkException) {
            log.error("No se pudo crear la tabla {}", tableName, e)
        }

        runCatching {
            client.updateTimeToLive(
                UpdateTimeToLiveRequest.builder()
                    .tableName(tableName)
                    .timeToLiveSpecification(
                        TimeToLiveSpecification.builder().attributeName("expiresAt").enabled(true).build()
                    )
                    .build()
            )
        }
    }
}
