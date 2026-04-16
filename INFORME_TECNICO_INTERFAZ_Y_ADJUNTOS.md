# Informe Tecnico - Interfaz Grafica y Envio de Archivos

## 1. Resumen ejecutivo
Se implemento una interfaz grafica para observabilidad y operacion de la red P2P, junto con una mejora funcional para envio de archivos entre peers.

La solucion final incorpora:
- Monitor grafico en tiempo real con vista tabular y vista de grafo.
- Flujo de envio de archivos desde la GUI (sin depender de consola).
- Terminal integrada en la GUI para eventos operativos.
- Conservacion del flujo CLI original para compatibilidad.

## 2. Objetivos de la implementacion
Objetivos funcionales:
- Visualizar peers y estado de red en tiempo real.
- Visualizar topologia de conexiones de forma grafica.
- Permitir enviar archivos desde la interfaz.
- Mostrar retroalimentacion operativa y errores en un panel central.

Objetivos no funcionales:
- Minimizar impacto en la logica P2P existente.
- Mantener compatibilidad con Java 8.
- Mantener arranque y comandos previos para no romper uso existente.

## 3. Arquitectura de alto nivel
La implementacion separa claramente tres canales:

1. Canal de telemetria (Peer -> Monitor):
- Protocolo UDP local para snapshots periodicos de estado.
- Periodicidad de publicacion: 1 segundo por peer.
- Uso principal: tabla de peers y vista de grafo.

2. Canal de control GUI (Monitor -> Peer):
- Protocolo UDP local para enviar comandos de control desde GUI.
- Comando implementado: solicitud de envio de archivo.
- Cada peer abre un puerto de control derivado de su puerto de bind.

3. Canal de eventos (Peer -> Monitor):
- Protocolo UDP local para informar eventos de operacion.
- Uso principal: pestana Terminal en la GUI.

## 4. Componentes agregados y responsabilidades
### 4.1 Interfaz grafica
- NetworkMonitorFrame:
  - Construye la ventana principal.
  - Gestiona tabs Peers, Graph y Terminal.
  - Incluye panel de control de envio de archivos.
  - Consume telemetria y eventos por UDP.

- NetworkMonitorMain:
  - Punto de arranque del monitor Swing.

### 4.2 Protocolo y transporte de telemetria
- PeerRuntimeSnapshot:
  - DTO inmutable con estado de peer para monitoreo.

- PeerTelemetryProtocol:
  - Serializa y deserializa snapshots de telemetria.

- PeerTelemetryPublisher:
  - Emite snapshots a puerto de monitor.

### 4.3 Protocolo de control GUI
- PeerControlProtocol:
  - Define formato de comando enviado por GUI.
  - Define regla de puerto de control por peer.
  - Codifica ruta de archivo de forma segura para transporte.

### 4.4 Protocolo de eventos para terminal GUI
- PeerMonitorEvent:
  - DTO de evento operativo.

- PeerMonitorEventProtocol:
  - Serializa y deserializa eventos.

- PeerMonitorEventPublisher:
  - Publica eventos desde peer hacia monitor.

### 4.5 Envio de archivos entre peers
- FileTransfer:
  - Mensaje P2P serializable para contenido binario de archivo.

- Peer:
  - Implementa sendFile y handleIncomingFile.
  - Persiste archivo recibido en carpeta local por peer.
  - Publica eventos de exito/error para la terminal GUI.

- PeerHandle:
  - Expone envio de archivo para CLI.
  - Inicia listener de control GUI y ejecuta orden SEND_FILE.

- PeerRunner:
  - Mantiene comando CLI sendfile peerName ruta.

## 5. Flujo operativo de envio de archivos desde GUI
Flujo completo implementado:

1. El monitor recibe telemetria y lista peers activos.
2. Usuario selecciona Source, Destination y archivo.
3. Usuario pulsa Send File.
4. GUI envia comando UDP SEND_FILE al puerto de control del Source.
5. El peer Source recibe la orden y ejecuta sendFile hacia Destination (conexion P2P directa requerida).
6. El peer Destination recibe mensaje FileTransfer y guarda archivo en received/PeerDestino.
7. Source y Destination publican eventos operativos.
8. El monitor muestra dichos eventos en la pestana Terminal.

## 6. Diseno de la vista de grafo
La vista Graph usa layout circular determinista:
- Nodos: peers activos.
- Aristas: vecinos directos reportados por snapshot.
- Color diferenciado para lider.
- Redibujado periodico para estado en tiempo real.

Ventajas:
- Lectura rapida de conectividad.
- Identificacion visual de nodos aislados.
- Baja complejidad computacional para tamano de red pequeno/medio.

## 7. Diseno de la terminal integrada
La pestana Terminal consume eventos de peers y muestra:
- Timestamp.
- Nivel (INFO/ERROR/WARN).
- Peer origen.
- Mensaje descriptivo.

Se aplico truncado de buffer para evitar crecimiento indefinido en sesiones largas.

## 8. Compatibilidad y coexistencia con CLI
La implementacion mantiene compatibilidad completa:
- Sigue existiendo modo interactivo por consola.
- Sigue existiendo comando sendfile por texto.
- El monitor agrega capacidades sin eliminar flujos previos.

Esto facilita adopcion incremental y pruebas mixtas GUI/CLI.

## 9. Seguridad y limites actuales
Consideraciones actuales:
- Control y eventos por UDP local sin autenticacion (127.0.0.1).
- Envio de archivo requiere conexion directa entre peers.
- Transferencia en un solo mensaje con bytes en memoria.
- Tamano de archivo efectivo limitado por memoria y por serializacion.
- No hay checksum, ACK de aplicacion ni reintento de transferencia.

Mitigaciones aplicadas:
- Sanitizacion de nombre de archivo al persistir.
- Carpeta de salida segregada por peer receptor.
- Errores reportados en logs y en terminal GUI.

## 10. Pruebas realizadas
Pruebas de compilacion:
- Empaquetado completo con Maven finalizando en BUILD SUCCESS.

Pruebas funcionales:
- Arranque monitor en modo --monitor.
- Aparicion de peers en tabla y grafo al iniciar nodos.
- Envio de archivo por comando CLI.
- Envio de archivo por GUI.
- Persistencia de archivo recibido en carpeta local esperada.
- Visualizacion de eventos de exito/error en Terminal.

## 11. Riesgos tecnicos identificados
- Riesgo de payload grande en transferencias voluminosas.
- Riesgo de split-brain de liderazgo en redes parcialmente conectadas.
- Riesgo de perdida de eventos UDP bajo carga alta.

## 12. Mejoras recomendadas (siguiente iteracion)
1. Transferencia por chunks con control de secuencia y reensamblado.
2. ACK por chunk y reintentos con timeout.
3. Barra de progreso por transferencia en GUI.
4. Historial de transferencias con estado final.
5. Filtro y busqueda en terminal de eventos.
6. Cifrado y autenticacion para canal de control.
7. Persistencia opcional de eventos a archivo.

## 13. Impacto final de la mejora
Impacto academico y operativo:
- Incrementa observabilidad de la red.
- Facilita demostraciones y laboratorios.
- Reduce friccion de uso al habilitar acciones desde GUI.
- Mantiene simplicidad conceptual del sistema P2P original.

## 14. Archivos clave modificados
- src/main/java/com/basrikahveci/p2p/monitor/NetworkMonitorFrame.java
- src/main/java/com/basrikahveci/p2p/monitor/NetworkMonitorMain.java
- src/main/java/com/basrikahveci/p2p/monitor/PeerRuntimeSnapshot.java
- src/main/java/com/basrikahveci/p2p/monitor/PeerTelemetryProtocol.java
- src/main/java/com/basrikahveci/p2p/monitor/PeerTelemetryPublisher.java
- src/main/java/com/basrikahveci/p2p/monitor/PeerControlProtocol.java
- src/main/java/com/basrikahveci/p2p/monitor/PeerMonitorEvent.java
- src/main/java/com/basrikahveci/p2p/monitor/PeerMonitorEventProtocol.java
- src/main/java/com/basrikahveci/p2p/monitor/PeerMonitorEventPublisher.java
- src/main/java/com/basrikahveci/p2p/peer/network/message/file/FileTransfer.java
- src/main/java/com/basrikahveci/p2p/peer/Peer.java
- src/main/java/com/basrikahveci/p2p/peer/PeerHandle.java
- src/main/java/com/basrikahveci/p2p/PeerRunner.java
- MANUAL_USUARIO.md
- README.md
