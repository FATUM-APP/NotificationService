package fatum.notification.model.constant

/**
 * Lo que el front necesita para decidir ícono y destino. El texto lo arma el backend.
 */
enum class NotificationType {
    /** Algo pasó con una orden. */
    ORDER,

    /** Algo pasó con la cuenta del usuario. */
    ACCOUNT,

    /** Aviso del sistema. */
    SYSTEM
}
