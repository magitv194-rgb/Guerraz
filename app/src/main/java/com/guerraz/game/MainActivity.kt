package com.guerraz.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
    var game by remember { mutableStateOf(newGame()) }
    var tab by remember { mutableStateOf("Mapa") }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val now = System.currentTimeMillis()
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
            if (now - game.lastProduction >= 120000L) {
                val factories = game.provinces.sumOf { it.buildings["Fábrica"] ?: 0 }
                game = game.copy(money = game.money + 50 * factories, metal = game.metal + 20 * factories,
                    food = game.food + 15 * factories, lastProduction = now, message = "Producción nacional completada.")
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
                "Mapa" -> MapView(game) { game = it }
                "Economía" -> EconomyView(game)
                "Ejército" -> ArmyView(game) { game = it }
                else -> ResearchView(game) { game = it }
            }
        }
    }
}

@Composable
private fun Resource(icon: String, value: Int) {
    Surface(color = Panel, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon); Text(value.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MapView(g: GameState, set: (GameState) -> Unit) {
    val p = g.provinces.first { it.name == g.selected }
    Column(Modifier.fillMaxSize()) {
        Text("ARGENTINA · 24 PROVINCIAS", color = Color.White, fontWeight = FontWeight.Bold)
        Text("Selecciona un territorio para administrarlo.", color = Color.LightGray, fontSize = 11.sp)
        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(g.provinces) { province ->
                Surface(color = if (province.name == g.selected) Gold else Forest, shape = RoundedCornerShape(7.dp),
                    modifier = Modifier.clickable { set(g.copy(selected = province.name)) }) {
                    Column(Modifier.padding(8.dp)) {
                        Text(province.name, color = if (province.name == g.selected) Background else Color.White,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("🪖 " + province.infantry + "  🛡️ " + province.tanks, color = Color.White, fontSize = 10.sp)
                    }
                }
            }
        }
        Text("📍 " + p.name, color = Gold, fontWeight = FontWeight.Bold)
        Text(if (p.buildings.isEmpty()) "Sin edificios" else p.buildings.entries.joinToString { it.key + " nivel " + it.value },
            color = Color.White, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Cuartel" to 3, "Fábrica" to 5, "Aeródromo" to 4).forEach { (name, minutes) ->
                Button(onClick = { buildOrder(g, p, name, minutes * 60000L, set) },
                    enabled = p.buildings[name] == null && g.money >= 100, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(2.dp)) { Text(name, fontSize = 9.sp) }
            }
        }
        OrderList(g)
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
private fun ArmyView(g: GameState, set: (GameState) -> Unit) {
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
        OrderList(g)
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
private fun OrderList(g: GameState) {
    if (g.orders.isNotEmpty()) {
        Text("COLA DE ÓRDENES", color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        g.orders.forEach { order ->
            val seconds = ((order.finishAt - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L)
            Text("⌛ " + order.item + " · " + order.province + " · " + (seconds / 60) + ":" +
                (seconds % 60).toString().padStart(2, '0'), color = Color.White, fontSize = 11.sp)
        }
    }
}
