# Mapa del Repositorio y Arquitectura

## Vista general
Estructura principal:
- src/main/java/com/basrikahveci/p2p
- src/main/java/com/basrikahveci/p2p/peer
- src/main/java/com/basrikahveci/p2p/peer/network
- src/main/java/com/basrikahveci/p2p/peer/network/message
- src/main/java/com/basrikahveci/p2p/peer/service
- src/main/resources

## 1. Capa de arranque y consola
### Main.java
Responsabilidades:
- Parsear argumentos CLI.
- Cargar configuracion opcional de archivo.
- Arrancar PeerRunner.
- Leer comandos desde stdin y despacharlos.

### PeerRunner.java
Responsabilidades:
- Traducir comandos de texto a llamadas de alto nivel:
  - ping
  - leave
  - connect
  - disconnect
  - election

## 2. Capa de dominio del peer
### Config.java
Contiene parametros de operacion y defaults:
- minActiveConnections
- maxReadIdleSeconds
- keepAlivePingPeriodSeconds
- pingTimeoutSeconds
- pingTTL
- autoDiscoveryPingFrequency
- leaderElectionTimeoutSeconds
- leaderRejectionTimeoutSeconds

### PeerHandle.java
Responsabilidades:
- Inicializar server Netty.
- Mantener event loops.
- Programar tareas periodicas:
  - keepAlivePing
  - timeoutPings
- Exponer API para ping, leave, connect, disconnect, election.

### Peer.java
Orquestador central del comportamiento:
- Gestion de conexion abierta/cerrada.
- Recepcion y delegacion de mensajes.
- Integracion con ConnectionService, PingService y LeadershipService.

## 3. Capa de red
### Connection.java
Representa una conexion TCP a un vecino:
- peerName remoto
- direccion remota
- envio de mensajes
- cierre de canal

### PeerChannelInitializer.java
Configura pipeline Netty:
- ObjectDecoder
- ObjectEncoder
- IdleStateHandler
- PeerChannelHandler

### PeerChannelHandler.java
Maneja eventos de canal y mensajes:
- channelActive: crea Connection y envia Handshake.
- channelInactive: notifica cierre de conexion.
- channelRead0: invoca handle() del mensaje.
- userEventTriggered: cierra por inactividad de lectura.

## 4. Protocolo de mensajes
### message/Message.java
Interfaz base serializable:
- void handle(Peer peer, Connection connection)

### message/Handshake.java
Intercambia identidad de peer y lider conocido.

### message/KeepAlive.java
Mantiene viva la conexion entre vecinos.

### message/ping/Ping.java
Inicia/propaga descubrimiento con TTL y hops.

### message/ping/Pong.java
Respuesta a ping con datos de peer y ruta de retorno.

### message/ping/CancelPings.java
Cancela contexto de pings cuando un peer cae/sale.

### message/ping/CancelPongs.java
Elimina pongs de peers ya no disponibles.

### message/leader/Election.java
Notifica inicio de eleccion.

### message/leader/Rejection.java
Rechaza eleccion de un peer mas debil.

### message/leader/AnnounceLeader.java
Propaga lider elegido.

## 5. Servicios internos
### ConnectionService.java
Gestiona mapa de conexiones activas por peerName.

### PingService.java
Gestiona ciclo completo de discovery:
- pings activos
- pongs recibidos
- timeout
- keepalive periodico
- autoconexion a peers descubiertos

### PingContext.java
Estado de una operacion ping en curso:
- ping original
- conexion de retorno
- pongs acumulados
- futures a completar en timeout

### LeadershipService.java
Implementa Bully:
- inicio de eleccion
- rechazo por peers mas fuertes
- timeout de eleccion
- anuncio de lider

## 6. Logging
### src/main/resources/log4j2.xml
- Formato de log incluye peerName.
- Root por defecto en INFO a consola.

## 7. Flujo operativo resumido
1. Peer arranca y hace bind en puerto local.
2. Al conectar peers, se intercambia Handshake.
3. KeepAlive mantiene conexiones activas.
4. Ping/Pong descubre peers a N saltos.
5. Si faltan conexiones minimas, hay intentos de autoconexion.
6. Election usa Bully para definir lider.
7. Leave desconecta y limpia estado de pings/pongs.
