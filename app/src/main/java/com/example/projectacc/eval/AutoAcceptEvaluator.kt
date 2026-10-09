package com.example.projectacc.eval

/**
 * Configuración de umbrales para la evaluación de auto-aceptación.
 * Es la representación pura y compartida de los umbrales que cada app
 * (Picap / WhatsApp) lee de [com.example.projectacc.OrderStateManager].
 *
 * @param minGanancia1 umbral de ganancia para la condición 1 (entero).
 * @param minGanancia2 umbral de ganancia para la condición 2 (entero).
 * @param maxKmCond2 km máximos permitidos para la condición 2.
 * @param isKmEnabled indica si la condición 3 (por km) está habilitada.
 * @param maxKm umbral de km de la condición 3; valores >= 5.0 significan
 *        "umbral al máximo" y desactivan el límite de km.
 */
data class AutoAcceptConfig(
    val minGanancia1: Int,
    val minGanancia2: Int,
    val maxKmCond2: Double,
    val isKmEnabled: Boolean,
    val maxKm: Double,
)

/**
 * Resultado de evaluar las condiciones de auto-aceptación.
 *
 * @param accepted true si se cumplió alguna condición.
 * @param condition número de condición cumplida (1, 2 o 3) cuando
 *        [accepted] es true; 0 en caso contrario.
 */
data class AutoAcceptResult(
    val accepted: Boolean,
    val condition: Int = 0,
)

/**
 * Evaluador puro de las condiciones de auto-aceptación, compartido por
 * todas las apps. El orden de evaluación es estricto (cortocircuito de
 * izquierda a derecha):
 *
 * 1. Condición 1: [conditionTypeOk] permite el tipo Y ganancia >= umbral 1.
 * 2. Condición 2: [conditionTypeOk] permite el tipo Y ganancia >= umbral 2
 *    Y km <= km máximo de la condición 2.
 * 3. Condición 3: solo si [AutoAcceptConfig.isKmEnabled] Y [conditionTypeOk]
 *    permite el tipo Y (umbral al máximo [>= 5.0] O km <= umbral).
 *
 * Función pura: sin imports de Android y sin logging — cada call site
 * conserva sus propios strings de log.
 *
 * @param valor ganancia en COP ya parseada.
 * @param km km de recogida (999.0 se usa como centinela de "desconocido").
 * @param config umbrales de la app.
 * @param conditionTypeOk gate por tipo de pedido para la condición n (1..3).
 */
fun evaluateAutoAccept(
    valor: Int,
    km: Double,
    config: AutoAcceptConfig,
    conditionTypeOk: (Int) -> Boolean = { true },
): AutoAcceptResult {
    if (conditionTypeOk(1) && valor >= config.minGanancia1) {
        return AutoAcceptResult(true, 1)
    }
    if (conditionTypeOk(2) && valor >= config.minGanancia2 && km <= config.maxKmCond2) {
        return AutoAcceptResult(true, 2)
    }
    if (config.isKmEnabled && conditionTypeOk(3) && (config.maxKm >= 5.0 || km <= config.maxKm)) {
        return AutoAcceptResult(true, 3)
    }
    return AutoAcceptResult(false, 0)
}
