package com.example.kotlinshoppinglist

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


@Entity(tableName = "shopping_items")
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val quantity: String
)

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items ORDER BY id DESC")
    fun getAllItems(): Flow<List<ShoppingItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ShoppingItem)

    @Update
    suspend fun updateItem(item: ShoppingItem)

    @Delete
    suspend fun deleteItem(item: ShoppingItem)
}

@Database(entities = [ShoppingItem::class], version = 1, exportSchema = false)
abstract class ShoppingDatabase : RoomDatabase() {
    abstract fun dao(): ShoppingDao

    companion object {
        @Volatile
        private var INSTANCE: ShoppingDatabase? = null

        fun getDatabase(context: Context): ShoppingDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    ShoppingDatabase::class.java,
                    "shopping_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

class ShoppingViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = ShoppingDatabase.getDatabase(application).dao()

    val items = dao.getAllItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun addItem(name: String, quantity: String) {
        viewModelScope.launch { dao.insertItem(ShoppingItem(name = name, quantity = quantity)) }
    }

    fun updateItem(item: ShoppingItem) {
        viewModelScope.launch { dao.updateItem(item) }
    }

    fun deleteItem(item: ShoppingItem) {
        viewModelScope.launch { dao.deleteItem(item) }
    }
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShoppingListApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListApp(viewModel: ShoppingViewModel = viewModel()) {
    val items by viewModel.items.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var currentItem by remember { mutableStateOf<ShoppingItem?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Bevásárlólista") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                currentItem = null
                showDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Hozzáadás")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            items(items) { item ->
                ShoppingItemRow(
                    item = item,
                    onEdit = {
                        currentItem = item
                        showDialog = true
                    },
                    onDelete = { viewModel.deleteItem(item) }
                )
            }
        }

        if (showDialog) {
            ShoppingItemDialog(
                item = currentItem,
                onDismiss = { showDialog = false },
                onConfirm = { name, quantity ->
                    if (currentItem == null) {
                        viewModel.addItem(name, quantity)
                    } else {
                        viewModel.updateItem(currentItem!!.copy(name = name, quantity = quantity))
                    }
                    showDialog = false
                }
            )
        }
    }
}

@Composable
fun ShoppingItemRow(item: ShoppingItem, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onEdit() },
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.name, style = MaterialTheme.typography.titleMedium)
                Text(text = "Mennyiség: ${item.quantity}", style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Törlés", tint = Color.Red)
            }
        }
    }
}

@Composable
fun ShoppingItemDialog(item: ShoppingItem?, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var quantity by remember { mutableStateOf(item?.quantity ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Új tétel" else "Módosítás") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Termék neve") })
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text("Mennyiség") })
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name, quantity) }) {
                Text("Mentés")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Mégse") } }
    )
}