# ControlInv

Aplicación Android (Jetpack Compose) para control de inventario, autenticación y gestión de pedidos.

## Estructura del proyecto

```text
app/src/main/java/com/example/controlinv/
├── auth/                 # Login y cliente Supabase
├── empleado/             # Flujo de pedidos de empleado y administración de pedidos
├── inventario/           # Pantallas, logs y ViewModel de inventario
│   └── model/            # Modelos de dominio de inventario
├── main/                 # Entry point de la app (MainActivity)
└── ui/theme/             # Tema de Compose
```

## Criterios de orden aplicados

- **Paquetes en minúscula** para seguir convenciones de Kotlin/Android.
- **Modelo `Inventario` separado** en su propio archivo para evitar mezclar UI con dominio.
- **Imports alineados por feature** (`auth`, `empleado`, `inventario`, `main`).

## Siguiente paso recomendado

Si quieres, en una siguiente iteración puedo separar `MainActivity.kt` en múltiples archivos de pantalla (`LoginScreen`, `InventarioScreen`, etc.) para dejar cada flujo en su carpeta y reducir el tamaño del archivo principal.
