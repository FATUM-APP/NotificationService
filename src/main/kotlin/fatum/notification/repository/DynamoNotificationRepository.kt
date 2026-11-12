package fatum.notification.repository

import fatum.notification.model.Notification
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.Expression
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest

/**
 * DynamoDB detrás del puerto [NotificationRepository].
 *
 * <p>Las lecturas y las escrituras nuevas van por el cliente *enhanced* (mapea el bean); los
 * cambios puntuales van por el cliente de bajo nivel porque ahí solo se toca un atributo y no se
 * quiere reescribir el ítem entero con valores por defecto.</p>
 */
@Repository
class DynamoNotificationRepository(
    enhancedClient: DynamoDbEnhancedClient,
    private val dynamoDbClient: DynamoDbClient,
    @Value("\${fatum.dynamodb.table-name}") private val tableName: String
) : NotificationRepository {

    private val table: DynamoDbTable<Notification> =
        enhancedClient.table(tableName, TableSchema.fromBean(Notification::class.java))

    override fun save(notification: Notification) {
        table.putItem(notification)
    }

    override fun findPage(
        userId: String,
        limit: Int,
        cursor: String?,
        unreadOnly: Boolean
    ): NotificationPage {
        val builder = QueryEnhancedRequest.builder()
            .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(userId).build()))
            .scanIndexForward(false)
            // Se pide uno de más para saber si queda otra página.
            .limit(limit + 1)

        if (unreadOnly) {
            builder.filterExpression(unreadFilter())
        }

        if (!cursor.isNullOrBlank()) {
            builder.exclusiveStartKey(key(userId, cursor))
        }

        val items = table.query(builder.build()).items().toList()
        val page = if (items.size > limit) items.subList(0, limit) else items
        return NotificationPage(page, if (items.size > limit) page.last().id else null)
    }

    override fun countUnread(userId: String): Long {
        var total = 0L
        table.query(
            QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(userId).build()))
                .filterExpression(unreadFilter())
                .build()
        ).forEach { page -> total += page.items().size }
        return total
    }

    override fun markRead(userId: String, id: String): Boolean = try {
        dynamoDbClient.updateItem(
            UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key(userId, id))
                .updateExpression("SET #read = :true")
                .conditionExpression("attribute_exists(userId)")
                .expressionAttributeNames(mapOf("#read" to "read"))
                .expressionAttributeValues(mapOf(":true" to AttributeValue.fromBool(true)))
                .build()
        )
        true
    } catch (e: ConditionalCheckFailedException) {
        false
    }

    override fun markAllRead(userId: String): Int {
        val unread = table.query(
            QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(userId).build()))
                .filterExpression(unreadFilter())
                .build()
        ).items().toList()

        unread.forEach { markRead(userId, it.id) }
        return unread.size
    }

    override fun delete(userId: String, id: String): Boolean = try {
        dynamoDbClient.deleteItem(
            DeleteItemRequest.builder()
                .tableName(tableName)
                .key(key(userId, id))
                .conditionExpression("attribute_exists(userId)")
                .build()
        )
        true
    } catch (e: ConditionalCheckFailedException) {
        false
    }

    private fun unreadFilter(): Expression = Expression.builder()
        .expression("attribute_not_exists(#read) OR #read = :false")
        .expressionNames(mapOf("#read" to "read"))
        .expressionValues(mapOf(":false" to AttributeValue.fromBool(false)))
        .build()

    private fun key(userId: String, id: String): Map<String, AttributeValue> =
        mapOf(
            "userId" to AttributeValue.fromS(userId),
            "id" to AttributeValue.fromS(id)
        )
}
