# Documento de Diseño de Software: STA.RH Biometric

## 01. Modelo de Dominio y Entidades

El modelo de dominio está diseñado utilizando anotaciones de **JPA (Java Persistence API)** y enriquecido con anotaciones de vista de **OpenXava** (como `@View`, `@Tab`, `@DefaultValueCalculator`). 

La estructura separa estrictamente el "Core Business" (Modelo) de los objetos auxiliares (catálogos y diccionarios) y los componentes embebidos que componen entidades más complejas.

### 1. Paquete `com.sta.biometric.modelo` (Entidades Principales)

Estas clases representan las tablas principales donde ocurren las transacciones del sistema.

#### `Personal.java` (`@Entity`)
Es la entidad central del módulo de Recursos Humanos, representando a un empleado.
- **Responsabilidad:** Almacenar datos personales, información laboral (sucursal, puesto, contrato, antigüedad), credenciales de acceso (usuario, contraseña, `deviceId`), y referenciar su historial (licencias, liquidaciones, notas de desempeño).
- **Atributos Clave:**
  - `nombreCompleto`, `dni` (`@OneToOne`), `cuil`, `foto`.
  - `userId`: String único autogenerado.
  - `deviceId`: String único generado desde el móvil para autenticación.
  - `activo`, `eliminado`: Estados booleanos (soft delete).
  - `aceptaPausa`: Boolean, define si registran descansos.
- **Relaciones Clave:**
  - `@ManyToOne` con `Sucursales` y `Nacionalidades`.
  - `@OneToMany` con `Licencia` y `AuditoriaRegistros` (vía persistencia o cálculos en el dashboard).

#### `AuditoriaRegistros.java` (`@Entity`)
Representa la liquidación diaria o jornada laboral de un empleado en una fecha específica.
- **Responsabilidad:** Centralizar las fichadas de un día (`ColeccionRegistros`), persistir configuración de la jornada (turno planificado), y calcular automáticamente horas y estado (Presente, Tarde, Ausente, etc.).
- **Atributos Clave:**
  - `fecha`: LocalDate de la jornada.
  - `empleado`: Relación al Personal.
  - Snapshot monetario: `valorHoraSnapshot`, `montoTeoricoTurno`.
  - Snapshot de turno: `nombreTurno`, `horaEsperadaEntrada`.
  - Tiempos: `minutosTrabajados`, `minutosExtras`.
  - `evaluacion`: Enum (`EvaluacionJornada`).
  - Tolera y guarda información sobre ajustes y redondeos (`ajusteMinutosNormales`, `ajusteRedondeoExtras`).
- **Lógica asociada:** Incorpora métodos como `consolidarDesdeRegistros()` para evaluar faltantes, nocturnidad y feriados.

#### `LiquidacionJornadas.java` (`@Entity`)
Consolida un rango de fechas de un empleado sumando las transacciones de `AuditoriaRegistros`.
- **Responsabilidad:** Agrupar todos los valores monetarios y tiempos de un período (`estadoPeriodo` Abierto o Cerrado).
- **Atributos Clave:**
  - `periodoDesde`, `periodoHasta`.
  - Totales: `totalMinutosNormales`, `totalMinutosExtras`, `totalMinutosEspeciales`.
  - Snapshot monetario liquidado: `valorHoraSnapshot`, `montoGranTotal`.

#### `ColeccionRegistros.java` (`@Entity`)
- Entidad "Línea de Movimiento". Representa una fichada individual referenciando a `AuditoriaRegistros`. Ej: `ENTRADA`, `SALIDA`, `PAUSA_INICIO`.

#### `ContratoLaboral.java` (`@Entity`), `NotaDesempeno.java` (`@Entity`) 
- Modelan historial y relación laboral contractual o disciplinaria con el personal, referenciando como `@ManyToOne` al `Personal`.

### 2. Paquete `com.sta.biometric.auxiliares` (Catálogos y Transacciones de Apoyo)

Entidades configurables que sirven de soporte a las transacciones diarias y parámetros globales.

- **`TurnosHorarios.java` (`@Entity`)**: Define horarios por día de la semana, márgenes de tolerancia de fichadas y modificadores salariales (bonificaciones nocturnas).
- **`Licencia.java` (`@Entity`)**: Registro de un permiso de ausencia. Relacionada con `Personal`. Incluye bandera `conGoce`, discriminante para imputar o no minutos de trabajo a una liquidación.
- **`Feriados.java` (`@Entity`)**: Persiste fechas no laborables en el calendario anual a validar dentro del cálculo de jornada.
- **`ImportadorFichadas.java`**: Entidad virtual o transitoria para mapear la importación masiva de marcaciones (fichadas offline / importaciones USB).
- **Catálogos Geográficos**: `Localidades`, `Provincias`, `Partidos`, `Nacionalidades`.
- **`Sucursales.java` (`@Entity`)**: Dependencia departamental del `Personal`.

### 3. Paquete `com.sta.biometric.embebidas` (`@Embeddable`)

Clases incrustadas directamente dentro de otras entidades grandes para modularizar la vista sin crear tablas separadas.

- **`Direccion.java`**: Calle, número y relaciones `@ManyToOne` con Localidades y Provincias. Incluida en `Personal`.
- **`DatosContacto.java`**: Email, teléfono móvil.
- **`JornadaAsignada.java`**: Asociación (`@ManyToOne`) entre `TurnosHorarios` y un día de la semana o modalidad; mapeada adentro de una colección embebida en `Personal`.

### Consideraciones de JPA y Base de Datos (DDL)

- El DDL subyacente define automáticamente tablas para todas las clases marcadas con `@Entity`. Por ej: tabla `Personal`, `AuditoriaRegistros`, `liquidacion_jornadas`.
- Índices de base de datos definidos en las anotaciones JPA (ej. en `AuditoriaRegistros`: idx_auditoria_fecha para mejorar la lectura de liquidaciones).
- Relaciones unidireccionales y bidireccionales utilizan ampliamente el patrón transaccional diferido (`fetch = FetchType.LAZY`).

---

**Siguiente Documento Sugerido:** `02_Logica_de_Negocio_Servicios.md` (Analiza el flujo y cálculo del tiempo, feriados, jobs).
