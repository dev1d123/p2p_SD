# Manual de Usuario - Red P2P

## 1. Que es este proyecto
Este proyecto implementa una red P2P no estructurada en Java + Netty.

Capacidades principales:
- Un peer puede unirse y salir de la red.
- Los peers pueden conectarse manualmente.
- Hay descubrimiento de peers con Ping/Pong y TTL.
- Hay eleccion de lider con algoritmo Bully.

## 2. Requisitos
- Java 8
- Maven

## 3. Compilar
```bash
mvn clean package
```

Esto genera el ejecutable:
- target/p2p.jar

## 4. Ejecutar visor grafico
Abre el monitor primero:
```bash
java -jar target/p2p.jar --monitor
```

Este visor escucha telemetria local UDP y muestra peers activos en tiempo real.
Incluye 2 vistas:
- Peers: tabla con metricas y estado.
- Graph: topologia de conexiones entre peers.

Ademas incluye un panel de control para envio de archivos:
- Source: peer emisor
- Destination: peer destino
- Path + Browse: archivo local a enviar
- Send File: ejecuta el envio

Tambien incluye una pestana Terminal con eventos utiles en vivo:
- inicio/parada de peers
- resultado de sendfile
- archivos recibidos y errores

## 5. Ejecutar un peer
Ejemplo:
```bash
java -DpeerName=Peer1 -jar target/p2p.jar -n Peer1 -b 50670
```

Parametros:
- -n: nombre del peer
- -b: puerto local de escucha
- -c: archivo opcional de configuracion
- --monitor: arranca solo el visor grafico
- --help: ayuda de opciones CLI

Importante:
- Usa nombres unicos para cada peer.
- Cada peer debe usar un puerto distinto.
- -DpeerName se usa para etiquetar logs por peer (si no se pasa, se usa el valor de -n).

## 6. Comandos interactivos
Una vez iniciado un peer, escribe comandos en su terminal:

- help: muestra menu de comandos.
- ping: lista peers descubiertos en la red.
- connect host port: conecta con otro peer.
- disconnect peerName: desconecta del peer indicado.
- election: inicia eleccion de lider.
- sendfile peerName ruta/al/archivo: envia un archivo a un peer conectado directamente.
- leave: sale de la red.

## 7. Flujo recomendado de prueba
Abre una terminal extra para el visor:

Terminal 0:
```bash
java -jar target/p2p.jar --monitor
```

Abre 3 terminales y arranca:

Terminal 1:
```bash
java -DpeerName=Peer1 -jar target/p2p.jar -n Peer1 -b 50670
```

Terminal 2:
```bash
java -DpeerName=Peer2 -jar target/p2p.jar -n Peer2 -b 50671
```

Terminal 3:
```bash
java -DpeerName=Peer3 -jar target/p2p.jar -n Peer3 -b 50672
```

Luego crea conexiones:
- En Peer2: connect 127.0.0.1 50670
- En Peer3: connect 127.0.0.1 50670

Prueba funcionalidades:
- Ejecuta ping en cada peer.
- Ejecuta election y observa quien queda como lider.
- Prueba disconnect PeerX y luego ping.
- Prueba leave en un peer y observa reconvergencia.
- Observa en el visor: peers activos, conexiones directas, lider conocido, estado de eleccion y pings activos.
- Cambia a la pestana Graph para ver el grafo de la red en vivo.

Prueba envio de archivos:
- Flujo por GUI:
	- En el monitor, elige Source=Peer2 y Destination=Peer1
	- Selecciona archivo con Browse
	- Pulsa Send File
	- Verifica resultado en pestana Terminal
- Flujo por comando (alternativo):
	- sendfile Peer1 MANUAL_USUARIO.md
- En Peer1, revisa carpeta recibida:
	- received/Peer1/

## 8. Archivo de configuracion opcional
Puedes pasar un archivo properties con -c.

Ejemplo config.properties:
```properties
minActiveConnections=3
maxReadIdleSeconds=90
keepAlivePingPeriodSeconds=10
pingTimeoutSeconds=5
autoDiscoveryPingFrequency=5
pingTTL=5
leaderElectionTimeoutSeconds=4
leaderRejectionTimeoutSeconds=8
```

## 9. Solucion de problemas
### El peer arranca pero no ve a nadie
- Normal: al inicio solo queda escuchando.
- Debes conectar manualmente con connect host port a al menos un peer existente.

### Error de puerto en uso
- Cambia -b por otro puerto libre.

### El visor no muestra peers
- Verifica que el visor este abierto con --monitor.
- El visor y los peers deben correr en la misma maquina (telemetria local 127.0.0.1).
- Espera 1-2 segundos para recibir heartbeats.

### sendfile falla
- Solo funciona hacia peers conectados directamente.
- Verifica que la ruta del archivo exista desde la carpeta donde ejecutaste el peer.
- Revisa logs del peer emisor y receptor.

### Peer no aparece en ping
- Verifica conectividad entre peers.
- Repite ping luego de unos segundos para permitir propagacion.

### Eleccion no converge bien
- Bully funciona mejor con conectividad amplia.
- Asegura que la red este suficientemente conectada antes de election.

## 10. Notas de comportamiento
- Mensajes en modo fire-and-forget (sin ACK ni retry).
- El lider se decide por comparacion lexicografica del nombre del peer (nombre mayor gana).
- Idle timeout cierra conexiones inactivas automaticamente.
