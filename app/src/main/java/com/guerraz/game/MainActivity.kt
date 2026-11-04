package com.guerraz.game

import android.os.Bundle
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Forest = Color(0xFF287A43)
private val Background = Color(0xFF17231C)
private val Panel = Color(0xFF26372D)
private val Gold = Color(0xFFE2B95B)

data class Province(val name: String, val buildings: Map<String, Int> = emptyMap(), val infantry: Int = 0, val tanks: Int = 0)
data class Order(val province: String, val item: String, val finishAt: Long, val kind: String)
data class GameState(
    val money: Int = 1000, val metal: Int = 500, val fuel: Int = 300, val food: Int = 500,
    val provinces: List<Province>, val selected: String = "Ciudad Autónoma de Buenos Aires",
    val orders: List<Order> = emptyList(), val researched: Set<String> = emptySet(),
    val message: String = "Selecciona una provincia y comienza a desarrollar tu nación.",
    val lastProduction: Long = System.currentTimeMillis()
)

private object GameStore {
    private const val PREFS = "guerra_z_save"
    private const val KEY_STATE = "game_state"

    fun save(context: Context, game: GameState) {
        val root = JSONObject()
        root.put("money", game.money)
        root.put("metal", game.metal)
        root.put("fuel", game.fuel)
        root.put("food", game.food)
        root.put("selected", game.selected)
        root.put("message", game.message)
        root.put("lastProduction", game.lastProduction)
        val provinces = JSONArray()
        game.provinces.forEach { p ->
            val item = JSONObject()
            item.put("name", p.name)
            item.put("infantry", p.infantry)
            item.put("tanks", p.tanks)
            val buildings = JSONObject()
            p.buildings.forEach { (name, level) -> buildings.put(name, level) }
            item.put("buildings", buildings)
            provinces.put(item)
        }
        root.put("provinces", provinces)
        val orders = JSONArray()
        game.orders.forEach { order ->
            orders.put(JSONObject().put("province", order.province).put("item", order.item)
                .put("finishAt", order.finishAt).put("kind", order.kind))
        }
        root.put("orders", orders)
        root.put("researched", JSONArray(game.researched.toList()))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_STATE, root.toString()).apply()
    }

    fun load(context: Context): GameState {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STATE, null)
            ?: return newGame()
        return try {
            val root = JSONObject(raw)
            val provinceArray = root.getJSONArray("provinces")
            val provinces = (0 until provinceArray.length()).map { index ->
                val item = provinceArray.getJSONObject(index)
                val buildingsJson = item.optJSONObject("buildings") ?: JSONObject()
                val buildings = buildingsJson.keys().asSequence().associateWith { buildingsJson.optInt(it) }
                Province(item.getString("name"), buildings, item.optInt("infantry"), item.optInt("tanks"))
            }
            val orderArray = root.optJSONArray("orders") ?: JSONArray()
            val orders = (0 until orderArray.length()).map { index ->
                val item = orderArray.getJSONObject(index)
                Order(item.getString("province"), item.getString("item"), item.getLong("finishAt"), item.getString("kind"))
            }
            val techArray = root.optJSONArray("researched") ?: JSONArray()
            val tech = (0 until techArray.length()).map { techArray.getString(it) }.toSet()
            val selected = root.optString("selected", provinces.firstOrNull()?.name ?: names.first())
            GameState(
                money = root.optInt("money", 1000), metal = root.optInt("metal", 500),
                fuel = root.optInt("fuel", 300), food = root.optInt("food", 500),
                provinces = provinces.ifEmpty { newGame().provinces },
                selected = selected, orders = orders, researched = tech,
                message = root.optString("message", "Partida recuperada."),
                lastProduction = root.optLong("lastProduction", System.currentTimeMillis())
            )
        } catch (_: Exception) {
            newGame().copy(message = "No se pudo leer el guardado; se inició una partida nueva.")
        }
    }
}

private val names = listOf(
    "Ciudad Autónoma de Buenos Aires", "Buenos Aires", "Catamarca", "Chaco", "Chubut", "Córdoba",
    "Corrientes", "Entre Ríos", "Formosa", "Jujuy", "La Pampa", "La Rioja", "Mendoza", "Misiones",
    "Neuquén", "Río Negro", "Salta", "San Juan", "San Luis", "Santa Cruz", "Santa Fe",
    "Santiago del Estero", "Tierra del Fuego", "Tucumán"
)
private fun newGame() = GameState(provinces = names.map { n ->
    if (n.startsWith("Ciudad Autónoma")) Province(n, mapOf("Base militar" to 1, "Cuartel" to 1, "Fábrica" to 1), 20)
    else Province(n)
})

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GuerraZ() }
    }
}

@Composable
fun GuerraZ() {
    val context = LocalContext.current
    var game by remember { mutableStateOf(GameStore.load(context)) }
    LaunchedEffect(game) { GameStore.save(context, game) }
    var tab by remember { mutableStateOf("Mapa") }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val now = System.currentTimeMillis()
            clock = now
            val done = game.orders.filter { it.finishAt <= now }
            if (done.isNotEmpty()) {
                var provinces = game.provinces
                var tech = game.researched
                done.forEach { order ->
                    if (order.kind == "research") tech = tech + order.item
                    else {
                        val i = provinces.indexOfFirst { it.name == order.province }
                        if (i >= 0) {
                            val p = provinces[i]
                            val next = when (order.kind) {
                                "building" -> p.copy(buildings = p.buildings + (order.item to ((p.buildings[order.item] ?: 0) + 1)))
                                else -> if (order.item == "Tanque") p.copy(tanks = p.tanks + 1) else p.copy(infantry = p.infantry + 10)
                            }
                            provinces = provinces.toMutableList().also { it[i] = next }
                        }
                    }
                }
                game = game.copy(provinces = provinces, researched = tech, orders = game.orders - done.toSet(),
                    message = "Orden completada: " + done.joinToString { it.item })
            }
            val productionCycles = ((now - game.lastProduction).coerceAtLeast(0L) / 120000L).toInt()
            if (productionCycles > 0) {
                val factories = game.provinces.sumOf { it.buildings["Fábrica"] ?: 0 }
                game = game.copy(money = game.money + 50 * factories * productionCycles,
                    metal = game.metal + 20 * factories * productionCycles,
                    food = game.food + 15 * factories * productionCycles,
                    lastProduction = game.lastProduction + productionCycles * 120000L,
                    message = "Producción recuperada: $productionCycles ciclo(s).")
            }
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Forest, secondary = Gold, background = Background, surface = Panel)) {
        Column(Modifier.fillMaxSize().background(Background).padding(12.dp)) {
            Text("GUERRA Z", color = Gold, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text("Estrategia territorial · Campaña local", color = Color.LightGray, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Resource("💰", game.money); Resource("🔩", game.metal); Resource("⛽", game.fuel); Resource("🌾", game.food)
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Mapa", "Economía", "Ejército", "Tecnología").forEach { label ->
                    Button(onClick = { tab = label }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(3.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (tab == label) Forest else Panel)) {
                        Text(label, fontSize = 10.sp)
                    }
                }
            }
            Text(game.message, color = Gold, fontSize = 11.sp, modifier = Modifier.padding(vertical = 7.dp))
            when (tab) {
                "Mapa" -> MapView(game, clock) { game = it }
                "Economía" -> EconomyView(game)
                "Ejército" -> ArmyView(game, clock) { game = it }
                else -> ResearchView(game) { game = it }
            }
        }
    }
}

@Composable
private fun RowScope.Resource(icon: String, value: Int) {
    Surface(color = Panel, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon); Text(value.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MapView(g: GameState, clock: Long, set: (GameState) -> Unit) {
    val p = g.provinces.firstOrNull { it.name == g.selected } ?: g.provinces.first()
    val shortNames = listOf("CABA", "BUENOS AIRES", "CATAMARCA", "CHACO", "CHUBUT", "CÓRDOBA",
        "CORRIENTES", "ENTRE RÍOS", "FORMOSA", "JUJUY", "LA PAMPA", "LA RIOJA", "MENDOZA",
        "MISIONES", "NEUQUÉN", "RÍO NEGRO", "SALTA", "SAN JUAN", "SAN LUIS", "SANTA CRUZ",
        "SANTA FE", "S. DEL ESTERO", "T. DEL FUEGO", "TUCUMÁN")
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TEATRO DE OPERACIONES", color = Gold, fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text("ARGENTINA · MAPA ESTRATÉGICO", color = Color.LightGray, fontSize = 10.sp)
            }
            Text("● CONTROL PROPIO", color = Color(0xFF80C98B), fontSize = 9.sp)
        }
        Canvas(Modifier.fillMaxWidth().weight(1f).pointerInput(g.provinces, g.selected) {
            detectTapGestures { tap ->
                val cellW = size.width / 4f
                val cellH = size.height / 6f
                val col = (tap.x / cellW).toInt().coerceIn(0, 3)
                val row = (tap.y / cellH).toInt().coerceIn(0, 5)
                val index = (row * 4 + col).coerceIn(0, g.provinces.lastIndex)
                set(g.copy(selected = g.provinces[index].name))
            }
        }) {
            val cellW = size.width / 4f
            val cellH = size.height / 6f
            val native = drawContext.canvas.nativeCanvas
            val labelPaint = AndroidPaint().apply {
                color = android.graphics.Color.WHITE
                textAlign = AndroidPaint.Align.CENTER
                textSize = (size.width * 0.025f).coerceAtLeast(9f)
                isFakeBoldText = true
                isAntiAlias = true
            }
            val unitPaint = AndroidPaint().apply {
                color = android.graphics.Color.rgb(244, 220, 151)
                textAlign = AndroidPaint.Align.CENTER
                textSize = (size.width * 0.022f).coerceAtLeast(8f)
                isAntiAlias = true
            }
            g.provinces.forEachIndexed { index, province ->
                val col = index % 4
                val row = index / 4
                val left = col * cellW
                val top = row * cellH
                val inset = cellW * 0.035f
                val wobble = ((index * 17) % 9) * cellW * 0.004f
                val points = listOf(
                    Offset(left + inset + wobble, top + inset),
                    Offset(left + cellW - inset, top + inset + wobble),
                    Offset(left + cellW - inset - wobble, top + cellH * 0.48f),
                    Offset(left + cellW - inset * 1.4f, top + cellH - inset),
                    Offset(left + cellW * 0.48f, top + cellH - inset * 0.6f),
                    Offset(left + inset, top + cellH * 0.72f)
                )
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                val selected = province.name == g.selected
                val owned = province.buildings.isNotEmpty() || province.infantry > 0 || province.tanks > 0
                drawPath(path, if (selected) Color(0xFFC99F45) else if (owned) Color(0xFF386B48) else Color(0xFF526052))
                drawPath(path, Color(0xFFB8C3A9), style = Stroke(width = 1.6f))
                val cx = left + cellW / 2f
                val cy = top + cellH * 0.43f
                native.drawText(shortNames.getOrElse(index) { province.name.uppercase() }, cx, cy, labelPaint)
                if (province.infantry + province.tanks > 0) {
                    native.drawText("SOLD " + province.infantry + "  TANQ " + province.tanks, cx, cy + cellH * 0.19f, unitPaint)
                } else {
                    native.drawText("—", cx, cy + cellH * 0.19f, unitPaint)
                }
            }
        }
        Surface(color = Color(0xFF293B30), shape = RoundedCornerShape(10.dp)) {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name.uppercase(), color = Gold, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("PROVINCIA", color = Color.LightGray, fontSize = 9.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("INF. " + p.infantry, color = Color.White, fontSize = 11.sp)
                    Text("TANQUES " + p.tanks, color = Color.White, fontSize = 11.sp)
                    Text("EDIFICIOS " + p.buildings.values.sum(), color = Color.White, fontSize = 11.sp)
                }
                Text(if (p.buildings.isEmpty()) "Sin instalaciones" else p.buildings.entries.joinToString("  ·  ") { it.key + " Nv." + it.value },
                    color = Color.LightGray, fontSize = 10.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("Cuartel" to 3, "Fábrica" to 5, "Aeródromo" to 4).forEach { (name, minutes) ->
                        Button(onClick = { buildOrder(g, p, name, minutes * 60000L, set) },
                            enabled = p.buildings[name] == null && g.money >= 100,
                            modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp, vertical = 5.dp)) {
                            Text(name, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
        OrderList(g, clock)
    }
}

private fun buildOrder(g: GameState, p: Province, name: String, duration: Long, set: (GameState) -> Unit) {
    if (g.money < 100) return
    set(g.copy(money = g.money - 100, orders = g.orders + Order(p.name, name, System.currentTimeMillis() + duration, "building"),
        message = name + " en construcción."))
}

@Composable
private fun EconomyView(g: GameState) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("ECONOMÍA NACIONAL", color = Gold, fontWeight = FontWeight.Bold)
        Text("Las fábricas generan recursos cada 2 minutos.", color = Color.White)
        val count = g.provinces.sumOf { it.buildings["Fábrica"] ?: 0 }
        Text("Fábricas: " + count, color = Color.White)
        Text("Por ciclo: dinero +" + (50 * count) + " · metal +" + (20 * count) + " · alimentos +" + (15 * count), color = Color.LightGray)
        Text("Los recursos se acumulan automáticamente mientras la partida está abierta.", color = Color.LightGray, fontSize = 12.sp)
    }
}

@Composable
private fun ArmyView(g: GameState, clock: Long, set: (GameState) -> Unit) {
    val p = g.provinces.first { it.name == g.selected }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("CENTRO DE MANDO · " + p.name, color = Gold, fontWeight = FontWeight.Bold)
        Text("Infantería: " + p.infantry + " · Tanques: " + p.tanks, color = Color.White)
        Button(onClick = { unitOrder(g, p, "Infantería", 60000L, 50, 0, set) }, enabled = g.money >= 50) {
            Text("Reclutar 10 soldados · 1 min · $50")
        }
        Button(onClick = { unitOrder(g, p, "Tanque", 300000L, 200, 100, set) }, enabled = g.money >= 200 && g.metal >= 100) {
            Text("Producir 1 tanque · 5 min · $200 + 100 metal")
        }
        Text("Movimiento de tropas y combate táctico: siguiente fase del motor.", color = Color.LightGray, fontSize = 12.sp)
        OrderList(g, clock)
    }
}
private fun unitOrder(g: GameState, p: Province, name: String, time: Long, cost: Int, metal: Int, set: (GameState) -> Unit) {
    set(g.copy(money = g.money - cost, metal = g.metal - metal,
        orders = g.orders + Order(p.name, name, System.currentTimeMillis() + time, "unit"), message = name + " en producción."))
}

@Composable
private fun ResearchView(g: GameState, set: (GameState) -> Unit) {
    val technologies = listOf("Logística I", "Infantería I", "Blindados I", "Industria I", "Aviación I", "Doctrina defensiva I")
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("CENTRO DE INVESTIGACIÓN", color = Gold, fontWeight = FontWeight.Bold)
        Text("Cada investigación tarda 3 minutos.", color = Color.White, fontSize = 12.sp)
        technologies.forEach { tech ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text((if (tech in g.researched) "✓ " else "🔬 ") + tech, color = Color.White, modifier = Modifier.weight(1f), fontSize = 12.sp)
                Button(onClick = { set(g.copy(money = g.money - 100,
                    orders = g.orders + Order(g.selected, tech, System.currentTimeMillis() + 180000L, "research"),
                    message = "Investigación iniciada: " + tech)) },
                    enabled = tech !in g.researched && g.money >= 100 && g.orders.none { it.item == tech }) { Text("Investigar", fontSize = 10.sp) }
            }
        }
    }
}

@Composable
private fun OrderList(g: GameState, clock: Long) {
    if (g.orders.isNotEmpty()) {
        Text("COLA DE ÓRDENES", color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        g.orders.forEach { order ->
            val seconds = ((order.finishAt - clock).coerceAtLeast(0L) / 1000L)
            Text("⌛ " + order.item + " · " + order.province + " · " + (seconds / 60) + ":" +
                (seconds % 60).toString().padStart(2, '0'), color = Color.White, fontSize = 11.sp)
        }
    }
}
